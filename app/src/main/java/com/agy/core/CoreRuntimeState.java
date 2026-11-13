package com.agy.core;

import com.agy.runtime.RuntimeConfig;

import java.net.URI;
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

    /** The only origin allowed to publish a Remote Control session URL. */
    public static final String TRUSTED_SESSION_HOST = "antigravity.google.com";

    /**
     * Strict trusted-session-URL policy (B02/R04).
     *
     * Accepts only https, the exact trusted host, no userinfo, the default/443
     * port, and exactly one opaque segment after /r/. HTTP, lookalike or suffix
     * hosts, encoded separators, extra path segments and control characters are
     * rejected. Query/fragment encoding is left untouched.
     */
    public static boolean isValidSessionUrl(String url) {
        if (url == null) {
            return false;
        }
        String u = url.trim();
        if (u.isEmpty()) {
            return false;
        }
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c <= 0x20 || c == 0x7F) {
                return false;
            }
        }
        URI uri;
        try {
            uri = new URI(u);
        } catch (Exception e) {
            return false;
        }
        if (uri.isOpaque() || uri.getScheme() == null
                || !uri.getScheme().equalsIgnoreCase("https")) {
            return false;
        }
        if (uri.getRawUserInfo() != null) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase(TRUSTED_SESSION_HOST)) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return false;
        }
        String rawPath = uri.getRawPath();
        if (rawPath == null) {
            return false;
        }
        String lowerPath = rawPath.toLowerCase(Locale.US);
        if (lowerPath.contains("%2f") || lowerPath.contains("%5c") || rawPath.indexOf('\\') >= 0) {
            return false;
        }
        if (!rawPath.startsWith("/r/")) {
            return false;
        }
        String segment = rawPath.substring(3);
        if (segment.isEmpty() || segment.indexOf('/') >= 0
                || segment.equals(".") || segment.equals("..")) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            boolean safe = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~';
            if (safe) {
                continue;
            }
            if (c == '%' && i + 2 < segment.length()
                    && isHex(segment.charAt(i + 1)) && isHex(segment.charAt(i + 2))) {
                i += 2;
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
