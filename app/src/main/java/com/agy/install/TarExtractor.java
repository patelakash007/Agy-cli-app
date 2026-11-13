package com.agy.install;

import android.system.ErrnoException;
import android.system.Os;

import com.agy.util.FileModeUtil;
import com.agy.util.PathSafety;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Pure-Java tar fallback. Every header goes through {@link TarPolicy}; this
 * class only performs the Android-specific materialization (files, dirs, links)
 * and enforces archive-wide entry/total bounds and duplicate rejection.
 */
public final class TarExtractor {

    private TarExtractor() {
    }

    public static int untarGz(InputStream raw, File dest) throws IOException {
        try (InputStream in = new GZIPInputStream(new BufferedInputStream(raw, 1 << 16), 1 << 16)) {
            return untar(in, dest);
        }
    }

    /** @return number of symlinks materialized from the archive. */
    public static int untar(InputStream in, File dest) throws IOException {
        byte[] header = new byte[512];
        String pendingName = null;
        Set<String> seen = new HashSet<>();
        int links = 0;
        int entries = 0;
        long totalBytes = 0;

        while (TarHeader.readBlock(in, header)) {
            if (TarHeader.isZeroBlock(header)) {
                break;
            }
            TarPolicy.Entry entry = TarPolicy.parse(header);

            if (entry.type == 'L') {
                if (pendingName != null) {
                    throw new IOException("nested GNU long-name entry");
                }
                byte[] nameBytes = readLongName(in, entry.size);
                String longName = new String(nameBytes, StandardCharsets.UTF_8).trim();
                String normalized = TarPolicy.normalizeName(longName);
                if (normalized == null || normalized.isEmpty()) {
                    throw new IOException("unsafe GNU long-name entry");
                }
                pendingName = normalized;
                continue;
            }

            entries++;
            if (entries > TarPolicy.MAX_ENTRIES) {
                throw new IOException("tar archive has too many entries");
            }
            String name = entry.name;
            if (pendingName != null) {
                name = pendingName;
                pendingName = null;
            }
            if (name.isEmpty()) {
                skipEntry(in, entry.size);
                continue;
            }
            if (!seen.add(name)) {
                throw new IOException("duplicate tar entry: " + name);
            }
            totalBytes += entry.size;
            if (totalBytes > TarPolicy.MAX_TOTAL_BYTES) {
                throw new IOException("tar archive expands beyond the allowed total size");
            }

            File out = new File(dest, name);
            ensureParentInside(dest, out);

            if (entry.type == '5') {
                skipEntry(in, entry.size);
                removeConflictingLeaf(out, true);
                if (!out.isDirectory() && !out.mkdirs() && !out.isDirectory()) {
                    throw new IOException("cannot create dir " + out);
                }
                FileModeUtil.applyMode(out, entry.mode);
            } else if (entry.type == '0') {
                removeConflictingLeaf(out, false);
                writeFile(in, out, entry.size, dest);
                FileModeUtil.applyMode(out, entry.mode);
            } else if (entry.type == '2') {
                skipEntry(in, entry.size);
                validateLinkTarget(dest, out, entry.link);
                removeConflictingLeaf(out, false);
                try {
                    Os.symlink(entry.link, out.getAbsolutePath());
                } catch (ErrnoException e) {
                    throw new IOException("cannot create symlink " + out + " -> " + entry.link, e);
                }
                links++;
            } else {
                // TarPolicy already rejected unsupported types; keep a defensive guard.
                skipEntry(in, entry.size);
                throw new IOException("unsupported tar entry type " + (char) entry.type + " at " + name);
            }
        }

        if (pendingName != null) {
            throw new IOException("dangling GNU long-name entry");
        }
        return links;
    }

    private static void validateLinkTarget(File dest, File linkFile, String target) throws IOException {
        TarPolicy.validateLinkTarget(target);
        File resolved = new File(linkFile.getParentFile(), target);
        if (!PathSafety.isInside(dest, resolved)) {
            throw new IOException("tar symlink escapes env prefix: " + linkFile + " -> " + target);
        }
    }

    private static void ensureParentInside(File dest, File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !PathSafety.isInside(dest, parent)) {
            throw new IOException("tar entry parent escapes env prefix: " + out);
        }
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create parent " + parent);
        }
    }

    private static void removeConflictingLeaf(File out, boolean allowDirectory) throws IOException {
        if (TextFileDetector.isSymlink(out)) {
            try {
                Os.remove(out.getAbsolutePath());
            } catch (ErrnoException e) {
                throw new IOException("cannot replace tar symlink " + out, e);
            }
            return;
        }
        if (!out.exists()) {
            return;
        }
        if (out.isDirectory()) {
            if (allowDirectory) {
                return;
            }
            if (!out.delete()) {
                throw new IOException("cannot replace non-empty tar directory " + out);
            }
            return;
        }
        if (!out.delete()) {
            throw new IOException("cannot replace tar file " + out);
        }
    }

    private static byte[] readLongName(InputStream in, long size) throws IOException {
        if (size < 0 || size > TarPolicy.MAX_LONG_NAME_BYTES) {
            throw new IOException("unreasonable GNU long-name size: " + size);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
        copy(in, out, size);
        skipPadding(in, size);
        return out.toByteArray();
    }

    private static void skipEntry(InputStream in, long size) throws IOException {
        skipExactly(in, size);
        skipPadding(in, size);
    }

    private static void writeFile(InputStream in, File out, long size, File dest) throws IOException {
        ensureParentInside(dest, out);
        try (OutputStream os = new FileOutputStream(out)) {
            copy(in, os, size);
        }
        skipPadding(in, size);
    }

    private static void copy(InputStream in, OutputStream out, long size) throws IOException {
        if (size < 0) {
            throw new IOException("negative tar entry size: " + size);
        }
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("truncated tar entry");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipExactly(InputStream in, long size) throws IOException {
        if (size <= 0) {
            return;
        }
        long remaining = size;
        byte[] buffer = new byte[8192];
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("truncated tar entry");
            }
            remaining -= read;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipExactly(in, pad);
    }
}
