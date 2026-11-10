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
    public void recommendPageKeepsBothCursorsAndStopsOnAnEmptyLastPage() {
        String page = "{\"items\":[{\"id\":\"p1\",\"reason\":\"recommend\"},{\"id\":\"p1\",\"reason\":\"recommend\"}],\"cursor\":\"c2\",\"next_cursor\":\"c2\"}";
        FeedContract.FeedList list = FeedContract.parseFeedList(page);
        assertEquals("c2", list.cursor);
        assertEquals(Arrays.asList("p1"), list.idsToHydrate());
        assertEquals("/v1/feed/recommend?cursor=c2", FeedContract.nextPagePath("recommend", list.cursor, "", ""));
        assertNull(FeedContract.nextPagePath("recommend", "", "", ""));
        assertNull(FeedContract.nextPagePath("recommend", "null", "", ""));

        String done = "{\"items\":[]}";
        FeedContract.FeedList last = FeedContract.parseFeedList(done);
        assertEquals("", last.cursor);
        FeedContract.PageApply end = FeedContract.applyPage(Arrays.asList("p1", "p2"), last.idsToHydrate(), false, last.cursor);
        assertEquals(Arrays.asList("p1", "p2"), end.ids);
        assertEquals(0, end.added);
        assertTrue(end.stopPaging);
        assertFalse(end.keptExisting);
        assertEquals("", end.nextCursor);
    }

    @Test
    public void loadMoreDedupesIdsAndAnEmptyRefreshKeepsTheList() {
        FeedContract.PageApply more = FeedContract.applyPage(
                Arrays.asList("a", "b"),
                Arrays.asList("b", "b", "c"),
                false,
                "next");
        assertEquals(Arrays.asList("a", "b", "c"), more.ids);
        assertEquals(1, more.added);
        assertFalse(more.stopPaging);
        assertEquals("next", more.nextCursor);

        FeedContract.PageApply repeated = FeedContract.applyPage(
                Arrays.asList("a", "b"),
                Arrays.asList("a", "b"),
                false,
                "again");
        assertEquals(Arrays.asList("a", "b"), repeated.ids);
        assertEquals(0, repeated.added);
        assertTrue(repeated.stopPaging);
        assertEquals("", repeated.nextCursor);

        FeedContract.PageApply refresh = FeedContract.applyPage(
                Arrays.asList("a", "b"),
                Arrays.asList(),
                true,
                "");
        assertTrue(refresh.keptExisting);
        assertEquals(Arrays.asList("a", "b"), refresh.ids);
        assertFalse(refresh.stopPaging);
    }

    @Test
    public void likeTapDoesNotOpenThePost() {
        FeedContract.Zone like = new FeedContract.Zone(40, 200, 88, 248);
        java.util.List<FeedContract.Zone> actions = Arrays.asList(like);
        assertFalse(FeedContract.tapOpensDetail(false, actions, 50, 220));
        assertTrue(FeedContract.tapOpensDetail(false, actions, 12, 30));
        assertFalse(FeedContract.tapOpensDetail(true, actions, 12, 30));
        assertEquals("Hello", FeedContract.plainText("<p>Hello</p>"));
        assertEquals("/v1/feed/posts/post%2F1", FeedContract.postPath("post/1"));
        assertNull(FeedContract.postPath(""));
        assertTrue(FeedContract.noBodySuccess(204));
        assertFalse(FeedContract.noBodySuccess(405));
        assertFalse(FeedContract.noBodySuccess(200));
    }

    @Test
    public void likeControlIsSeparateFromTheRowClick() throws Exception {
        String cell = read("ui/Feed/FeedPostCell.java");
        String list = read("ui/Feed/FeedListView.java");
        String activity = read("ui/Feed/FeedActivity.java");
        assertTrue(cell.contains("FeedContract.ACTION_TAG"));
        assertTrue(cell.contains("delegate.onLike"));
        assertTrue(cell.contains("tapOpensDetail"));
        assertTrue(list.contains("tapOpensDetail"));
        int like = activity.indexOf("void onLike");
        int comment = activity.indexOf("void onComment");
        assertTrue(like >= 0 && comment > like);
        String method = activity.substring(like, comment);
        assertTrue(method.contains("toggleLike"));
        assertFalse(method.contains("FeedDetailActivity"));
        assertFalse(method.contains("presentFragment"));
    }

    @Test
    public void feedStringsShareMomentsKeysAndChineseCopy() throws Exception {
        String english = read("../../../res/values/strings.xml");
        String chinese = read("../../../res/values-zh-rCN/strings.xml");
        java.util.regex.Matcher names = java.util.regex.Pattern.compile("name=\"(Feed[A-Za-z0-9]+)\"")
                .matcher(english);
        int count = 0;
        while (names.find()) {
            String name = names.group(1);
            assertTrue(name, chinese.contains("name=\"" + name + "\""));
            count++;
        }
        assertTrue(count >= 40);
        String ui = read("ui/Feed/FeedUi.java")
                + read("ui/Feed/FeedActivity.java")
                + read("ui/Feed/FeedPostCell.java");
        assertTrue(ui.contains("Moments.Title"));
        assertTrue(ui.contains("Moments.Segment.Recommend"));
        assertTrue(ui.contains("Moments.Like"));
        assertTrue(ui.contains("Moments.Empty.Recommend"));
        assertTrue(ui.contains("LocaleController.getString(key, res)"));
        String design = readDesign();
        assertTrue(design.contains("36 dp"));
        assertTrue(design.contains("5 lines"));
        assertTrue(design.contains("#FF2E38"));
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

    private static String readDesign() throws Exception {
        java.nio.file.Path[] candidates = new java.nio.file.Path[]{
                java.nio.file.Paths.get("docs/FEED-DESIGN.md"),
                java.nio.file.Paths.get("../docs/FEED-DESIGN.md")
        };
        for (int i = 0; i < candidates.length; i++) {
            if (java.nio.file.Files.isRegularFile(candidates[i])) {
                return new String(java.nio.file.Files.readAllBytes(candidates[i]), "UTF-8");
            }
        }
        throw new IllegalStateException("missing docs/FEED-DESIGN.md");
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
