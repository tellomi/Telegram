package org.telegram.messenger;

import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
        HttpServer primary = server(500, "down");
        HttpServer backup = server(200, RELEASE);
        try {
            String[] urls = {
                    "http://127.0.0.1:" + primary.getAddress().getPort() + "/android/latest.json",
                    "http://127.0.0.1:" + backup.getAddress().getPort() + "/android/latest.json"
            };
            TellomiReleaseUpdate.Manifest manifest = TellomiReleaseUpdate.fetch(urls, new String[]{"127.0.0.1"});
            assertEquals(1802949, manifest.versionCode);
            assertTrue(manifest.fromFallback);
            assertEquals("https://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk", TellomiReleaseUpdate.downloadUrl(manifest));
        } finally {
            primary.stop(0);
            backup.stop(0);
        }
    }

    @Test
    public void redirectOffTheFeedHostIsDropped() throws Exception {
        HttpServer server = server(302, "");
        try {
            String body = TellomiReleaseUpdate.httpGet(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/android/latest.json",
                    new String[]{"127.0.0.1"},
                    0);
            assertNull(body);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(int status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/android/latest.json", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (status >= 300 && status < 400) {
                exchange.getResponseHeaders().add("Location", "https://example.com/android/latest.json");
            }
            if (status >= 300 && status < 400) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return server;
    }
}
