package org.telegram.messenger.feed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * GET /v1/feed/config. {@code limits} is a map of ints. The keys are the
 * ones content-engine writes in server.go. A missing key, or a value that
 * is not a positive whole number, keeps the fallback. Names that are not
 * in that map are ignored.
 */
public final class FeedLimits {

    public static final String PREFERENCES_PATH = "/v1/feed/me/preferences";
    public static final String PREFERENCES_METHOD = "PUT";
    public static final String PERSONALIZED = "personalized";

    /**
     * Same numbers as content-engine when a key is omitted, and the same
     * defaults as iOS. Voice is media/audio.go MaxAudioSeconds.
     */
    public static final int FALLBACK_PAGE_SIZE = 20;
    public static final int FALLBACK_TEXT = 2000;
    public static final int FALLBACK_TEXT_COUNTER_FROM = 1950;
    public static final int FALLBACK_COMMENT = 1000;
    public static final int FALLBACK_IMAGES = 12;
    public static final int FALLBACK_VIDEOS = 3;
    public static final int FALLBACK_VIDEO_SEC = 60;
    public static final int FALLBACK_VOICE_POST_SEC = 180;
    public static final int FALLBACK_VOICE_COMMENT_SEC = 180;
    public static final int FALLBACK_MENTIONS = 10;
    public static final int FALLBACK_TOPICS = 3;

    private FeedLimits() {
    }

    public static final class Values {
        public final int pageSize;
        public final int text;
        public final int textCounterFrom;
        public final int comment;
        public final int images;
        public final int videos;
        public final int videoSec;
        public final int voicePostSec;
        public final int voiceCommentSec;
        public final int mentions;
        public final int topics;
        /** Top-level bool. Omitted or non-boolean hides the settings switch. */
        public final boolean personalizationToggleVisible;

        public Values(int pageSize, int text, int textCounterFrom, int comment, int images, int videos, int videoSec, int voicePostSec, int voiceCommentSec, int mentions, int topics, boolean personalizationToggleVisible) {
            this.pageSize = pageSize;
            this.text = text;
            this.textCounterFrom = textCounterFrom;
            this.comment = comment;
            this.images = images;
            this.videos = videos;
            this.videoSec = videoSec;
            this.voicePostSec = voicePostSec;
            this.voiceCommentSec = voiceCommentSec;
            this.mentions = mentions;
            this.topics = topics;
            this.personalizationToggleVisible = personalizationToggleVisible;
        }
    }

    public static Values fallback() {
        return new Values(
                FALLBACK_PAGE_SIZE,
                FALLBACK_TEXT,
                FALLBACK_TEXT_COUNTER_FROM,
                FALLBACK_COMMENT,
                FALLBACK_IMAGES,
                FALLBACK_VIDEOS,
                FALLBACK_VIDEO_SEC,
                FALLBACK_VOICE_POST_SEC,
                FALLBACK_VOICE_COMMENT_SEC,
                FALLBACK_MENTIONS,
                FALLBACK_TOPICS,
                false
        );
    }

    /** Positive whole numbers on {@code limits} replace the fallback. Anything else does not. */
    public static Values parse(String json) {
        Values base = fallback();
        JsonObject root = object(json);
        if (root == null) {
            return base;
        }
        boolean toggle = bool(root, "personalization_toggle_visible");
        JsonElement limitsElement = root.get("limits");
        if (limitsElement == null || !limitsElement.isJsonObject()) {
            return copy(base, toggle);
        }
        JsonObject limits = limitsElement.getAsJsonObject();
        int text = limit(limits, "post_text_chars", base.text);
        int gap = FALLBACK_TEXT - FALLBACK_TEXT_COUNTER_FROM;
        int counter = text > gap ? text - gap : 0;
        return new Values(
                limit(limits, "page_size", base.pageSize),
                text,
                counter,
                limit(limits, "comment_text_chars", base.comment),
                limit(limits, "post_images", base.images),
                limit(limits, "post_videos", base.videos),
                limit(limits, "video_max_seconds", base.videoSec),
                limit(limits, "voice_post_max_seconds", base.voicePostSec),
                limit(limits, "voice_comment_max_seconds", base.voiceCommentSec),
                limit(limits, "max_mentions", base.mentions),
                limit(limits, "max_topics", base.topics),
                toggle
        );
    }

    private static Values copy(Values base, boolean personalizationToggleVisible) {
        return new Values(
                base.pageSize,
                base.text,
                base.textCounterFrom,
                base.comment,
                base.images,
                base.videos,
                base.videoSec,
                base.voicePostSec,
                base.voiceCommentSec,
                base.mentions,
                base.topics,
                personalizationToggleVisible
        );
    }

    /**
     * PUT body. {@code personalized} is a JSON boolean. Other fields already
     * on the document are kept so a toggle does not wipe keys this client
     * does not render.
     */
    public static String preferenceRequest(boolean personalized) {
        return preferenceRequest(null, personalized);
    }

    public static String preferenceRequest(String currentJson, boolean personalized) {
        JsonObject body = new JsonObject();
        if (currentJson != null && currentJson.length() > 0) {
            try {
                JsonElement element = JsonParser.parseString(currentJson);
                if (element.isJsonObject()) {
                    body = element.getAsJsonObject();
                }
            } catch (Exception ignore) {
            }
        }
        body.add(PERSONALIZED, new JsonPrimitive(personalized));
        return body.toString();
    }

    /** Null when the server omits the key or sends a non-boolean. */
    public static Boolean parsePersonalized(String json) {
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement element = root.getAsJsonObject().get(PERSONALIZED);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return null;
            }
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (!primitive.isBoolean()) {
                return null;
            }
            return primitive.getAsBoolean();
        } catch (Exception ignore) {
            return null;
        }
    }

    /** Off adds {@code personalized=false}. On leaves the recommend URL unchanged. */
    public static String recommendPath(String path, boolean personalized) {
        if (path == null || personalized) {
            return path;
        }
        return path + "&personalized=false";
    }

    private static JsonObject object(String json) {
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            return root.getAsJsonObject();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return false;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        return element.getAsBoolean();
    }

    private static int limit(JsonObject limits, String key, int fallback) {
        if (!limits.has(key)) {
            return fallback;
        }
        Integer value = number(limits, key);
        return value == null ? fallback : value;
    }

    private static Integer number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (!primitive.isNumber()) {
            return null;
        }
        double value = primitive.getAsDouble();
        if (value < 1d || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            return null;
        }
        return (int) value;
    }
}
