package org.telegram.ui.tellomi.feed;

import org.junit.Test;
import org.telegram.ui.TellomiPack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** City normalize, the three-tab bar, and the nearby request shape. */
public class TellomiDiscoverNearbyTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";
    private static final List<TellomiDiscover.FeedKind> HOME = Arrays.asList(
        TellomiDiscover.FeedKind.RECOMMEND,
        TellomiDiscover.FeedKind.FOLLOWING,
        TellomiDiscover.FeedKind.NEARBY
    );

    @Test
    public void cityNormalizeDropsStreetProvinceAndCoordinates() {
        assertEquals("cn:chengdu", TellomiDiscoverCity.codeFromLocality("成都市", "CN"));
        assertEquals("cn:chengdu", TellomiDiscoverCity.codeFromLocality("成都", null));
        assertEquals("cn:taipei", TellomiDiscoverCity.codeFromLocality("台北市", "TW"));
        assertEquals("cn:taipei", TellomiDiscoverCity.codeFromLocality("臺北市", "TW"));
        assertEquals("cn:hong-kong", TellomiDiscoverCity.codeFromLocality("Hong Kong", "HK"));
        assertEquals("cn:macau", TellomiDiscoverCity.codeFromLocality("澳门", "MO"));
        assertEquals("cn:chengdu", TellomiDiscoverCity.cityOnly("春熙路 1 号", "成都市", "四川省"));
        assertNull(TellomiDiscoverCity.cityOnly("春熙路 1 号", null, "四川省"));
        assertNull(TellomiDiscoverCity.cityOnly("人民南路", "30.67", "104.06"));
        assertNull(TellomiDiscoverCity.codeFromLocality("30.5728", "CN"));
        assertNull(TellomiDiscoverCity.codeFromLocality("人民南路", "CN"));
        assertNull(TellomiDiscoverCity.code("hk:hong-kong"));
        assertNull(TellomiDiscoverCity.code("tw:taipei"));
        assertNull(TellomiDiscoverCity.knownCode("cn:not-a-city"));
        assertEquals("cn:chengdu", TellomiDiscoverNearby.publishCity("CN:Chengdu"));
        assertNull(TellomiDiscoverNearby.publishCity("成都市"));
        assertNull(TellomiDiscoverNearby.publishCity("30.5728,104.0668"));
        assertEquals("成都", TellomiDiscoverNearby.displayName("cn:chengdu", "zh-Hant", null));
        assertEquals("Chengdu", TellomiDiscoverNearby.displayName("cn:chengdu", "en", null));
        assertNull(TellomiDiscoverNearby.displayName("人民南路", "zh", null));
    }

    @Test
    public void homeTabsStayRecommendFollowingNearby() {
        assertEquals(HOME, TellomiDiscoverNearby.homeTabs());
        List<TellomiDiscover.FeedKind> omitted = Arrays.asList(
            TellomiDiscover.FeedKind.RECOMMEND,
            TellomiDiscover.FeedKind.FOLLOWING
        );
        assertTrue(TellomiDiscoverNearby.engineOmitsNearby(omitted));
        assertEquals(HOME, TellomiDiscoverNearby.homeTabs());
        List<TellomiDiscover.FeedKind> reversed = Arrays.asList(
            TellomiDiscover.FeedKind.NEARBY,
            TellomiDiscover.FeedKind.RECOMMEND
        );
        assertFalse(TellomiDiscoverNearby.engineOmitsNearby(reversed));
        assertEquals(HOME, TellomiDiscoverNearby.homeTabs());
        assertEquals(2, HOME.indexOf(TellomiDiscover.FeedKind.NEARBY));
    }

    @Test
    public void nearbyRequestAndPublishUseCityOnly() {
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall(
            "https://feed.example",
            TellomiDiscover.Operation.nearby("cn:chengdu", "page-2"),
            "token"
        );
        assertEquals("GET", call.method);
        assertEquals("/v1/feed/nearby", call.path());
        List<TellomiDiscover.QueryItem> query = call.queryItems();
        assertEquals(Arrays.asList(
            new TellomiDiscover.QueryItem("city", "cn:chengdu"),
            new TellomiDiscover.QueryItem("cursor", "page-2")
        ), query);
        for (int i = 0; i < query.size(); i++) {
            assertTrue(TellomiDiscoverNearby.nearbyQueryName(query.get(i).name));
            assertFalse("lat".equals(query.get(i).name));
            assertFalse("lng".equals(query.get(i).name));
            assertFalse("latitude".equals(query.get(i).name));
            assertFalse("longitude".equals(query.get(i).name));
            assertFalse("location".equals(query.get(i).name));
        }
        assertNull(TellomiDiscover.nearbyUrl("https://feed.example", "人民南路", null));
        assertNull(TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.nearby("30.57", null), "token"));

        byte[] posted = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, TellomiDiscoverNearby.publishCity("cn:chengdu"));
        assertEquals(Arrays.asList("kind", "text", "visibility", "client_nonce", "city"), keys(posted));
        assertEquals("cn:chengdu", TellomiJson.parse(posted).objectValue.get("city").stringValue);
        assertNull(TellomiJson.parse(posted).objectValue.get("latitude"));
        assertNull(TellomiJson.parse(posted).objectValue.get("location"));
        byte[] omitted = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, TellomiDiscoverNearby.publishCity(null));
        assertEquals(Arrays.asList("kind", "text", "visibility", "client_nonce"), keys(omitted));
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, "人民南路"));
    }

    @Test
    public void missingNearbyRouteIsNotAnEmptyFeed() {
        assertTrue(TellomiDiscoverNearby.missingEndpoint(404));
        assertTrue(TellomiDiscoverNearby.missingEndpoint(405));
        assertTrue(TellomiDiscoverNearby.missingEndpoint(501));
        assertFalse(TellomiDiscoverNearby.missingEndpoint(200));
        assertFalse(TellomiDiscoverNearby.missingEndpoint(400));
        assertFalse(TellomiDiscoverNearby.missingEndpoint(500));
        TellomiDiscover.Operation nearby = TellomiDiscover.Operation.nearby("cn:chengdu", null);
        assertEquals(TellomiDiscover.ClientError.NEARBY_UNAVAILABLE, TellomiDiscover.decodeHttp(nearby, new TellomiDiscover.HttpResult(404, new byte[0])).error);
        assertEquals(TellomiDiscover.ClientError.NEARBY_UNAVAILABLE, TellomiDiscover.decodeHttp(nearby, new TellomiDiscover.HttpResult(405, "{\"error\":\"method_not_allowed\"}".getBytes(StandardCharsets.UTF_8))).error);
        assertEquals(TellomiDiscover.ClientError.HIDDEN, TellomiDiscover.decodeHttp(nearby, new TellomiDiscover.HttpResult(404, "{\"error\":\"feed_hidden\"}".getBytes(StandardCharsets.UTF_8))).error);
        assertEquals(TellomiDiscover.ClientError.INVALID_CITY, TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, "cn:chengdu"),
            new TellomiDiscover.HttpResult(400, "{\"error\":\"invalid_city\"}".getBytes(StandardCharsets.UTF_8))
        ).error);
        TellomiDiscover.Outcome empty = TellomiDiscover.decodeHttp(nearby, new TellomiDiscover.HttpResult(200, "{\"items\":[]}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(empty.ok());
        assertTrue(empty.value.feed.items.isEmpty());
        TellomiDiscover.NearbyGate first = TellomiDiscover.nearbyGate(null, TellomiDiscover.LocationGate.NOT_DETERMINED, false);
        assertFalse(first.feed);
        assertTrue(first.requestLocation);
        TellomiDiscover.NearbyGate denied = TellomiDiscover.nearbyGate(null, TellomiDiscover.LocationGate.DENIED, true);
        assertFalse(denied.feed);
        assertFalse(denied.requestLocation);
        TellomiDiscover.NearbyGate chosen = TellomiDiscover.nearbyGate("cn:chengdu", TellomiDiscover.LocationGate.DENIED, true);
        assertTrue(chosen.feed);
        assertEquals("cn:chengdu", chosen.city);
    }

    @Test
    public void chineseCopyIsSharedByHansAndTraditional() {
        assertEquals("同城", TellomiPack.bundled("Tellomi.Discover.Nearby", "zh-Hans"));
        assertEquals("同城", TellomiPack.bundled("Tellomi.Discover.Nearby", "zh-Hant"));
        assertEquals("同城", TellomiPack.bundled("Tellomi.Discover.Nearby", "zh-TW"));
        assertEquals("同城", TellomiPack.bundled("Tellomi.Discover.Nearby", "zh-HK"));
        assertEquals("发布到 成都", TellomiDiscoverNearby.composeLine("cn:chengdu", "zh-Hans", null));
        assertEquals("发布到 成都", TellomiDiscoverNearby.composeLine("cn:chengdu", "zh-TW", null));
        assertEquals("发布到 成都", TellomiDiscoverNearby.composeLine("cn:chengdu", "zh-Hant", null));
        assertEquals("Posting in Chengdu", TellomiDiscoverNearby.composeLine("cn:chengdu", "en", null));
        assertEquals("这条不标注城市", TellomiDiscoverNearby.composeLine(null, "zh-TW", null));
        assertEquals("No city on this post", TellomiDiscoverNearby.composeLine("人民南路", "en", null));
        assertEquals("同城暂时还不能刷新。", TellomiPack.bundled("Tellomi.Discover.NearbyUnavailable", "zh-Hant"));
        assertEquals("City posts are not available yet.", TellomiPack.bundled("Tellomi.Discover.NearbyUnavailable", "en"));
    }

    private static List<String> keys(byte[] body) {
        TellomiJson json = TellomiJson.parse(body);
        return new ArrayList<>(json.objectValue.keySet());
    }
}
