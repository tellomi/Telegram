package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * D-58. Off sends {@code personalized=false}. On matches the production
 * recommend URL, which omits the parameter.
 */
public class TellomiDiscoverPersonalizationTest {

    private static final String BASE = "https://feed.example";

    @Test
    public void missingValueIsOnAndOmitsPersonalized() {
        TellomiDiscoverPersonalization.MapPrefs prefs = new TellomiDiscoverPersonalization.MapPrefs();
        assertTrue(TellomiDiscoverPersonalization.DEFAULT_ON);
        assertTrue(TellomiDiscoverPersonalization.enabled(null));
        assertTrue(TellomiDiscoverPersonalization.read(prefs));
        assertTrue(TellomiDiscoverPersonalization.read((TellomiDiscoverPersonalization.BoolPrefs) null));

        String production = TellomiDiscover.feedUrl(BASE, TellomiDiscover.FeedKind.RECOMMEND, null, true);
        String on = recommendUrl(TellomiDiscoverPersonalization.read(prefs));
        assertEquals(production, on);
        assertEquals("/v1/feed/recommend", path(on));
        assertNull(query(on));
        assertFalse(on.contains("personalized"));
        assertRequestOmitsPersonalized(true);
    }

    @Test
    public void offSendsPersonalizedFalseAndOnAgainOmitsIt() {
        TellomiDiscoverPersonalization.MapPrefs prefs = new TellomiDiscoverPersonalization.MapPrefs();
        TellomiDiscoverPersonalization.write(prefs, false);
        assertFalse(TellomiDiscoverPersonalization.read(prefs));
        String off = recommendUrl(TellomiDiscoverPersonalization.read(prefs));
        assertEquals("/v1/feed/recommend", path(off));
        assertEquals(Collections.singletonList(new TellomiDiscover.QueryItem("personalized", "false")), query(off));
        assertTrue(off.contains("personalized=false"));
        assertFalse(off.contains("personalized=true"));
        assertRequestCarriesPersonalizedFalse();

        TellomiDiscoverPersonalization.write(prefs, true);
        String on = recommendUrl(TellomiDiscoverPersonalization.read(prefs));
        assertEquals(TellomiDiscover.feedUrl(BASE, TellomiDiscover.FeedKind.RECOMMEND, null, true), on);
        assertNull(query(on));
        assertRequestOmitsPersonalized(true);
    }

    @Test
    public void followingIgnoresTheSwitch() {
        String following = TellomiDiscover.feedUrl(
            BASE,
            TellomiDiscover.FeedKind.FOLLOWING,
            null,
            TellomiDiscoverPersonalization.forFeed(TellomiDiscover.FeedKind.FOLLOWING, false)
        );
        assertEquals("/v1/feed/following", path(following));
        assertNull(query(following));
        assertFalse(following.contains("personalized"));
    }

    @Test
    public void recommendTabReloadsImmediately() {
        assertTrue(TellomiDiscoverPersonalization.reloadRecommendImmediately(TellomiDiscover.FeedKind.RECOMMEND));
        assertFalse(TellomiDiscoverPersonalization.reloadRecommendImmediately(TellomiDiscover.FeedKind.FOLLOWING));
        assertFalse(TellomiDiscoverPersonalization.reloadRecommendImmediately(TellomiDiscover.FeedKind.NEARBY));
    }

    @Test
    public void listenerFiresWithTheStoredValue() {
        final List<Boolean> seen = new ArrayList<>();
        TellomiDiscoverPersonalization.Listener listener = new TellomiDiscoverPersonalization.Listener() {
            @Override
            public void onChanged(int account, boolean on) {
                if (account == 3) {
                    seen.add(on);
                }
            }
        };
        TellomiDiscoverPersonalization.addListener(listener);
        try {
            TellomiDiscoverPersonalization.MapPrefs prefs = new TellomiDiscoverPersonalization.MapPrefs();
            TellomiDiscoverPersonalization.write(prefs, false);
            TellomiDiscoverPersonalization.notifyForTest(3, TellomiDiscoverPersonalization.read(prefs));
            assertEquals(Collections.singletonList(Boolean.FALSE), seen);
        } finally {
            TellomiDiscoverPersonalization.removeListener(listener);
        }
    }

    @Test
    public void screenReadsTheSwitchAndChineseLabelIsPersonalized() throws Exception {
        String screen = new String(Files.readAllBytes(find("TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverScreen.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(screen.contains("TellomiDiscoverPersonalization.forFeed"));
        assertFalse(screen.contains("Operation.feed(requested, cursor, true)"));
        String zh = new String(Files.readAllBytes(find("TMessagesProj/tellomi/res/values-zh/strings.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue(zh.contains("name=\"Tellomi_Discover_Personalization\">个性化推荐<"));
        String privacy = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(privacy.contains("TellomiDiscoverPersonalization.toggle"));
        assertTrue(privacy.contains("R.string.Tellomi_Discover_Personalization"));
    }

    private static String recommendUrl(boolean switchOn) {
        return TellomiDiscover.feedUrl(
            BASE,
            TellomiDiscover.FeedKind.RECOMMEND,
            null,
            TellomiDiscoverPersonalization.forFeed(TellomiDiscover.FeedKind.RECOMMEND, switchOn)
        );
    }

    private static void assertRequestOmitsPersonalized(boolean switchOn) {
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall(
            BASE,
            TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, TellomiDiscoverPersonalization.forFeed(TellomiDiscover.FeedKind.RECOMMEND, switchOn)),
            "jwt-1"
        );
        assertEquals("/v1/feed/recommend", call.path());
        assertNull(call.rawQuery());
        assertFalse(call.url.contains("personalized"));
    }

    private static void assertRequestCarriesPersonalizedFalse() {
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall(
            BASE,
            TellomiDiscover.Operation.feed(TellomiDiscover.FeedKind.RECOMMEND, null, TellomiDiscoverPersonalization.forFeed(TellomiDiscover.FeedKind.RECOMMEND, false)),
            "jwt-1"
        );
        assertEquals("/v1/feed/recommend", call.path());
        assertEquals("personalized=false", call.rawQuery());
        assertTrue(call.url.contains("personalized=false"));
    }

    private static String path(String url) {
        int scheme = url.indexOf("://");
        int start = url.indexOf('/', scheme + 3);
        int query = url.indexOf('?', start);
        return query < 0 ? url.substring(start) : url.substring(start, query);
    }

    private static List<TellomiDiscover.QueryItem> query(String url) {
        if (url == null || url.indexOf('?') < 0) {
            return null;
        }
        return new TellomiDiscover.HttpCall("GET", url, null, "", false, null).queryItems();
    }

    private static File find(String relative) {
        File direct = new File(relative);
        if (direct.isFile()) {
            return direct;
        }
        File nested = new File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        String stripped = relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative;
        File fromModule = new File(stripped);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative + " from " + new File(".").getAbsolutePath());
    }
}
