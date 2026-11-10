package com.agy.oauth;

import android.net.Uri;

import java.util.Locale;

/** Recognizes the URLs that must leave the WebView for the system browser. */
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
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        String path = uri.getPath();
        if (path == null) {
            path = "";
        }
        String pathLower = path.toLowerCase(Locale.US);

        // Remote Control session URLs (/r/<session-id>) are strictly studio session URLs, not auth URLs
        if (pathLower.contains("/r/")) {
            return false;
        }

        if (host.equals("accounts.google.com") || host.endsWith(".accounts.google.com")
                || host.equals("accounts.googleusercontent.com")
                || host.equals("oauth2.googleapis.com")) {
            return true;
        }

        // The OAuth authorize endpoint: google.com/o/oauth2[/v2]/auth
        if ((host.equals("google.com") || host.endsWith(".google.com"))
                && pathLower.contains("/o/oauth2")) {
            return true;
        }

        // The official CLI auth callback and login endpoints
        if (host.equals("antigravity.google") || host.endsWith(".antigravity.google")
                || host.equals("antigravity.google.com") || host.endsWith(".antigravity.google.com")) {
            if (pathLower.contains("/oauth") || pathLower.contains("/auth") || pathLower.contains("/login")) {
                return true;
            }
        }

        String query = uri.getQuery();
        if (query != null) {
            String queryLower = query.toLowerCase(Locale.US);
            if (queryLower.contains("redirect_uri=") && queryLower.contains("oauth")) {
                return true;
            }
            if (queryLower.contains("response_type=code") && queryLower.contains("client_id=")) {
                return true;
            }
        }

        return false;
    }
}
