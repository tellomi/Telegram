package org.telegram.messenger;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiFileHashesTest {

    @Test
    public void constructorIsTheCurrentLongOffsetMethod() {
        assertEquals(0x9156982a, TellomiFileHashes.CONSTRUCTOR);
        assertEquals(128 * 1024, TellomiFileHashes.PART_BYTES);
        assertEquals(12_000, TellomiFileHashes.FAIL_OPEN_MS);
        String rules = read("tellomi/java/org/telegram/messenger/TellomiFileHashes.java");
        assertTrue(rules.contains("stream.writeInt32(CONSTRUCTOR)"));
        assertTrue(rules.contains("stream.writeInt64(offset)"));
        assertFalse(rules.contains("0xc7025931"));
        assertFalse(rules.contains("writeInt32(offset)"));
        String legacy = read("src/main/java/org/telegram/tgnet/TLRPC.java");
        int at = legacy.indexOf("class TL_upload_getFileHashes");
        assertTrue(at > 0);
        String oldMethod = legacy.substring(at, legacy.indexOf("class TL_upload_webFile", at));
        assertTrue(oldMethod.contains("0xc7025931"));
        assertTrue(oldMethod.contains("stream.writeInt32(offset)"));
    }

    @Test
    public void killSwitchDefaultsOn() {
        assertEquals("tellomi_get_file_hashes", TellomiFileHashes.PREF_KEY);
        TellomiFileHashes.enabledOverride = null;
        assertTrue(TellomiFileHashes.enabled());
        TellomiFileHashes.enabledOverride = Boolean.FALSE;
        try {
            assertFalse(TellomiFileHashes.enabled());
            assertFalse(plainDownload(false));
        } finally {
            TellomiFileHashes.enabledOverride = null;
        }
        assertTrue(plainDownload(true));
        String rules = read("tellomi/java/org/telegram/messenger/TellomiFileHashes.java");
        assertTrue(rules.contains("mainconfig"));
        assertTrue(rules.contains("getBoolean(PREF_KEY, true)"));
        String upload = read("tellomi/java/org/telegram/messenger/TellomiDocumentByHash.java");
        assertTrue(upload.contains("tellomi_get_document_by_hash"));
        assertFalse(upload.contains("tellomi_get_file_hashes"));
    }

    @Test
    public void onlyAPlainFullDownloadIsChecked() {
        assertTrue(plainDownload(true));
        assertFalse(TellomiFileHashes.applies(true, false, false, false, false, false, false, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, true, false, false, false, false, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, true, false, false, false, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, false, true, false, false, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, false, false, true, false, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, false, false, false, true, false, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, false, false, false, false, true, false));
        assertFalse(TellomiFileHashes.applies(true, true, false, false, false, false, false, false, true));
        String rules = read("tellomi/java/org/telegram/messenger/TellomiFileHashes.java");
        assertTrue(rules.contains("TL_inputFileLocation"));
    }

    @Test
    public void partsMatchCdnPartHashes() throws Exception {
        byte[] body = new byte[TellomiFileHashes.PART_BYTES + 80];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        List<TellomiFileHashes.Part> all = TellomiFileHashes.partsOf(body, 0);
        assertEquals(2, all.size());
        assertEquals(0L, all.get(0).offset);
        assertEquals(TellomiFileHashes.PART_BYTES, all.get(0).limit);
        assertArrayEquals(digest(body, 0, TellomiFileHashes.PART_BYTES), all.get(0).hash);
        assertEquals((long) TellomiFileHashes.PART_BYTES, all.get(1).offset);
        assertEquals(80, all.get(1).limit);
        assertArrayEquals(digest(body, TellomiFileHashes.PART_BYTES, 80), all.get(1).hash);

        assertPartsEqual(all.subList(1, 2), TellomiFileHashes.partsOf(body, 1));
        assertPartsEqual(all.subList(1, 2), TellomiFileHashes.partsOf(body, TellomiFileHashes.PART_BYTES - 1));
        assertPartsEqual(all.subList(1, 2), TellomiFileHashes.partsOf(body, TellomiFileHashes.PART_BYTES));
        assertTrue(TellomiFileHashes.partsOf(body, body.length).isEmpty());
        assertTrue(TellomiFileHashes.partsOf(body, body.length + 10L).isEmpty());
        assertTrue(TellomiFileHashes.partsOf(body, -1).isEmpty());
        assertTrue(TellomiFileHashes.partsOf(new byte[0], 0).isEmpty());

        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
        List<TellomiFileHashes.Part> one = TellomiFileHashes.partsOf(abc, 0);
        assertEquals(1, one.size());
        assertEquals(3, one.get(0).limit);
        assertArrayEquals(hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), one.get(0).hash);
    }

    @Test
    public void mismatchFailsAndAMissStaysOpen() throws Exception {
        byte[] body = new byte[TellomiFileHashes.PART_BYTES + 3];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        List<TellomiFileHashes.Part> all = TellomiFileHashes.partsOf(body, 0);
        assertEquals(TellomiFileHashes.Verdict.MATCH, TellomiFileHashes.check(body, all, false));
        assertEquals(TellomiFileHashes.Verdict.FAIL_OPEN, TellomiFileHashes.check(body, Collections.<TellomiFileHashes.Part>emptyList(), false));
        TellomiFileHashes.Part broken = new TellomiFileHashes.Part(0, 3, new byte[] {1, 2, 3});
        assertFalse(broken.wellFormed());
        assertEquals(TellomiFileHashes.Verdict.FAIL_OPEN, TellomiFileHashes.check(body, Collections.singletonList(broken), false));

        body[TellomiFileHashes.PART_BYTES + 1] ^= 0x5a;
        assertEquals(TellomiFileHashes.Verdict.MISMATCH, TellomiFileHashes.check(body, all, false));

        byte[] intact = new byte[body.length];
        for (int i = 0; i < intact.length; i++) {
            intact[i] = (byte) i;
        }
        List<TellomiFileHashes.Part> first = TellomiFileHashes.partsOf(intact, 0).subList(0, 1);
        assertEquals(TellomiFileHashes.Verdict.MATCH, TellomiFileHashes.check(intact, first, false));
        assertEquals(TellomiFileHashes.Verdict.MISMATCH, TellomiFileHashes.check(intact, first, true));

        File file = File.createTempFile("tellomi-file-hash", ".bin");
        File shortFile = File.createTempFile("tellomi-file-hash-short", ".bin");
        try {
            Files.write(file.toPath(), intact);
            assertEquals(TellomiFileHashes.Verdict.MATCH, TellomiFileHashes.check(file, intact.length, all, false));
            assertEquals(TellomiFileHashes.Verdict.FAIL_OPEN, TellomiFileHashes.check(new File("/tmp/tellomi-missing-file-hash"), intact.length, all, false));
            byte[] shortBody = new byte[] {1, 2, 3};
            Files.write(shortFile.toPath(), shortBody);
            TellomiFileHashes.Part claimsTen = new TellomiFileHashes.Part(0, 10, digest(new byte[10], 0, 10));
            assertEquals(TellomiFileHashes.Verdict.MISMATCH, TellomiFileHashes.check(shortFile, shortBody.length, Collections.singletonList(claimsTen), false));
            assertEquals(TellomiFileHashes.Verdict.MISMATCH, TellomiFileHashes.check(shortFile, 10, Collections.singletonList(claimsTen), false));
        } finally {
            file.delete();
            shortFile.delete();
        }
    }

    @Test
    public void sessionPagesLikeTheServerAndStopsOnAMiss() {
        byte[] body = new byte[TellomiFileHashes.PART_BYTES + 3];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) i;
        }
        List<TellomiFileHashes.Part> all = TellomiFileHashes.partsOf(body, 0);
        TellomiFileHashes.Session session = new TellomiFileHashes.Session();
        assertEquals(0L, session.open());
        assertEquals(-1L, session.open());
        assertEquals(-1L, session.requestNext(body.length));
        assertFalse(session.stopped());
        assertEquals((long) TellomiFileHashes.PART_BYTES, session.accept(body.length, all.subList(0, 1)));
        assertEquals(-1L, session.accept(body.length, all.subList(1, 2)));
        assertTrue(session.hasParts());
        assertFalse(session.confirmedEnd());
        assertEquals(-1L, TellomiFileHashes.nextOffset(body.length, session.parts()));

        TellomiFileHashes.Session miss = new TellomiFileHashes.Session();
        miss.open();
        assertEquals(-1L, miss.accept(body.length, Collections.<TellomiFileHashes.Part>emptyList()));
        assertTrue(miss.confirmedEnd());
        assertTrue(miss.stopped());
        assertFalse(miss.hasParts());

        TellomiFileHashes.Session error = new TellomiFileHashes.Session();
        error.open();
        error.stop();
        assertEquals(-1L, error.accept(body.length, all));
        assertTrue(error.stopped());
        assertFalse(error.confirmedEnd());
        assertEquals(0L, TellomiFileHashes.nextOffset(body.length, Collections.<TellomiFileHashes.Part>emptyList()));
        assertEquals(-1L, TellomiFileHashes.nextOffset(0, all));
    }

    @Test
    public void downloadFinishAsksBeforeTheFileIsMarkedDone() {
        String load = read("src/main/java/org/telegram/messenger/FileLoadOperation.java");
        int hold = load.indexOf("tellomiHoldForFileHashes");
        int finished = load.indexOf("state = stateFinished");
        assertTrue(hold > 0 && finished > hold);
        assertTrue(load.contains("tellomiStartFileHashes()"));
        assertTrue(load.contains("new TellomiFileHashes.Get()"));
        assertTrue(load.contains("RequestFlagFailOnServerErrors"));
        assertTrue(load.contains("RequestFlagDoNotWaitFloodWait"));
        assertTrue(load.contains("TellomiFileHashes.FAIL_OPEN_MS"));
        assertTrue(load.contains("tellomiFileHashLock"));
        assertFalse(load.contains("TL_upload_getFileHashes"));
        assertFalse(load.contains("getDocumentByHash"));
        String send = read("src/main/java/org/telegram/messenger/SendMessagesHelper.java");
        assertFalse(send.contains("TellomiFileHashes"));
        assertFalse(send.contains("getFileHashes"));
    }

    private static boolean plainDownload(boolean enabled) {
        return TellomiFileHashes.applies(enabled, true, false, false, false, false, false, false, false);
    }

    private static void assertPartsEqual(List<TellomiFileHashes.Part> expected, List<TellomiFileHashes.Part> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).offset, actual.get(i).offset);
            assertEquals(expected.get(i).limit, actual.get(i).limit);
            assertArrayEquals(expected.get(i).hash, actual.get(i).hash);
        }
    }

    private static byte[] digest(byte[] data, int offset, int length) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(data, offset, length);
        return digest.digest();
    }

    private static byte[] hex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String read(String relative) {
        File file = find(relative);
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(file.getPath(), e);
        }
    }

    private static File find(String relative) {
        String[] prefixes = {"", "TMessagesProj/", "../", "../TMessagesProj/"};
        for (String prefix : prefixes) {
            File file = new File(prefix + relative);
            if (file.isFile()) {
                return file;
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
