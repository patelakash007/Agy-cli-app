package com.agy.core;

import android.util.Log;

import com.agy.runtime.RuntimeManager;
import com.agy.util.ProcessUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Installs (phase 1 + phase 2) then launches the patched core process. */
public final class CoreLauncher {

    private static final String TAG = "CoreLauncher";

    private CoreLauncher() {
    }

    /**
     * Terminate leftover core/helper processes from this app's own files
     * directory only. There is no broad app-name or package-substring match:
     * the release and dev flavors (and unrelated apps) must never kill each
     * other (R01/B07 isolation; C02 owns the full owned-group contract).
     */
    public static void killOrphanCoreProcesses() {
        // No scope means no scanning: callers must provide their own filesDir.
    }

    public static void killOrphanCoreProcesses(File filesDir) {
        if (filesDir == null) {
            return;
        }
        int myPid = android.os.Process.myPid();
        File procDir = new File("/proc");
        File[] entries = procDir.listFiles();
        if (entries == null) {
            return;
        }
        String appScope = filesDir.getAbsolutePath();
        boolean killedAny = false;
        for (File entry : entries) {
            String name = entry.getName();
            if (!name.matches("\\d+")) {
                continue;
            }
            try {
                int pid = Integer.parseInt(name);
                if (pid == myPid) {
                    continue;
                }
                File cmdlineFile = new File(entry, "cmdline");
                if (!cmdlineFile.canRead()) {
                    continue;
                }
                byte[] buf = new byte[512];
                int n;
                try (FileInputStream fis = new FileInputStream(cmdlineFile)) {
                    n = fis.read(buf);
                }
                if (n > 0) {
                    String cmdline = new String(buf, 0, n);
                    // Only this app's exact private directory qualifies. This
                    // excludes Termux, the other flavor, and sibling apps.
                    if (!ProcessScope.isOwnedCoreProcess(cmdline, appScope)) {
                        continue;
                    }
                    Log.w(TAG, "Terminating orphan core process (PID " + pid + ")");
                    android.os.Process.sendSignal(pid, android.os.Process.SIGNAL_KILL);
                    killedAny = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (killedAny) {
            ProcessUtils.sleep(200);
        }
    }

    public static Process launch(RuntimeManager runtime, File filesDir) throws IOException {
        // Pre-flight: kill any zombie/orphan core instances in app scope
        killOrphanCoreProcesses(filesDir);

        // Extract + verify APK-owned runtime assets (fail-closed, throws on
        // corruption). No delayed shim refresh is scheduled: a delayed writer
        // could otherwise mutate live executables after a cancelled start
        // (C08c/R28). installCore() already refreshes the bridges.
        runtime.installCore();
        ProcessBuilder pb = new ProcessBuilder(runtime.coreCommand());
        pb.directory(filesDir);
        pb.redirectErrorStream(true);
        runtime.applyEnvironment(pb);
        return pb.start();
    }
}
