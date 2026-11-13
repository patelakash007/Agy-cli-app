package com.agy.runtime;

import android.content.Context;

import com.agy.core.CoreEnvironment;
import com.agy.core.CoreRuntimeState;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the native Antigravity CLI runtime, network configuration and
 * fallback shell bridges. Vendor setup state under .gemini is user-owned and is
 * never seeded by the host (B01).
 *
 * targetSdk 28 is required so the native CLI binary can be exec'd from the data dir.
 */
public final class RuntimeManager {

    public static final String CORE_VERSION = RuntimeConfig.CORE_VERSION;

    private final Context appContext;
    private final String csrfToken;
    private final RuntimePaths paths;
    private final NetworkMonitorHolder network;
    /** Guards against starting the background env install more than once. */
    private final AtomicBoolean envInstallStarted = new AtomicBoolean(false);

    public RuntimeManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.csrfToken = UUID.randomUUID().toString();
        this.paths = new RuntimePaths(context);
        this.network = new NetworkMonitorHolder(context);
    }

    public Context appContext() {
        return appContext;
    }

    public int getHttpsPort() {
        return RuntimeConfig.httpsPort(appContext);
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public File getRuntimeDir() {
        return paths.getRuntimeDir();
    }

    public File getWebDir() {
        return paths.getWebDir();
    }

    public File getBinDir() {
        return paths.getBinDir();
    }

    public File getLibDir() {
        return paths.getLibDir();
    }

    public File getCertFile() {
        return paths.getCertFile();
    }

    public File getGeminiDir() {
        return paths.getGeminiDir();
    }

    public File getAppDataDir() {
        return paths.getAppDataDir();
    }

    public File getProjectsDir() {
        return paths.getProjectsDir();
    }

    /** Prepares network configuration, DNS settings and shell shims. */
    public synchronized void installCore() {
        CoreInstaller.installCore(this);
    }

    /** Native command to launch Google Antigravity CLI in Remote Control mode. */
    public String[] coreCommand() {
        File cliBin = CliInstaller.getCliBinary(appContext);
        if (!cliBin.isFile()) {
            cliBin = new File(getBinDir(), "agy");
        }
        if (!cliBin.isFile()) {
            throw new IllegalStateException("Antigravity CLI binary missing at: " + cliBin.getAbsolutePath());
        }

        java.util.List<String> cmd = new java.util.ArrayList<>();
        File ptyBin = new File(getBinDir(), "agy-pty");
        if (ptyBin.isFile() && ptyBin.canExecute()) {
            cmd.add(ptyBin.getAbsolutePath());
        }
        cmd.add(cliBin.getAbsolutePath());
        cmd.add("--remote-control");
        return cmd.toArray(new String[0]);
    }

    public void applyEnvironment(ProcessBuilder pb) {
        CoreEnvironment.apply(this, pb);
    }

    // ---- network config ---------------------------------------------------

    /** Start watching the default network; on changes the DNS config is rewritten. */
    public void startNetworkMonitor() {
        network.start();
    }

    /** Unregisters the process network callback when the core service stops. */
    public void stopNetworkMonitor() {
        network.unregister();
    }

    /**
     * Refresh DNS config in place. Rewriting resolv.conf is enough for a running
     * core: it uses GODEBUG=netdns=go and re-reads the file, so the process is
     * not restarted.
     */
    public synchronized void refreshNetwork() {
        writeNetworkConfig();
        if (CoreRuntimeState.serverUrl != null) {
            android.util.Log.d("RuntimeManager", "network changed; rewrote DNS config (core stays up)");
        } else {
            android.util.Log.d("RuntimeManager", "network changed; DNS config refreshed");
        }
    }

    public synchronized void writeNetworkConfig() {
        NetworkConfigWriter.write(appContext);
    }
}
