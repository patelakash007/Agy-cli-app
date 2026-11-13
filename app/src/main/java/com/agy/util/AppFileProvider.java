package com.agy.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Read-only ContentProvider for files the user explicitly shares out of this
 * app (F02/R34).
 *
 * The provider is deliberately narrow: only regular files under
 * {@code filesDir/share} are reachable, URIs carry a relative id instead of an
 * absolute app path, write modes are refused, and traversal/symlink escapes are
 * rejected by {@link SharePathPolicy}. Credentials, the CLI, the package prefix,
 * logs and the rest of app data cannot be addressed even by a receiver that
 * already holds a URI grant.
 */
public class AppFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    public static File getShareRoot(Context context) {
        return new File(context.getFilesDir(), SharePathPolicy.SHARE_DIR);
    }

    /**
     * Builds a content URI for a file inside the share root. Throws for any
     * other path so a caller cannot mint a URI for credentials or runtime state.
     */
    public static Uri getUriForFile(Context context, File file) {
        String relative;
        try {
            relative = SharePathPolicy.relativeId(getShareRoot(context), file);
        } catch (IOException e) {
            relative = null;
        }
        if (relative == null) {
            throw new IllegalArgumentException(
                    "Only files inside the app share directory can be shared");
        }
        String authority = context.getPackageName() + ".fileprovider";
        return new Uri.Builder()
                .scheme("content")
                .authority(authority)
                .path(relative)
                .build();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!SharePathPolicy.isReadOnlyMode(mode)) {
            throw new FileNotFoundException("AppFileProvider serves read-only shares only");
        }
        File file = resolve(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException("Share not found");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            String extension = name.substring(lastDot + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection, 0);
        File file;
        try {
            file = resolve(uri);
        } catch (FileNotFoundException e) {
            return cursor;
        }
        if (!file.isFile()) {
            return cursor;
        }
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(file.length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || uri == null) {
            throw new FileNotFoundException("Provider has no context");
        }
        File resolved;
        try {
            resolved = SharePathPolicy.resolve(getShareRoot(context), uri.getPath());
        } catch (IOException e) {
            resolved = null;
        }
        if (resolved == null) {
            throw new FileNotFoundException("Unresolvable share id");
        }
        return resolved;
    }
}
