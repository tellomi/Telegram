package org.telegram.messenger.feed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * GET /v1/feed/config. Durations and lengths live on the nested object
 * {@code limits}. The same names at the top of the document are not the
 * contract and are ignored. A missing key keeps the engine fallback.
 * {@code post_text_chars} is the older text key, used only when
 * {@code text_limit} is absent.
 */
public final class FeedLimits {

    public static final String PREFERENCES_PATH = "/v1/feed/me/preferences";
    public static final String PREFERENCES_METHOD = "PUT";
    public static final String PERSONALIZED = "personalized";

    /** Engine fallbacks. Voice is 60 seconds. The old client cap of 180 is not used. */
    public static final int FALLBACK_PAGE_SIZE = 20;
    public static final int FALLBACK_TEXT = FeedContract.MAX_TEXT;
    public static final int FALLBACK_TEXT_COUNTER_FROM = FeedContract.TEXT_COUNTER_FROM;
    public static final int FALLBACK_COMMENT = FeedContract.MAX_COMMENT;
    public static final int FALLBACK_IMAGES = FeedContract.MAX_IMAGES;
    public static final int FALLBACK_VIDEOS = 3;
    public static final int FALLBACK_VIDEO_SEC = 60;
    public static final int FALLBACK_VOICE_SEC = 60;

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
        public final int voiceSec;

        public Values(int pageSize, int text, int textCounterFrom, int comment, int images, int videos, int videoSec, int voiceSec) {
            this.pageSize = pageSize;
            this.text = text;
            this.textCounterFrom = textCounterFrom;
            this.comment = comment;
            this.images = images;
            this.videos = videos;
            this.videoSec = videoSec;
            this.voiceSec = voiceSec;
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
                FALLBACK_VOICE_SEC
        );
    }

    /** Positive whole numbers on {@code limits} replace the fallback. Anything else does not. */
    public static Values parse(String json) {
        Values base = fallback();
        JsonObject limits = limitsObject(json);
        if (limits == null) {
            return base;
        }
        int text = limit(limits, "text_limit", "post_text_chars", base.text);
        int gap = FALLBACK_TEXT - FALLBACK_TEXT_COUNTER_FROM;
        int counter = text > gap ? text - gap : 0;
        return new Values(
                limit(limits, "page_size", null, base.pageSize),
                text,
                counter,
                limit(limits, "comment_text_limit", null, base.comment),
                limit(limits, "image_limit", null, base.images),
                limit(limits, "video_limit", null, base.videos),
                limit(limits, "video_max_seconds", null, base.videoSec),
                limit(limits, "voice_max_seconds", null, base.voiceSec)
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

    private static JsonObject limitsObject(String json) {
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement limits = root.getAsJsonObject().get("limits");
            if (limits == null || !limits.isJsonObject()) {
                return null;
            }
            return limits.getAsJsonObject();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static int limit(JsonObject limits, String key, String legacyKey, int fallback) {
        if (limits.has(key)) {
            Integer value = number(limits, key);
            return value == null ? fallback : value;
        }
        if (legacyKey != null && limits.has(legacyKey)) {
            Integer value = number(limits, legacyKey);
            return value == null ? fallback : value;
        }
        return fallback;
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
