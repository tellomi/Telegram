package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Same cases as Telegram-iOS TellomiDiscoverFeedTests (C-M3a).
 * The loader callbacks in these tests run inline, matching the iOS recording loader.
 */
public class TellomiDiscoverFeedTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";
    private static final byte[] CARDS_JSON = ("{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"version\":1,\"card\":{\"kind\":\"post\",\"display_mode\":\"post\",\"source_url\":\"\",\"ai_generated\":true,\"edited\":false,\"text\":\"好看\",\"author\":{\"id\":\"profile-1\",\"display_name\":\"林\",\"handle\":\"lin\",\"native\":true},\"media\":[{\"type\":\"image\",\"key\":\"m/a\"}],\"ip_region_label\":\"四川\",\"city\":\"cn:chengdu\"},\"stats\":{\"likes\":3,\"comments\":1,\"reposts\":0},\"me\":{\"liked\":false},\"shareable\":true,\"quoted\":{\"id\":\"post-1\",\"state\":\"deleted\"}}]}").getBytes(StandardCharsets.UTF_8);

    @Test
    public void featureFlagDefaultsOff() {
        assertFalse(TellomiDiscover.enabled(TellomiDiscover.Field.missing()));
        assertFalse(TellomiDiscover.enabled(TellomiDiscover.Field.bool(false)));
        assertTrue(TellomiDiscover.enabled(TellomiDiscover.Field.string("enabled")));
    }

    @Test
    public void configurationStaysOffWithoutAFlag() {
        TellomiDiscover.Configuration off = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.missing(), TellomiDiscover.Field.string("https://feed.tellomi.cn"));
        assertFalse(off.enabled);
        assertNull(off.baseUrl);
        TellomiDiscover.Configuration http = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.bool(true), TellomiDiscover.Field.string("http://feed.tellomi.cn"));
        assertFalse(http.enabled);
        TellomiDiscover.Configuration on = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.bool(true), TellomiDiscover.Field.missing());
        assertEquals("https://feed.tellomi.cn", on.baseUrl);
    }

    @Test
    public void signedOutNeverBuildsARequest() {
        TellomiDiscover.Configuration configuration = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.bool(true), TellomiDiscover.Field.missing());
        TellomiDiscover.Operation[] operations = {
            TellomiDiscover.Operation.capabilities(),
            TellomiDiscover.Operation.status(),
            TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, true),
            TellomiDiscover.Operation.cards(Collections.singletonList("post-9")),
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null),
            TellomiDiscover.Operation.nearby("cn:chengdu", null)
        };
        for (int i = 0; i < operations.length; i++) {
            RecordingLoader loader = new RecordingLoader(null);
            TellomiDiscover.Outcome result = run(loader, false, configuration, operations[i]);
            assertEquals(TellomiDiscover.ClientError.SIGNED_OUT, result.error);
            assertEquals(0, loader.credentialFetches);
            assertTrue(loader.calls.isEmpty());
        }
        TellomiDiscover.Chrome chrome = TellomiDiscover.chrome(false, true, TellomiDiscover.FeedKind.RECOMMEND, "post-9", false, null);
        assertFalse(chrome.issuesNetwork());
        assertEquals(TellomiDiscover.Surface.LEGACY_EMPTY, chrome.surface);
        assertTrue(chrome.postUnavailable);
    }

    @Test
    public void disabledFlagNeverBuildsARequest() {
        TellomiDiscover.Configuration configuration = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.missing(), TellomiDiscover.Field.missing());
        TellomiDiscover.Chrome chrome = TellomiDiscover.chrome(true, false, TellomiDiscover.FeedKind.RECOMMEND, null, false, null);
        assertEquals(TellomiDiscover.Surface.LEGACY_EMPTY, chrome.surface);
        assertFalse(chrome.postUnavailable);
        assertFalse(chrome.issuesNetwork());
        RecordingLoader loader = new RecordingLoader(null);
        TellomiDiscover.Outcome result = run(loader, true, configuration, TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, true));
        assertEquals(TellomiDiscover.ClientError.DISABLED, result.error);
        assertEquals(0, loader.credentialFetches);
        assertTrue(loader.calls.isEmpty());
    }

    @Test
    public void capabilitiesDoNotSendACredential() {
        TellomiDiscover.Configuration configuration = new TellomiDiscover.Configuration(true, "https://feed.example");
        RecordingLoader loader = new RecordingLoader(null);
        TellomiDiscover.Outcome result = run(loader, true, configuration, TellomiDiscover.Operation.capabilities());
        assertTrue(result.ok());
        assertEquals(TellomiDiscover.Decoded.Kind.CAPABILITIES, result.value.kind);
        assertEquals(1, result.value.capabilities.version);
        assertTrue(result.value.capabilities.features.contains("feed"));
        assertEquals(0, loader.credentialFetches);
        assertEquals(1, loader.calls.size());
        assertFalse(loader.calls.get(0).authorizes);
        assertEquals("", loader.calls.get(0).token);
        assertEquals("/v1/capabilities", loader.calls.get(0).path());
    }

    @Test
    public void feedThenCardsUseTheDocumentedPaths() {
        TellomiDiscover.Configuration configuration = TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.string("true"), TellomiDiscover.Field.string("https://feed.example/"));
        RecordingLoader loader = new RecordingLoader(null);
        loader.credential = new TellomiDiscover.Credential("jwt-1", 10_000, "cn");
        TellomiDiscover.Outcome result = run(loader, true, configuration, TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, true));
        assertEquals(TellomiDiscover.Decoded.Kind.FEED, result.value.kind);
        assertEquals(Collections.singletonList("post-9"), ids(result.value.feed));
        assertEquals("editorial", result.value.feed.items.get(0).reason);
        assertNull(result.value.feed.cursor);
        assertEquals(1, loader.credentialFetches);
        assertEquals("GET", loader.calls.get(0).method);
        assertEquals("/v1/feed/recommend", loader.calls.get(0).path());
        assertNull(loader.calls.get(0).rawQuery());
        assertFalse(loader.calls.get(0).url.contains("limit="));
        assertFalse(loader.calls.get(0).url.contains("jwt-1"));

        TellomiDiscover.Outcome cards = run(loader, true, configuration, TellomiDiscover.Operation.cards(Collections.singletonList("post-9")));
        assertEquals(TellomiDiscover.Decoded.Kind.CARDS, cards.value.kind);
        assertEquals("好看", cards.value.cards.get(0).text);
        assertEquals("林", cards.value.cards.get(0).authorName);
        assertEquals("lin", cards.value.cards.get(0).authorHandle);
        assertEquals(3, cards.value.cards.get(0).likeCount);
        assertTrue(cards.value.cards.get(0).shareable);
        TellomiDiscover.HttpCall last = loader.calls.get(loader.calls.size() - 1);
        assertEquals("POST", last.method);
        assertEquals("/v1/feed/cards", last.path());
        TellomiJson body = TellomiJson.parse(last.body);
        assertEquals("post-9", body.objectValue.get("ids").arrayValue.get(0).stringValue);
    }

    @Test
    public void followingCursorAndUnpersonalizedRecommend() {
        String base = "https://feed.example";
        String following = TellomiDiscover.feedUrl(base, TellomiDiscover.FeedKind.FOLLOWING, "c1", true);
        assertEquals("/v1/feed/following", path(following));
        assertEquals(Collections.singletonList(new TellomiDiscover.QueryItem("cursor", "c1")), query(following));
        String recommend = TellomiDiscover.feedUrl(base, TellomiDiscover.FeedKind.RECOMMEND, "ignored", false);
        assertEquals("/v1/feed/recommend", path(recommend));
        assertEquals(Collections.singletonList(new TellomiDiscover.QueryItem("personalized", "false")), query(recommend));
        assertNull(query(TellomiDiscover.feedUrl(base, TellomiDiscover.FeedKind.RECOMMEND, null, true)));
    }

    @Test
    public void publishBodyMatchesTheEngine() {
        byte[] body = TellomiDiscover.publishBody(" 你好 ", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null);
        TellomiJson object = TellomiJson.parse(body);
        assertEquals("post", object.objectValue.get("kind").stringValue);
        assertEquals("你好", object.objectValue.get("text").stringValue);
        assertEquals("public", object.objectValue.get("visibility").stringValue);
        assertEquals(NONCE, object.objectValue.get("client_nonce").stringValue);
        assertNull(object.objectValue.get("audience"));
        assertEquals(new HashSet<String>(Arrays.asList("kind", "text", "visibility", "client_nonce")), object.objectValue.keySet());
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.INCLUDE, null, NONCE, 500, null));
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EXCLUDE, " ", NONCE, 500, null));
        TellomiJson selected = TellomiJson.parse(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.INCLUDE, "signed-jwt", NONCE, 500, null));
        assertEquals("include", selected.objectValue.get("visibility").stringValue);
        assertEquals("signed-jwt", selected.objectValue.get("audience").objectValue.get("audience_token").stringValue);
        TellomiJson contacts = TellomiJson.parse(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.CONTACTS, null, NONCE, 500, null));
        assertEquals("contacts", contacts.objectValue.get("visibility").stringValue);
    }

    @Test
    public void audienceTokenRequestAndErrors() {
        assertEquals("tellomi-feed-audience", TellomiDiscover.AudienceList.TOKEN_TYPE);
        assertEquals(500, TellomiDiscover.AudienceList.MEMBER_LIMIT);
        assertEquals(10 * 60, TellomiDiscover.AudienceList.LIFETIME);
        assertEquals(Collections.singletonList("abc"), TellomiDiscover.audienceList("include", Collections.singletonList("abc")).members);
        assertNull(TellomiDiscover.audienceList("include", Collections.<String>emptyList()));
        assertNull(TellomiDiscover.audienceList("include", repeat("abc", 501)));
        assertNull(TellomiDiscover.audienceList("public", Collections.singletonList("abc")));
        assertTrue(TellomiDiscover.audienceExpiryValid(600, 0));
        assertFalse(TellomiDiscover.audienceExpiryValid(601, 0));
        assertFalse(TellomiDiscover.audienceTokenNeedsRefresh(1_000, 939));
        assertTrue(TellomiDiscover.audienceTokenNeedsRefresh(1_000, 940));
        assertEquals("public", TellomiDiscover.audience(TellomiDiscover.StoryBase.EVERYONE, new long[0], 1).visibility.raw);
        TellomiDiscover.AudienceChoice include = TellomiDiscover.audience(TellomiDiscover.StoryBase.NOBODY, new long[] {7}, 1);
        assertEquals(TellomiDiscover.Visibility.INCLUDE, include.visibility);
        assertTrue(include.publishable);
        assertArrayEquals(new long[] {7}, include.userIds);
        assertFalse(TellomiDiscover.audience(TellomiDiscover.StoryBase.NOBODY, new long[] {1}, 1).publishable);
        TellomiDiscover.AudienceChoice exclude = TellomiDiscover.audience(TellomiDiscover.StoryBase.EVERYONE, new long[] {3}, 1);
        assertEquals(TellomiDiscover.Visibility.EXCLUDE, exclude.visibility);
        assertTrue(exclude.publishable);
        assertEquals("close_friends", TellomiDiscover.audience(TellomiDiscover.StoryBase.CLOSE_FRIENDS, new long[] {7}, 1).visibility.raw);
        assertNull(TellomiDiscover.audience(TellomiDiscover.StoryBase.NOBODY, new long[0], 1));
        assertNull(TellomiDiscover.audienceUserIds("include", new long[] {2, 2}, 1));
        assertNull(TellomiDiscover.audienceUserIds("include", new long[0], 1));
        assertNull(TellomiDiscover.audienceUserIds("include", new long[] {0}, 1));
        TellomiDiscover.PublishStep step = TellomiDiscover.publishStep("hi", TellomiDiscover.Visibility.INCLUDE, new long[] {7}, 1, NONCE, 500, null, 0, null);
        assertEquals(TellomiDiscover.PublishStep.Kind.NEEDS_AUDIENCE_TOKEN, step.kind);
        assertEquals("include", step.tokenKind);
        assertArrayEquals(new long[] {7}, step.userIds);
        TellomiDiscover.PublishStep ready = TellomiDiscover.publishStep("hi", TellomiDiscover.Visibility.INCLUDE, new long[] {7}, 1, NONCE, 500, new TellomiDiscover.AudienceToken("signed-jwt", 600), 0, null);
        assertEquals(TellomiDiscover.PublishStep.Kind.READY, ready.kind);
        TellomiJson posted = TellomiJson.parse(ready.body);
        assertEquals("include", posted.objectValue.get("visibility").stringValue);
        assertEquals("signed-jwt", posted.objectValue.get("audience").objectValue.get("audience_token").stringValue);
        TellomiDiscover.PublishStep stale = TellomiDiscover.publishStep("hi", TellomiDiscover.Visibility.EXCLUDE, new long[] {7}, 1, NONCE, 500, new TellomiDiscover.AudienceToken("old", 100), 40, null);
        assertEquals(TellomiDiscover.PublishStep.Kind.NEEDS_AUDIENCE_TOKEN, stale.kind);
        TellomiDiscover.PublishStep rejected = TellomiDiscover.publishStep("hi", TellomiDiscover.Visibility.INCLUDE, new long[] {1, 7}, 1, NONCE, 500, null, 0, null);
        assertEquals(TellomiDiscover.PublishStep.Kind.REJECTED, rejected.kind);
        assertEquals(TellomiDiscover.ClientError.AUDIENCE_INVALID, rejected.error);
        assertEquals(TellomiDiscover.ClientError.DISABLED, TellomiDiscover.clientError("DISCOVER_DISABLED", 400));
        assertEquals(TellomiDiscover.ClientError.AUDIENCE_INVALID, TellomiDiscover.clientError("AUDIENCE_INVALID", 400));
        assertEquals(TellomiDiscover.ClientError.USER_INVALID, TellomiDiscover.clientError("USER_ID_INVALID", 400));
        assertEquals(TellomiDiscover.ClientError.USER_BLOCKED, TellomiDiscover.clientError("USER_IS_BLOCKED", 400));
        assertEquals(TellomiDiscover.ClientError.SIGNED_OUT, TellomiDiscover.clientError("AUTH_KEY_UNREGISTERED", 401));
        TellomiDiscover.Copy copy = TellomiDiscover.Copy.resolve("zh");
        assertEquals("发现暂时不可用。", copy.failureText(TellomiDiscover.ClientError.DISABLED));
        assertEquals("名单无效，请重新选择。", copy.failureText(TellomiDiscover.ClientError.AUDIENCE_INVALID));
        assertEquals("名单里有人无法使用。", copy.failureText(TellomiDiscover.ClientError.USER_INVALID));
        assertEquals("名单里有人已互相拉黑。", copy.failureText(TellomiDiscover.ClientError.USER_BLOCKED));
        assertArrayEquals(new byte[] {
            (byte) 0xC2, 0x13, 0x52, (byte) 0xDC,
            0x07, 0x69, 0x6E, 0x63, 0x6C, 0x75, 0x64, 0x65,
            0x15, (byte) 0xC4, (byte) 0xB5, 0x1C,
            0x02, 0x00, 0x00, 0x00,
            0x2A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x2B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        }, TellomiDiscover.Tl.encodeAudienceRequest("include", new long[] {42, 43}));
        byte[] response = concat(new byte[] {(byte) 0x81, 0x71, 0x17, (byte) 0xDA}, encodeString("signed-jwt"), new byte[] {0x58, 0x02, 0x00, 0x00});
        assertEquals(new TellomiDiscover.AudienceToken("signed-jwt", 600), TellomiDiscover.Tl.decodeAudienceToken(response));
        RecordingLoader loggedOut = new RecordingLoader(null);
        TellomiDiscover.Outcome signedOut = run(loggedOut, false, new TellomiDiscover.Configuration(true, "https://feed.example"), TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.INCLUDE, "signed-jwt", NONCE, 500, "cn:chengdu"));
        assertEquals(TellomiDiscover.ClientError.SIGNED_OUT, signedOut.error);
        assertTrue(loggedOut.calls.isEmpty());
        assertNull(TellomiDiscover.feedUrl("https://feed.example", TellomiDiscover.FeedKind.NEARBY, "chengdu", true));
        RecordingLoader nearby = new RecordingLoader(null);
        TellomiDiscover.Outcome nearbyResult = run(nearby, true, new TellomiDiscover.Configuration(true, "https://feed.example"), TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.NEARBY, "chengdu", true));
        assertEquals(TellomiDiscover.ClientError.UNAVAILABLE, nearbyResult.error);
        assertTrue(nearby.calls.isEmpty());
        assertEquals(0, nearby.credentialFetches);
    }

    @Test
    public void credentialRoundTripAndRefresh() {
        assertArrayEquals(new byte[] {0x76, 0x7F, 0x0A, (byte) 0xA8}, TellomiDiscover.Tl.encodeRequest());
        byte[] body = concat(new byte[] {(byte) 0xAA, (byte) 0xCC, 0x78, 0x05}, encodeString("jwt.token"), new byte[] {0x39, 0x30, 0x00, 0x00}, encodeString("cn"));
        assertEquals(new TellomiDiscover.Credential("jwt.token", 12345, "cn"), TellomiDiscover.Tl.decodeCredential(body));
        assertTrue(TellomiDiscover.credentialNeedsRefresh(1_000, 880));
        assertFalse(TellomiDiscover.credentialNeedsRefresh(1_000, 879));
    }

    @Test
    public void hiddenFeedIsNotAnEmptyList() {
        TellomiDiscover.Outcome result = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, true),
            new TellomiDiscover.HttpResult(404, "{\"error\":\"feed_hidden\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.HIDDEN, result.error);
        assertEquals(Boolean.FALSE, TellomiDiscoverParse.status("{\"visible\":false}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void cardParsesAuthorQuoteAndDropsStreet() {
        List<TellomiDiscover.Card> cards = TellomiDiscoverParse.cards(CARDS_JSON);
        TellomiDiscover.Card card = cards.get(0);
        assertEquals("好看", card.text);
        assertEquals("lin", card.authorHandle);
        assertEquals(3, card.likeCount);
        assertEquals(1, card.imageCount);
        assertTrue(card.aiGenerated);
        assertEquals("四川", card.ipRegionLabel);
        assertEquals("deleted", card.quoteState);
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(card);
        TellomiDiscover.PostSummary summary = TellomiDiscover.postSummary(post, TellomiDiscover.Copy.resolve("zh-Hans"), true);
        assertEquals("林 @lin", summary.title);
        assertTrue(summary.body.contains("原动态已被作者删除"));
        assertTrue(summary.meta.contains("AI 生成"));
        assertTrue(summary.meta.contains("四川"));
        assertTrue(summary.meta.contains("成都"));
        assertEquals("cn:chengdu", card.city);
        assertFalse(summary.body.contains("人民路"));
        List<TellomiDiscover.Card> unavailable = TellomiDiscoverParse.cards("{\"cards\":[{\"id\":\"post-1\",\"state\":\"unavailable\"}]}".getBytes(StandardCharsets.UTF_8));
        assertTrue(TellomiDiscoverParse.displayPost(unavailable.get(0)).unavailable);
        assertEquals("这条动态已不可见", TellomiDiscover.postSummary(TellomiDiscoverParse.displayPost(unavailable.get(0)), TellomiDiscover.Copy.resolve("zh"), false).body);
        List<TellomiDiscover.Card> street = TellomiDiscoverParse.cards("{\"cards\":[{\"id\":\"post-2\",\"state\":\"ok\",\"card\":{\"text\":\"x\",\"city\":\"人民南路 1 号\"}}]}".getBytes(StandardCharsets.UTF_8));
        assertNull(street.get(0).city);
    }

    @Test
    public void nearbyCityCodeAndFeed() {
        assertEquals("cn:chengdu", TellomiDiscoverCity.knownCode("cn:chengdu"));
        assertEquals("cn:chengdu", TellomiDiscoverCity.knownCode("CN:Chengdu"));
        assertNull(TellomiDiscoverCity.code("hk:hong-kong"));
        assertNull(TellomiDiscoverCity.code("tw:taipei"));
        assertNull(TellomiDiscoverCity.code("mo:macau"));
        assertNull(TellomiDiscoverCity.knownCode("cn:not-a-city"));
        assertNull(TellomiDiscoverCity.knownCode("30.57,104.07"));
        assertEquals("cn:chengdu", TellomiDiscoverCity.codeFromLocality("成都市", "CN"));
        assertEquals("cn:taipei", TellomiDiscoverCity.codeFromLocality("台北市", "TW"));
        assertEquals("cn:taipei", TellomiDiscoverCity.codeFromLocality("臺北市", "TW"));
        assertEquals("cn:hong-kong", TellomiDiscoverCity.codeFromLocality("Hong Kong", "HK"));
        assertEquals("cn:macau", TellomiDiscoverCity.codeFromLocality("澳门", "MO"));
        assertNull(TellomiDiscoverCity.codeFromLocality("人民南路", "CN"));
        assertNull(TellomiDiscoverCity.cityOnly("春熙路", null, "四川省"));
        assertEquals("cn:chengdu", TellomiDiscoverCity.cityOnly("春熙路", "成都市", "四川省"));
        assertEquals("成都", TellomiDiscoverCity.name("cn:chengdu", "zh"));
        TellomiDiscover.NearbyGate first = TellomiDiscover.nearbyGate(null, TellomiDiscover.LocationGate.NOT_DETERMINED, false);
        assertFalse(first.feed);
        assertTrue(first.requestLocation);
        TellomiDiscover.NearbyGate asked = TellomiDiscover.nearbyGate(null, TellomiDiscover.LocationGate.NOT_DETERMINED, true);
        assertFalse(asked.feed);
        assertFalse(asked.requestLocation);
        TellomiDiscover.NearbyGate denied = TellomiDiscover.nearbyGate(null, TellomiDiscover.LocationGate.DENIED, false);
        assertFalse(denied.requestLocation);
        TellomiDiscover.NearbyGate chosen = TellomiDiscover.nearbyGate("cn:chengdu", TellomiDiscover.LocationGate.NOT_DETERMINED, false);
        assertTrue(chosen.feed);
        assertEquals("cn:chengdu", chosen.city);
        String url = TellomiDiscover.nearbyUrl("https://feed.example", "cn:chengdu", "page-2");
        assertEquals("/v1/feed/nearby", path(url));
        assertEquals(Arrays.asList(new TellomiDiscover.QueryItem("city", "cn:chengdu"), new TellomiDiscover.QueryItem("cursor", "page-2")), query(url));
        assertNull(TellomiDiscover.nearbyUrl("https://feed.example", "chengdu", null));
        TellomiJson posted = TellomiJson.parse(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, "cn:chengdu"));
        assertEquals("cn:chengdu", posted.objectValue.get("city").stringValue);
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, "人民南路"));
        TellomiJson omitted = TellomiJson.parse(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null));
        assertNull(omitted.objectValue.get("city"));
        TellomiDiscover.Outcome rejected = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, "cn:chengdu"),
            new TellomiDiscover.HttpResult(400, "{\"error\":\"invalid_city\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.INVALID_CITY, rejected.error);
        assertEquals("这个城市不能用，请重新选择。", TellomiDiscover.Copy.resolve("zh").failureText(TellomiDiscover.ClientError.INVALID_CITY));
        byte[] profileRequired = "{\"error\":\"profile_required\"}".getBytes(StandardCharsets.UTF_8);
        TellomiDiscover.Outcome firstPost = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null),
            new TellomiDiscover.HttpResult(409, profileRequired)
        );
        assertEquals(TellomiDiscover.ClientError.PROFILE_REQUIRED, firstPost.error);
        assertNotEquals(TellomiDiscover.ClientError.UNAVAILABLE, firstPost.error);
        TellomiDiscover.Outcome otherConflict = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null),
            new TellomiDiscover.HttpResult(409, "{\"error\":\"something_else\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.UNAVAILABLE, otherConflict.error);
        String english = TellomiDiscover.Copy.resolve("en").failureText(TellomiDiscover.ClientError.PROFILE_REQUIRED);
        String chinese = TellomiDiscover.Copy.resolve("zh").failureText(TellomiDiscover.ClientError.PROFILE_REQUIRED);
        assertFalse(english.equals(TellomiDiscover.Copy.resolve("en").failed));
        assertFalse(chinese.equals(TellomiDiscover.Copy.resolve("zh").failed));
        assertTrue(english.contains("Discover profile"));
        assertTrue(chinese.contains("发现主页"));
        assertTrue(chinese.contains("再发一次"));
        TellomiDiscover.Outcome decoded = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.nearby("cn:chengdu", null),
            new TellomiDiscover.HttpResult(200, "{\"items\":[{\"id\":\"post-9\",\"reason\":\"nearby\"}],\"cursor\":\"next\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(Collections.singletonList("nearby"), reasons(decoded.value.feed));
        assertEquals("next", decoded.value.feed.cursor);
        RecordingLoader loader = new RecordingLoader(null);
        TellomiDiscover.Outcome page = run(loader, true, new TellomiDiscover.Configuration(true, "https://feed.example"), TellomiDiscover.Operation.nearby("cn:chengdu", null));
        assertTrue(page.ok());
        assertEquals(TellomiDiscover.Decoded.Kind.FEED, page.value.kind);
        assertEquals(1, loader.calls.size());
        assertEquals("/v1/feed/nearby", loader.calls.get(0).path());
        assertEquals(Collections.singletonList(new TellomiDiscover.QueryItem("city", "cn:chengdu")), loader.calls.get(0).queryItems());
        RecordingLoader bad = new RecordingLoader(null);
        TellomiDiscover.Outcome rejectedLocally = run(bad, true, new TellomiDiscover.Configuration(true, "https://feed.example"), TellomiDiscover.Operation.nearby("not-a-code", "chengdu"));
        assertEquals(TellomiDiscover.ClientError.INVALID_CITY, rejectedLocally.error);
        assertTrue(bad.calls.isEmpty());
        assertEquals(0, bad.credentialFetches);
    }

    @Test
    public void engineConfigTabsAndTextLimit() {
        byte[] data = "{\"enabled\":true,\"region\":\"cn\",\"tabs\":[\"recommend\",\"hot\",\"nearby\"],\"recommend_mode\":\"editorial_chronological\",\"personalization_toggle_visible\":false,\"limits\":{\"post_text_chars\":500}}".getBytes(StandardCharsets.UTF_8);
        TellomiDiscover.EngineConfig config = TellomiDiscoverParse.engineConfig(data);
        assertEquals(Arrays.asList(TellomiDiscover.FeedKind.RECOMMEND, TellomiDiscover.FeedKind.NEARBY), config.tabs);
        byte[] official = "{\"enabled\":true,\"region\":\"cn\",\"tabs\":[\"recommend\",\"following\",\"nearby\"],\"limits\":{\"post_text_chars\":500}}".getBytes(StandardCharsets.UTF_8);
        assertEquals(Arrays.asList(TellomiDiscover.FeedKind.RECOMMEND, TellomiDiscover.FeedKind.FOLLOWING, TellomiDiscover.FeedKind.NEARBY), TellomiDiscoverParse.engineConfig(official).tabs);
        assertEquals(500, config.postTextChars);
        assertEquals("editorial_chronological", config.recommendMode);
    }

    @Test
    public void createdPostAndUiChrome() {
        TellomiDiscover.CreatedPost created = TellomiDiscoverParse.createdPost("{\"id\":\"post-9\",\"state\":\"pending\",\"version\":1}".getBytes(StandardCharsets.UTF_8));
        assertEquals(new TellomiDiscover.CreatedPost("post-9", "pending", 1), created);
        TellomiDiscover.Chrome on = TellomiDiscover.chrome(true, true, TellomiDiscover.FeedKind.RECOMMEND, "post-9", false, null);
        assertEquals(TellomiDiscover.Surface.DETAIL, on.surface);
        assertEquals("post-9", on.detailId);
        assertTrue(on.loadsPost);
        assertFalse(on.loadsFeed);
        TellomiDiscover.Chrome tabs = TellomiDiscover.chrome(true, true, TellomiDiscover.FeedKind.RECOMMEND, null, false, null);
        assertEquals(TellomiDiscover.Surface.FEED, tabs.surface);
        assertTrue(tabs.showsCompose);
        assertEquals("推荐", TellomiDiscover.Copy.resolve("zh-Hans").recommend);
    }

    @Test
    public void expiredCredentialIsRefreshedBeforeTheFeedRequest() {
        TellomiDiscover.Configuration configuration = new TellomiDiscover.Configuration(true, "https://feed.example");
        RecordingLoader loader = new RecordingLoader(new TellomiDiscover.Credential("old", 5_000, "global"));
        loader.nowValue = 5_000;
        loader.credential = new TellomiDiscover.Credential("new", 9_000, "global");
        TellomiDiscover.Outcome result = run(loader, true, configuration, TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.FOLLOWING, null, true));
        assertTrue(result.ok());
        assertEquals(TellomiDiscover.Decoded.Kind.FEED, result.value.kind);
        assertEquals(1, loader.credentialFetches);
        assertEquals(Collections.singletonList("new"), tokens(loader.calls));
        assertEquals("/v1/feed/following", loader.calls.get(0).path());
    }

    @Test
    public void unauthorizedResponseFetchesOneNewCredential() {
        TellomiDiscover.Configuration configuration = new TellomiDiscover.Configuration(true, "https://feed.example");
        RecordingLoader loader = new RecordingLoader(new TellomiDiscover.Credential("old", 9_000, "cn"));
        loader.credential = new TellomiDiscover.Credential("new", 9_000, "cn");
        loader.statuses.add(401);
        loader.statuses.add(200);
        TellomiDiscover.Outcome result = run(loader, true, configuration, TellomiDiscover.Operation.status());
        assertTrue(result.ok());
        assertEquals(TellomiDiscover.Decoded.Kind.STATUS, result.value.kind);
        assertTrue(result.value.status);
        assertEquals(1, loader.credentialFetches);
        assertEquals(Arrays.asList("old", "new"), tokens(loader.calls));
    }

    @Test
    public void uiFeedStaysOnTheEmptyPageUntilTheFlagIsOn() {
        TellomiDiscover.Chrome signedOut = TellomiDiscover.chrome(false, true, TellomiDiscover.FeedKind.RECOMMEND, "post-9", false, null);
        assertEquals(TellomiDiscover.Surface.LEGACY_EMPTY, signedOut.surface);
        assertTrue(signedOut.postUnavailable);
        assertFalse(signedOut.issuesNetwork());
        assertFalse(signedOut.showsCompose);
        TellomiDiscover.Chrome tabs = TellomiDiscover.chrome(true, true, TellomiDiscover.FeedKind.RECOMMEND, null, false, null);
        assertEquals(TellomiDiscover.Surface.FEED, tabs.surface);
        TellomiDiscover.Chrome nearby = TellomiDiscover.chrome(true, true, TellomiDiscover.FeedKind.NEARBY, null, false, null);
        assertEquals(TellomiDiscover.Surface.CHOOSE_CITY, nearby.surface);
        assertFalse(nearby.loadsFeed);
        assertFalse(nearby.issuesNetwork());
        TellomiDiscover.Chrome chosen = TellomiDiscover.chrome(true, true, TellomiDiscover.FeedKind.NEARBY, null, false, "cn:chengdu");
        assertEquals(TellomiDiscover.Surface.FEED, chosen.surface);
        assertTrue(chosen.loadsFeed);
        assertTrue(chosen.issuesNetwork());
    }

    private static TellomiDiscover.Outcome run(RecordingLoader loader, boolean loggedIn, TellomiDiscover.Configuration configuration, TellomiDiscover.Operation operation) {
        final TellomiDiscover.Outcome[] box = new TellomiDiscover.Outcome[1];
        loader.loader.run(loggedIn, configuration, operation, new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                box[0] = outcome;
            }
        });
        return box[0];
    }

    private static List<String> ids(TellomiDiscover.FeedPage page) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < page.items.size(); i++) {
            ids.add(page.items.get(i).id);
        }
        return ids;
    }

    private static List<String> reasons(TellomiDiscover.FeedPage page) {
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < page.items.size(); i++) {
            reasons.add(page.items.get(i).reason);
        }
        return reasons;
    }

    private static List<String> tokens(List<TellomiDiscover.HttpCall> calls) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            tokens.add(calls.get(i).token);
        }
        return tokens;
    }

    private static String path(String url) {
        int scheme = url.indexOf("://");
        int start = url.indexOf('/', scheme + 3);
        int query = url.indexOf('?', start);
        return query < 0 ? url.substring(start) : url.substring(start, query);
    }

    private static List<TellomiDiscover.QueryItem> query(String url) {
        if (url == null) {
            return null;
        }
        int query = url.indexOf('?');
        if (query < 0) {
            return null;
        }
        return new TellomiDiscover.HttpCall("GET", url, null, "", false, null).queryItems();
    }

    private static List<String> repeat(String value, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(value);
        }
        return values;
    }

    private static byte[] encodeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        int padding = (4 - ((length + 1) % 4)) % 4;
        byte[] out = new byte[1 + length + padding];
        out[0] = (byte) length;
        System.arraycopy(bytes, 0, out, 1, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (int i = 0; i < parts.length; i++) {
            length += parts[i].length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (int i = 0; i < parts.length; i++) {
            System.arraycopy(parts[i], 0, out, offset, parts[i].length);
            offset += parts[i].length;
        }
        return out;
    }

    private static final class RecordingLoader {
        long nowValue = 1_000;
        TellomiDiscover.Credential credential = new TellomiDiscover.Credential("jwt", 9_000, "cn");
        final List<Integer> statuses = new ArrayList<>();
        int credentialFetches;
        final List<TellomiDiscover.HttpCall> calls = new ArrayList<>();
        final TellomiDiscover.Loader loader;

        RecordingLoader(final TellomiDiscover.Credential cached) {
            final RecordingLoader self = this;
            loader = new TellomiDiscover.Loader(new TellomiDiscover.Clock() {
                @Override
                public long now() {
                    return self.nowValue;
                }
            }, cached, new TellomiDiscover.CredentialFetch() {
                @Override
                public void fetch(TellomiDiscover.CredentialCallback callback) {
                    self.credentialFetches++;
                    if (self.credential != null) {
                        callback.onResult(self.credential, null);
                    } else {
                        callback.onResult(null, TellomiDiscover.ClientError.UNAVAILABLE);
                    }
                }
            }, new TellomiDiscover.HttpTransport() {
                @Override
                public void perform(TellomiDiscover.HttpCall call, TellomiDiscover.HttpCallback callback) {
                    self.calls.add(call);
                    int status = self.statuses.isEmpty() ? 200 : self.statuses.remove(0);
                    byte[] body = status == 200 ? self.body(call) : new byte[0];
                    callback.onResult(new TellomiDiscover.HttpResult(status, body));
                }
            });
        }

        byte[] body(TellomiDiscover.HttpCall call) {
            if ("/v1/capabilities".equals(call.path())) {
                return "{\"version\":1,\"features\":[\"feed\",\"posting\"]}".getBytes(StandardCharsets.UTF_8);
            }
            if ("/v1/feed/status".equals(call.path())) {
                return "{\"visible\":true}".getBytes(StandardCharsets.UTF_8);
            }
            if ("/v1/feed/cards".equals(call.path())) {
                return CARDS_JSON;
            }
            if ("/v1/feed/posts".equals(call.path())) {
                return "{\"id\":\"post-9\",\"state\":\"pending\",\"version\":1}".getBytes(StandardCharsets.UTF_8);
            }
            return "{\"items\":[{\"id\":\"post-9\",\"reason\":\"editorial\"}]}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
