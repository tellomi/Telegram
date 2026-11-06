package org.telegram.ui.tellomi.feed;

/**
 * Discover video cap (D-41 / v2.1 M3). A clip longer than 60 seconds is not
 * kept and is not uploaded. Exactly 60 seconds is allowed.
 *
 * Image bytes go through {@code /v1/feed/media/uploads}. This gate does not upload
 * a clip. {@link #beforeUpload(long)} still returns {@link Decision#NOT_UPLOADED}
 * for a clip inside the cap, so a text post is not sent while a video is attached.
 *
 * Gallery rows in {@code MediaController.PhotoEntry} store duration as whole
 * seconds ({@code MediaStore} milliseconds / 1000). That truncation is not the
 * compose check. The picker reads milliseconds.
 */
public final class TellomiDiscoverVideo {

    /** Inclusive. 60_000 is allowed. 60_001 is not. */
    public static final long MAX_DURATION_MS = 60_000L;
    public static final int MAX_SECONDS = 60;

    public enum Decision {
        NONE,
        ACCEPT,
        TOO_LONG,
        UNKNOWN,
        NOT_UPLOADED
    }

    private TellomiDiscoverVideo() {
    }

    /**
     * Selection gate. {@code durationMs <= 0} means the length could not be read.
     * Those clips are not uploaded.
     */
    public static Decision select(long durationMs) {
        if (durationMs <= 0L) {
            return Decision.UNKNOWN;
        }
        if (durationMs > MAX_DURATION_MS) {
            return Decision.TOO_LONG;
        }
        return Decision.ACCEPT;
    }

    /**
     * Hard limit immediately before upload. Over 60 seconds or an unknown length
     * is rejected. A length inside the cap is {@link Decision#NOT_UPLOADED} until
     * the engine accepts video bytes.
     */
    public static Decision beforeUpload(long durationMs) {
        Decision selected = select(durationMs);
        if (selected != Decision.ACCEPT) {
            return selected;
        }
        return Decision.NOT_UPLOADED;
    }

    /**
     * {@code MediaController.PhotoEntry.duration} is whole seconds. 60 is inside
     * the cap. 61 is not. Zero is unknown. This is coarser than {@link #select(long)}
     * because the gallery already dropped the remainder under one second.
     */
    public static Decision selectPhotoEntrySeconds(int seconds) {
        if (seconds <= 0) {
            return Decision.UNKNOWN;
        }
        if (seconds > MAX_SECONDS) {
            return Decision.TOO_LONG;
        }
        return select(seconds * 1000L);
    }

    /** Metadata duration string, in milliseconds. Blank or non-positive is 0. */
    public static long parseMillis(String raw) {
        if (raw == null) {
            return 0L;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return 0L;
        }
        try {
            if (trimmed.indexOf('.') >= 0) {
                double value = Double.parseDouble(trimmed);
                if (value <= 0d || value > Long.MAX_VALUE) {
                    return 0L;
                }
                return (long) value;
            }
            long value = Long.parseLong(trimmed);
            return value > 0L ? value : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /** {@code m:ss} for a selected clip. A positive length under one second is {@code 0:01}. */
    public static String clock(long durationMs) {
        long total = durationMs / 1000L;
        if (durationMs > 0L && total == 0L) {
            total = 1L;
        }
        if (total < 0L) {
            total = 0L;
        }
        long minutes = total / 60L;
        long seconds = total % 60L;
        StringBuilder builder = new StringBuilder();
        builder.append(minutes);
        builder.append(':');
        if (seconds < 10L) {
            builder.append('0');
        }
        builder.append(seconds);
        return builder.toString();
    }
}
