package org.telegram.messenger;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiDocumentByHashTest {

    @Test
    public void constructorIsTheCurrentLongSizeMethod() {
        assertEquals(0xb1f2061f, TellomiDocumentByHash.CONSTRUCTOR);
        assertTrue(TellomiDocumentByHash.FAIL_OPEN_MS > 0);
        String send = read("tellomi/java/org/telegram/messenger/TellomiDocumentByHashSend.java");
        assertTrue(send.contains("TellomiDocumentByHash.CONSTRUCTOR"));
        assertTrue(send.contains("stream.writeInt64(size)"));
        assertFalse(send.contains("0x338e2464"));
        assertFalse(send.contains("writeInt32(size)"));
    }

    @Test
    public void gifDocumentAndUnconvertedVideoAreEligible() {
        assertEquals(TellomiDocumentByHash.GIF, kind("image/gif", false, false));
        assertEquals(TellomiDocumentByHash.GIF, kind("video/mp4", true, true));
        assertEquals(TellomiDocumentByHash.DOCUMENT, kind("application/pdf", false, false));
        assertEquals(TellomiDocumentByHash.VIDEO, kind("video/mp4", false, true));
        assertEquals(TellomiDocumentByHash.VIDEO, kind("video/webm", false, false));
    }

    @Test
    public void transcodeSecretVoiceCoverAndPhotoSourcesStayOnUpload() {
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "video/mp4", false, true, false, true, false, false, false, false, false));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "image/gif", true, false, false, false, true, false, false, false, false));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "audio/ogg", false, false, true, false, false, false, false, false, false));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "video/mp4", false, true, false, false, false, false, false, true, false));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "video/mp4", false, true, false, false, false, false, true, false, false));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "image/gif", true, false, false, false, false, false, false, false, true));
        assertEquals(TellomiDocumentByHash.NONE, TellomiDocumentByHash.kind(
                "video/mp4", false, true, false, false, false, true, false, false, false));
        assertEquals(TellomiDocumentByHash.NONE, kind("", false, false));
        assertEquals(TellomiDocumentByHash.NONE, kind(null, true, true));
    }

    @Test
    public void onlyARealDocumentIsReusable() {
        assertTrue(TellomiDocumentByHash.reusable(false, 7L, 9L, 2));
        assertFalse(TellomiDocumentByHash.reusable(true, 7L, 9L, 2));
        assertFalse(TellomiDocumentByHash.reusable(false, 0L, 9L, 2));
        assertFalse(TellomiDocumentByHash.reusable(false, 7L, 0L, 2));
        assertFalse(TellomiDocumentByHash.reusable(false, 7L, 9L, 0));
    }

    @Test
    public void mimeFollowsTheDocumentThenAKnownExtension() {
        assertEquals("video/mp4", TellomiDocumentByHash.mimeFor(" video/mp4 ", "clip.gif"));
        assertEquals("image/gif", TellomiDocumentByHash.mimeFor(null, "/tmp/a.GIF"));
        assertEquals("application/pdf", TellomiDocumentByHash.mimeFor("application/pdf", null));
        assertNull(TellomiDocumentByHash.mimeFor("  ", "/tmp/notes.bin"));
        assertNull(TellomiDocumentByHash.mimeFor(null, "no-extension"));
    }

    @Test
    public void remoteAndContentPathsAreNotHashed() {
        assertFalse(TellomiDocumentByHash.isLocalFilePath(null));
        assertFalse(TellomiDocumentByHash.isLocalFilePath(""));
        assertFalse(TellomiDocumentByHash.isLocalFilePath("https://cdn.example/a.gif"));
        assertFalse(TellomiDocumentByHash.isLocalFilePath("content://media/1"));
        assertTrue(TellomiDocumentByHash.isLocalFilePath("/storage/emulated/0/a.gif"));
    }

    @Test
    public void sha256MatchesTheWholeFile() throws Exception {
        File file = File.createTempFile("tellomi-hash", ".gif");
        try {
            byte[] body = "abc".getBytes(StandardCharsets.US_ASCII);
            Files.write(file.toPath(), body);
            long[] size = new long[1];
            byte[] hash = TellomiDocumentByHash.sha256(file, size);
            assertEquals(3L, size[0]);
            assertArrayEquals(hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), hash);
            assertArrayEquals(hash, TellomiDocumentByHash.sha256(file));
        } finally {
            file.delete();
        }
        assertNull(TellomiDocumentByHash.sha256(new File("/tmp/tellomi-missing-document-by-hash")));
        File empty = File.createTempFile("tellomi-empty", ".gif");
        try {
            assertNull(TellomiDocumentByHash.sha256(empty));
        } finally {
            empty.delete();
        }
    }

    @Test
    public void uploadHooksSkipTranscodeSecretAndPhotoBytes() {
        String send = read("src/main/java/org/telegram/messenger/SendMessagesHelper.java");
        assertEquals(4, count(send, "tellomiDocumentByHash("));
        assertTrue(send.contains("TellomiDocumentByHashSend.lookupBeforeUpload"));
        assertTrue(send.contains("TL_inputMediaDocument"));
        int video = send.indexOf("message.obj.videoEditedInfo.needConvert()");
        assertTrue(video > 0);
        int videoHook = send.indexOf("tellomiDocumentByHash(message, -1, uploadPath, upload)", video);
        assertTrue(videoHook > video);
        assertTrue(send.substring(video, videoHook).contains("FileTypeVideo, false"));
        assertTrue(send.contains("uploadFile(location, true, false, ConnectionsManager.FileTypeFile)"));
        assertTrue(send.contains("uploadFile(location, false, true, ConnectionsManager.FileTypePhoto)"));
        assertFalse(send.contains("tellomiDocumentByHash(message, -1, uploadPath, upload);\n                        getFileLoader().uploadFile(location, false, true"));
    }

    private static int kind(String mime, boolean animated, boolean video) {
        return TellomiDocumentByHash.kind(mime, animated, video, false, false, false, false, false, false, false);
    }

    private static int count(String text, String needle) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + needle.length();
        }
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
            String stripped = relative.startsWith("src/") ? relative : relative;
            File fromModule = new File(prefix + stripped);
            if (fromModule.isFile()) {
                return fromModule;
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
