package org.telegram.messenger.feed;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

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
    public void momentsTabIsAlwaysPresent() throws Exception {
        assertTrue(FeedContract.showMomentsTab(false, true));
        assertTrue(FeedContract.showMomentsTab(true, false));
        assertTrue(FeedContract.showMomentsTab(false, false));
        assertTrue(FeedContract.showMomentsTab(true, true));
        int full = FeedContract.imageSampleSize(4032, 3024, "800_800", 3f);
        assertEquals(1, full);
        String filter = FeedContract.imageFilter(864, 864, 3f);
        assertTrue(FeedContract.imageSampleSize(4032, 3024, filter, 3f) >= 4);
        assertFalse(read("ui/Feed/FeedPostCell.java").contains("800_800"));
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
        assertTrue(photo.contains("media_keys"));
        assertTrue(photo.contains("img-key"));
        assertFalse(photo.contains("attachment"));
        String sticker = FeedContract.commentBody("", "parent", "sticker", "stk");
        assertTrue(sticker.contains("media_keys"));
        assertTrue(sticker.contains("\"sticker\":true"));
        assertEquals("/v1/feed/posts/p1/comments?sort=hot&thread=1", FeedContract.commentsPath("p1", ""));
        assertEquals("/v1/feed/posts/p1/comments?sort=hot&thread=1&cursor=c2", FeedContract.commentsPath("p1", "c2"));
        assertEquals("/v1/feed/comments/c1/replies", FeedContract.repliesPath("c1", ""));
        assertEquals("/v1/feed/comments/c1/replies?cursor=n", FeedContract.repliesPath("c1", "n"));
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
    public void prefetchPlanMatchesTheSharedSpec() {
        assertEquals(10, FeedContract.LOAD_MORE_REMAINING);
        assertEquals(3, FeedContract.PREFETCH_AHEAD_POSTS);
        assertEquals(1, FeedContract.PREFETCH_BEHIND_POSTS);
        assertEquals(2, FeedContract.PREFETCH_IMAGES_PER_POST);
        assertEquals(2, FeedContract.PREFETCH_MAX_INFLIGHT);
        assertEquals(4f, FeedContract.FLING_PAUSE_SPEED, 0f);
        assertTrue(FeedContract.shouldLoadMore(10, true, false));
        assertFalse(FeedContract.shouldLoadMore(11, true, false));
        assertFalse(FeedContract.shouldLoadMore(10, true, true));
        assertFalse(FeedContract.shouldLoadMore(10, false, false));

        ArrayList<FeedContract.PrefetchPost> posts = new ArrayList<FeedContract.PrefetchPost>();
        for (int i = 0; i < 8; i++) {
            FeedContract.PrefetchPost post = new FeedContract.PrefetchPost();
            post.avatar = "avatar-" + i;
            post.imageFilter = "288_288";
            if (i == 3) {
                post.images.add("cover-3");
            } else {
                post.images.add("img-" + i + "-0");
                post.images.add("img-" + i + "-1");
                post.images.add("img-" + i + "-2");
            }
            posts.add(post);
        }
        ArrayList<FeedContract.PrefetchHit> down = FeedContract.prefetchPlan(posts, 0, 1, 1, true, false);
        String joined = keys(down);
        assertTrue(joined.contains("avatar-2"));
        assertTrue(joined.contains("img-2-0"));
        assertTrue(joined.contains("img-2-1"));
        assertFalse(joined.contains("img-2-2"));
        assertTrue(joined.contains("cover-3"));
        assertFalse(joined.contains("img-3"));
        assertTrue(joined.contains("avatar-4"));
        assertFalse(joined.contains("avatar-1"));
        assertFalse(joined.contains("avatar-5"));

        ArrayList<FeedContract.PrefetchHit> up = FeedContract.prefetchPlan(posts, 5, 6, -1, true, false);
        String upKeys = keys(up);
        assertTrue(upKeys.contains("avatar-4"));
        assertTrue(upKeys.contains("avatar-3"));
        assertTrue(upKeys.contains("avatar-2"));
        assertTrue(upKeys.contains("avatar-7"));
        assertFalse(upKeys.contains("avatar-1"));
        assertFalse(upKeys.contains("avatar-5"));

        assertTrue(FeedContract.prefetchPlan(posts, 0, 1, 1, false, false).isEmpty());
        ArrayList<FeedContract.PrefetchHit> low = FeedContract.prefetchPlan(posts, 0, 1, 1, true, true);
        int images = 0;
        for (int i = 0; i < low.size(); i++) {
            if (!low.get(i).filter.equals(FeedContract.AVATAR_FILTER)) {
                images++;
            }
        }
        assertEquals(1, images);
        assertTrue(keys(low).contains("avatar-2"));
        assertFalse(keys(low).contains("avatar-3"));

        ArrayList<FeedContract.PrefetchHit> next = FeedContract.prefetchPlan(posts, 1, 2, 1, true, false);
        HashSet<String> left = new HashSet<String>();
        for (int i = 0; i < down.size(); i++) {
            left.add(down.get(i).key);
        }
        for (int i = 0; i < next.size(); i++) {
            left.remove(next.get(i).key);
        }
        assertTrue(left.contains("avatar-2\n" + FeedContract.AVATAR_FILTER));
        assertFalse(left.contains("avatar-3\n" + FeedContract.AVATAR_FILTER));
    }

    private static String keys(ArrayList<FeedContract.PrefetchHit> hits) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            builder.append(hits.get(i).url).append(' ');
        }
        return builder.toString();
    }

    @Test
    public void momentsTabTitleCannotCrashLaunch() throws Exception {
        String tabs = read("ui/MainTabsActivity.java");
        assertTrue(tabs.contains("FeedUi.s(\"Moments.Title\""));
        String ui = read("ui/Feed/FeedUi.java");
        int method = ui.indexOf("String resourceString");
        assertTrue(method > 0);
        String body = ui.substring(method, ui.indexOf("public static boolean reduceMotion"));
        assertTrue(body.contains("createConfigurationContext"));
        assertTrue(body.contains("catch (Throwable"));
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
        assertTrue(design.contains("40 dp"));
        assertTrue(design.contains("5 lines"));
        assertTrue(design.contains("78%"));
        assertTrue(design.contains("msg_send"));
        assertTrue(design.contains("#FF2E38"));
        assertTrue(design.contains("tab_article"));
        assertTrue(design.contains("msg_discussion"));
        assertTrue(design.contains("filled_fab_compose_32"));
        assertTrue(design.contains("media_like_active"));
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
