package com.agy.runtime;

import android.content.Context;

import com.agy.util.AtomicFileWriter;
import com.agy.util.Sha256;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/** Initialize the runtime environment and install app-owned runtime assets. */
public final class CoreInstaller {

    private static final String TAG = "CoreInstaller";
    private static final String CA_ASSET = "runtime/certs/ca-certificates.crt";
    /** Defensive bound; the bundled bundle is a few hundred KiB. */
    private static final int MAX_CA_BYTES = 4 * 1024 * 1024;

    private CoreInstaller() {
    }

    public static void installCore(RuntimeManager rm) {
        Context ctx = rm.appContext();
        installCertificates(rm);
        rm.writeNetworkConfig();
        rm.startNetworkMonitor();

        // Vendor state under .gemini is user-owned. The host no longer seeds a
        // fabricated completed onboarding state or permissive always-proceed
        // settings (B01a/B01b/R07); setup stays a visible user action.

        // Ensure runtime shims are ready for CLI processes
        ShellBridgeInstaller.installFallbackBridges(rm.getBinDir(), ctx);
        ShellBridgeInstaller.installOpenUrlShims(rm.getBinDir(), ctx);

        File cliShims = new File(ctx.getFilesDir(), "cli/shims");
        ShellBridgeInstaller.installFallbackBridges(cliShims, ctx);
        ShellBridgeInstaller.installOpenUrlShims(cliShims, ctx);
    }

    /**
     * Installs the bundled CA file by identity, not by size: an interrupted
     * partial file is replaced on the next start, unchanged bytes are skipped,
     * and a failed publish keeps the previous valid bundle (C08b/R27).
     */
    private static void installCertificates(RuntimeManager rm) {
        File certFile = rm.getCertFile();
        byte[] bundled;
        try (InputStream in = rm.appContext().getAssets().open(CA_ASSET)) {
            bundled = readBounded(in, MAX_CA_BYTES);
        } catch (Throwable t) {
            android.util.Log.w(TAG, "bundled CA asset unavailable: " + t.getMessage());
            return;
        }
        if (bundled.length == 0) {
            android.util.Log.w(TAG, "bundled CA asset is empty; keeping existing file");
            return;
        }
        String bundledDigest;
        try {
            bundledDigest = Sha256.of(bundled);
        } catch (Exception e) {
            android.util.Log.w(TAG, "cannot hash bundled CA asset: " + e.getMessage());
            return;
        }
        if (certFile.isFile() && certFile.length() == bundled.length) {
            try {
                if (bundledDigest.equals(Sha256.of(certFile))) {
                    return;
                }
            } catch (Exception ignored) {
                // Unreadable/partial: fall through and republish.
            }
        }
        if (AtomicFileWriter.writeBytes(certFile, bundled)) {
            android.util.Log.i(TAG, "Published verified CA certificates to "
                    + certFile.getAbsolutePath());
        } else {
            android.util.Log.w(TAG, "Failed to publish CA certificates; "
                    + "keeping the previous bundle at " + certFile.getAbsolutePath());
        }
    }

    private static byte[] readBounded(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > maxBytes) {
                throw new IllegalStateException("asset exceeds " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
