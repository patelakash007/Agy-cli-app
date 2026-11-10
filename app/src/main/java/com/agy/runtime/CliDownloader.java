package com.agy.runtime;

import android.content.Context;
import android.util.Log;

import com.agy.install.TarExtractor;
import com.agy.util.FileModeUtil;

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
import java.security.MessageDigest;

/**
 * Downloads the official Google Antigravity CLI (Bionic ARM64) on demand,
 * validates its SHA-512 against Google's live manifest, and installs it to files/cli/agy.
 */
public final class CliDownloader {

    private static final String TAG = "CliDownloader";
    public static final String MANIFEST_URL =
            "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/android_arm64.json";

    public interface DownloadListener {
        void onProgress(int percent, String message);
        void onSuccess(File binary);
        void onError(String error);
    }

    private CliDownloader() {
    }

    public static File getCliBinary(Context context) {
        return new File(context.getFilesDir(), "cli/agy");
    }

    public static boolean isInstalled(Context context) {
        File bin = getCliBinary(context);
        return bin.isFile() && bin.canExecute() && bin.length() > 10 * 1024 * 1024;
    }

    public static void downloadAndInstall(final Context context, final DownloadListener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (listener != null) {
                        listener.onProgress(0, "Fetching manifest...");
                    }
                    URL manifestUrl = new URL(MANIFEST_URL);
                    HttpURLConnection conn = (HttpURLConnection) manifestUrl.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    if (conn.getResponseCode() != 200) {
                        throw new IOException("Failed to fetch manifest: HTTP " + conn.getResponseCode());
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream in = conn.getInputStream()) {
                        byte[] buf = new byte[4096];
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            baos.write(buf, 0, r);
                        }
                    }
                    String manifestJson = baos.toString("UTF-8");
                    JSONObject json = new JSONObject(manifestJson);
                    String downloadUrl = json.getString("url");
                    String expectedSha512 = json.getString("sha512").trim().toLowerCase();
                    String version = json.optString("version", "latest");

                    File cliDir = new File(context.getFilesDir(), "cli");
                    if (!cliDir.isDirectory() && !cliDir.mkdirs() && !cliDir.isDirectory()) {
                        throw new IOException("Cannot create cli directory: " + cliDir);
                    }
                    File tempArchive = new File(context.getCacheDir(), "cli_download.tar.gz");

                    if (listener != null) {
                        listener.onProgress(5, "Downloading Antigravity v" + version + "...");
                    }
                    URL dl = new URL(downloadUrl);
                    HttpURLConnection dlConn = (HttpURLConnection) dl.openConnection();
                    dlConn.setConnectTimeout(15000);
                    dlConn.setReadTimeout(30000);
                    int totalLength = dlConn.getContentLength();

                    MessageDigest md = MessageDigest.getInstance("SHA-512");
                    try (InputStream in = new BufferedInputStream(dlConn.getInputStream());
                         FileOutputStream out = new FileOutputStream(tempArchive)) {
                        byte[] buf = new byte[65536];
                        long downloaded = 0;
                        int r;
                        while ((r = in.read(buf)) != -1) {
                            out.write(buf, 0, r);
                            md.update(buf, 0, r);
                            downloaded += r;
                            if (totalLength > 0 && listener != null) {
                                int pct = (int) (5 + (downloaded * 80 / totalLength));
                                listener.onProgress(pct, "Downloading: " + (downloaded / (1024 * 1024))
                                        + "MB / " + (totalLength / (1024 * 1024)) + "MB");
                            }
                        }
                    }

                    byte[] digest = md.digest();
                    StringBuilder actualSha512 = new StringBuilder();
                    for (byte b : digest) {
                        actualSha512.append(String.format("%02x", b));
                    }

                    if (!actualSha512.toString().equalsIgnoreCase(expectedSha512)) {
                        tempArchive.delete();
                        throw new SecurityException("SHA-512 mismatch! Security halt. Expected: "
                                + expectedSha512.substring(0, 16) + "... Got: "
                                + actualSha512.substring(0, 16) + "...");
                    }

                    if (listener != null) {
                        listener.onProgress(90, "Extracting binary...");
                    }
                    try (InputStream in = new FileInputStream(tempArchive)) {
                        TarExtractor.untarGz(in, cliDir);
                    }
                    tempArchive.delete();

                    File extracted = new File(cliDir, "antigravity");
                    File finalBin = new File(cliDir, "agy");
                    if (extracted.isFile()) {
                        if (finalBin.exists()) {
                            finalBin.delete();
                        }
                        extracted.renameTo(finalBin);
                    }

                    if (!finalBin.isFile()) {
                        throw new IOException("Failed to find extracted binary: " + finalBin.getAbsolutePath());
                    }

                    finalBin.setReadable(true, false);
                    finalBin.setExecutable(true, false);
                    FileModeUtil.setExec(finalBin);

                    if (listener != null) {
                        listener.onProgress(100, "Ready!");
                        listener.onSuccess(finalBin);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Download failed: " + e.getMessage(), e);
                    if (listener != null) {
                        listener.onError(e.getMessage());
                    }
                }
            }
        }, "CliDownloadThread").start();
    }
}
