package com.agy.runtime;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;

import com.agy.install.TarExtractor;
import com.agy.util.AtomicFileWriter;
import com.agy.util.FileModeUtil;
import com.agy.util.IoUtil;
import com.agy.util.Sha256;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transactional installer for the official native Antigravity CLI (B06).
 *
 * Only an explicit user Install/Update calls {@link #install}. A single job is
 * allowed at a time, the device ABI is checked before any network traffic, the
 * manifest and artifact origins are strict, the archive is streamed with a
 * bounded size and SHA-512 verification, extraction happens in a unique cache
 * staging directory, the executable is checked as AArch64 Android ELF, and the
 * new binary replaces the old one only through a same-directory atomic rename.
 * A failed download, verification or publication leaves the previous install
 * untouched.
 *
 * Live update integration while a supervised session owns the binary is the
 * C02 gate: this class only publishes a complete validated replacement and
 * leaves stop/restart orchestration to that task.
 */
public final class CliInstaller {

    private static final String TAG = "CliInstaller";
    public static final String CLI_DIR = "cli";
    public static final String BINARY_NAME = "agy";
    public static final String METADATA_NAME = "install.meta";
    /** Cheap presence check only; full ELF verification happens at install time. */
    private static final long MIN_INSTALLED_BINARY_BYTES = 1024L * 1024;
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final AtomicBoolean JOB = new AtomicBoolean(false);
    private static volatile boolean cancelRequested = false;

    public interface Listener {
        void onProgress(int percent, String message);

        void onSuccess(File binary, CliInstallMetadata metadata);

        void onError(String error);
    }

    private CliInstaller() {
    }

    public static File getCliBinary(Context context) {
        return new File(new File(context.getFilesDir(), CLI_DIR), BINARY_NAME);
    }

    /** Cheap, non-blocking check used by the dashboard and launcher. */
    public static boolean isInstalled(Context context) {
        File bin = getCliBinary(context);
        return bin.isFile() && bin.length() >= MIN_INSTALLED_BINARY_BYTES && bin.canExecute();
    }

    public static boolean isBusy() {
        return JOB.get();
    }

    /** Requests cancellation of the running job; no effect when none is active. */
    public static void cancel() {
        if (JOB.get()) {
            cancelRequested = true;
        }
    }

    /** Reads the last install record, or null when absent/corrupt. */
    public static CliInstallMetadata readMetadata(Context context) {
        File file = new File(new File(context.getFilesDir(), CLI_DIR), METADATA_NAME);
        if (!file.isFile() || file.length() <= 0
                || file.length() > CliInstallMetadata.MAX_BYTES) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > CliInstallMetadata.MAX_BYTES) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return CliInstallMetadata.parse(new String(out.toByteArray(), UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Starts one explicit, user-initiated install. Never called automatically:
     * the caller must have a visible user action. A second call while a job is
     * running reports an error and starts nothing.
     */
    public static void install(Context context, Listener listener) {
        if (context == null || listener == null) {
            throw new IllegalArgumentException("context and listener are required");
        }
        if (!JOB.compareAndSet(false, true)) {
            listener.onError("An Antigravity CLI installation is already running.");
            return;
        }
        cancelRequested = false;
        Thread thread = new Thread(() -> {
            try {
                runInstall(context, listener);
            } catch (SecurityException e) {
                Log.e(TAG, "CLI install rejected: " + e.getMessage());
                listener.onError("Security check failed: " + describe(e));
            } catch (Exception e) {
                Log.e(TAG, "CLI install failed: " + e.getMessage(), e);
                listener.onError(describe(e));
            } finally {
                cancelRequested = false;
                JOB.set(false);
            }
        }, "CliInstallThread");
        thread.start();
    }

    private static void runInstall(Context context, Listener listener) throws Exception {
        Context ctx = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;

        // ABI gate before network: never fetch a generic Linux build.
        if (!CliInstallPolicy.isArm64Device(Build.SUPPORTED_ABIS)) {
            throw new IOException("This app supports 64-bit ARM Android devices only "
                    + "(arm64-v8a). No CLI was downloaded.");
        }

        progress(listener, 2, "Fetching CLI manifest...");
        String manifestText = fetchText(CliInstallPolicy.MANIFEST_URL,
                CliInstallPolicy.MAX_MANIFEST_BYTES);
        JSONObject manifest = new JSONObject(manifestText);
        String version = manifest.optString("version", "");
        String artifactUrl = manifest.optString("url", "");
        String expectedSha = CliInstallPolicy.normalizeSha512(manifest.optString("sha512", ""));
        if (!CliInstallPolicy.isValidVersion(version)) {
            throw new IOException("CLI manifest version is invalid.");
        }
        if (!CliInstallPolicy.isValidArtifactUrl(artifactUrl)) {
            throw new IOException("CLI manifest download URL is not the approved HTTPS origin.");
        }
        if (expectedSha == null) {
            throw new IOException("CLI manifest SHA-512 is invalid.");
        }

        File stage = new File(ctx.getCacheDir(), "cli-install-" + UUID.randomUUID());
        if (!stage.mkdirs() && !stage.isDirectory()) {
            throw new IOException("Cannot create the installer staging directory.");
        }
        try {
            File archive = new File(stage, "cli.tar.gz");
            long archiveBytes = downloadArtifact(ctx, artifactUrl, archive, expectedSha, listener);
            if (cancelRequested) {
                throw new IOException("Installation cancelled.");
            }

            progress(listener, 88, "Extracting and verifying CLI...");
            File extractDir = new File(stage, "extract");
            if (!extractDir.mkdirs() && !extractDir.isDirectory()) {
                throw new IOException("Cannot create the extraction directory.");
            }
            try (InputStream in = new BufferedInputStream(new FileInputStream(archive), 64 * 1024)) {
                TarExtractor.untarGz(in, extractDir);
            }

            File candidate = findBinary(extractDir);
            String elfProblem = CliInstallPolicy.elfProblem(candidate);
            if (elfProblem != null) {
                throw new IOException("Downloaded CLI failed verification: " + elfProblem);
            }
            if (cancelRequested) {
                throw new IOException("Installation cancelled.");
            }

            String binarySha256 = Sha256.of(candidate);
            CliInstallMetadata metadata = new CliInstallMetadata(
                    CliInstallMetadata.SCHEMA, version, expectedSha, artifactUrl,
                    binarySha256, System.currentTimeMillis() / 1000L);

            File cliDir = new File(ctx.getFilesDir(), CLI_DIR);
            if (!cliDir.isDirectory() && !cliDir.mkdirs() && !cliDir.isDirectory()) {
                throw new IOException("Cannot create the CLI directory.");
            }
            File binary = new File(cliDir, BINARY_NAME);
            progress(listener, 94, "Installing Antigravity CLI v" + version + " ("
                    + (archiveBytes / (1024 * 1024)) + " MB download)...");
            publishBinary(candidate, binary);

            if (!AtomicFileWriter.writeText(new File(cliDir, METADATA_NAME), metadata.serialize())) {
                Log.w(TAG, "CLI published but the install record could not be saved");
                progress(listener, 99, "Ready (install record could not be saved)");
            }
            progress(listener, 100, "Ready! Antigravity CLI v" + version);
            listener.onSuccess(binary, metadata);
        } finally {
            deleteTree(stage);
        }
    }

    private static String fetchText(String url, long maxBytes) throws Exception {
        HttpURLConnection conn = open(url, 10000, 15000);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("CLI manifest request refused: HTTP " + code);
            }
            long declared = conn.getContentLength();
            if (declared > maxBytes) {
                throw new IOException("CLI manifest is unexpectedly large.");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            try (InputStream in = conn.getInputStream()) {
                while ((read = in.read(buffer)) != -1) {
                    if (out.size() + read > maxBytes) {
                        throw new IOException("CLI manifest is unexpectedly large.");
                    }
                    out.write(buffer, 0, read);
                }
            }
            return new String(out.toByteArray(), UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static long downloadArtifact(Context ctx, String artifactUrl, File archive,
                                         String expectedSha, Listener listener) throws Exception {
        HttpURLConnection conn = open(artifactUrl, 15000, 30000);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                // Redirects are refused rather than followed off-origin.
                throw new IOException("CLI download refused: HTTP " + code);
            }
            long declared = conn.getContentLength();
            if (!CliInstallPolicy.isPlausibleArchiveSize(declared)) {
                throw new IOException("CLI download size is missing or out of bounds.");
            }
            long available;
            try {
                available = new StatFs(ctx.getFilesDir().getAbsolutePath()).getAvailableBytes();
            } catch (Exception e) {
                available = 0;
            }
            if (!CliInstallPolicy.hasFreeSpace(available, declared)) {
                throw new IOException("Not enough free storage for the CLI download, "
                        + "extraction and install copy (about "
                        + (declared / (1024 * 1024)) + " MB download).");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            long total = 0;
            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 64 * 1024);
                 FileOutputStream out = new FileOutputStream(archive)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelRequested) {
                        throw new IOException("Installation cancelled.");
                    }
                    total += read;
                    if (total > declared || total > CliInstallPolicy.MAX_ARCHIVE_BYTES) {
                        throw new IOException("CLI download exceeded its declared size.");
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    int percent = (int) (5 + Math.min(80, total * 80 / declared));
                    progress(listener, percent, "Downloading Antigravity CLI: "
                            + (total / (1024 * 1024)) + " MB / "
                            + (declared / (1024 * 1024)) + " MB");
                }
                out.flush();
                out.getFD().sync();
            }
            if (total != declared) {
                throw new IOException("CLI download was truncated.");
            }
            if (!hex(digest.digest()).equals(expectedSha)) {
                throw new SecurityException("SHA-512 mismatch; the download was rejected.");
            }
            return total;
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept-Encoding", "identity");
        return conn;
    }

    private static File findBinary(File extractDir) throws IOException {
        File antigravity = new File(extractDir, "antigravity");
        File agy = new File(extractDir, "agy");
        boolean hasAntigravity = antigravity.isFile();
        boolean hasAgy = agy.isFile();
        if (hasAntigravity && hasAgy) {
            throw new IOException("CLI archive contains two unexpected executables.");
        }
        if (hasAntigravity) {
            return antigravity;
        }
        if (hasAgy) {
            return agy;
        }
        throw new IOException("CLI archive did not contain the expected binary.");
    }

    /**
     * Publishes the verified binary through a unique sibling temporary and one
     * atomic rename. The previous executable stays intact until the rename
     * succeeds; there is no delete-then-copy window.
     */
    private static void publishBinary(File candidate, File target) throws Exception {
        File dir = target.getParentFile();
        File temp = new File(dir, "." + target.getName() + ".tmp-" + UUID.randomUUID());
        try {
            try (InputStream in = new BufferedInputStream(new FileInputStream(candidate), 64 * 1024);
                 FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelRequested) {
                        throw new IOException("Installation cancelled.");
                    }
                    out.write(buffer, 0, read);
                }
                out.flush();
                out.getFD().sync();
            }
            FileModeUtil.setExec(temp);
            if (!temp.renameTo(target)) {
                throw new IOException("Could not publish the CLI over the previous installation.");
            }
            temp = null;
        } finally {
            IoUtil.deleteQuietly(temp);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toLowerCase(Locale.US);
    }

    private static void progress(Listener listener, int percent, String message) {
        if (listener != null) {
            listener.onProgress(percent, message);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
