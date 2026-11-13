package com.agy.install;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Low-level ustar header field parsing. */
public final class TarHeader {

    private TarHeader() {
    }

    public static boolean readBlock(InputStream in, byte[] block) throws IOException {
        int off = 0;
        while (off < block.length) {
            int read = in.read(block, off, block.length - off);
            if (read < 0) {
                if (off == 0) {
                    return false;
                }
                throw new IOException("truncated tar header");
            }
            off += read;
        }
        return true;
    }

    public static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public static String readField(byte[] block, int off, int len) {
        int end = off + len;
        int i = off;
        while (i < end && block[i] != 0) {
            i++;
        }
        return new String(block, off, i - off, StandardCharsets.UTF_8);
    }

    /** Lenient parser retained for compatibility; returns 0 for empty/malformed. */
    public static long readOctal(byte[] block, int off, int len) {
        try {
            return readOctalStrict(block, off, len, "octal field");
        } catch (IOException | RuntimeException malformed) {
            return 0;
        }
    }

    /**
     * Strict ustar numeric field: leading spaces/NULs, octal digits, optional
     * trailing space/NUL. Malformed characters, overflow and negatives fail.
     */
    public static long readOctalStrict(byte[] block, int off, int len, String field)
            throws IOException {
        if (block == null || off < 0 || len <= 0 || off + len > block.length) {
            throw new IOException("invalid " + field + " field bounds");
        }
        int end = off + len;
        int i = off;
        while (i < end && (block[i] == ' ' || block[i] == 0)) {
            i++;
        }
        int start = i;
        long value = 0;
        while (i < end && block[i] >= '0' && block[i] <= '7') {
            if (value > (Long.MAX_VALUE >> 3)) {
                throw new IOException("overflow in " + field);
            }
            value = (value << 3) + (block[i] - '0');
            i++;
        }
        if (i == start) {
            throw new IOException("missing " + field);
        }
        while (i < end && (block[i] == ' ' || block[i] == 0)) {
            i++;
        }
        if (i != end) {
            throw new IOException("malformed " + field);
        }
        return value;
    }

    /** True when the ustar checksum field matches the header bytes. */
    public static boolean hasValidChecksum(byte[] block) {
        try {
            long expected = readOctalStrict(block, 148, 8, "checksum");
            long actual = 0;
            for (int i = 0; i < 512; i++) {
                int value = block[i] & 0xFF;
                if (i >= 148 && i < 156) {
                    value = ' ';
                }
                actual += value;
            }
            return actual == expected;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    public static void verifyChecksum(byte[] block) throws IOException {
        if (block == null || block.length != 512) {
            throw new IOException("tar header must be 512 bytes");
        }
        if (!hasValidChecksum(block)) {
            throw new IOException("tar header checksum mismatch");
        }
    }
}
