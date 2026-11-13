package com.agy.oauth;

import android.net.Uri;

import java.util.Locale;

/**
 * Recognizes the exact URLs that may leave the WebView for the system browser.
 *
 * B02/B04a: HTTPS only, exact hosts, and no "arbitrary host that happens to
 * contain redirect_uri=oauth" heuristic. A crafted URL must not be treated as
 * an auth endpoint just because of its query string.
 */
public final class AuthUrlMatcher {

    public boolean isAuthUrl(String url) {
        if (url == null) {
            return false;
        }
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        String path = uri.getPath();
        if (path == null) {
            path = "";
        }
        String pathLower = path.toLowerCase(Locale.US);

        // Remote Control session URLs (/r/<session-id>) are never auth URLs.
        if (pathLower.contains("/r/")) {
            return false;
        }

        if (host.equals("accounts.google.com")
                || host.equals("accounts.googleusercontent.com")
                || host.equals("oauth2.googleapis.com")) {
            return true;
        }

        if (host.equals("google.com")
                && (pathLower.startsWith("/o/oauth2") || pathLower.startsWith("/accounts"))) {
            return true;
        }

        if (host.equals("antigravity.google.com")
                && (pathLower.startsWith("/oauth")
                || pathLower.startsWith("/auth")
                || pathLower.startsWith("/login"))) {
            return true;
        }

        return false;
    }
}
