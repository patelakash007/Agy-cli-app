package com.agy.runtime;

import android.content.Context;

import java.io.File;

/** Initialize runtime environment and install shims. */
public final class CoreInstaller {

    private CoreInstaller() {
    }

    public static void installCore(RuntimeManager rm) {
        Context ctx = rm.appContext();
        installCertificates(rm);
        rm.writeNetworkConfig();
        rm.startNetworkMonitor();
        OnboardingSeeder.seed(ctx, rm.getAppDataDir(), rm.getProjectsDir());

        // Ensure runtime shims are ready for CLI processes
        ShellBridgeInstaller.installFallbackBridges(rm.getBinDir(), ctx);
        ShellBridgeInstaller.installOpenUrlShims(rm.getBinDir(), ctx);

        File cliShims = new File(ctx.getFilesDir(), "cli/shims");
        ShellBridgeInstaller.installFallbackBridges(cliShims, ctx);
        ShellBridgeInstaller.installOpenUrlShims(cliShims, ctx);
    }

    private static void installCertificates(RuntimeManager rm) {
        File certFile = rm.getCertFile();
        if (certFile.isFile() && certFile.length() > 1000) {
            return;
        }
        File certDir = certFile.getParentFile();
        if (certDir != null && !certDir.isDirectory()) {
            certDir.mkdirs();
        }
        try (java.io.InputStream in = rm.appContext().getAssets().open("runtime/certs/ca-certificates.crt");
             java.io.FileOutputStream out = new java.io.FileOutputStream(certFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            android.util.Log.i("CoreInstaller", "Extracted CA certificates to " + certFile.getAbsolutePath());
        } catch (Throwable t) {
            android.util.Log.w("CoreInstaller", "CA certificates asset not available: " + t.getMessage());
        }
    }
}
