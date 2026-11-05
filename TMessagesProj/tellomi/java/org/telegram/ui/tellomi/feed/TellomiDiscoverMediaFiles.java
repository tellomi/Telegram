package org.telegram.ui.tellomi.feed;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Reads a picked photo into memory, stopping once it passes 20MB. */
final class TellomiDiscoverMediaFiles {

    private TellomiDiscoverMediaFiles() {
    }

    static final class Opened {
        final String name;
        final byte[] bytes;
        final TellomiDiscover.ClientError error;

        private Opened(String name, byte[] bytes, TellomiDiscover.ClientError error) {
            this.name = name == null || name.isEmpty() ? "photo" : name;
            this.bytes = bytes;
            this.error = error;
        }
    }

    static Opened read(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) {
            return new Opened(null, null, TellomiDiscover.ClientError.MEDIA_TYPE);
        }
        String name = "photo";
        long size = -1L;
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex);
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (RuntimeException ignored) {
            name = "photo";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (size > TellomiDiscoverMedia.MAX_BYTES) {
            return new Opened(name, null, TellomiDiscover.ClientError.MEDIA_TOO_LARGE);
        }
        InputStream stream = null;
        try {
            stream = resolver.openInputStream(uri);
            if (stream == null) {
                return new Opened(name, null, TellomiDiscover.ClientError.MEDIA_TYPE);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > TellomiDiscoverMedia.MAX_BYTES) {
                    return new Opened(name, null, TellomiDiscover.ClientError.MEDIA_TOO_LARGE);
                }
                out.write(buffer, 0, read);
            }
            byte[] bytes = out.toByteArray();
            TellomiDiscover.ClientError problem = TellomiDiscoverMedia.inspect(bytes);
            if (problem != null) {
                return new Opened(name, null, problem);
            }
            return new Opened(name, bytes, null);
        } catch (Exception ignored) {
            return new Opened(name, null, TellomiDiscover.ClientError.MEDIA_TYPE);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
