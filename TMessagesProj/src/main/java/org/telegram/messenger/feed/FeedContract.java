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
                if (ref.fullJson == null && ref.id.length() > 0) {
                    ids.add(ref.id);
                }
            }
            return ids;
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
        String path = "/v1/feed/" + stream + "?cursor=" + enc(cursor);
        if (STREAM_NEARBY.equals(stream)) {
            String nearbySort = sort == null || sort.length() == 0 ? "latest" : sort;
            path += "&city=" + enc(city) + "&sort=" + enc(nearbySort);
        }
        return path;
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
    public static boolean showMomentsTab(boolean configKeyPresent, boolean statusVisible) {
        return configKeyPresent && statusVisible;
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
        list.cursor = string(root, "cursor");
        if (list.cursor.length() == 0) {
            list.cursor = string(root, "next_cursor");
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
        if (isVoice(attachmentType) && attachmentKey != null && attachmentKey.length() > 0) {
            JsonArray keys = new JsonArray();
            keys.add(attachmentKey);
            body.add("media_keys", keys);
        } else if (attachmentKey != null && attachmentKey.length() > 0) {
            JsonObject attachment = new JsonObject();
            attachment.addProperty("type", attachmentType == null ? "" : attachmentType);
            attachment.addProperty("key", attachmentKey);
            body.add("attachment", attachment);
        }
        return body.toString();
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
