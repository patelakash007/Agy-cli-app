package com.agy.install;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic policy for extracting the vendor CLI archive (B05/R09).
 *
 * This class is Android-free so every rule can be exercised by offline host
 * tests. Bootstrap/package extraction is a different policy and must not reuse
 * this silently (D02).
 */
public final class TarPolicy {

    public static final int MAX_ENTRIES = 100_000;
    public static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    public static final int MAX_PATH_BYTES = 4096;
    public static final int MAX_LINK_TARGET_BYTES = 4096;
    public static final int MAX_LONG_NAME_BYTES = 4096;

    private TarPolicy() {
    }

    /** One validated entry header. */
    public static final class Entry {
        public final String name;
        public final long mode;
        public final long size;
        public final int type;
        public final String link;

        Entry(String name, long mode, long size, int type, String link) {
            this.name = name;
            this.mode = mode;
            this.size = size;
            this.type = type;
            this.link = link;
        }
    }

    public static Entry parse(byte[] block) throws IOException {
        TarHeader.verifyChecksum(block);
        long mode = TarHeader.readOctalStrict(block, 100, 8, "mode");
        long size = TarHeader.readOctalStrict(block, 124, 12, "size");
        TarHeader.readOctalStrict(block, 136, 12, "mtime");
        int type = block[156] & 0xFF;
        if (type == 0) {
            type = '0';
        }
        if (size < 0 || size > MAX_ENTRY_BYTES) {
            throw new IOException("tar entry size out of bounds: " + size);
        }
        String name = TarHeader.readField(block, 0, 100);
        String prefix = TarHeader.readField(block, 345, 155);
        if (!prefix.isEmpty()) {
            name = prefix + "/" + name;
        }
        String normalized = normalizeName(name);
        if (normalized == null) {
            throw new IOException("unsafe tar entry path");
        }
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES) {
            throw new IOException("tar entry path too long");
        }
        String link = TarHeader.readField(block, 157, 100);
        if (!isSupportedType(type)) {
            throw new IOException("unsupported tar entry type '"
                    + (char) type + "' at " + normalized);
        }
        if (type == '2') {
            validateLinkTarget(link);
        }
        return new Entry(normalized, mode, size, type, link);
    }

    /**
     * Supported layout: regular files, directories, symlinks and GNU long names.
     * Hardlinks, devices, FIFOs and pax/global metadata are rejected rather than
     * silently skipped or misinterpreted.
     */
    public static boolean isSupportedType(int type) {
        return type == '0' || type == '5' || type == '2' || type == 'L';
    }

    /**
     * Normalizes to a relative "/"-separated path. Returns null for anything
     * absolute, traversal-bearing or containing control characters; returns the
     * empty string for the archive root entry.
     */
    public static String normalizeName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/")) {
            return null;
        }
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.isEmpty() || ".".equals(name)) {
            return "";
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return null;
            }
        }
        List<String> parts = new ArrayList<>();
        for (String part : name.split("/")) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                return null;
            }
            parts.add(part);
        }
        StringBuilder normalized = new StringBuilder();
        for (String part : parts) {
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(part);
        }
        return normalized.toString();
    }

    /**
     * Absolute targets and any relative target containing ".." are rejected: the
     * CLI archive has no legitimate cross-directory link. The caller still
     * verifies the final resolved parent stays inside the staging root.
     */
    public static void validateLinkTarget(String target) throws IOException {
        if (target == null || target.isEmpty() || target.indexOf('\0') >= 0) {
            throw new IOException("empty/invalid tar symlink target");
        }
        if (target.getBytes(StandardCharsets.UTF_8).length > MAX_LINK_TARGET_BYTES) {
            throw new IOException("tar symlink target too long");
        }
        if (target.startsWith("/") || target.startsWith("\\")) {
            throw new IOException("absolute tar symlink target is not allowed: " + target);
        }
        String normalized = target.replace('\\', '/');
        for (String part : normalized.split("/")) {
            if ("..".equals(part)) {
                throw new IOException("tar symlink target escapes the staging root: " + target);
            }
        }
    }
}
