package com.agy.core;

/**
 * Decides whether a /proc cmdline belongs to this flavor's owned core/helper
 * process. Pure and host-testable: matching is by this app's exact private
 * files directory, never by a package-name substring.
 */
public final class ProcessScope {

    private ProcessScope() {
    }

    public static boolean isOwnedCoreProcess(String cmdline, String filesDir) {
        if (cmdline == null || filesDir == null || filesDir.isEmpty()) {
            return false;
        }
        if (!cmdline.contains(filesDir)) {
            return false;
        }
        return cmdline.contains("cli/agy")
                || cmdline.contains("bin/agy")
                || cmdline.contains("agy-pty")
                || cmdline.contains("--remote-control");
    }
}
