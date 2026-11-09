package org.telegram.messenger;

import org.junit.Test;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiReleaseUpdateTest {

    private static final String RELEASE = ""
            + "{\n"
            + " \"platform\": \"android\",\n"
            + " \"channel\": \"release\",\n"
            + " \"versionName\": \"14.0.7\",\n"
            + " \"versionCode\": 1802949,\n"
            + " \"url\": \"https://www.tellomi.cn/dl/android/14.0.7-1802949/Tellomi.apk\",\n"
            + " \"fallbackUrl\": \"https://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk\",\n"
            + " \"files\": [{\"url\": \"https://dl.tellomi.cn/android/other/Tellomi.apk\", \"fallbackUrl\": \"https://updates.tellomi.app/android/other/Tellomi.apk\"}]\n"
            + "}";

    private static final String OLDER_RELEASE = ""
            + "{\"platform\":\"android\",\"channel\":\"release\",\"versionName\":\"14.0.4\",\"versionCode\":1800979,"
            + "\"url\":\"https://www.tellomi.cn/dl/android/14.0.4-1800979/Tellomi.apk\","
            + "\"fallbackUrl\":\"https://dl.tellomi.cn/android/14.0.4-1800979/Tellomi.apk\"}";

    @Test
    public void feedsAreTheReleaseManifestNotThePageOrTheTestChannel() {
        assertEquals("https://www.tellomi.cn/dl/android/latest.json", TellomiReleaseUpdate.PRIMARY_FEED);
        assertEquals("https://dl.tellomi.cn/android/latest.json", TellomiReleaseUpdate.FALLBACK_FEED);
        assertEquals(2, TellomiReleaseUpdate.FEED_URLS.length);
        assertEquals(TellomiReleaseUpdate.PRIMARY_FEED, TellomiReleaseUpdate.FEED_URLS[0]);
        assertEquals(TellomiReleaseUpdate.FALLBACK_FEED, TellomiReleaseUpdate.FEED_URLS[1]);
        for (String url : TellomiReleaseUpdate.FEED_URLS) {
            assertFalse(url.contains("versions.json"));
            assertFalse(url.contains("/test/"));
        }
    }

    @Test
    public void publishedManifestUsesSha256sum() {
        String live = RELEASE.replace("\"versionCode\": 1802949", "\"versionCode\": 1804679, \"sha256sum\": \"6e700b0122f2dba03a32cfaaee77b977614f86fada33355c4666a3d249756a87\"");
        TellomiReleaseUpdate.Manifest remote = TellomiReleaseUpdate.parse(live, true);
        assertEquals(1804679, remote.versionCode);
        assertEquals("6e700b0122f2dba03a32cfaaee77b977614f86fada33355c4666a3d249756a87", remote.sha256);
        assertFalse(TellomiReleaseUpdate.isNewer(1804679, "14.0.9", remote));
        assertTrue(TellomiReleaseUpdate.isNewer(1804669, "14.0.9", remote));
    }

    @Test
    public void sameMarketingVersionWithAHigherCodeIsAnUpdate() {
        TellomiReleaseUpdate.Manifest remote = TellomiReleaseUpdate.parse(RELEASE, false);
        assertEquals("14.0.7", remote.versionName);
        assertEquals(1802949, remote.versionCode);
        assertTrue(TellomiReleaseUpdate.isNewer(1800079, "14.0.7", remote));
        assertTrue(TellomiReleaseUpdate.isNewer(1802948, "14.0.8", remote));
        assertFalse(TellomiReleaseUpdate.isNewer(1802949, "14.0.7", remote));
        assertFalse(TellomiReleaseUpdate.isNewer(1802959, "14.0.6", remote));
        assertEquals("https://www.tellomi.cn/dl/android/14.0.7-1802949/Tellomi.apk", TellomiReleaseUpdate.downloadUrl(remote));
    }

    @Test
    public void higherCodeWinsWhenTheOfficialFeedIsBehind() {
        TellomiReleaseUpdate.Manifest chosen = TellomiReleaseUpdate.choose(OLDER_RELEASE, RELEASE);
        assertEquals(1802949, chosen.versionCode);
        assertTrue(chosen.fromFallback);
        assertEquals("https://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk", TellomiReleaseUpdate.downloadUrl(chosen));

        TellomiReleaseUpdate.Manifest primary = TellomiReleaseUpdate.choose(RELEASE, OLDER_RELEASE);
        assertEquals(1802949, primary.versionCode);
        assertFalse(primary.fromFallback);
        assertEquals("https://www.tellomi.cn/dl/android/14.0.7-1802949/Tellomi.apk", TellomiReleaseUpdate.downloadUrl(primary));
    }

    @Test
    public void pageManifestAndTestChannelAreNotARelease() {
        String page = "{\"android\":{\"version\":\"14.0.4\",\"versionCode\":1800979}}";
        assertNull(TellomiReleaseUpdate.parse(page, false));
        String test = RELEASE.replace("\"release\"", "\"test\"");
        assertNull(TellomiReleaseUpdate.parse(test, false));
        assertNull(TellomiReleaseUpdate.parse("not json", false));
        assertNull(TellomiReleaseUpdate.parse(null, false));
        assertNull(TellomiReleaseUpdate.choose(page, test));
    }

    @Test
    public void missingVersionCodeFallsBackToTheVersionName() {
        TellomiReleaseUpdate.Manifest remote = TellomiReleaseUpdate.parse(RELEASE, true);
        assertFalse(TellomiReleaseUpdate.isNewer(0, "14.0.7", remote));
        assertTrue(TellomiReleaseUpdate.isNewer(0, "14.0.6", remote));
        assertTrue(TellomiReleaseUpdate.versionNameIsNewer("14.0.10", "14.0.9"));
        assertFalse(TellomiReleaseUpdate.versionNameIsNewer("14.0.9", "14.0.10"));
        assertFalse(TellomiReleaseUpdate.isNewer(1802949, null, null));
    }

    @Test
    public void savedOfferStaysUntilThisInstallReachesThatCode() {
        assertTrue(TellomiReleaseUpdate.keepPendingForVersionCode(1800079, 1802949, 1800079));
        assertFalse(TellomiReleaseUpdate.keepPendingForVersionCode(1802949, 1802949, 1802949));
        assertFalse(TellomiReleaseUpdate.keepPendingForVersionCode(1802949, 1802949, 1800079));
        assertFalse(TellomiReleaseUpdate.keepPendingForVersionCode(1800079, 0, 1800079));
    }

    @Test
    public void downloadLinkHasToBeAnApkOnAReleaseHost() {
        assertNull(TellomiReleaseUpdate.acceptableApkUrl("http://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk"));
        assertNull(TellomiReleaseUpdate.acceptableApkUrl("https://example.com/Tellomi.apk"));
        assertNull(TellomiReleaseUpdate.acceptableApkUrl("https://dl.tellomi.cn/android/latest.json"));
        assertEquals(
                "https://updates.tellomi.app/android/14.0.7-1802949/Tellomi.apk",
                TellomiReleaseUpdate.acceptableApkUrl("https://updates.tellomi.app/android/14.0.7-1802949/Tellomi.apk"));
    }

    @Test
    public void failedOfficialHostUsesTheBackupFeed() throws Exception {
        FeedServer primary = new FeedServer(500, "down");
        FeedServer backup = new FeedServer(200, RELEASE);
        primary.listen();
        backup.listen();
        try {
            String[] urls = {
                    "http://127.0.0.1:" + primary.getListeningPort() + "/android/latest.json",
                    "http://127.0.0.1:" + backup.getListeningPort() + "/android/latest.json"
            };
            TellomiReleaseUpdate.Manifest manifest = TellomiReleaseUpdate.fetch(urls, new String[]{"127.0.0.1"});
            assertEquals(1802949, manifest.versionCode);
            assertTrue(manifest.fromFallback);
            assertEquals("https://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk", TellomiReleaseUpdate.downloadUrl(manifest));
        } finally {
            primary.stop();
            backup.stop();
        }
    }

    @Test
    public void redirectOffTheFeedHostIsDropped() throws Exception {
        FeedServer server = new FeedServer(302, "");
        server.listen();
        try {
            String body = TellomiReleaseUpdate.httpGet(
                    "http://127.0.0.1:" + server.getListeningPort() + "/android/latest.json",
                    new String[]{"127.0.0.1"},
                    0);
            assertNull(body);
        } finally {
            server.stop();
        }
    }

    /** Local stand-in for the release feed. Android unit tests cannot see com.sun.net.httpserver. */
    private static final class FeedServer extends NanoHTTPD {
        private final int status;
        private final String body;

        FeedServer(int status, String body) throws IOException {
            super("127.0.0.1", 0);
            this.status = status;
            this.body = body;
        }

        void listen() throws IOException {
            start(SOCKET_READ_TIMEOUT, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (status >= 300 && status < 400) {
                Response response = newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "");
                response.addHeader("Location", "https://example.com/android/latest.json");
                return response;
            }
            Response.IStatus code = status >= 500 ? Response.Status.INTERNAL_ERROR : Response.Status.OK;
            return newFixedLengthResponse(code, "application/json", body == null ? "" : body);
        }
    }
}
