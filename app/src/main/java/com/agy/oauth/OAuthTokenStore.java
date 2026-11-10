package com.agy.oauth;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/** Null-safe handle to the core's persisted OAuth token file. */
public final class OAuthTokenStore {

    /** Longest token snapshot we inspect; the real file is a small JSON blob. */
    private static final int MAX_SCAN = 8192;

    private OAuthTokenStore() {
    }

    private static File[] candidateFiles(Context context) {
        if (context == null) {
            return new File[0];
        }
        File filesDir = context.getFilesDir();
        File geminiDir = new File(filesDir, OAuthConstants.TOKEN_DIR);
        return new File[]{
                new File(new File(geminiDir, OAuthConstants.CLI_DIR), OAuthConstants.TOKEN_NAME),
                new File(geminiDir, OAuthConstants.TOKEN_NAME),
                new File(geminiDir, OAuthConstants.LEGACY_TOKEN_NAME),
                new File(new File(geminiDir, "antigravity-app"), OAuthConstants.TOKEN_NAME)
        };
    }

    public static File tokenFile(Context context) {
        if (context == null) {
            return null;
        }
        File[] candidates = candidateFiles(context);
        for (File f : candidates) {
            if (f.isFile() && f.length() > 0) {
                return f;
            }
        }
        return candidates[0];
    }

    /**
     * True once the official CLI has persisted a real token in the app private home.
     * Checks all candidate token file locations.
     */
    public static boolean hasToken(Context context) {
        if (context == null) {
            return false;
        }
        File[] candidates = candidateFiles(context);
        for (File file : candidates) {
            if (file != null && file.isFile() && file.length() > 0) {
                try (FileInputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[(int) Math.min(file.length(), MAX_SCAN)];
                    int n = in.read(buf);
                    if (n > 0) {
                        String text = new String(buf, 0, n, StandardCharsets.UTF_8);
                        if (!isNullOrBlankToken(text)) {
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /**
     * True when {@code text} is missing a token or carries an explicit JSON
     * {@code null} for it (the CLI's not-logged-in placeholder).
     */
    private static boolean isNullOrBlankToken(String text) {
        if (text == null) {
            return true;
        }
        String t = text.trim();
        if (t.isEmpty() || t.equals("{}")) {
            return true;
        }
        int at = t.indexOf("\"token\"");
        if (at >= 0) {
            int colon = t.indexOf(':', at);
            if (colon >= 0) {
                int i = colon + 1;
                while (i < t.length() && Character.isWhitespace(t.charAt(i))) {
                    i++;
                }
                if (t.startsWith("null", i)) {
                    return true;
                }
            }
        }
        // If it contains access_token, it is definitely a valid token payload
        if (t.contains("\"access_token\"")) {
            return false;
        }
        return at < 0;
    }
}
