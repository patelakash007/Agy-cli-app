package com.agy.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Deterministic policy for the downloaded native CLI installer (B06).
 *
 * This class is Android-free so every rule is exercised by offline host tests.
 * It covers the manifest/artifact origins, digest and version syntax, the ABI
 * gate, the conservative free-space preflight, the install-record codec bounds
 * and the AArch64 Android ELF verification. Network, staging and publication
 * live in {@link CliInstaller}.
 */
public final class CliInstallPolicy {

    /** Exact vendor manifest for the Android ARM64 CLI. */
    public static final String MANIFEST_URL =
            "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app"
                    + "/manifests/android_arm64.json";

    /** Only the vendor's public object host may serve the CLI archive. */
    public static final String ARTIFACT_HOST = "storage.googleapis.com";
    public static final String ARTIFACT_PATH_PREFIX = "/antigravity-public/antigravity-cli/";
    public static final String ARTIFACT_SUFFIX = ".tar.gz";

    public static final long MAX_MANIFEST_BYTES = 64L * 1024;
    public static final long MIN_ARCHIVE_BYTES = 1024L * 1024;
    public static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;
    public static final long MAX_BINARY_BYTES = 256L * 1024 * 1024;
    public static final long FREE_SPACE_RESERVE_BYTES = 32L * 1024 * 1024;

    public static final String REQUIRED_ABI = "arm64-v8a";
    public static final String ANDROID_LINKER = "/system/bin/linker64";

    private static final int ELF_HEADER_BYTES = 64;
    private static final int EM_AARCH64 = 0xB7;

    private CliInstallPolicy() {
    }

    /** The manifest location is a single fixed HTTPS resource, not a family. */
    public static boolean isValidManifestUrl(String url) {
        return MANIFEST_URL.equals(url);
    }

    /**
     * Strict artifact origin: HTTPS, exact host, no credentials/port/query/
     * fragment, vendor path prefix, plain {@code .tar.gz} name and no encoded
     * separators or traversal segments.
     */
    public static boolean isValidArtifactUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > 2048) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= 0x20 || c == 0x7F || c == '\\') {
                return false;
            }
        }
        URI uri;
        try {
            uri = new URI(url);
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
        if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(ARTIFACT_HOST)) {
            return false;
        }
        if (uri.getPort() != -1) {
            return false;
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return false;
        }
        String path = uri.getRawPath();
        if (path == null || !path.startsWith(ARTIFACT_PATH_PREFIX)
                || !path.endsWith(ARTIFACT_SUFFIX)) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%2e")
                || path.contains("//")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /** Lower-case 128-hex-character SHA-512, or null when malformed. */
    public static String normalizeSha512(String value) {
        return normalizeHex(value, 128);
    }

    /** Lower-case 64-hex-character SHA-256, or null when malformed. */
    public static String normalizeSha256(String value) {
        return normalizeHex(value, 64);
    }

    private static String normalizeHex(String value, int digits) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        if (normalized.length() != digits) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return null;
            }
        }
        return normalized;
    }

    /** Manifest version is metadata only; keep it printable and bounded. */
    public static boolean isValidVersion(String version) {
        if (version == null || version.isEmpty() || version.length() > 64) {
            return false;
        }
        char first = version.charAt(0);
        if (!isAsciiLetterOrDigit(first)) {
            return false;
        }
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (isAsciiLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                continue;
            }
            return false;
        }
        return true;
    }

    /** The initial supported runtime is Android ARM64/Bionic only. */
    public static boolean isArm64Device(String[] supportedAbis) {
        if (supportedAbis == null) {
            return false;
        }
        for (String abi : supportedAbis) {
            if (abi != null && REQUIRED_ABI.equalsIgnoreCase(abi.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPlausibleArchiveSize(long bytes) {
        return bytes >= MIN_ARCHIVE_BYTES && bytes <= MAX_ARCHIVE_BYTES;
    }

    /**
     * Peak usage is the archive itself, its extracted contents and the
     * publication copy that lives beside the old binary. Requiring roughly
     * three times the compressed size plus a reserve is deliberately
     * conservative; an unknown size returns -1 (caller must refuse).
     */
    public static long requiredFreeBytes(long archiveBytes) {
        if (!isPlausibleArchiveSize(archiveBytes)) {
            return -1L;
        }
        long peak = archiveBytes * 3L;
        return peak + FREE_SPACE_RESERVE_BYTES;
    }

    public static boolean hasFreeSpace(long availableBytes, long archiveBytes) {
        long required = requiredFreeBytes(archiveBytes);
        return required > 0 && availableBytes >= required;
    }

    /**
     * Verifies the staged install is a 64-bit little-endian AArch64 Android
     * executable. Returns null when acceptable, otherwise a short reason.
     */
    public static String elfProblem(File file) {
        if (file == null || !file.isFile()) {
            return "binary is missing";
        }
        long length = file.length();
        if (length < ELF_HEADER_BYTES) {
            return "binary is truncated";
        }
        if (length > MAX_BINARY_BYTES) {
            return "binary exceeds the allowed size";
        }
        byte[] header = new byte[ELF_HEADER_BYTES];
        try (FileInputStream in = new FileInputStream(file)) {
            if (readFully(in, header) != ELF_HEADER_BYTES) {
                return "binary header is truncated";
            }
        } catch (IOException e) {
            return "binary header is unreadable";
        }
        if (header[0] != 0x7F || header[1] != 'E' || header[2] != 'L' || header[3] != 'F') {
            return "binary is not an ELF file";
        }
        if (header[4] != 2) {
            return "binary is not ELF64";
        }
        if (header[5] != 1) {
            return "binary is not little-endian";
        }
        int machine = (header[18] & 0xFF) | ((header[19] & 0xFF) << 8);
        if (machine != EM_AARCH64) {
            return "binary is not AArch64";
        }
        if (!containsAndroidLinker(file)) {
            return "binary is not linked for Android (" + ANDROID_LINKER + " missing)";
        }
        return null;
    }

    private static boolean containsAndroidLinker(File file) {
        byte[] needle = ANDROID_LINKER.getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[64 * 1024 + needle.length];
        int carry = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            while (true) {
                int read = in.read(buffer, carry, 64 * 1024);
                if (read <= 0) {
                    return false;
                }
                int total = carry + read;
                if (indexOf(buffer, total, needle) >= 0) {
                    return true;
                }
                carry = Math.min(needle.length - 1, total);
                System.arraycopy(buffer, total - carry, buffer, 0, carry);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static int indexOf(byte[] haystack, int haystackLength, byte[] needle) {
        int limit = haystackLength - needle.length;
        for (int i = 0; i <= limit; i++) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return i;
            }
        }
        return -1;
    }

    private static int readFully(FileInputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                break;
            }
            offset += read;
        }
        return offset;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }
}
