package org.telegram.messenger.feed;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeedLimitsTest {

    /** Shape of GET /v1/feed/config from content-engine server.go. */
    private static final String SERVER_CONFIG = "{"
            + "\"media_base_url\":\"https://media.example/feed\","
            + "\"personalization_toggle_visible\":true,"
            + "\"text_limit\":900,"
            + "\"limits\":{"
            + "\"post_text_chars\":420,"
            + "\"post_images\":4,"
            + "\"post_videos\":2,"
            + "\"article_chars\":8000,"
            + "\"edit_window_seconds\":900,"
            + "\"voice_comment_max_seconds\":20,"
            + "\"voice_post_max_seconds\":30,"
            + "\"video_max_seconds\":45,"
            + "\"comment_text_chars\":80,"
            + "\"page_size\":15,"
            + "\"posts_per_hour\":10,"
            + "\"comments_per_hour\":40,"
            + "\"max_topics\":2,"
            + "\"max_mentions\":5,"
            + "\"probe_interval_seconds\":30,"
            + "\"notification_days\":90,"
            + "\"text_limit\":111,"
            + "\"comment_text_limit\":222,"
            + "\"voice_max_seconds\":180,"
            + "\"image_limit\":9,"
            + "\"video_limit\":8"
            + "}}";

    @Test
    public void serverPayloadMapsTheRealLimitKeys() {
        FeedLimits.Values values = FeedLimits.parse(SERVER_CONFIG);
        assertEquals(420, values.text);
        assertEquals(370, values.textCounterFrom);
        assertEquals(80, values.comment);
        assertEquals(4, values.images);
        assertEquals(2, values.videos);
        assertEquals(45, values.videoSec);
        assertEquals(30, values.voicePostSec);
        assertEquals(20, values.voiceCommentSec);
        assertEquals(15, values.pageSize);
        assertEquals(5, values.mentions);
        assertEquals(2, values.topics);
        assertTrue(values.personalizationToggleVisible);
        assertFalse(values.text == 900);
        assertFalse(values.text == 111);
        assertFalse(values.comment == 222);
        assertFalse(values.voicePostSec == 180);
    }

    @Test
    public void omittedOrMistypedKeysKeepFallbacksAndHideTheSwitch() {
        FeedLimits.Values missing = FeedLimits.parse("{\"media_base_url\":\"https://media.example/feed\"}");
        assertEquals(FeedLimits.FALLBACK_TEXT, missing.text);
        assertEquals(FeedLimits.FALLBACK_COMMENT, missing.comment);
        assertEquals(FeedLimits.FALLBACK_VOICE_POST_SEC, missing.voicePostSec);
        assertEquals(FeedLimits.FALLBACK_VOICE_COMMENT_SEC, missing.voiceCommentSec);
        assertEquals(60, missing.voicePostSec);
        assertFalse(missing.personalizationToggleVisible);

        FeedLimits.Values cn = FeedLimits.parse("{\"personalization_toggle_visible\":false,\"limits\":{\"post_text_chars\":500}}");
        assertFalse(cn.personalizationToggleVisible);
        assertEquals(500, cn.text);

        FeedLimits.Values bad = FeedLimits.parse("{"
                + "\"personalization_toggle_visible\":\"true\","
                + "\"limits\":{"
                + "\"post_text_chars\":\"420\","
                + "\"comment_text_chars\":0,"
                + "\"voice_post_max_seconds\":30.5,"
                + "\"post_images\":4"
                + "}}");
        assertFalse(bad.personalizationToggleVisible);
        assertEquals(FeedLimits.FALLBACK_TEXT, bad.text);
        assertEquals(FeedLimits.FALLBACK_COMMENT, bad.comment);
        assertEquals(FeedLimits.FALLBACK_VOICE_POST_SEC, bad.voicePostSec);
        assertEquals(4, bad.images);

        FeedLimits.Values invented = FeedLimits.parse("{\"limits\":{\"text_limit\":100,\"comment_text_limit\":80,\"voice_max_seconds\":30,\"image_limit\":4,\"video_limit\":2}}");
        assertEquals(FeedLimits.FALLBACK_TEXT, invented.text);
        assertEquals(FeedLimits.FALLBACK_COMMENT, invented.comment);
        assertEquals(FeedLimits.FALLBACK_VOICE_POST_SEC, invented.voicePostSec);
        assertEquals(FeedLimits.FALLBACK_IMAGES, invented.images);
        assertEquals(FeedLimits.FALLBACK_VIDEOS, invented.videos);
    }

    @Test
    public void appliedLimitsChangeCommentImageVoiceAndMentionsUntilReset() {
        FeedConfig.apply(FeedLimits.fallback());
        FeedConfig.apply(FeedLimits.parse(SERVER_CONFIG));
        try {
            assertEquals(80, FeedConfig.MAX_COMMENT);
            assertEquals(4, FeedConfig.MAX_IMAGES);
            assertEquals(2, FeedConfig.MAX_VIDEOS);
            assertEquals(45, FeedConfig.MAX_VIDEO_SEC);
            assertEquals(30, FeedConfig.MAX_VOICE_SEC);
            assertEquals(20, FeedConfig.MAX_VOICE_COMMENT_SEC);
            assertEquals(5, FeedConfig.MAX_MENTIONS);
            assertEquals(2, FeedConfig.MAX_TOPICS);
            assertEquals(15, FeedConfig.PAGE_SIZE);
            assertTrue(FeedConfig.personalizationToggleVisible());
            assertEquals(3, FeedContract.imageSlotsLeft(1));
            JsonObject comment = JsonParser.parseString(FeedContract.commentBody(repeat('b', 100), null, null, null)).getAsJsonObject();
            assertEquals(80, comment.get("text").getAsString().length());
            assertEquals(420, FeedContract.clampText(repeat('a', 900)).length());
        } finally {
            FeedConfig.apply(FeedLimits.fallback());
        }
        assertEquals(500, FeedConfig.MAX_COMMENT);
        assertEquals(10, FeedContract.imageSlotsLeft(0));
        assertEquals(60, FeedConfig.MAX_VOICE_SEC);
        assertEquals(60, FeedConfig.MAX_VOICE_COMMENT_SEC);
        assertEquals(10, FeedConfig.MAX_MENTIONS);
        assertFalse(FeedConfig.personalizationToggleVisible());
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void preferenceToggleRequestIsABooleanPut() {
        assertEquals("/v1/feed/me/preferences", FeedLimits.PREFERENCES_PATH);
        assertEquals("PUT", FeedLimits.PREFERENCES_METHOD);

        JsonObject off = JsonParser.parseString(FeedLimits.preferenceRequest(false)).getAsJsonObject();
        assertEquals(1, off.size());
        assertTrue(off.get("personalized").getAsJsonPrimitive().isBoolean());
        assertFalse(off.get("personalized").getAsBoolean());

        JsonObject on = JsonParser.parseString(FeedLimits.preferenceRequest("{\"locale\":\"zh\"}", true)).getAsJsonObject();
        assertEquals("zh", on.get("locale").getAsString());
        assertTrue(on.get("personalized").getAsJsonPrimitive().isBoolean());
        assertTrue(on.get("personalized").getAsBoolean());
        assertFalse(FeedLimits.preferenceRequest(true).contains("\"personalized\":\"true\""));

        assertNull(FeedLimits.parsePersonalized("{\"personalized\":\"false\"}"));
        assertEquals(Boolean.FALSE, FeedLimits.parsePersonalized("{\"personalized\":false}"));
        assertEquals(Boolean.TRUE, FeedLimits.parsePersonalized("{\"personalized\":true,\"locale\":\"zh\"}"));
        assertNull(FeedLimits.parsePersonalized("{}"));

        assertEquals("/v1/feed/recommend?cursor=", FeedLimits.recommendPath("/v1/feed/recommend?cursor=", true));
        assertEquals("/v1/feed/recommend?cursor=&personalized=false", FeedLimits.recommendPath("/v1/feed/recommend?cursor=", false));
        assertNull(FeedLimits.recommendPath(null, false));
    }

    @Test
    public void toggleLivesOnTheFeedSettingsPageOnly() throws Exception {
        String settings = read("ui/Feed/FeedSettingsActivity.java");
        String privacy = read("ui/PrivacySettingsActivity.java");
        String controller = read("messenger/feed/FeedController.java");
        assertTrue(settings.contains("FeedLimits.PREFERENCES_PATH"));
        assertTrue(settings.contains("FeedLimits.PREFERENCES_METHOD"));
        assertTrue(settings.contains("LocaleController.getString(R.string.Moments_Personalized)"));
        assertTrue(settings.contains("LocaleController.getString(R.string.Moments_PersonalizedInfo)"));
        assertTrue(settings.contains("TextCheckCell"));
        assertTrue(settings.contains("TextInfoPrivacyCell"));
        assertTrue(settings.contains("HeaderCell"));
        assertTrue(settings.contains("personalizationToggleVisible()"));
        assertTrue(read("ui/Feed/FeedComposerActivity.java").contains("FeedConfig.MAX_MENTIONS"));
        assertTrue(read("ui/Feed/FeedDetailActivity.java").contains("FeedConfig.MAX_VOICE_COMMENT_SEC"));
        assertTrue(read("ui/Feed/FeedVoiceCapture.java").contains("FeedConfig.MAX_VOICE_SEC"));
        assertFalse(privacy.contains("Moments_Personalized"));
        assertFalse(privacy.contains("PREFERENCES_PATH"));
        assertTrue(controller.contains("FeedLimits.parse"));
        assertTrue(controller.contains("FeedLimits.PREFERENCES_PATH"));
        assertTrue(controller.contains("recommendPath"));
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
        throw new IllegalStateException("missing " + relative);
    }
}
