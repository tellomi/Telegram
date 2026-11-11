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

    @Test
    public void nestedLimitsReplaceVoiceAndCommentAndIgnoreTheTopLevel() {
        String json = "{"
                + "\"media_base_url\":\"https://media.example/feed\","
                + "\"text_limit\":900,"
                + "\"voice_max_seconds\":180,"
                + "\"comment_text_limit\":1000,"
                + "\"limits\":{"
                + "\"text_limit\":420,"
                + "\"image_limit\":4,"
                + "\"video_limit\":2,"
                + "\"video_max_seconds\":45,"
                + "\"voice_max_seconds\":30,"
                + "\"comment_text_limit\":80,"
                + "\"page_size\":15"
                + "}}";
        FeedLimits.Values values = FeedLimits.parse(json);
        assertEquals(420, values.text);
        assertEquals(370, values.textCounterFrom);
        assertEquals(4, values.images);
        assertEquals(2, values.videos);
        assertEquals(45, values.videoSec);
        assertEquals(30, values.voiceSec);
        assertEquals(80, values.comment);
        assertEquals(15, values.pageSize);
        assertEquals(60, FeedLimits.FALLBACK_VOICE_SEC);
        assertEquals(500, FeedLimits.FALLBACK_COMMENT);
    }

    @Test
    public void omittedLimitKeysKeepTheEngineFallbacks() {
        FeedLimits.Values missing = FeedLimits.parse("{\"media_base_url\":\"https://media.example/feed\"}");
        assertEquals(FeedLimits.fallback().text, missing.text);
        assertEquals(FeedLimits.FALLBACK_VOICE_SEC, missing.voiceSec);
        assertEquals(FeedLimits.FALLBACK_COMMENT, missing.comment);
        assertEquals(60, missing.voiceSec);
        assertFalse(missing.voiceSec == 180);

        FeedLimits.Values partial = FeedLimits.parse("{\"limits\":{\"voice_max_seconds\":\"60\",\"comment_text_limit\":0,\"video_max_seconds\":60.5}}");
        assertEquals(FeedLimits.FALLBACK_VOICE_SEC, partial.voiceSec);
        assertEquals(FeedLimits.FALLBACK_COMMENT, partial.comment);
        assertEquals(FeedLimits.FALLBACK_VIDEO_SEC, partial.videoSec);

        FeedLimits.Values legacy = FeedLimits.parse("{\"limits\":{\"post_text_chars\":320}}");
        assertEquals(320, legacy.text);
        assertEquals(270, legacy.textCounterFrom);
        assertEquals(FeedLimits.FALLBACK_COMMENT, legacy.comment);

        FeedLimits.Values canonicalWins = FeedLimits.parse("{\"limits\":{\"text_limit\":100,\"post_text_chars\":320}}");
        assertEquals(100, canonicalWins.text);
    }

    @Test
    public void appliedLimitsChangeCommentAndImageCapsUntilReset() {
        FeedConfig.apply(FeedLimits.fallback());
        FeedConfig.apply(FeedLimits.parse("{\"limits\":{\"comment_text_limit\":4,\"image_limit\":3,\"text_limit\":6}}"));
        try {
            assertEquals(4, FeedConfig.MAX_COMMENT);
            assertEquals(3, FeedContract.imageSlotsLeft(0));
            assertEquals(0, FeedContract.imageSlotsLeft(3));
            String body = FeedContract.commentBody("abcdef", null, null, null);
            assertTrue(body.contains("\"text\":\"abcd\""));
            assertFalse(body.contains("abcdef"));
            assertEquals("abcdef", FeedContract.clampText("abcdefghi"));
        } finally {
            FeedConfig.apply(FeedLimits.fallback());
        }
        assertEquals(500, FeedConfig.MAX_COMMENT);
        assertEquals(10, FeedContract.imageSlotsLeft(0));
        assertEquals(60, FeedConfig.MAX_VOICE_SEC);
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
