package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.io.EOFException;
import java.io.File;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * ADR-0083 B2. {@code upload.getFileHashes#9156982a} checks a finished
 * download against the plaintext SHA-256 parts Teamgram stores.
 *
 * <p>Parts are 128 KiB, the same size as {@code upload.getCdnFileHashes}
 * ({@code cdn.PartBytes} in teamgram {@code 59922c63} / #215). The last
 * part is shorter. {@code offset} drops parts that start before it.
 * A miss, an empty vector, an RPC error, or a timeout leaves the download
 * alone. A part whose bytes do not match is a failed download.
 *
 * <p>The generated {@code TL_upload_getFileHashes#c7025931} still has the
 * old {@code offset:int}. This request is the current {@code offset:long}
 * method. {@code messages.getDocumentByHash} is a different call and is
 * not used here.
 */
public final class TellomiFileHashes {

    /** {@code upload.getFileHashes#9156982a location:InputFileLocation offset:long}. */
    public static final int CONSTRUCTOR = 0x9156982a;

    /** SHA-256 part size shared with {@code upload.getCdnFileHashes}. */
    public static final int PART_BYTES = 128 * 1024;

    /**
     * After the bytes are on disk, give up waiting for hashes and keep the
     * file. The timer starts when the download is ready to finish, so a
     * hung RPC cannot hold the file.
     */
    public static final int FAIL_OPEN_MS = 12_000;

    /**
     * Account 0 {@code mainconfig} boolean. Missing or true runs the check.
     * False skips the RPC. Read when a download is about to ask.
     */
    public static final String PREF_KEY = "tellomi_get_file_hashes";

    /** Enough parts for a multi-gigabyte file at {@link #PART_BYTES}. */
    static final int MAX_REQUESTS = 20_000;

    /** Unit tests only. {@code null} reads {@link #PREF_KEY}. */
    static volatile Boolean enabledOverride;

    private TellomiFileHashes() {
    }

    /**
     * The check is on unless {@link #PREF_KEY} in {@code mainconfig} is false.
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
     * A full MTProto download whose temp file is the plaintext
     * {@code upload.getFile} returns. CDN downloads already check ciphertext
     * hashes. Web files have no {@code InputFileLocation}. Secret-chat
     * decryption and local cache encryption store different bytes. Preload,
     * a prefix stop, and a file already on disk are not this download.
     */
    public static boolean applies(boolean enabled, boolean hasLocation, boolean webFile, boolean cdn,
                                  boolean preload, boolean alreadyOnDisk, boolean secretEncrypted,
                                  boolean cacheEncrypted, boolean prefixOnly) {
        if (!enabled || !hasLocation) {
            return false;
        }
        if (webFile || cdn || preload || alreadyOnDisk || secretEncrypted || cacheEncrypted || prefixOnly) {
            return false;
        }
        return true;
    }

    /**
     * Teamgram rejects legacy {@code inputFileLocation} with
     * {@code INPUT_REQUEST_INVALID}. There is no hash to compare.
     */
    public static boolean locationSupported(TLRPC.InputFileLocation location) {
        return location != null && !(location instanceof TLRPC.TL_inputFileLocation);
    }

    public enum Verdict {
        MATCH,
        MISMATCH,
        FAIL_OPEN
    }

    /** One {@code fileHash}. The hash bytes are copied. */
    public static final class Part {
        public final long offset;
        public final int limit;
        public final byte[] hash;

        public Part(long offset, int limit, byte[] hash) {
            this.offset = offset;
            this.limit = limit;
            this.hash = hash == null ? null : Arrays.copyOf(hash, hash.length);
        }

        public boolean wellFormed() {
            return offset >= 0 && limit > 0 && limit <= PART_BYTES
                    && offset <= Long.MAX_VALUE - limit
                    && hash != null && hash.length == 32;
        }
    }

    /**
     * SHA-256 parts of {@code body}, using the same offset filter as
     * teamgram {@code cdn.PartHashes}. A negative offset yields nothing;
     * the client does not send one ({@code OFFSET_INVALID} on the server).
     */
    public static List<Part> partsOf(byte[] body, long requestOffset) {
        ArrayList<Part> out = new ArrayList<>();
        if (requestOffset < 0 || body == null || body.length == 0) {
            return out;
        }
        for (int off = 0; off < body.length; off += PART_BYTES) {
            if ((long) off < requestOffset) {
                continue;
            }
            int length = Math.min(PART_BYTES, body.length - off);
            byte[] hash = sha256(body, off, length);
            if (hash == null) {
                return new ArrayList<>();
            }
            out.add(new Part(off, length, hash));
        }
        return out;
    }

    /** Bytes from 0 covered by a contiguous run of well-formed parts. */
    public static long coveredPrefix(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return 0;
        }
        ArrayList<Part> sorted = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part != null && part.wellFormed()) {
                sorted.add(part);
            }
        }
        Collections.sort(sorted, (left, right) -> Long.compare(left.offset, right.offset));
        long cursor = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Part part = sorted.get(i);
            if (part.offset > cursor) {
                break;
            }
            long end = part.offset + part.limit;
            if (end > cursor) {
                cursor = end;
            }
        }
        return cursor;
    }

    /**
     * Next {@code offset} to request so the parts cover {@code fileSize}.
     * {@code -1} when the size is unknown or the prefix already covers it.
     */
    public static long nextOffset(long fileSize, List<Part> parts) {
        if (fileSize <= 0) {
            return -1;
        }
        long cursor = coveredPrefix(parts);
        if (cursor >= fileSize) {
            return -1;
        }
        return cursor;
    }

    public static boolean hasWellFormed(List<Part> parts) {
        if (parts == null) {
            return false;
        }
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part != null && part.wellFormed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compare well-formed parts to {@code body}. No well-formed part is a
     * miss. A part that runs past the body, or whose SHA-256 differs, is a
     * mismatch. When {@code confirmedEnd} is set, a body longer than the
     * contiguous prefix is a mismatch too: the server said the file ends there.
     */
    public static Verdict check(byte[] body, List<Part> parts, boolean confirmedEnd) {
        if (!hasWellFormed(parts)) {
            return Verdict.FAIL_OPEN;
        }
        if (body == null) {
            return Verdict.FAIL_OPEN;
        }
        if (confirmedEnd) {
            long cursor = coveredPrefix(parts);
            if (cursor > 0 && body.length > cursor) {
                return Verdict.MISMATCH;
            }
        }
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part == null || !part.wellFormed()) {
                continue;
            }
            if (part.offset > body.length || part.limit > body.length - part.offset) {
                return Verdict.MISMATCH;
            }
            byte[] digest = sha256(body, (int) part.offset, part.limit);
            if (digest == null || !Arrays.equals(digest, part.hash)) {
                return Verdict.MISMATCH;
            }
        }
        return Verdict.MATCH;
    }

    /**
     * Same rules as {@link #check(byte[], List, boolean)} for a file on disk.
     * A file that cannot be read is fail-open so the download is not blocked
     * by a local read error.
     */
    public static Verdict check(File file, long fileSize, List<Part> parts, boolean confirmedEnd) {
        if (!hasWellFormed(parts)) {
            return Verdict.FAIL_OPEN;
        }
        if (confirmedEnd) {
            long cursor = coveredPrefix(parts);
            if (cursor > 0 && fileSize > cursor) {
                return Verdict.MISMATCH;
            }
        }
        if (file == null || !file.isFile()) {
            return Verdict.FAIL_OPEN;
        }
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            byte[] buffer = new byte[PART_BYTES];
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                if (part == null || !part.wellFormed()) {
                    continue;
                }
                if (part.offset > fileSize || part.limit > fileSize - part.offset) {
                    return Verdict.MISMATCH;
                }
                in.seek(part.offset);
                in.readFully(buffer, 0, part.limit);
                byte[] digest = sha256(buffer, 0, part.limit);
                if (digest == null || !Arrays.equals(digest, part.hash)) {
                    return Verdict.MISMATCH;
                }
            }
            return Verdict.MATCH;
        } catch (EOFException end) {
            return Verdict.MISMATCH;
        } catch (Exception ignored) {
            return Verdict.FAIL_OPEN;
        }
    }

    public static ArrayList<Part> partsFrom(Vector<?> vector) {
        ArrayList<Part> out = new ArrayList<>();
        if (vector == null || vector.objects == null) {
            return out;
        }
        for (int i = 0; i < vector.objects.size(); i++) {
            Object object = vector.objects.get(i);
            if (object instanceof TLRPC.TL_fileHash) {
                TLRPC.TL_fileHash hash = (TLRPC.TL_fileHash) object;
                out.add(new Part(hash.offset, hash.limit, hash.hash));
            }
        }
        return out;
    }

    static byte[] sha256(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(data, offset, length);
            byte[] out = digest.digest();
            if (out == null || out.length != 32) {
                return null;
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * One download's hash RPC. The caller serializes access.
     * An empty page means the server has no further parts ({@link #confirmedEnd()}).
     * An error or a timeout is {@link #stop()} and does not confirm the length.
     */
    public static final class Session {
        private final ArrayList<Part> parts = new ArrayList<>();
        private final HashSet<Long> asked = new HashSet<>();
        private boolean started;
        private boolean stopped;
        private boolean confirmedEnd;

        public boolean started() {
            return started;
        }

        public boolean stopped() {
            return stopped;
        }

        public boolean confirmedEnd() {
            return confirmedEnd;
        }

        public boolean hasParts() {
            return hasWellFormed(parts);
        }

        public ArrayList<Part> parts() {
            return parts;
        }

        /** First request is offset 0. Further calls return {@code -1}. */
        public long open() {
            if (started) {
                return -1;
            }
            started = true;
            asked.add(0L);
            return 0L;
        }

        /** RPC error or timeout. Keep any parts already stored. */
        public void stop() {
            stopped = true;
        }

        /**
         * Merge one page. Returns the next offset to request, or {@code -1}
         * when no further request should be sent.
         */
        public long accept(long fileSize, List<Part> page) {
            if (stopped) {
                return -1;
            }
            if (page == null || page.isEmpty()) {
                confirmedEnd = true;
                stopped = true;
                return -1;
            }
            int added = 0;
            for (int i = 0; i < page.size(); i++) {
                Part part = page.get(i);
                if (part != null && part.wellFormed()) {
                    parts.add(part);
                    added++;
                }
            }
            if (added == 0) {
                stopped = true;
                return -1;
            }
            return requestNext(fileSize);
        }

        /**
         * Next page for a file whose length is now known. {@code -1} when the
         * file is covered, the offset is already in flight, or the session has
         * stopped. An offset already asked is left in flight; it is not a miss.
         */
        public long requestNext(long fileSize) {
            if (stopped) {
                return -1;
            }
            if (asked.size() >= MAX_REQUESTS) {
                stopped = true;
                return -1;
            }
            long next = nextOffset(fileSize, parts);
            if (next < 0 || asked.contains(next)) {
                return -1;
            }
            asked.add(next);
            return next;
        }
    }

    /** {@code upload.getFileHashes#9156982a location:InputFileLocation offset:long = Vector<FileHash>}. */
    public static final class Get extends TLObject {
        public TLRPC.InputFileLocation location;
        public long offset;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return Vector.TLDeserialize(stream, constructor, exception, TLRPC.TL_fileHash::TLdeserialize);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(CONSTRUCTOR);
            location.serializeToStream(stream);
            stream.writeInt64(offset);
        }
    }
}
