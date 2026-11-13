package com.agy.util;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Process helpers: interrupts, liveness and bounded wait/drain. */
public final class ProcessUtils {

    private ProcessUtils() {
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isAlive(Process proc) {
        try {
            proc.exitValue();
            return false;
        } catch (IllegalThreadStateException stillRunning) {
            return true;
        }
    }

    public static int exitCode(Process proc) {
        try {
            return proc.exitValue();
        } catch (IllegalThreadStateException stillRunning) {
            return -1;
        }
    }

    /**
     * Drains stdout then waits up to {@code timeout}; true when it exited in time.
     *
     * Bounded polling instead of Process.waitFor(long, TimeUnit), which only
     * exists on API 26+ while this app supports API 24/25 (C06/R24).
     */
    public static boolean drainAndWait(Process p, long timeout, TimeUnit unit) {
        try (InputStream in = p.getInputStream()) {
            StreamDrain.drain(in);
        } catch (Exception ignored) {
        }
        long deadline = System.nanoTime() + unit.toNanos(Math.max(0L, timeout));
        while (isAlive(p)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            sleep(50);
        }
        return true;
    }
}
