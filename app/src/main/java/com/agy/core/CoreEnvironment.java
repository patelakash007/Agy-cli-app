package com.agy.core;

import android.util.Log;

import com.agy.runtime.RuntimeManager;

import java.io.File;
import java.util.Map;

/** Builds the environment for the native CLI process. */
public final class CoreEnvironment {

    private static final String TAG = "CoreEnvironment";

    private CoreEnvironment() {
    }

    public static void apply(RuntimeManager rm, ProcessBuilder pb) {
        File files = rm.appContext().getFilesDir();
        File bin = rm.getBinDir();
        Map<String, String> env = pb.environment();
        // HOME is the app files dir and never a temp dir; it is stable across
        // builds so the core always finds $HOME/.gemini (token, config, chats).
        env.put("HOME", files.getAbsolutePath());
        env.put("TMPDIR", rm.appContext().getCacheDir().getAbsolutePath());
        Log.i(TAG, "core env: HOME=" + files.getAbsolutePath()
                + " appDataDir=" + rm.getAppDataDir().getAbsolutePath()
                + " projectsDir=" + rm.getProjectsDir().getAbsolutePath()
                + " TMPDIR=" + rm.appContext().getCacheDir().getAbsolutePath());
        env.put("SSL_CERT_FILE", rm.getCertFile().getAbsolutePath());
        File cliDir = new File(files, "cli");
        String inheritedPath = env.get("PATH");
        env.put("PATH", cliDir.getAbsolutePath() + ":" + bin.getAbsolutePath()
                + (inheritedPath == null || inheritedPath.isEmpty() ? ":/system/bin" : ":" + inheritedPath));
        // The core's PTY spawner uses $SHELL (or falls back to /bin/bash, which
        // does not exist on Android). Prefer the prefix bash, then the bridge in
        // runtime/bin, then Android's own sh.
        File prefix = new File(files, com.agy.install.EnvPaths.PREFIX_DIR);
        File prefixBash = new File(prefix, com.agy.install.EnvPaths.BASH_REL);
        File runtimeBash = new File(bin, "bash");
        if (prefixBash.isFile() && prefixBash.canExecute()) {
            env.put("SHELL", prefixBash.getAbsolutePath());
        } else if (runtimeBash.isFile() && runtimeBash.canExecute()) {
            env.put("SHELL", runtimeBash.getAbsolutePath());
        } else {
            env.put("SHELL", "/system/bin/sh");
        }
        // PTY children need a terminal type, and the bootstrap prefix ships the
        // terminfo database that resolves it.
        env.put("TERM", "xterm-256color");
        File terminfo = new File(prefix, "share/terminfo");
        if (terminfo.isDirectory()) {
            env.put("TERMINFO", terminfo.getAbsolutePath());
        }
        env.put("LANG", "C.UTF-8");
        env.put("LC_ALL", "C.UTF-8");

        // Do NOT set netdns=go! Pure-Go DNS resolver requires /etc/resolv.conf,
        // which does not exist on Android and forces fallback to localhost [::1]:53 (connection refused).
        // Leaving netdns unset lets Go use Android Bionic libc's native getaddrinfo for DNS resolution.
        String godebug = env.get("GODEBUG");
        if (godebug != null) {
            godebug = godebug.replace("netdns=go", "").replace(",,", ",").replaceAll("^,|,$", "");
            if (godebug.isEmpty()) {
                env.remove("GODEBUG");
            } else {
                env.put("GODEBUG", godebug);
            }
        }
        // Emits "ANTIGRAVITY_OPEN_URL: <url>" directly to stdout from agy CLI
        // so OpenUrlWatcher captures the auth URL and opens the external browser reliably.
        env.put("ANTIGRAVITY_VSCODE_HOST", "1");
        // Configure browser and desktop environment so core OAuth/open tools find the browser
        File xdgOpen = new File(bin, "xdg-open");
        env.put("BROWSER", xdgOpen.getAbsolutePath());
        env.put("DISPLAY", ":0");
        env.put("WAYLAND_DISPLAY", "wayland-0");
        env.put("XDG_CURRENT_DESKTOP", "X-Generic");
        env.put("XDG_DATA_DIRS", rm.getRuntimeDir().getAbsolutePath());
        env.put("XDG_DATA_HOME", new File(files, ".local/share").getAbsolutePath());
        env.put("XDG_CONFIG_HOME", new File(files, ".config").getAbsolutePath());
        // Clean dynamic linker environment
        env.remove("LD_LIBRARY_PATH");
        env.remove("LD_PRELOAD");
    }
}
