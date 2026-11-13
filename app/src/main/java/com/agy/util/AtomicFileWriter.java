package com.agy.util;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;

/**
 * Publishes files atomically: each writer stages a unique sibling temporary,
 * fsyncs it, then renames it over the target.
 *
 * R25/C07: concurrent writers never share a temp path, a failed rename keeps
 * the previous target intact (there is no delete-then-rename or in-place
 * fallback), and failures are reported to the caller instead of being called
 * "atomic".
 */
public final class AtomicFileWriter {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private AtomicFileWriter() {
    }

    /** Text write. Returns true only when the new content was published. */
    public static boolean writeText(File target, String content) {
        if (target == null || content == null) {
            return false;
        }
        return publish(target, content.getBytes(UTF_8), true);
    }

    /**
     * Byte write used by the prefix/runtime rewriters. Preserves the target's
     * existing permissions when it already exists. Returns true when published.
     */
    public static boolean writeBytes(File file, byte[] data) {
        if (file == null || data == null) {
            return false;
        }
        return publish(file, data, true);
    }

    private static boolean publish(File target, byte[] data, boolean preservePermissions) {
        File dir = target.getParentFile();
        if (dir == null) {
            dir = new File(".");
        }
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            return false;
        }
        File tmp = null;
        try {
            // Unique per writer: two concurrent writers never share this path.
            tmp = File.createTempFile(target.getName() + ".", ".tmp", dir);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.flush();
                out.getFD().sync();
            }
            if (preservePermissions && target.isFile()) {
                FileModeUtil.copyPermissions(target, tmp);
            }
            if (!tmp.renameTo(target)) {
                // Leave the previous target untouched; the caller sees failure.
                return false;
            }
            tmp = null;
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (tmp != null) {
                IoUtil.deleteQuietly(tmp);
            }
        }
    }
}
