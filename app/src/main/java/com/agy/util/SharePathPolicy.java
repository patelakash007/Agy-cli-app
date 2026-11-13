package com.agy.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Containment policy for files the user explicitly shares out of this app
 * (F02/R34).
 *
 * Only regular files below {@code filesDir/share} are addressable, and URIs
 * carry a relative id rather than an absolute app path. Credentials
 * ({@code .gemini}), the CLI, the package prefix, logs and the rest of app data
 * are therefore outside the share namespace even for a receiver that holds a
 * grant. Canonical resolution also rejects symlink escapes.
 */
public final class SharePathPolicy {

    public static final String SHARE_DIR = "share";
    public static final int MAX_ID_BYTES = 1024;

    private SharePathPolicy() {
    }

    /** Shares are read-only; any write mode is refused. */
    public static boolean isReadOnlyMode(String mode) {
        return "r".equals(mode);
    }

    /**
     * Normalizes a relative "/"-separated id. Returns null for an empty,
     * absolute-looking, traversal-bearing, overlong or control-character id.
     */
    public static String normalizeRelative(String id) {
        if (id == null) {
            return null;
        }
        String value = id.replace('\\', '/');
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > MAX_ID_BYTES) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return null;
            }
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (String part : value.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return null;
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(part);
        }
        return normalized.toString();
    }

    /**
     * Resolves a URI path to a canonical file inside {@code shareRoot}, or null
     * when the id is unsafe, outside the root, or names the root itself.
     */
    public static File resolve(File shareRoot, String id) throws IOException {
        if (shareRoot == null || id == null) {
            return null;
        }
        String normalized = normalizeRelative(id);
        if (normalized == null) {
            return null;
        }
        File root = shareRoot.getCanonicalFile();
        File candidate = new File(root, normalized).getCanonicalFile();
        if (candidate.equals(root) || !PathSafety.isInside(root, candidate)) {
            return null;
        }
        return candidate;
    }

    /**
     * Returns the relative id for a file inside {@code shareRoot}, or null when
     * the file is the root itself or lies outside it (including via a symlink).
     */
    public static String relativeId(File shareRoot, File file) throws IOException {
        if (shareRoot == null || file == null) {
            return null;
        }
        File root = shareRoot.getCanonicalFile();
        File target = file.getCanonicalFile();
        if (target.equals(root) || !PathSafety.isInside(root, target)) {
            return null;
        }
        String rootPath = root.getAbsolutePath();
        String targetPath = target.getAbsolutePath();
        if (!targetPath.startsWith(rootPath + File.separator)) {
            return null;
        }
        String relative = targetPath.substring(rootPath.length() + 1)
                .replace(File.separatorChar, '/');
        String normalized = normalizeRelative(relative);
        return relative.equals(normalized) ? relative : null;
    }
}
