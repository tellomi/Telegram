package org.telegram.messenger.feed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * content-engine contract (docs/api.md). Feed lists are id references.
 * Cards, notification tabs, capabilities, and status live here so the
 * request shapes can be tested without the Android HTTP stack.
 */
public final class FeedContract {

    private FeedContract() {
    }

    /** Engine body limit. The client used to allow 2000. */
    public static final int MAX_TEXT = 500;
    /** Counter appears near the end of the engine limit. */
    public static final int TEXT_COUNTER_FROM = 450;
    /** Engine comment limit. */
    public static final int MAX_COMMENT = 500;
    /** Engine image limit. The client used to allow 12. */
    public static final int MAX_IMAGES = 10;

    public static final String STREAM_RECOMMEND = "recommend";
    public static final String STREAM_FOLLOWING = "following";
    public static final String STREAM_NEARBY = "nearby";

    public static final String CARDS_PATH = "/v1/feed/cards";
    public static final String STATUS_PATH = "/v1/feed/status";
    public static final String CAPABILITIES_PATH = "/v1/capabilities";
    /** Passed to the load callback when a refresh returned no rows and the list was kept. */
    public static final String KEPT_EXISTING = "kept";

    public static final String NOTIFICATION_INTERACTIONS = "interactions";
    public static final String NOTIFICATION_FOLLOWS = "follows";
    public static final String NOTIFICATION_SYSTEM = "system";

    public static final class Ref {
        public final String id;
        public final String reason;
        /** Set when the list already returned a full post. Null when the row must be hydrated. */
        public final String fullJson;

        public Ref(String id, String reason, String fullJson) {
            this.id = id == null ? "" : id;
            this.reason = reason == null ? "" : reason;
            this.fullJson = fullJson;
        }
    }

    public static final class FeedList {
        public final ArrayList<Ref> refs = new ArrayList<>();
        public String cursor = "";
        public boolean needsCards;

        public ArrayList<String> idsToHydrate() {
            ArrayList<String> ids = new ArrayList<>();
            for (int i = 0; i < refs.size(); i++) {
                Ref ref = refs.get(i);
                if (ref.fullJson == null && ref.id.length() > 0 && !ids.contains(ref.id)) {
                    ids.add(ref.id);
                }
            }
            return ids;
        }
    }

    /**
     * Result of merging one feed page into the rows already on screen.
     * {@code keptExisting} means a refresh came back empty and the visible
     * rows were left alone. {@code stopPaging} means another load-more would
     * only repeat ids or the cursor is missing.
     */
    public static final class PageApply {
        public final ArrayList<String> ids;
        public final int added;
        public final boolean keptExisting;
        public final boolean stopPaging;
        public final String nextCursor;

        public PageApply(ArrayList<String> ids, int added, boolean keptExisting, boolean stopPaging, String nextCursor) {
            this.ids = ids;
            this.added = added;
            this.keptExisting = keptExisting;
            this.stopPaging = stopPaging;
            this.nextCursor = nextCursor == null ? "" : nextCursor;
        }
    }

    /** View tag for a control that must not open the post. */
    public static final String ACTION_TAG = "feed-action";

    /** A rectangle in the post cell. A tap inside an action zone does not open the post. */
    public static final class Zone {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Zone(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public boolean contains(float x, float y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    public static ArrayList<String> defaultTabs() {
        ArrayList<String> tabs = new ArrayList<>();
        tabs.add(STREAM_RECOMMEND);
        tabs.add(STREAM_FOLLOWING);
        tabs.add(STREAM_NEARBY);
        return tabs;
    }

    public static boolean isEngineStream(String stream) {
        return STREAM_RECOMMEND.equals(stream) || STREAM_FOLLOWING.equals(stream) || STREAM_NEARBY.equals(stream);
    }

    /** Null when the stream is not a real engine route (friends, places, new-count). */
    public static String streamPath(String stream, String cursor, String city, String sort) {
        if (!isEngineStream(stream)) {
            return null;
        }
        String path = "/v1/feed/" + stream + "?cursor=" + enc(normalizeCursor(cursor));
        if (STREAM_NEARBY.equals(stream)) {
            String nearbySort = sort == null || sort.length() == 0 ? "latest" : sort;
            path += "&city=" + enc(city) + "&sort=" + enc(nearbySort);
        }
        return path;
    }

    /**
     * Load-more URL. Null when there is no cursor.
     * {@code /v1/feed/recommend?cursor=} is the first page. Sending it again
     * used to repeat the same ids.
     */
    public static String nextPagePath(String stream, String cursor, String city, String sort) {
        if (normalizeCursor(cursor).length() == 0) {
            return null;
        }
        return streamPath(stream, cursor, city, sort);
    }

    /** GET /v1/feed/posts/{id}. Null when the id is missing. */
    public static String postPath(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        return "/v1/feed/posts/" + enc(id);
    }

    /** 204/205 have no JSON body. They are success, including notifications/read. */
    public static boolean noBodySuccess(int code) {
        return code == 204 || code == 205;
    }

    /**
     * Top tabs come from capabilities `tabs` (or config.tabs). Unknown ids,
     * including friends, are dropped because those routes do not exist.
     * A missing field falls back to recommend / following / nearby.
     */
    public static ArrayList<String> tabsFromCapabilities(String json) {
        JsonObject root = object(json);
        JsonArray raw = null;
        boolean present = false;
        if (root != null) {
            if (root.has("tabs")) {
                present = true;
                raw = array(root.get("tabs"));
            } else if (root.has("config") && root.get("config").isJsonObject()) {
                JsonObject config = root.getAsJsonObject("config");
                if (config.has("tabs")) {
                    present = true;
                    raw = array(config.get("tabs"));
                }
            } else if (root.has("feed") && root.get("feed").isJsonObject()) {
                JsonObject feed = root.getAsJsonObject("feed");
                if (feed.has("tabs")) {
                    present = true;
                    raw = array(feed.get("tabs"));
                }
            }
        }
        if (!present) {
            return defaultTabs();
        }
        ArrayList<String> tabs = new ArrayList<>();
        if (raw != null) {
            for (int i = 0; i < raw.size(); i++) {
                JsonElement element = raw.get(i);
                if (element == null || !element.isJsonPrimitive()) {
                    continue;
                }
                String id = element.getAsString();
                if (isEngineStream(id) && !tabs.contains(id)) {
                    tabs.add(id);
                }
            }
        }
        return tabs;
    }

    public static String joinTabs(List<String> tabs) {
        StringBuilder builder = new StringBuilder();
        if (tabs == null) {
            return "";
        }
        for (int i = 0; i < tabs.size(); i++) {
            if (!isEngineStream(tabs.get(i))) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(tabs.get(i));
        }
        return builder.toString();
    }

    public static ArrayList<String> parseTabPref(String raw) {
        ArrayList<String> tabs = new ArrayList<>();
        if (raw == null || raw.length() == 0) {
            return tabs;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < parts.length; i++) {
            String id = parts[i].trim();
            if (isEngineStream(id) && !tabs.contains(id)) {
                tabs.add(id);
            }
        }
        return tabs;
    }

    /** The 动态 tab is shown only when both gates pass. */
    /** The Moments tab is part of the client. App-config and engine status do not hide it. */
    public static boolean showMomentsTab(boolean configKeyPresent, boolean statusVisible) {
        return true;
    }

    /**
     * ImageLoader reads a "W_H" filter as dp and multiplies by density.
     * "800_800" on a 3x phone asks for a 2400 px bitmap and then decodes a
     * 12 MP photo at sample size 1. The filter is the cell size in dp.
     */
    public static String imageFilter(int cellWidthPx, int cellHeightPx, float density) {
        float scale = density <= 0f ? 1f : density;
        int width = Math.max(1, Math.min(360, Math.round(cellWidthPx / scale)));
        int height = Math.max(1, Math.min(360, Math.round(cellHeightPx / scale)));
        return width + "_" + height;
    }

    /** Same sample-size rule as ImageLoader.CacheOutTask for a JPEG. */
    public static int imageSampleSize(int photoW, int photoH, String filter, float density) {
        String[] args = filter == null ? new String[0] : filter.split("_");
        if (args.length < 2 || photoW <= 0 || photoH <= 0) {
            return 1;
        }
        float scale = density <= 0f ? 1f : density;
        float wFilter = Float.parseFloat(args[0]) * scale;
        float hFilter = Float.parseFloat(args[1]) * scale;
        float factor;
        if (wFilter >= hFilter && photoW > photoH) {
            factor = Math.max(photoW / wFilter, photoH / hFilter);
        } else {
            factor = Math.min(photoW / wFilter, photoH / hFilter);
        }
        if (factor < 1.2f) {
            factor = 1f;
        }
        if (factor > 1f && (photoW > wFilter || photoH > hFilter)) {
            int sample = 1;
            while (sample * 2 < factor) {
                sample *= 2;
            }
            return sample;
        }
        return Math.max(1, (int) factor);
    }

    public static boolean statusVisible(String json) {
        JsonObject root = object(json);
        if (root == null) {
            return false;
        }
        if (root.has("visible")) {
            return bool(root.get("visible"));
        }
        if (root.has("status") && root.get("status").isJsonObject()) {
            return bool(root.getAsJsonObject("status").get("visible"));
        }
        return false;
    }

    private static boolean bool(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt() != 0;
            }
            return "true".equalsIgnoreCase(element.getAsString());
        } catch (Exception ignore) {
            return false;
        }
    }

    public static FeedList parseFeedList(String json) {
        FeedList list = new FeedList();
        JsonObject root = object(json);
        if (root == null) {
            return list;
        }
        list.cursor = normalizeCursor(string(root, "cursor"));
        if (list.cursor.length() == 0) {
            list.cursor = normalizeCursor(string(root, "next_cursor"));
        }
        JsonArray items = array(root.get("items"));
        if (items == null) {
            items = array(root.get("cards"));
        }
        if (items == null) {
            items = array(root.get("posts"));
        }
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.size(); i++) {
            JsonElement element = items.get(i);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            if (isPostRef(item)) {
                list.needsCards = true;
                list.refs.add(new Ref(string(item, "id"), string(item, "reason"), null));
            } else if (item.has("id") || item.has("text") || item.has("author")) {
                list.refs.add(new Ref(string(item, "id"), string(item, "reason"), item.toString()));
            }
        }
        return list;
    }

    public static String cardsRequest(List<String> ids) {
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                if (id != null && id.length() > 0) {
                    array.add(id);
                }
            }
        }
        body.add("ids", array);
        return body.toString();
    }

    /**
     * Cards response aligned to the feed order. A missing id becomes
     * `{id, state:unavailable}` so the row is not a blank post.
     */
    public static ArrayList<String> orderedCardJson(String cardsJson, List<Ref> refs) {
        ArrayList<String> ordered = new ArrayList<>();
        if (refs == null) {
            return ordered;
        }
        java.util.HashMap<String, String> byId = new java.util.HashMap<>();
        JsonObject root = object(cardsJson);
        JsonArray cards = null;
        if (root != null) {
            cards = array(root.get("cards"));
            if (cards == null) {
                cards = array(root.get("items"));
            }
            if (cards == null) {
                cards = array(root.get("posts"));
            }
        }
        if (cards != null) {
            for (int i = 0; i < cards.size(); i++) {
                JsonElement element = cards.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject raw = element.getAsJsonObject();
                JsonObject body = unwrapCard(raw);
                String id = string(body, "id");
                if (id.length() == 0) {
                    id = string(raw, "id");
                }
                if (id.length() == 0) {
                    continue;
                }
                if (!body.has("id")) {
                    body.addProperty("id", id);
                }
                byId.put(id, body.toString());
            }
        }
        for (int i = 0; i < refs.size(); i++) {
            Ref ref = refs.get(i);
            if (ref.fullJson != null) {
                ordered.add(ref.fullJson);
                continue;
            }
            String card = byId.get(ref.id);
            if (card == null) {
                JsonObject missing = new JsonObject();
                missing.addProperty("id", ref.id);
                missing.addProperty("state", "unavailable");
                ordered.add(missing.toString());
            } else {
                ordered.add(card);
            }
        }
        return ordered;
    }

    public static String notificationsPath(String tab) {
        return "/v1/feed/me/notifications?tab=" + enc(notificationTab(tab));
    }

    public static String notificationTab(String tab) {
        if (NOTIFICATION_FOLLOWS.equals(tab) || NOTIFICATION_SYSTEM.equals(tab) || NOTIFICATION_INTERACTIONS.equals(tab)) {
            return tab;
        }
        return NOTIFICATION_INTERACTIONS;
    }

    public static boolean isVoice(String type) {
        return "audio".equals(type) || "voice".equals(type);
    }

    /** Comment voice is `media_keys`, a list of upload keys. Other attachments stay on `attachment`. */
    public static String commentBody(String text, String parentId, String attachmentType, String attachmentKey) {
        JsonObject body = new JsonObject();
        String value = text == null ? "" : text;
        if (value.length() > MAX_COMMENT) {
            value = value.substring(0, MAX_COMMENT);
        }
        body.addProperty("text", value);
        if (parentId != null && parentId.length() > 0) {
            body.addProperty("parent_id", parentId);
        }
        boolean sticker = "sticker".equals(attachmentType);
        boolean upload = isVoice(attachmentType) || "image".equals(attachmentType) || "photo".equals(attachmentType) || sticker;
        if (upload && attachmentKey != null && attachmentKey.length() > 0) {
            JsonArray keys = new JsonArray();
            keys.add(attachmentKey);
            body.add("media_keys", keys);
        }
        if (sticker) {
            body.addProperty("sticker", true);
        }
        return body.toString();
    }

    /** Hot thread. An empty cursor is omitted. Hot cursors are not time cursors. */
    public static String commentsPath(String postId, String cursor) {
        String path = "/v1/feed/posts/" + enc(postId) + "/comments?sort=hot&thread=1";
        if (cursor != null && cursor.length() > 0 && !"null".equals(cursor)) {
            path += "&cursor=" + enc(cursor);
        }
        return path;
    }

    public static String repliesPath(String commentId, String cursor) {
        String path = "/v1/feed/comments/" + enc(commentId) + "/replies";
        if (cursor != null && cursor.length() > 0 && !"null".equals(cursor)) {
            path += "?cursor=" + enc(cursor);
        }
        return path;
    }

    public static String commentLikePath(String commentId) {
        return "/v1/feed/comments/" + enc(commentId) + "/like";
    }

    public static String commentDislikePath(String commentId) {
        return "/v1/feed/comments/" + enc(commentId) + "/dislike";
    }

    public static String clampText(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TEXT) {
            return text;
        }
        return text.substring(0, MAX_TEXT);
    }

    public static int imageSlotsLeft(int already) {
        if (already >= MAX_IMAGES) {
            return 0;
        }
        return MAX_IMAGES - Math.max(0, already);
    }

    /** JSON null, a blank cursor, and the literal "null" all mean "no next page". */
    public static String normalizeCursor(String cursor) {
        if (cursor == null) {
            return "";
        }
        String value = cursor.trim();
        if (value.length() == 0 || "null".equalsIgnoreCase(value) || "undefined".equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    public static ArrayList<String> uniqueIds(List<String> ids) {
        ArrayList<String> out = new ArrayList<>();
        if (ids == null) {
            return out;
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.length() == 0 || out.contains(id)) {
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /**
     * Merge a page into the ids already shown. Load-more that adds nothing
     * stops, even when the server repeats a cursor or sends null. A refresh
     * with no rows keeps the ids that are already on screen.
     */
    public static PageApply applyPage(List<String> existingIds, List<String> incomingIds, boolean refresh, String nextCursor) {
        ArrayList<String> existing = uniqueIds(existingIds);
        ArrayList<String> incoming = uniqueIds(incomingIds);
        String cursor = normalizeCursor(nextCursor);
        if (refresh && incoming.isEmpty()) {
            boolean kept = !existing.isEmpty();
            return new PageApply(existing, 0, kept, !kept, kept ? cursor : "");
        }
        if (!refresh && incoming.isEmpty()) {
            return new PageApply(existing, 0, false, true, "");
        }
        ArrayList<String> result = new ArrayList<>();
        int added;
        if (refresh) {
            result.addAll(incoming);
            added = incoming.size();
        } else {
            result.addAll(existing);
            added = 0;
            for (int i = 0; i < incoming.size(); i++) {
                String id = incoming.get(i);
                if (!result.contains(id)) {
                    result.add(id);
                    added++;
                }
            }
        }
        boolean stop = cursor.length() == 0 || (!refresh && added == 0);
        if (!refresh && added == 0) {
            cursor = "";
        }
        return new PageApply(result, added, false, stop, cursor);
    }

    /**
     * Detail mode never opens another detail. A tap on a like/comment/media
     * zone toggles that control. Everywhere else in the row opens the post.
     */
    public static boolean tapOpensDetail(boolean detailCell, List<Zone> actionZones, float x, float y) {
        if (detailCell) {
            return false;
        }
        if (actionZones != null) {
            for (int i = 0; i < actionZones.size(); i++) {
                Zone zone = actionZones.get(i);
                if (zone != null && zone.contains(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Article HTML and story markup are shown as text, not raw tags. */
    public static String plainText(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        if (text.indexOf('<') < 0 && text.indexOf('&') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean tag = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                tag = true;
                continue;
            }
            if (c == '>' && tag) {
                tag = false;
                if (out.length() > 0 && !Character.isWhitespace(out.charAt(out.length() - 1))) {
                    out.append(' ');
                }
                continue;
            }
            if (!tag) {
                out.append(c);
            }
        }
        return out.toString()
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    /** Unix seconds and unix millis both become millis. */
    public static long epochMillis(long value) {
        if (value > 0 && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return Math.max(0, value);
    }

    private static boolean isPostRef(JsonObject item) {
        if (item == null || string(item, "id").length() == 0) {
            return false;
        }
        if (item.has("type")) {
            String type = string(item, "type");
            if (type.length() > 0 && !"post".equals(type)) {
                return false;
            }
        }
        for (String key : item.keySet()) {
            if (!"id".equals(key) && !"reason".equals(key) && !"type".equals(key)) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject unwrapCard(JsonObject raw) {
        if (raw.has("card") && raw.get("card").isJsonObject()) {
            return raw.getAsJsonObject("card");
        }
        if (raw.has("post") && raw.get("post").isJsonObject() && !raw.has("author") && !raw.has("text")) {
            return raw.getAsJsonObject("post");
        }
        return raw;
    }

    private static JsonObject object(String json) {
        if (json == null || json.length() == 0) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static JsonArray array(JsonElement element) {
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray();
        }
        return null;
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return "";
        }
        return element.getAsString();
    }

    public static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
