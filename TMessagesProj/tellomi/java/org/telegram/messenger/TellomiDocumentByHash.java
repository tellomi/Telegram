package org.telegram.messenger;

import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * ADR-0082 / ADR-0083 B1 / TDLib {@code FileHashUploader} rules for
 * {@code messages.getDocumentByHash}.
 *
 * <p>The current method is {@code messages.getDocumentByHash#b1f2061f}
 * ({@code sha256:bytes size:long mime_type:string}). A miss, an empty
 * document, an RPC error, a timeout, or a hash failure falls through to
 * the existing upload. A file the client is about to transcode is not the
 * bytes that will be stored, so that send stays on the upload path.
 *
 * <p>Compressed photos ({@code inputMediaUploadedPhoto}, delayed type 0,
 * and album slots that upload {@code FileTypePhoto}) stay on the upload
 * path. This RPC returns a {@code Document}. Those sends need a photo, and
 * the bytes are a generated JPEG from {@code generatePhotoSizes}, so hashing
 * the gallery original would not match the upload. Send-as-file images and
 * files {@code ImageLoader.shouldSendImageAsDocument} routes already use the
 * document path and are looked up. Server-side photo reuse still applies
 * after the photo bytes are uploaded. A later photo lookup needs an RPC that
 * returns an {@code InputPhoto}.
 */
public final class TellomiDocumentByHash {

    /** {@code messages.getDocumentByHash#b1f2061f}. */
    public static final int CONSTRUCTOR = 0xb1f2061f;

    /**
     * Give up and upload if hashing plus the RPC have not finished.
     * The timer starts before the hash, on purpose: a large non-convert
     * video must not sit behind a long SHA-256. The late result is dropped
     * and the upload that already started is the send.
     */
    public static final int FAIL_OPEN_MS = 12_000;

    /**
     * Account 0 {@code mainconfig} boolean. Missing or true keeps the lookup.
     * False skips it and uses the existing upload. Read on each send.
     */
    public static final String PREF_KEY = "tellomi_get_document_by_hash";

    /** Unit tests only. {@code null} reads {@link #PREF_KEY}. */
    static volatile Boolean enabledOverride;

    public static final int NONE = 0;
    public static final int GIF = 1;
    public static final int DOCUMENT = 2;
    public static final int VIDEO = 3;

    private TellomiDocumentByHash() {
    }

    /**
     * Lookup is on unless {@link #PREF_KEY} in {@code mainconfig} is false.
     * A missing context or a prefs failure stays on, matching a fresh install.
     */
    public static boolean enabled() {
        Boolean override = enabledOverride;
        if (override != null) {
            return override.booleanValue();
        }
        return readPreference();
    }

    private static boolean readPreference() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return true;
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences == null) {
                return true;
            }
            return preferences.getBoolean(PREF_KEY, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Which send is allowed to ask the server before uploading.
     * {@code needsConvert}, secret chats, http/content sources, live photos,
     * a separate cover, and attached mask documents stay on the upload path.
     */
    public static int kind(String mime, boolean animated, boolean videoAttribute, boolean voice,
                           boolean needsConvert, boolean encrypted, boolean remoteOrContent,
                           boolean livePhoto, boolean customCover, boolean hasMasks) {
        if (needsConvert || encrypted || remoteOrContent || livePhoto || customCover || hasMasks || voice) {
            return NONE;
        }
        if (mime == null || mime.isEmpty()) {
            return NONE;
        }
        String normalized = mime.toLowerCase(Locale.US);
        if ("image/gif".equals(normalized) || animated) {
            return GIF;
        }
        if (videoAttribute || normalized.startsWith("video/")) {
            return VIDEO;
        }
        return DOCUMENT;
    }

    public static String kindName(int kind) {
        switch (kind) {
            case GIF:
                return "gif";
            case DOCUMENT:
                return "document";
            case VIDEO:
                return "video";
            default:
                return "none";
        }
    }

    /** Server hit that can be sent as {@code inputDocument}. */
    public static boolean reusable(boolean empty, long id, long accessHash, int dcId) {
        return !empty && id != 0 && accessHash != 0 && dcId > 0;
    }

    /**
     * Mime stored with the bytes. Prefer the document mime the upload would
     * send. Fall back to a few extensions. Unknown types return null so the
     * caller uploads instead of asking with a guessed mime.
     */
    public static String mimeFor(String documentMime, String path) {
        if (documentMime != null) {
            String trimmed = documentMime.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        if (path == null) {
            return null;
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot < slash || dot == path.length() - 1) {
            return null;
        }
        String ext = path.substring(dot + 1).toLowerCase(Locale.US);
        switch (ext) {
            case "gif":
                return "image/gif";
            case "mp4":
                return "video/mp4";
            case "webm":
                return "video/webm";
            case "webp":
                return "image/webp";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            default:
                return null;
        }
    }

    public static boolean isLocalFilePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        return !lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("content:");
    }

    /** SHA-256 of the whole file, or null if it cannot be read. */
    public static byte[] sha256(File file) {
        return sha256(file, null);
    }

    /**
     * SHA-256 of the length captured at the start of the read.
     * {@code sizeOut[0]} is that length when the hash succeeds.
     */
    public static byte[] sha256(File file, long[] sizeOut) {
        if (file == null || !file.isFile()) {
            return null;
        }
        long size = file.length();
        if (size <= 0) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            long readTotal = 0;
            while (readTotal < size) {
                int want = (int) Math.min(buffer.length, size - readTotal);
                int n = in.read(buffer, 0, want);
                if (n < 0) {
                    return null;
                }
                digest.update(buffer, 0, n);
                readTotal += n;
            }
            byte[] out = digest.digest();
            if (out == null || out.length != 32) {
                return null;
            }
            if (sizeOut != null && sizeOut.length > 0) {
                sizeOut[0] = size;
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }
}
