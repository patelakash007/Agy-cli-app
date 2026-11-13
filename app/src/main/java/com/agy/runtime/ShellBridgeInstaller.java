package com.agy.runtime;

import android.util.Log;

import com.agy.install.EnvPaths;
import com.agy.util.Sha256;
import com.agy.util.ShellQuote;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Materializes the bionic bridges in getFilesDir()/runtime/bin. Must run after
 * EnvInstaller.install() (it provides $PREFIX/bin/bash). Idempotent; only tools
 * that actually exist at runtime get a bridge (missing tools are skipped).
 *
 * The external-URL openers (xdg-open/open_helper/sensible-browser) are NOT
 * bridged to the prefix: the core execs them to open the OAuth browser and the
 * native OpenUrlWatcher listens for the "AGY_OPEN_URL=" line they print. A
 * bionic bridge would swallow that line and no browser would open.
 */
public final class ShellBridgeInstaller {

    private static final String TAG = "ShellBridgeInstaller";

    private static final String[] OPEN_URL_SHIMS = {
            "xdg-open", "open", "open_helper", "sensible-browser",
            "x-www-browser", "gnome-open", "google-chrome", "chrome", "chromium",
            "termux-open-url"
    };

    /** Records which prefix the bulk bridges were generated for. Bump when the
     *  bridge script format changes so a warm launch regenerates them once. */
    private static final String BRIDGE_MARKER = ".agy-bridges";
    private static final int BRIDGE_FORMAT = 2;

    private ShellBridgeInstaller() {
    }

    public static synchronized void installFallbackBridges(File bin) {
        installFallbackBridges(bin, null);
    }

    public static synchronized void installFallbackBridges(File bin, android.content.Context context) {
        if (bin == null) return;
        if (!bin.isDirectory() && !bin.mkdirs() && !bin.isDirectory()) {
            Log.w(TAG, "cannot create " + bin + "; skipping fallback bridges");
            return;
        }
        // Always guarantee bash and sh exist immediately
        File bash = new File(bin, "bash");
        if (!bash.isFile() || bash.length() == 0) {
            writeScript(bash, "#!/system/bin/sh\n# AGY fallback shell\nexec /system/bin/sh \"$@\"\n");
        }
        File sh = new File(bin, "sh");
        if (!sh.isFile() || sh.length() == 0) {
            writeScript(sh, "#!/system/bin/sh\n# AGY fallback shell\nexec /system/bin/sh \"$@\"\n");
        }
        // Extract native agy-pty helper if present in assets
        if (context != null) {
            installPtyHelper(bin, context);
        }
    }

    /** CMake builds of the helper are far smaller than this defensive bound. */
    private static final int MAX_HELPER_BYTES = 32 * 1024 * 1024;

    /**
     * Installs the bundled PTY helper by content identity (C08a/R26). Size and
     * the executable bit are not identity: an APK upgrade that changes the
     * helper must deliver the new bytes, an unchanged asset is left alone, and a
     * failed/interrupted publish keeps the previous working helper.
     */
    private static void installPtyHelper(File bin, android.content.Context context) {
        if (context == null || bin == null) return;
        File target = new File(bin, "agy-pty");
        byte[] bundled;
        try (InputStream in = context.getAssets().open("runtime/bin/agy-pty")) {
            bundled = readBounded(in, MAX_HELPER_BYTES);
        } catch (Throwable t) {
            Log.w(TAG, "Native agy-pty asset not available: " + t.getMessage());
            return;
        }
        if (!isAarch64Elf(bundled)) {
            Log.w(TAG, "Bundled agy-pty asset is not an AArch64 ELF; keeping existing helper");
            return;
        }
        if (sameContent(target, bundled)) {
            ensureExecutable(target);
            return;
        }
        if (publishExecutable(target, bundled)) {
            Log.i(TAG, "Published native agy-pty helper to " + target.getAbsolutePath());
        } else {
            Log.w(TAG, "Failed to publish agy-pty helper; keeping the existing one at " + target);
        }
    }

    private static boolean isAarch64Elf(byte[] data) {
        return data.length >= 20
                && (data[0] & 0xFF) == 0x7F && data[1] == 'E' && data[2] == 'L' && data[3] == 'F'
                && data[4] == 2 // ELFCLASS64
                && (data[18] & 0xFF) == 0xB7 && (data[19] & 0xFF) == 0x00; // EM_AARCH64
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > maxBytes) {
                throw new IOException("asset exceeds " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static boolean sameContent(File file, byte[] data) {
        if (!file.isFile() || file.length() != data.length) {
            return false;
        }
        try {
            return Sha256.of(file).equals(Sha256.of(data));
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureExecutable(File file) {
        file.setReadable(true, false);
        if (!file.setExecutable(true, false)) {
            Log.w(TAG, "setExecutable failed: " + file);
        }
        try {
            android.system.Os.chmod(file.getAbsolutePath(), 0755);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Stages a complete sibling, sets its mode, verifies the staged bytes, then
     * renames it over the target. The old file is never truncated in place.
     */
    private static boolean publishExecutable(File target, byte[] data) {
        File dir = target.getParentFile();
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())) {
            return false;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile(target.getName() + ".", ".tmp", dir);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.flush();
                out.getFD().sync();
            }
            if (!sameContent(tmp, data)) {
                return false;
            }
            ensureExecutable(tmp);
            if (!tmp.renameTo(target)) {
                return false;
            }
            tmp = null;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "cannot publish executable " + target + ": " + e.getMessage());
            return false;
        } finally {
            if (tmp != null) {
                com.agy.util.IoUtil.deleteQuietly(tmp);
            }
        }
    }

    public static synchronized void installOpenUrlShims(File bin) {
        installOpenUrlShims(bin, null);
    }

    public static synchronized void installOpenUrlShims(File bin, android.content.Context context) {
        if (bin == null) return;
        if (!bin.isDirectory() && !bin.mkdirs() && !bin.isDirectory()) {
            Log.w(TAG, "cannot create " + bin + "; skipping open-url shims");
            return;
        }
        for (String opener : OPEN_URL_SHIMS) {
            writeOpenUrlShim(new File(bin, opener), context);
        }
    }

    public static synchronized void install(RuntimeManager rm) {
        install(rm, false);
    }

    /**
     * Materializes the bionic bridges. The bulk pass (one script per $PREFIX/bin
     * tool, ~400 files) only runs when the marker is missing/stale or {@code force}
     * is set (fresh env install); a warm launch just refreshes the cheap critical
     * shims.
     */
    public static synchronized void install(RuntimeManager rm, boolean force) {
        File bin = rm.getBinDir();
        if (!bin.isDirectory() && !bin.mkdirs() && !bin.isDirectory()) {
            Log.w(TAG, "cannot create " + bin + "; skipping shell bridges");
            return;
        }
        // Always ensure fallback shells and shims exist FIRST
        installFallbackBridges(bin, rm.appContext());
        installOpenUrlShims(bin, rm.appContext());

        File prefix = EnvPaths.prefix(rm.appContext());
        File prefixBash = new File(prefix, "bin/bash");
        if (!prefixBash.isFile()) {
            Log.w(TAG, "prefix bash missing (" + prefixBash + "); keeping fallback shell bridges");
            return;
        }
        String appId = rm.appContext().getPackageName();
        writeBridge(new File(bin, "bash"), prefix, "bash", appId);
        writeBridge(new File(bin, "sh"), prefix, "bash", appId);
        File[] prefixTools = new File(prefix, "bin").listFiles();
        int toolCount = countFiles(prefixTools);
        File marker = new File(bin, BRIDGE_MARKER);
        if (!force && markerMatches(marker, prefix, toolCount)) {
            return;
        }
        if (prefixTools != null) {
            for (File toolFile : prefixTools) {
                if (toolFile.isFile()) {
                    String name = toolFile.getName();
                    if ("rish".equals(name) || isOpenUrlShim(name)) {
                        continue;
                    }
                    writeBridge(new File(bin, name), prefix, name, appId);
                }
            }
        }
        writeMarker(marker, prefix, toolCount);
    }

    private static int countFiles(File[] files) {
        int n = 0;
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean markerMatches(File marker, File prefix, int toolCount) {
        if (!marker.isFile()) {
            return false;
        }
        try (InputStream in = new FileInputStream(marker)) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            String content = n > 0 ? new String(buf, 0, n, Charset.forName("UTF-8")) : "";
            return content.equals(markerContent(prefix, toolCount));
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeMarker(File marker, File prefix, int toolCount) {
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(markerContent(prefix, toolCount).getBytes(Charset.forName("UTF-8")));
        } catch (IOException e) {
            Log.w(TAG, "cannot write bridge marker " + marker + ": " + e.getMessage());
        }
    }

    /** Version + prefix + tool count: any of these changing forces a rebuild. */
    private static String markerContent(File prefix, int toolCount) {
        return BRIDGE_FORMAT + "\n" + prefix.getAbsolutePath() + "\n" + toolCount + "\n";
    }

    private static boolean isOpenUrlShim(String name) {
        for (String opener : OPEN_URL_SHIMS) {
            if (opener.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the AGY URL opener shim. It prints markers for the core stdout
     * reader and sends one signature-protected internal broadcast as a shell-side fallback.
     */
    private static void writeOpenUrlShim(File bridge, android.content.Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n");
        sb.append("# AGY URL opener shim (xdg-open / open / sensible-browser).\n");
        sb.append("unset LD_PRELOAD\n");
        sb.append("unset LD_LIBRARY_PATH\n");
        sb.append("URL=\"$1\"\n");
        sb.append("[ -z \"$URL\" ] && URL=\"$2\"\n");
        sb.append("[ -z \"$URL\" ] && exit 0\n");
        // Marker on owned stdout only. The former public broadcast fallback was
        // removed so a foreign app cannot inject URLs (B07/R23).
        sb.append("echo \"AGY_OPEN_URL=$URL\"\n");
        sb.append("echo \"ANTIGRAVITY_OPEN_URL: $URL\"\n");
        sb.append("exit 0\n");
        writeScript(bridge, sb.toString());
    }

    /**
     * Writes one 755 bridge script. It is run by Android's /system/bin/sh (a
     * POSIX shell, no bionic env needed), which then sets PREFIX/LD_LIBRARY_PATH/
     * LD_PRELOAD/PATH for the bionic child only and execs it.
     */
    private static void writeBridge(File bridge, File prefix, String target, String appId) {
        String p = ShellQuote.quote(prefix.getAbsolutePath());
        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n");
        sb.append("# AGY bridge -> bionic $PREFIX/bin/").append(target).append('\n');
        sb.append("PREFIX=").append(p).append('\n');
        sb.append("AGY_APPLICATION_ID=").append(ShellQuote.quote(appId)).append('\n');
        sb.append("export PREFIX AGY_APPLICATION_ID\n");
        sb.append("if [ -f \"$PREFIX/lib/libtermux-exec.so\" ]; then\n");
        sb.append("  export LD_PRELOAD=\"$PREFIX/lib/libtermux-exec.so\"\n");
        sb.append("else\n");
        sb.append("  unset LD_PRELOAD\n");
        sb.append("fi\n");
        sb.append("export LD_LIBRARY_PATH=\"$PREFIX/lib\"\n");
        sb.append("export PATH=\"$PREFIX/bin:/system/bin\"\n");
        sb.append("export OPENSSL_CONF=\"$PREFIX/etc/tls/openssl.cnf\"\n");
        sb.append("export SSL_CERT_FILE=\"$PREFIX/etc/tls/cert.pem\"\n");
        sb.append("export CURL_CA_BUNDLE=\"$PREFIX/etc/tls/cert.pem\"\n");
        sb.append("export GIT_CONFIG_NOSYSTEM=1\n");
        sb.append("export GIT_ATTR_NOSYSTEM=1\n");
        sb.append("export GIT_TEMPLATE_DIR=\"$PREFIX/share/git-core/templates\"\n");
        sb.append("export GIT_EXEC_PATH=\"$PREFIX/libexec/git-core\"\n");
        sb.append("export PAGER=\"cat\"\n");
        sb.append("if [ -x \"$PREFIX/bin/").append(target).append("\" ]; then\n");
        sb.append("  exec \"$PREFIX/bin/").append(target).append("\" \"$@\"\n");
        sb.append("else\n");
        sb.append("  exec /system/bin/sh \"$@\"\n");
        sb.append("fi\n");
        writeScript(bridge, sb.toString());
    }

    /**
     * Publishes a complete executable script atomically (C08c/R28): identical
     * content is left untouched so warm starts never truncate a live shim, and
     * a failed write preserves the previous executable.
     */
    private static void writeScript(File bridge, String script) {
        byte[] data = script.getBytes(Charset.forName("UTF-8"));
        if (sameContent(bridge, data)) {
            ensureExecutable(bridge);
            return;
        }
        if (!publishExecutable(bridge, data)) {
            Log.w(TAG, "cannot publish bridge " + bridge + "; keeping the previous file");
        }
    }
}
