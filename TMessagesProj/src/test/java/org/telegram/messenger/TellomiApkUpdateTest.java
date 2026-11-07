package org.telegram.messenger;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiApkUpdateTest {

    @Test
    public void downloadSurvivesASecondTapARestartAndAPermissionRoundTrip() {
        TellomiApkUpdate.Session started = TellomiApkUpdate.startDownload(null, "https://www.tellomi.cn/dl/android/Tellomi.apk", 1803009);
        assertEquals(TellomiApkUpdate.DOWNLOADING, started.state);
        assertTrue(TellomiApkUpdate.shouldEnqueue(started));
        TellomiApkUpdate.Session tracked = TellomiApkUpdate.attachDownloadId(started, 42L);
        TellomiApkUpdate.Session again = TellomiApkUpdate.startDownload(tracked, started.url, started.versionCode);
        assertEquals(42L, again.downloadId);
        assertFalse(TellomiApkUpdate.shouldEnqueue(again));

        TellomiApkUpdate.Session restored = TellomiApkUpdate.afterRestart(TellomiApkUpdate.decode(TellomiApkUpdate.encode(tracked)));
        assertEquals(TellomiApkUpdate.DOWNLOADING, restored.state);
        assertEquals(42L, restored.downloadId);

        TellomiApkUpdate.Session ready = TellomiApkUpdate.downloadFinished(restored, true, "/files/Tellomi-1803009.apk");
        assertEquals(TellomiApkUpdate.READY, ready.state);
        TellomiApkUpdate.Session waiting = TellomiApkUpdate.permissionNeeded(ready);
        assertTrue(waiting.awaitingPermission);
        assertFalse(TellomiApkUpdate.shouldOpenInstaller(waiting, false));
        TellomiApkUpdate.Session back = TellomiApkUpdate.permissionReturned(waiting, true);
        assertTrue(TellomiApkUpdate.shouldOpenInstaller(back, true));
        assertFalse(TellomiApkUpdate.shouldOpenInstaller(TellomiApkUpdate.installerOpened(back), true));

        TellomiApkUpdate.Session failed = TellomiApkUpdate.downloadFinished(tracked, false, null);
        assertEquals(TellomiApkUpdate.FAILED, failed.state);
        assertEquals(TellomiApkUpdate.BAD_FILE, failed.error);
        TellomiApkUpdate.Session retry = TellomiApkUpdate.startDownload(failed, started.url, started.versionCode);
        assertTrue(TellomiApkUpdate.shouldEnqueue(retry));
        assertEquals(0, TellomiApkUpdate.percent(0, 0));
        assertEquals(40, TellomiApkUpdate.percent(40, 100));
    }

    @Test
    public void rejectedHostAndForeignLinkNeverStart() {
        TellomiApkUpdate.Session foreign = TellomiApkUpdate.startDownload(null, "https://example.com/Tellomi.apk", 10);
        assertEquals(TellomiApkUpdate.FAILED, foreign.state);
        assertEquals(TellomiApkUpdate.HOST_REJECTED, foreign.error);
        assertFalse(TellomiApkUpdate.shouldEnqueue(foreign));
        assertEquals(TellomiApkUpdate.IDLE, TellomiApkUpdate.decode("nope").state);
    }

    @Test
    public void signatureAndPackageBeatAMatchingHash() throws Exception {
        byte[] ours = new byte[]{1, 2, 3, 4};
        byte[] theirs = new byte[]{9, 9, 9, 9};
        byte[] zip = zipBytes();
        String hash = TellomiApkUpdate.sha256Hex(new ByteArrayInputStream(zip));
        assertEquals(TellomiApkUpdate.OK, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 10, 11, ours, ours, true, hash, hash));
        assertEquals(TellomiApkUpdate.BAD_SIGNATURE, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 10, 11, ours, theirs, true, hash, hash));
        assertEquals(TellomiApkUpdate.BAD_PACKAGE, TellomiApkUpdate.verify("app.tellomi.chat", "org.telegram.messenger", 10, 11, ours, ours, true, null, null));
        assertEquals(TellomiApkUpdate.VERSION_NOT_NEWER, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 11, 11, ours, ours, true, null, null));
        assertEquals(TellomiApkUpdate.BAD_FILE, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 10, 11, ours, ours, false, null, null));
        assertEquals(TellomiApkUpdate.BAD_FILE, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 10, 11, ours, ours, true, hash, "00"));
        assertEquals(TellomiApkUpdate.OK, TellomiApkUpdate.verify("app.tellomi.chat", "app.tellomi.chat", 10, 11, ours, ours, true, null, null));

        byte[] head = new byte[]{zip[0], zip[1], zip[2], zip[3]};
        byte[] tail = new byte[Math.min(zip.length, 64)];
        System.arraycopy(zip, zip.length - tail.length, tail, 0, tail.length);
        assertTrue(TellomiApkUpdate.zipMarkersIntact(head, tail));
        head[0] = 'X';
        assertFalse(TellomiApkUpdate.zipMarkersIntact(head, tail));
    }

    @Test
    public void twoFeedsThatDisagreeOnTheHashAreNotAnUpdate() {
        String hashA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String hashB = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String left = release(hashA);
        String right = release(hashB);
        assertNull(TellomiReleaseUpdate.choose(left, right));
        assertTrue(TellomiReleaseUpdate.hashesDisagree(hashA, hashB));
        assertFalse(TellomiReleaseUpdate.hashesDisagree(hashA, null));
        TellomiReleaseUpdate.Manifest one = TellomiReleaseUpdate.parse(left, false);
        assertEquals(hashA, one.sha256);
        assertEquals(1803009, one.versionCode);
    }

    private static String release(String sha256) {
        return "{\"platform\":\"android\",\"channel\":\"release\",\"versionName\":\"14.0.8\",\"versionCode\":1803009,"
                + "\"url\":\"https://www.tellomi.cn/dl/android/Tellomi.apk\","
                + "\"fallbackUrl\":\"https://dl.tellomi.cn/android/Tellomi.apk\","
                + "\"sha256\":\"" + sha256 + "\"}";
    }

    private static byte[] zipBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
        zip.write(new byte[]{1, 2, 3});
        zip.closeEntry();
        zip.close();
        return output.toByteArray();
    }
}
