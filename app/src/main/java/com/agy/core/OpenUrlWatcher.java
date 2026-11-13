package com.agy.core;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.agy.oauth.OAuthManager;
import com.agy.ui.MainActivity;
import com.agy.util.SensitiveDataRedactor;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dispatches external browser URLs and OAuth flows detected from the core's
 * stdout stream or explicit ACTION_OPEN_URL broadcasts.
 *
 * Guarantees that Google authentication URLs are sent to the external browser (Chrome)
 * and never mistakenly loaded in WebView or treated as Remote Control session URLs.
 */
public final class OpenUrlWatcher {

    private static final String TAG = "OpenUrlWatcher";

    /** Candidate extraction only; CoreRuntimeState.isValidSessionUrl owns the policy. */
    private static final Pattern SESSION_URL_PATTERN =
            Pattern.compile("https://[^\\s\"'<>\\\\]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern AUTH_URL_PATTERN =
            Pattern.compile("https?://[\\w\\-\\.\\~:\\?\\#\\[\\]\\@\\!\\$\\&\\'\\(\\)\\*\\+,;=%/]+");

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private static final Pattern ANSI_PATTERN =
            Pattern.compile("\\x1B(?:\\[[0-9;?]*[a-zA-Z]|\\][^\\x07\\x1B]*[\\x07\\x1B\\\\])");

    public static String stripAnsi(String input) {
        if (input == null) return null;
        return ANSI_PATTERN.matcher(input).replaceAll(" ");
    }

    private final Context appContext;
    private final OAuthManager oauth = new OAuthManager();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Process supervisedProcess;
    private volatile String lastDispatchedAuthUrl = "";
    private volatile long lastDispatchedAuthTime = 0;
    private volatile String lastDispatchedSessionUrl = "";
    private volatile long lastDispatchedSessionTime = 0;

    // Multiline buffer for CLI wrapped URLs
    private final StringBuilder multilineAuthBuffer = new StringBuilder();
    private boolean isBufferingAuthUrl = false;

    public OpenUrlWatcher(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setProcess(Process process) {
        this.supervisedProcess = process;
    }

    /**
     * URL delivery is owned by the supervised process's own stdout stream. The
     * public ACTION_OPEN_URL broadcast input was removed (B07/R23): another app,
     * the other flavor, or a spoofed sender cannot drive URL/auth/state.
     */
    public void start() {
        Log.i(TAG, "URL discovery uses owned CLI output only; no broadcast receiver");
    }

    public void stop() {
        supervisedProcess = null;
    }

    /**
     * Inspects a line of stdout/stderr from the CLI process.
     * Detects login prompts, email attribution, multiline auth URLs, and Remote Control session URLs.
     */
    public void handleStreamLine(String rawLine) {
        if (rawLine == null) {
            return;
        }
        String line = stripAnsi(rawLine);
        if (line == null) {
            line = rawLine;
        }

        // 1. CLI output may only *describe* state. It never writes stdin: login
        // selection, terms acceptance and workspace trust are the user's own
        // visible actions (B01a/R07).
        if (line.contains("paste the authorization code") || line.contains("authorization code...")) {
            CoreRuntimeState.authPending = true;
        }

        if (line.contains("Choose your color scheme") || line.contains("[Next]")
                || line.contains("Terms of Service") || line.contains("[Done]")
                || rawLine.contains("Choose your color scheme") || rawLine.contains("[Next]")
                || rawLine.contains("Terms of Service") || rawLine.contains("[Done]")) {
            CoreRuntimeState.onboardingPending = true;
            Log.i(TAG, "CLI is waiting for interactive setup; user input is required");
        }

        // 2. Detect authenticated email from CLI banner
        if (line.contains("@") && (line.contains(".com") || line.contains(".org") || line.contains(".net") || line.contains(".io"))) {
            Matcher em = EMAIL_PATTERN.matcher(line);
            if (em.find()) {
                String email = em.group();
                if (!email.contains("example.com") && !email.contains("google.com/")) {
                    CoreRuntimeState.authenticatedEmail = email;
                    Log.i(TAG, "CLI authenticated user: " + email);
                }
            }
        }

        // 3. Detect explicit shim prefixes (emitted by xdg-open/open shims)
        for (String prefix : new String[]{"AGY_OPEN_URL=", "ANTIGRAVITY_OPEN_URL:", "ANTIGRAVITY_OPEN_URL="}) {
            int at = line.indexOf(prefix);
            if (at >= 0) {
                String url = line.substring(at + prefix.length()).trim();
                dispatchUrl(url);
                return;
            }
        }

        // 4. Handle multiline OAuth URL accumulation from CLI terminal output
        if (line.contains("accounts.google.com/o/oauth2") || (line.contains("https://accounts.google.com") && line.contains("client_id="))) {
            isBufferingAuthUrl = true;
            multilineAuthBuffer.setLength(0);
            Matcher m = AUTH_URL_PATTERN.matcher(line);
            if (m.find()) {
                multilineAuthBuffer.append(m.group());
            } else {
                multilineAuthBuffer.append(line.trim());
            }
            return;
        }

        if (isBufferingAuthUrl) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || line.contains("Navigate") || line.contains("lines)")
                    || line.contains("Welcome") || line.contains("Signing in") || trimmed.startsWith("─")) {
                // End of wrapped URL block
                String fullAuthUrl = multilineAuthBuffer.toString();
                isBufferingAuthUrl = false;
                multilineAuthBuffer.setLength(0);
                if (oauth.isAuthUrl(fullAuthUrl)) {
                    handleAuthUrl(fullAuthUrl);
                }
            } else if (trimmed.startsWith("&") || trimmed.contains("=") || trimmed.matches("^[a-zA-Z0-9_\\-\\.~%&=/+]+$")) {
                // Continuation line of the wrapped URL
                multilineAuthBuffer.append(trimmed);
                return;
            } else {
                String fullAuthUrl = multilineAuthBuffer.toString();
                isBufferingAuthUrl = false;
                multilineAuthBuffer.setLength(0);
                if (oauth.isAuthUrl(fullAuthUrl)) {
                    handleAuthUrl(fullAuthUrl);
                }
            }
        }

        // 5. Detect Remote Control session URL (/r/<session-id>)
        if (line.contains("/r/")) {
            String sessionUrl = firstValidSessionUrl(line);
            if (sessionUrl != null) {
                handleSessionUrl(sessionUrl);
                return;
            }
        }

        // 6. Direct line auth URL detection fallback
        if (line.contains("accounts.google.com") || (line.contains("/oauth") && line.contains("antigravity.google"))) {
            Matcher m = AUTH_URL_PATTERN.matcher(line);
            while (m.find()) {
                String candidate = m.group();
                if (oauth.isAuthUrl(candidate)) {
                    handleAuthUrl(candidate);
                    return;
                }
            }
        }
    }

    /** Extracts candidate URLs from child text and returns the first trusted one. */
    private static String firstValidSessionUrl(String line) {
        Matcher matcher = SESSION_URL_PATTERN.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group();
            // Trim trailing punctuation that commonly follows a pasted URL.
            while (candidate.length() > 0
                    && ".,;:)!]}>\"'".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (CoreRuntimeState.isValidSessionUrl(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void sendRawBytes(String data, String reason) {
        Process proc = supervisedProcess;
        if (proc != null && data != null) {
            try {
                OutputStream os = proc.getOutputStream();
                if (os != null) {
                    os.write(data.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Log.i(TAG, "Sent to CLI stdin (" + reason + ")");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed sending to CLI stdin: " + e.getMessage());
            }
        }
    }

    private void sendEnterToCli(String reason) {
        sendRawBytes("\r\n", reason);
    }

    private void dispatchUrl(String url) {
        if (url == null) return;
        url = url.trim();
        if (url.isEmpty()) return;

        // Never dispatch internal Google API endpoints
        if (url.contains("googleapis.com/oauth2/v2/userinfo")
                || url.contains("daily-cloudcode-pa.googleapis.com")) {
            return;
        }

        if (oauth.isAuthUrl(url)) {
            handleAuthUrl(url);
        } else if (CoreRuntimeState.isValidSessionUrl(url)) {
            handleSessionUrl(url);
        } else {
            Log.d(TAG, "Unclassified URL received: " + SensitiveDataRedactor.safeUrl(url));
        }
    }

    private void handleAuthUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        url = url.trim();

        long now = System.currentTimeMillis();
        if (url.equals(lastDispatchedAuthUrl) && (now - lastDispatchedAuthTime) < 3000) {
            return;
        }
        lastDispatchedAuthUrl = url;
        lastDispatchedAuthTime = now;

        CoreRuntimeState.authPending = true;
        CoreRuntimeState.pendingAuthUrl = url;
        Log.i(TAG, "Launching Google OAuth in external browser: "
                + SensitiveDataRedactor.safeUrl(url));
        oauth.openInBrowser(appContext, url);
    }

    private void handleSessionUrl(final String url) {
        if (url == null || url.trim().isEmpty()) return;
        final String sessionUrl = url.trim();

        if (!CoreRuntimeState.isValidSessionUrl(sessionUrl)) {
            Log.w(TAG, "Invalid session URL rejected: " + SensitiveDataRedactor.safeUrl(sessionUrl));
            return;
        }

        long now = System.currentTimeMillis();
        if (sessionUrl.equals(lastDispatchedSessionUrl) && (now - lastDispatchedSessionTime) < 2000) {
            return;
        }
        lastDispatchedSessionUrl = sessionUrl;
        lastDispatchedSessionTime = now;

        CoreRuntimeState.authPending = false;
        CoreRuntimeState.pendingAuthUrl = null;
        CoreRuntimeState.onboardingPending = false;
        CoreRuntimeState.serverUrl = sessionUrl;
        CoreRuntimeState.lastError = null;

        Log.i(TAG, "Valid Remote Control session URL captured: "
                + SensitiveDataRedactor.safeUrl(sessionUrl));

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (MainActivity.activeActivity != null) {
                    MainActivity.activeActivity.loadSessionUrl(sessionUrl);
                } else {
                    Intent intent = new Intent(appContext, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.putExtra("session_url", sessionUrl);
                    try {
                        appContext.startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed launching MainActivity with session URL", e);
                    }
                }
            }
        });
    }
}
