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
    public void engineLimitsMatchTheServerConstants() {
        assertEquals(2000, FeedContract.MAX_TEXT);
        assertEquals(1000, FeedContract.MAX_COMMENT);
        assertEquals(12, FeedContract.MAX_IMAGES);
        assertEquals(1950, FeedContract.TEXT_COUNTER_FROM);
        assertEquals(2000, FeedContract.clampText(repeat('a', 2500)).length());
        assertEquals(0, FeedContract.imageSlotsLeft(12));
        assertEquals(5, FeedContract.imageSlotsLeft(7));
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
        java.util.List<String> home = Arrays.asList("recommend", "following", "friends", "nearby");
        assertEquals(home, FeedContract.tabsFromCapabilities("{\"config\":{\"tabs\":[\"following\",\"friends\",\"nearby\",\"recommend\",\"places\"]}}"));
        assertEquals(home, FeedContract.tabsFromCapabilities("{\"friends_feed\":false}"));
        assertEquals(home, FeedContract.tabsFromCapabilities("{}"));
        assertEquals(home, FeedContract.tabsFromCapabilities("{\"tabs\":[]}"));
        assertEquals("/v1/feed/friends?cursor=", FeedContract.streamPath("friends", "", "", ""));
        assertEquals("/v1/feed/friends?cursor=c2", FeedContract.streamPath("friends", "c2", "cn:chengdu", "latest"));
        assertFalse(FeedContract.streamPath("friends", "c2", "", "").contains("city="));
        assertNull(FeedContract.streamPath("places", "", "", ""));
        assertEquals("/v1/feed/recommend?cursor=", FeedContract.streamPath("recommend", "", "cn:chengdu", "latest"));
        assertTrue(FeedContract.streamPath("nearby", "c", "cn:chengdu", "mixed").contains("city="));
        assertFalse(FeedContract.streamPath("nearby", "", "", "").contains("new-count"));
        String page = "{\"items\":[{\"id\":\"p1\",\"reason\":\"friend\"},{\"id\":\"p2\",\"reason\":\"topic\"},{\"id\":\"p3\",\"reason\":\"repost\"}],\"cursor\":\"c\"}";
        FeedContract.FeedList list = FeedContract.parseFeedList(page);
        assertEquals("friend", list.refs.get(0).reason);
        assertEquals("topic", list.refs.get(1).reason);
        assertFalse(FeedContract.showRepostHeader("topic"));
        assertFalse(FeedContract.showRepostHeader("source"));
        assertFalse(FeedContract.showRepostHeader("friend"));
        assertFalse(FeedContract.showRepostHeader("following"));
        assertTrue(FeedContract.showRepostHeader("repost"));
        assertTrue(FeedContract.showRepostHeader(""));
        assertTrue(FeedContract.isMutualAudience("mutual_followers"));
        assertFalse(FeedContract.isMutualAudience("public"));
        assertTrue(FeedContract.cardUnavailable(false, "unavailable"));
        assertTrue(FeedContract.cardUnavailable(true, "published"));
        assertFalse(FeedContract.cardUnavailable(false, "published"));
        assertEquals(1080, FeedContract.renditionTargetPx(1));
        assertEquals(360, FeedContract.renditionTargetPx(2));
        ArrayList<FeedContract.Tier> tiers = new ArrayList<>();
        tiers.add(new FeedContract.Tier("big", "", 1080, 720));
        tiers.add(new FeedContract.Tier("small", "https://cdn.example/s.jpg", 360, 240));
        assertEquals(1, FeedContract.chooseRendition(tiers, 360));
        assertEquals(0, FeedContract.chooseRendition(tiers, 1080));
        assertEquals(-1, FeedContract.chooseRendition(tiers, 2000));
        assertEquals("https://cdn.example/s.jpg", FeedContract.resolveMediaUrl("small", "https://cdn.example/s.jpg", "https://media.example"));
        assertEquals("https://media.example/big.jpg", FeedContract.resolveMediaUrl("big.jpg", "", "https://media.example/"));
        ArrayList<FeedContract.Tier> parsed = FeedContract.parseRenditions("{\"renditions\":[{\"key\":\"a\",\"w\":1080,\"h\":1080},{\"key\":\"b\",\"url\":\"https://cdn.example/b.jpg\",\"w\":360,\"h\":360}]}");
        assertEquals(2, parsed.size());
        assertEquals("a", parsed.get(0).key);
        assertEquals(360, parsed.get(1).longEdge());
        assertEquals("https://cdn.example/b.jpg", parsed.get(1).url);
        FeedContract.Tier poster = FeedContract.parsePoster("{\"poster\":{\"key\":\"p\",\"url\":\"https://cdn.example/p.jpg\",\"w\":640,\"h\":360}}");
        assertEquals("p", poster.key);
        assertEquals("https://cdn.example/p.jpg", poster.url);
        assertNull(FeedContract.parsePoster("{\"cover_key\":\"legacy\"}"));
    }

    @Test
    public void momentsTabNeedsConfigKeyAndVisibleStatus() {
        assertTrue(FeedContract.showMomentsTab(false, true));
        assertTrue(FeedContract.showMomentsTab(true, false));
        assertTrue(FeedContract.showMomentsTab(false, false));
        assertTrue(FeedContract.showMomentsTab(true, true));
        assertEquals(1, FeedContract.imageSampleSize(4032, 3024, "800_800", 3f));
        assertTrue(FeedContract.imageSampleSize(4032, 3024, FeedContract.imageFilter(864, 864, 3f), 3f) >= 4);
        long full = FeedContract.decodedArgbBytes(4032, 3024, 1);
        assertEquals(4032L * 3024L * 4L, full);
        assertTrue(full > 45L * 1024 * 1024);
        String cell = FeedContract.imageFilter(900, 900, 3f);
        int shrunk = FeedContract.imageSampleSize(4032, 3024, cell, 3f);
        long small = FeedContract.decodedArgbBytes(4032, 3024, shrunk);
        assertTrue(small < 8L * 1024 * 1024);
        assertTrue(full * 6 > 256L * 1024 * 1024);
        assertTrue(small * 6 < 48L * 1024 * 1024);
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
        assertEquals("/v1/feed/posts/p1/comments?sort=latest&thread=1", FeedContract.commentsPath("p1", "", "latest"));
        assertEquals("/v1/feed/posts/p1/comments?sort=hot&thread=1", FeedContract.commentsPath("p1", "", "nope"));
        assertFalse(FeedContract.hasNewerHead("", "p2"));
        assertFalse(FeedContract.hasNewerHead("p1", ""));
        assertFalse(FeedContract.hasNewerHead("p1", "p1"));
        assertTrue(FeedContract.hasNewerHead("p1", "p2"));
        assertEquals("WaitingForNetwork", FeedContract.connectionTitleKey(2));
        assertEquals("Connecting", FeedContract.connectionTitleKey(1));
        assertEquals("ConnectingToProxyWithDots", FeedContract.connectionTitleKey(4));
        assertEquals("Updating", FeedContract.connectionTitleKey(5));
        assertNull(FeedContract.connectionTitleKey(3));
        assertEquals("mixed", FeedContract.nextNearbySort("latest"));
        assertEquals("latest", FeedContract.nextNearbySort("mixed"));
        assertEquals("mixed", FeedContract.nextNearbySort(null));
        FeedContract.LoadEpoch epoch = new FeedContract.LoadEpoch();
        int first = epoch.begin();
        assertTrue(epoch.isCurrent(first));
        int second = epoch.begin();
        assertFalse(epoch.isCurrent(first));
        assertTrue(epoch.isCurrent(second));
        assertEquals("/v1/feed/comments/c1/replies", FeedContract.repliesPath("c1", ""));
        assertEquals("/v1/feed/comments/c1/replies?cursor=n", FeedContract.repliesPath("c1", "n"));
        String longText = FeedContract.commentBody(repeat('b', 1200), null, "voice", "v");
        assertFalse(longText.contains(repeat('b', 1001)));
        assertTrue(longText.contains(repeat('b', 1000)));
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
    public void momentsKeysMatchResourcesAndSurviveReleaseShrinking() throws Exception {
        String english = read("../../../res/values/strings.xml");
        String chinese = read("../../../res/values-zh-rCN/strings.xml");
        java.util.regex.Matcher names = java.util.regex.Pattern.compile("name=\"(Moments_[A-Za-z0-9_]+)\"")
                .matcher(english);
        int count = 0;
        while (names.find()) {
            String name = names.group(1);
            assertTrue(name, chinese.contains("name=\"" + name + "\""));
            count++;
        }
        assertTrue(count >= 40);
        assertFalse(english.contains("name=\"Feed"));
        String sources = "";
        String[] files = new String[]{
                "ui/MainTabsActivity.java",
                "ui/Feed/FeedUi.java",
                "ui/Feed/FeedActivity.java",
                "ui/Feed/FeedPostCell.java",
                "ui/Feed/FeedCommentSheet.java"
        };
        for (int i = 0; i < files.length; i++) {
            sources += read(files[i]);
        }
        assertTrue(sources.contains("LocaleController.getString(\"Moments_Title\", R.string.Moments_Title)"));
        assertFalse(sources.contains("createConfigurationContext"));
        assertFalse(sources.contains("FeedUi.s("));
        java.util.regex.Matcher calls = java.util.regex.Pattern.compile(
                "LocaleController\\.(?:getString|formatString)\\(\"([^\"]+)\", R\\.string\\.([A-Za-z0-9_]+)")
                .matcher(sources);
        int callsFound = 0;
        while (calls.find()) {
            assertEquals(calls.group(1), calls.group(2));
            assertTrue(english.contains("name=\"" + calls.group(1) + "\""));
            callsFound++;
        }
        assertTrue(callsFound >= 10);
    }

    @Test
    public void feedStringsShareMomentsKeysAndChineseCopy() throws Exception {
        String english = read("../../../res/values/strings.xml");
        String chinese = read("../../../res/values-zh-rCN/strings.xml");
        java.util.regex.Matcher names = java.util.regex.Pattern.compile("name=\"(Moments_[A-Za-z0-9_]+)\"")
                .matcher(english);
        int count = 0;
        while (names.find()) {
            assertTrue(names.group(1), chinese.contains("name=\"" + names.group(1) + "\""));
            count++;
        }
        assertTrue(count >= 40);
        String ui = read("ui/Feed/FeedActivity.java") + read("ui/Feed/FeedPostCell.java");
        assertTrue(ui.contains("Moments_Title"));
        assertTrue(ui.contains("Moments_Segment_Recommend"));
        assertTrue(ui.contains("Moments_Like"));
        assertTrue(ui.contains("Moments_Empty_Recommend"));
        assertFalse(ui.contains("Moments.Title"));
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
        assertTrue(design.contains("360 dp"));
        assertTrue(design.contains("RecyclerViewPreloader"));
        assertTrue(design.contains("WaitingForNetwork"));
        assertTrue(design.contains("Moments_Nearby_Mixed"));
        String cell = read("ui/Feed/FeedPostCell.java");
        assertTrue(cell.contains("canScrollHorizontally"));
        assertTrue(cell.contains("setLockPager"));
        String activity = read("ui/Feed/FeedActivity.java");
        assertTrue(activity.contains("Moments_NewPosts"));
        assertTrue(activity.contains("probeHead"));
        int vote = activity.indexOf("void onVote");
        int media = activity.indexOf("void onMedia");
        assertTrue(vote >= 0 && media > vote);
        String voteMethod = activity.substring(vote, media);
        assertTrue(voteMethod.contains("controller().vote"));
        assertFalse(voteMethod.contains("FeedCommentSheet"));
        assertTrue(activity.contains("didUpdateConnectionState"));
        assertTrue(activity.contains("Moments_Nearby_Mixed"));
        String playback = read("ui/Feed/FeedPlayback.java");
        assertTrue(playback.contains("toggleVoice"));
        assertTrue(playback.contains("pauseMessage"));
        assertTrue(cell.contains("voiceRow"));
        assertTrue(cell.contains("bindLike"));
        assertTrue(cell.contains("listUrl"));
        assertTrue(activity.contains("FeedDetailActivity"));
        assertTrue(read("ui/Feed/FeedListView.java").contains("PAYLOAD_LIKE"));
        assertTrue(read("ui/Feed/FeedListView.java").contains("setHasStableIds"));
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

    @Test
    public void cancelledLoadsAvatarsClocksAndLikesStayScoped() throws Exception {
        assertFalse(FeedContract.acceptPage(false, null));
        assertFalse(FeedContract.acceptPage(true, "cancelled"));
        assertTrue(FeedContract.acceptPage(true, null));
        assertFalse(FeedContract.acceptPage(false, "network"));

        FeedContract.StableIds ids = new FeedContract.StableIds();
        assertTrue(ids.idFor("Aa") != ids.idFor("BB"));
        assertEquals(ids.idFor("Aa"), ids.idFor("Aa"));
        assertEquals(0, ids.idFor(""));

        assertEquals("0:00", FeedContract.clock(0));
        assertEquals("0:09", FeedContract.clock(9_000));
        assertEquals("1:05", FeedContract.clock(65_000));
        assertEquals("telegram", FeedContract.avatarSource(true, "https://cdn.example/a"));
        assertEquals("http", FeedContract.avatarSource(false, "https://cdn.example/a"));
        assertEquals("letter", FeedContract.avatarSource(false, "avatar-key"));

        String same = FeedContract.mediaBindKey("p1", java.util.Arrays.asList("https://cdn/a"));
        assertTrue(FeedContract.sameMediaBind(same, FeedContract.mediaBindKey("p1", java.util.Arrays.asList("https://cdn/a"))));
        assertFalse(FeedContract.sameMediaBind(same, FeedContract.mediaBindKey("p2", java.util.Arrays.asList("https://cdn/a"))));

        FeedContract.RelativeBucket minutes = FeedContract.relativeBucket(180_000L, 1L);
        assertEquals("minute", minutes.unit);
        assertEquals(2, minutes.count);
        assertEquals("2 minutes ago", minutes.english);
        assertTrue(FeedContract.replaceEnglishTimer("zh_cn", "2 minutes ago", minutes.english));
        assertFalse(FeedContract.replaceEnglishTimer("zh_cn", "2分钟前", minutes.english));
        assertFalse(FeedContract.replaceEnglishTimer("en", "2 minutes ago", minutes.english));
        assertEquals("now", FeedContract.relativeBucket(10_000L, 1L).unit);
        assertEquals("", FeedContract.relativeBucket(10_000L, 0L).unit);
        assertEquals("Moments_Conn_Waiting", FeedContract.connectionMomentsKey("WaitingForNetwork"));
        assertNull(FeedContract.connectionMomentsKey(null));

        String controller = read("messenger/feed/FeedController.java");
        int begin = controller.indexOf("public int beginLoad");
        int cancelReads = controller.indexOf("api.cancelReads()", begin);
        int epochBegin = controller.indexOf(".begin()", begin);
        assertTrue(begin >= 0 && epochBegin > begin && epochBegin < cancelReads);
        assertTrue(controller.contains("cancelLoadsExcept"));
        assertTrue(controller.contains("acceptPage"));
        assertTrue(controller.contains("dropPending"));
        String activity = read("ui/Feed/FeedActivity.java");
        int animation = activity.indexOf("void onTabAnimationUpdate");
        int scrollEnd = activity.indexOf("void onScrollEnd");
        assertTrue(animation >= 0 && scrollEnd > animation);
        String duringSwipe = activity.substring(animation, scrollEnd);
        assertFalse(duringSwipe.contains("ensure("));
        assertTrue(activity.contains("cancelLoadsExcept"));
        String cell = read("ui/Feed/FeedPostCell.java");
        assertTrue(cell.contains("sameMediaBind"));
        assertTrue(cell.contains("delegate.onMedia"));
        assertTrue(cell.contains("FeedContract.clock"));
        assertFalse(cell.contains("clearImage"));
        assertFalse(cell.contains("+ \"s\""));
        String list = read("ui/Feed/FeedListView.java");
        assertTrue(list.contains("PAYLOAD_LIKE"));
        assertTrue(list.contains("bindLike"));
        assertTrue(list.contains("StableIds"));
        String like = controller.substring(controller.indexOf("void toggleLike"), controller.indexOf("void toggleSave"));
        assertTrue(like.contains("notifyPost"));
        assertFalse(like.contains("notifyListeners"));
        String ui = read("ui/Feed/FeedUi.java");
        assertTrue(ui.contains("Moments_Time_Now"));
        assertTrue(ui.contains("avatarSource"));
        assertFalse(ui.contains("cancelLoadImage"));
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
