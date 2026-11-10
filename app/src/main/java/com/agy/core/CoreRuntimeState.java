package com.agy.core;

import com.agy.runtime.RuntimeConfig;

import java.util.Locale;

/** Cross-component state shared by the service, the bridge and the UI. */
public final class CoreRuntimeState {

    private CoreRuntimeState() {
    }

    /** Published once the core answers on its loopback port or Remote Control session. */
    public static volatile String serverUrl = null;
    public static volatile int httpsPort = RuntimeConfig.DEFAULT_HTTPS_PORT;
    public static volatile String csrfToken = null;

    /** Fail-closed startup error, surfaced to the UI instead of a hung splash. */
    public static volatile String lastError = null;
    /** True while an unexpected exit is being auto-restarted (transient, not fatal). */
    public static volatile boolean restarting = false;
    /** Bumped on every successful ready; the UI reloads the app when it changes. */
    public static volatile int readyGeneration = 0;

    /** True when the CLI has requested authentication and is awaiting browser login. */
    public static volatile boolean authPending = false;
    /** The active Google OAuth authorization URL to be opened in the browser. */
    public static volatile String pendingAuthUrl = null;
    /** Authenticated user email detected from CLI output or token. */
    public static volatile String authenticatedEmail = null;
    /** True when the CLI is in first-time welcome/onboarding wizard flow. */
    public static volatile boolean onboardingPending = false;

    /** Callback to pipe user input directly to the supervised CLI process stdin. */
    public interface StdinWriter {
        boolean writeStdin(String text);
    }
    public static volatile StdinWriter stdinWriter = null;

    public static boolean submitStdin(String text) {
        StdinWriter writer = stdinWriter;
        if (writer != null) {
            return writer.writeStdin(text);
        }
        return false;
    }

    /**
     * Validates whether a candidate string is an authentic Remote Control session URL.
     * Must be http/https, contain /r/<session-id>, and not be an auth or API URL.
     */
    public static boolean isValidSessionUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return false;
        }
        String lower = u.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        if (!lower.contains("/r/")) {
            return false;
        }
        if (lower.contains("accounts.google.com") || lower.contains("/o/oauth2")
                || lower.contains("googleapis.com/oauth2")
                || lower.contains("/oauth-callback")
                || lower.contains("/auth-success")) {
            return false;
        }
        return u.matches("^https?://[a-zA-Z0-9\\-\\.]+(?::\\d+)?/r/[a-zA-Z0-9\\-_/]+(?:[?#][a-zA-Z0-9\\-_\\.~%&=/#+]*)?$");
    }

    public static void reset() {
        serverUrl = null;
        lastError = null;
        restarting = false;
        authPending = false;
        pendingAuthUrl = null;
        authenticatedEmail = null;
        onboardingPending = false;
        stdinWriter = null;
    }
}
