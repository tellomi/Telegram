package org.telegram.ui.tellomi.feed;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * Reads a picked video's length in milliseconds. The compose screen passes that
 * number to {@link TellomiDiscoverVideo#select(long)} before it keeps the clip.
 */
public final class TellomiDiscoverVideoDuration {

    private TellomiDiscoverVideoDuration() {
    }

    public static long millis(Context context, Uri uri) {
        if (context == null || uri == null) {
            return 0L;
        }
        long fromFile = retriever(context, uri);
        if (fromFile > 0L) {
            return fromFile;
        }
        return mediaStore(context, uri);
    }

    private static long retriever(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if ("file".equals(uri.getScheme()) && uri.getPath() != null) {
                retriever.setDataSource(uri.getPath());
            } else {
                retriever.setDataSource(context, uri);
            }
            return TellomiDiscoverVideo.parseMillis(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Throwable ignored) {
            return 0L;
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private static long mediaStore(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[] { MediaStore.Video.Media.DURATION }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                if (index >= 0 && !cursor.isNull(index)) {
                    long duration = cursor.getLong(index);
                    return duration > 0L ? duration : 0L;
                }
            }
        } catch (Throwable ignored) {
            return 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0L;
    }
}
