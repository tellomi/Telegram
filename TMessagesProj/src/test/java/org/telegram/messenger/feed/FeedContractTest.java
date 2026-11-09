package org.telegram.messenger.feed;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedContractTest {

    @Test
    public void engineLimitsAreFiveHundredCharsAndTenImages() {
        assertEquals(500, FeedContract.MAX_TEXT);
        assertEquals(500, FeedContract.MAX_COMMENT);
        assertEquals(10, FeedContract.MAX_IMAGES);
        assertEquals(450, FeedContract.TEXT_COUNTER_FROM);
        assertEquals(500, FeedContract.clampText(repeat('a', 800)).length());
        assertEquals(0, FeedContract.imageSlotsLeft(10));
        assertEquals(3, FeedContract.imageSlotsLeft(7));
    }

    @Test
    public void feedListIsIdAndReasonUntilCardsHydrate() {
        String page = "{\"items\":[{\"id\":\"p1\",\"reason\":\"recommend\"},{\"id\":\"p2\",\"reason\":\"following\"}],\"cursor\":\"c2\"}";
        FeedContract.FeedList list = FeedContract.parseFeedList(page);
        assertTrue(list.needsCards);
        assertEquals("c2", list.cursor);
        assertEquals(Arrays.asList("p1", "p2"), list.idsToHydrate());
        assertEquals("recommend", list.refs.get(0).reason);

        String request = FeedContract.cardsRequest(list.idsToHydrate());
        assertTrue(request.contains("\"ids\""));
        assertTrue(request.contains("p1"));
        assertTrue(request.contains("p2"));
        assertEquals("/v1/feed/cards", FeedContract.CARDS_PATH);

        String cards = "{\"cards\":[{\"id\":\"p2\",\"text\":\"second\",\"author\":{\"name\":\"Bo\"}}]}";
        ArrayList<String> ordered = FeedContract.orderedCardJson(cards, list.refs);
        assertEquals(2, ordered.size());
        assertTrue(ordered.get(0).contains("unavailable"));
        assertTrue(ordered.get(1).contains("second"));
        assertFalse(ordered.get(1).contains("\"reason\""));
    }

    @Test
    public void fullPostsAreNotSentBackThroughCards() {
        String page = "{\"items\":[{\"id\":\"p1\",\"text\":\"hello\",\"author\":{\"name\":\"Ada\"}}],\"next_cursor\":\"n\"}";
        FeedContract.FeedList list = FeedContract.parseFeedList(page);
        assertFalse(list.needsCards);
        assertEquals("n", list.cursor);
        assertTrue(list.idsToHydrate().isEmpty());
        assertTrue(list.refs.get(0).fullJson.contains("hello"));
    }

    @Test
    public void topTabsComeFromCapabilitiesAndDropMissingRoutes() {
        ArrayList<String> tabs = FeedContract.tabsFromCapabilities("{\"config\":{\"tabs\":[\"following\",\"friends\",\"nearby\",\"recommend\",\"places\"]}}");
        assertEquals(Arrays.asList("following", "nearby", "recommend"), tabs);
        assertEquals(FeedContract.defaultTabs(), FeedContract.tabsFromCapabilities("{}"));
        assertTrue(FeedContract.tabsFromCapabilities("{\"tabs\":[]}").isEmpty());
        assertNull(FeedContract.streamPath("friends", "", "", ""));
        assertNull(FeedContract.streamPath("places", "", "", ""));
        assertEquals("/v1/feed/recommend?cursor=", FeedContract.streamPath("recommend", "", "cn:chengdu", "latest"));
        assertTrue(FeedContract.streamPath("nearby", "c", "cn:chengdu", "mixed").contains("city="));
        assertFalse(FeedContract.streamPath("nearby", "", "", "").contains("new-count"));
    }

    @Test
    public void momentsTabNeedsConfigKeyAndVisibleStatus() {
        assertFalse(FeedContract.showMomentsTab(false, true));
        assertFalse(FeedContract.showMomentsTab(true, false));
        assertFalse(FeedContract.showMomentsTab(false, false));
        assertTrue(FeedContract.showMomentsTab(true, true));
        assertTrue(FeedContract.statusVisible("{\"visible\":true}"));
        assertFalse(FeedContract.statusVisible("{\"visible\":false}"));
        assertTrue(FeedContract.statusVisible("{\"status\":{\"visible\":true}}"));
        assertFalse(FeedContract.statusVisible("{}"));
        assertFalse(FeedContract.statusVisible("not-json"));
        assertEquals("/v1/feed/status", FeedContract.STATUS_PATH);
        assertEquals("/v1/capabilities", FeedContract.CAPABILITIES_PATH);
    }

    @Test
    public void notificationsUseTabNotKind() {
        assertEquals("/v1/feed/me/notifications?tab=interactions", FeedContract.notificationsPath("interactions"));
        assertEquals("/v1/feed/me/notifications?tab=follows", FeedContract.notificationsPath("follows"));
        assertEquals("/v1/feed/me/notifications?tab=system", FeedContract.notificationsPath("system"));
        assertEquals("/v1/feed/me/notifications?tab=interactions", FeedContract.notificationsPath("all"));
        assertEquals("/v1/feed/me/notifications?tab=interactions", FeedContract.notificationsPath("like"));
        assertFalse(FeedContract.notificationsPath("like").contains("kind"));
    }

    @Test
    public void commentVoiceUsesMediaKeys() {
        String voice = FeedContract.commentBody("hello", "root-1", "audio", "upload-key");
        assertTrue(voice.contains("\"media_keys\""));
        assertTrue(voice.contains("upload-key"));
        assertTrue(voice.contains("root-1"));
        assertFalse(voice.contains("attachment"));
        String photo = FeedContract.commentBody("pic", null, "image", "img-key");
        assertTrue(photo.contains("attachment"));
        assertFalse(photo.contains("media_keys"));
        String longText = FeedContract.commentBody(repeat('b', 800), null, "voice", "v");
        assertFalse(longText.contains(repeat('b', 501)));
        assertTrue(longText.contains("\"media_keys\""));
    }

    @Test
    public void clientSourcesDoNotCallRemovedEndpoints() throws Exception {
        String feed = read("messenger/feed/FeedController.java")
                + read("messenger/feed/FeedContract.java")
                + read("messenger/feed/FeedApi.java")
                + read("ui/Feed/FeedActivity.java")
                + read("ui/Feed/FeedNotificationsActivity.java")
                + read("ui/Feed/FeedCityActivity.java")
                + read("ui/Feed/FeedComposerActivity.java");
        assertFalse(feed.contains("/v1/feed/friends"));
        assertFalse(feed.contains("/new-count"));
        assertFalse(feed.contains("/v1/feed/places"));
        assertFalse(feed.contains("notifications?kind="));
        assertTrue(feed.contains("/v1/feed/cards"));
        assertTrue(feed.contains("/v1/feed/status"));
        assertTrue(feed.contains("/v1/capabilities"));
        assertTrue(feed.contains("media_keys"));
        assertTrue(feed.contains("CARDS_PATH"));
        assertTrue(feed.contains("STATUS_PATH"));
        assertTrue(feed.contains("CAPABILITIES_PATH"));
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String read(String relative) throws Exception {
        Path[] roots = new Path[]{
                Paths.get("src/main/java/org/telegram", relative),
                Paths.get("TMessagesProj/src/main/java/org/telegram", relative)
        };
        for (int i = 0; i < roots.length; i++) {
            if (Files.isRegularFile(roots[i])) {
                return new String(Files.readAllBytes(roots[i]), "UTF-8");
            }
        }
        throw new IllegalStateException("missing " + relative + " from " + Paths.get("").toAbsolutePath());
    }
}
