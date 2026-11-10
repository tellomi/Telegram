package org.telegram.messenger.feed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

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
    public static final String STREAM_FRIENDS = "friends";
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
        tabs.add(STREAM_FRIENDS);
        tabs.add(STREAM_NEARBY);
        return tabs;
    }

    public static boolean isEngineStream(String stream) {
        return STREAM_RECOMMEND.equals(stream) || STREAM_FOLLOWING.equals(stream) || STREAM_FRIENDS.equals(stream) || STREAM_NEARBY.equals(stream);
    }

    /** The repost line is only for reason=repost. topic and source are not shown. */
    public static boolean showRepostHeader(String reason) {
        return reason == null || reason.length() == 0 || "repost".equals(reason);
    }

    public static boolean isMutualAudience(String audience) {
        return "mutual_followers".equals(audience);
    }

    /** state=unavailable, deleted, or the boolean flag. Non-mutual cards use this. */
    public static boolean cardUnavailable(boolean flag, String state) {
        return flag || "unavailable".equals(state) || "deleted".equals(state);
    }

    /** Null when the stream is not a real engine route (places, new-count). */
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
     * Home segments are always recommend, following, friends, nearby.
     * capabilities.friends_feed and a missing or partial tab list do not hide Friends.
     * Unknown ids such as places are ignored.
     */
    public static ArrayList<String> tabsFromCapabilities(String json) {
        return defaultTabs();
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

    /** The Moments tab is part of the client. App-config and engine status do not hide it. */
    public static boolean showMomentsTab(boolean configKeyPresent, boolean statusVisible) {
        return true;
    }

    public static final int LOAD_MORE_REMAINING = 10;
    public static final int PREFETCH_AHEAD_POSTS = 3;
    public static final int PREFETCH_BEHIND_POSTS = 1;
    public static final int PREFETCH_IMAGES_PER_POST = 2;
    public static final int PREFETCH_MAX_INFLIGHT = 2;
    public static final float FLING_PAUSE_SPEED = 4f;
    public static final String AVATAR_FILTER = "40_40";

    public static final class PrefetchPost {
        public String avatar = "";
        public final ArrayList<String> images = new ArrayList<String>();
        public String linkImage = "";
        public String imageFilter = "";
    }

    public static final class PrefetchHit {
        public final String url;
        public final String filter;
        public final String key;

        public PrefetchHit(String url, String filter) {
            this.url = url == null ? "" : url;
            this.filter = filter == null ? "" : filter;
            this.key = this.url + "\n" + this.filter;
        }
    }

    public static ArrayList<PrefetchHit> prefetchPlan(List<PrefetchPost> posts, int firstVisible, int lastVisible, int direction, boolean allowed, boolean lowTier) {
        ArrayList<PrefetchHit> hits = new ArrayList<PrefetchHit>();
        if (!allowed || posts == null || posts.isEmpty() || lastVisible < 0 || firstVisible < 0) {
            return hits;
        }
        int ahead = lowTier ? 1 : PREFETCH_AHEAD_POSTS;
        int perPost = lowTier ? 1 : PREFETCH_IMAGES_PER_POST;
        int size = posts.size();
        ArrayList<Integer> indexes = new ArrayList<Integer>();
        if (direction >= 0) {
            for (int step = 1; step <= ahead; step++) {
                int index = lastVisible + step;
                if (index >= 0 && index < size) {
                    indexes.add(index);
                }
            }
            int behind = firstVisible - 1;
            if (behind >= 0 && behind < size) {
                indexes.add(behind);
            }
        } else {
            for (int step = 1; step <= ahead; step++) {
                int index = firstVisible - step;
                if (index >= 0 && index < size) {
                    indexes.add(index);
                }
            }
            int behind = lastVisible + 1;
            if (behind >= 0 && behind < size) {
                indexes.add(behind);
            }
        }
        for (int i = 0; i < indexes.size(); i++) {
            PrefetchPost post = posts.get(indexes.get(i));
            if (post == null) {
                continue;
            }
            if (post.avatar != null && post.avatar.length() > 0) {
                hits.add(new PrefetchHit(post.avatar, AVATAR_FILTER));
            }
            int images = Math.min(perPost, post.images.size());
            for (int image = 0; image < images; image++) {
                String url = post.images.get(image);
                if (url != null && url.length() > 0) {
                    hits.add(new PrefetchHit(url, post.imageFilter));
                }
            }
            if (post.linkImage != null && post.linkImage.length() > 0) {
                hits.add(new PrefetchHit(post.linkImage, post.imageFilter));
            }
        }
        return hits;
    }

    public static boolean shouldLoadMore(int remaining, boolean hasCursor, boolean loading) {
        return hasCursor && !loading && remaining <= LOAD_MORE_REMAINING;
    }

    public static int[] mediaCellPx(int count, int screenWidthPx, float density) {
        float scale = density <= 0f ? 1f : density;
        int column = Math.max(Math.round(200f * scale), screenWidthPx - Math.round(72f * scale));
        if (count <= 1) {
            int height = Math.min(Math.round(360f * scale), Math.max(Math.round(120f * scale), column));
            return new int[]{column, height};
        }
        int width = (int) (column * 0.78f);
        return new int[]{width, width};
    }

    /**
     * ImageLoader reads "W_H" as dp and multiplies by density. 800_800 on a
     * 3x phone asks for 2400 px and leaves a 12 MP photo at sample size 1.
     */
    /** Single large image uses the ~1080 tier. Carousel cells use the ~360 tier. */
    public static int renditionTargetPx(int visualCount) {
        return visualCount <= 1 ? 1080 : 360;
    }

    /**
     * One JPEG tier. Renditions are largest-first and never larger than the original.
     * A direct http(s) url wins. Otherwise the path is media_base_url + key.
     */
    public static final class Tier {
        public final String key;
        public final String url;
        public final int w;
        public final int h;

        public Tier(String key, String url, int w, int h) {
            this.key = key == null ? "" : key;
            this.url = url == null ? "" : url;
            this.w = w;
            this.h = h;
        }

        public int longEdge() {
            return Math.max(w, h);
        }
    }

    /** Index of the smallest tier whose long edge is at least targetPx, or -1 to use the original. */
    public static int chooseRendition(List<Tier> tiers, int targetPx) {
        int best = -1;
        int bestEdge = Integer.MAX_VALUE;
        if (tiers == null || targetPx <= 0) {
            return -1;
        }
        for (int i = 0; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            if (tier == null) {
                continue;
            }
            int edge = tier.longEdge();
            if (edge >= targetPx && edge < bestEdge) {
                best = i;
                bestEdge = edge;
            }
        }
        return best;
    }

    public static String resolveMediaUrl(String key, String url, String base) {
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            return url;
        }
        String path = key != null && key.length() > 0 ? key : (url == null ? "" : url);
        if (path.startsWith("https://") || path.startsWith("http://")) {
            return path;
        }
        if (path.length() == 0 || base == null || base.length() == 0) {
            return "";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String root = base;
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root + "/" + path;
    }

    public static ArrayList<Tier> parseRenditions(String mediaJson) {
        ArrayList<Tier> tiers = new ArrayList<>();
        JsonObject root = object(mediaJson);
        if (root == null || !root.has("renditions") || !root.get("renditions").isJsonArray()) {
            return tiers;
        }
        JsonArray renditions = root.getAsJsonArray("renditions");
        for (int i = 0; i < renditions.size(); i++) {
            if (renditions.get(i) == null || !renditions.get(i).isJsonObject()) {
                continue;
            }
            tiers.add(tier(renditions.get(i).getAsJsonObject()));
        }
        return tiers;
    }

    /** poster, or a cover object. Null when neither is an object. */
    public static Tier parsePoster(String mediaJson) {
        JsonObject root = object(mediaJson);
        if (root == null) {
            return null;
        }
        if (root.has("poster") && root.get("poster").isJsonObject()) {
            return tier(root.getAsJsonObject("poster"));
        }
        if (root.has("cover") && root.get("cover").isJsonObject()) {
            return tier(root.getAsJsonObject("cover"));
        }
        return null;
    }

    private static Tier tier(JsonObject object) {
        return new Tier(string(object, "key"), string(object, "url"), number(object, "w"), number(object, "h"));
    }

    private static int number(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonPrimitive()) {
            return 0;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignore) {
            return 0;
        }
    }

    public static String imageFilter(int cellWidthPx, int cellHeightPx, float density) {
        float scale = density <= 0f ? 1f : density;
        int width = Math.max(1, Math.min(360, Math.round(cellWidthPx / scale)));
        int height = Math.max(1, Math.min(360, Math.round(cellHeightPx / scale)));
        return width + "_" + height;
    }

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

    /**
     * ARGB_8888 bytes after ImageLoader's power-of-two sample. A 4032×3024
     * photo at sample size 1 is about 48 MB. Several of those while flinging
     * are enough for the system to kill the process with no Java exception,
     * which is what "scrolling returns to the launcher" looks like.
     */
    public static long decodedArgbBytes(int photoW, int photoH, int sample) {
        int step = Math.max(1, sample);
        long width = Math.max(1, photoW / step);
        long height = Math.max(1, photoH / step);
        return width * height * 4L;
    }

    /**
     * Same values as {@code ConnectionsManager}: connecting 1, waiting 2,
     * proxy 4, updating 5. Null means the Moments title stays.
     */
    public static String connectionTitleKey(int state) {
        if (state == 1) {
            return "Connecting";
        }
        if (state == 2) {
            return "WaitingForNetwork";
        }
        if (state == 4) {
            return "ConnectingToProxyWithDots";
        }
        if (state == 5) {
            return "Updating";
        }
        return null;
    }

    /**
     * One counter per feed stream. Starting a load invalidates the previous
     * token so a late response cannot update that stream.
     */
    public static final class LoadEpoch {
        private int current;

        public int begin() {
            current++;
            return current;
        }

        public boolean isCurrent(int token) {
            return token == current && token != 0;
        }
    }

    /** Nearby toggles between time order and the mixed recommendation order. */
    public static String nextNearbySort(String current) {
        return "mixed".equals(current) ? "latest" : "mixed";
    }

    /** True when a freshly fetched first row is not the row already on top. An empty list is not "new posts". */
    public static boolean hasNewerHead(String currentTopId, String incomingHeadId) {
        if (incomingHeadId == null || incomingHeadId.length() == 0) {
            return false;
        }
        if (currentTopId == null || currentTopId.length() == 0) {
            return false;
        }
        return !currentTopId.equals(incomingHeadId);
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
        return commentBody(text, parentId, attachmentType, attachmentKey, null);
    }

    /** Same body, with a client_nonce the caller keeps until the send succeeds. */
    public static String commentBody(String text, String parentId, String attachmentType, String attachmentKey, String clientNonce) {
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
        if (isClientNonce(clientNonce)) {
            body.addProperty("client_nonce", clientNonce);
        }
        return body.toString();
    }

    /** Hot thread. An empty cursor is omitted. Hot cursors are not time cursors. */
    public static String commentsPath(String postId, String cursor) {
        return commentsPath(postId, cursor, "hot");
    }

    /** {@code latest} is the time order. Anything else stays on the hot thread. */
    public static String commentsPath(String postId, String cursor, String sort) {
        String which = "latest".equals(sort) ? "latest" : "hot";
        String path = "/v1/feed/posts/" + enc(postId) + "/comments?sort=" + which + "&thread=1";
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

    public static final String VIS_PUBLIC = "public";
    public static final String VIS_FRIENDS = "friends";
    public static final String VIS_CONTACTS = "contacts";
    public static final String VIS_CLOSE_FRIENDS = "close_friends";
    public static final String VIS_INCLUDE = "include";
    public static final String VIS_EXCLUDE = "exclude";
    public static final String VIS_ONLY_ME = "only_me";

    /** List, topic, notification, and comment taps. */
    public static final String ENTRY_BODY = "body";
    public static final String ENTRY_COMMENT = "comment";
    public static final String ENTRY_TOPIC = "topic";
    public static final String ENTRY_NOTIFICATION = "notification";
    /** https://tell.cc/m and tellomi://tell.cc/m stay on the standalone detail screen. */
    public static final String ENTRY_DEEP_LINK = "deep_link";

    private static final Pattern CLIENT_NONCE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /**
     * Sheet over the current list. Closing it returns to that tab and scroll position.
     * Only a deep link opens the standalone detail screen.
     */
    public static boolean opensCommentSheet(String entry) {
        return !opensStandaloneDetail(entry);
    }

    public static boolean opensStandaloneDetail(String entry) {
        return ENTRY_DEEP_LINK.equals(entry);
    }

    public static boolean isClientNonce(String nonce) {
        return nonce != null && CLIENT_NONCE.matcher(nonce).matches();
    }

    public static String newClientNonce() {
        return UUID.randomUUID().toString();
    }

    /** include and exclude are the only visibilities that take discover.getAudienceToken. */
    public static boolean needsAudienceToken(String visibility) {
        return VIS_INCLUDE.equals(visibility) || VIS_EXCLUDE.equals(visibility);
    }

    /**
     * Picker kinds become the engine visibility. Public plus a member list is exclude.
     * An unknown kind is left as-is and is never rewritten to public.
     */
    public static String visibilityOf(String kind, boolean hasMembers) {
        if (kind == null || kind.length() == 0) {
            return "";
        }
        if ("selected".equals(kind) || VIS_INCLUDE.equals(kind)) {
            return VIS_INCLUDE;
        }
        if ("excluded".equals(kind) || VIS_EXCLUDE.equals(kind)) {
            return VIS_EXCLUDE;
        }
        if ("mutual_followers".equals(kind) || VIS_FRIENDS.equals(kind)) {
            return VIS_FRIENDS;
        }
        if (VIS_CLOSE_FRIENDS.equals(kind)) {
            return VIS_CLOSE_FRIENDS;
        }
        if (VIS_CONTACTS.equals(kind)) {
            return VIS_CONTACTS;
        }
        if (VIS_ONLY_ME.equals(kind)) {
            return VIS_ONLY_ME;
        }
        if (VIS_PUBLIC.equals(kind)) {
            return hasMembers ? VIS_EXCLUDE : VIS_PUBLIC;
        }
        return kind;
    }

    public static ArrayList<Long> uniqueMemberIds(List<Long> direct, List<? extends List<Long>> groups) {
        ArrayList<Long> out = new ArrayList<>();
        addMembers(out, direct);
        if (groups != null) {
            for (int i = 0; i < groups.size(); i++) {
                addMembers(out, groups.get(i));
            }
        }
        return out;
    }

    private static void addMembers(ArrayList<Long> out, List<Long> ids) {
        if (ids == null) {
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            if (id == null || id == 0L || out.contains(id)) {
                continue;
            }
            out.add(id);
        }
    }

    /** Images stop at {@link #MAX_IMAGES}. Video and other keys stay. */
    public static ArrayList<String> limitMediaKeys(List<String> keys, List<String> types) {
        ArrayList<String> out = new ArrayList<>();
        if (keys == null) {
            return out;
        }
        int images = 0;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            if (key == null || key.length() == 0) {
                continue;
            }
            String type = types != null && i < types.size() && types.get(i) != null ? types.get(i) : "image";
            boolean image = "image".equals(type) || "photo".equals(type);
            if (image) {
                if (images >= MAX_IMAGES) {
                    continue;
                }
                images++;
            }
            out.add(key);
        }
        return out;
    }

    /**
     * Create-post fields the engine accepts. Display names and picker kinds are not sent.
     * A missing or failed audience token does not produce a body and does not become public.
     */
    public static final class PostRequest {
        public String kind = "post";
        public String text = "";
        public String title = "";
        public String visibility = "";
        public final ArrayList<Long> userIds = new ArrayList<>();
        public String audienceToken = "";
        public final ArrayList<String> mediaKeys = new ArrayList<>();
        public final ArrayList<String> mediaTypes = new ArrayList<>();
        public String city = "";
        public String cityName = "";
        public boolean allowNearby;
        public String placeId = "";
        public String placeName = "";
        public String quoteOf = "";
        public String clientNonce = "";
        public String pollJson = "";

        public PostRequest copy() {
            PostRequest copy = new PostRequest();
            copy.kind = kind;
            copy.text = text;
            copy.title = title;
            copy.visibility = visibility;
            copy.userIds.addAll(userIds);
            copy.audienceToken = audienceToken;
            copy.mediaKeys.addAll(mediaKeys);
            copy.mediaTypes.addAll(mediaTypes);
            copy.city = city;
            copy.cityName = cityName;
            copy.allowNearby = allowNearby;
            copy.placeId = placeId;
            copy.placeName = placeName;
            copy.quoteOf = quoteOf;
            copy.clientNonce = clientNonce;
            copy.pollJson = pollJson;
            return copy;
        }
    }

    public static final class PostBuild {
        public final boolean send;
        public final String error;
        public final String body;
        public final String visibility;
        public final ArrayList<Long> userIds;
        public final String nonce;

        private PostBuild(boolean send, String error, String body, String visibility, List<Long> userIds, String nonce) {
            this.send = send;
            this.error = error == null ? "" : error;
            this.body = body;
            this.visibility = visibility == null ? "" : visibility;
            this.userIds = new ArrayList<>();
            if (userIds != null) {
                this.userIds.addAll(userIds);
            }
            this.nonce = nonce == null ? "" : nonce;
        }
    }

    public static PostBuild buildPost(PostRequest request, String token, String tokenError) {
        PostRequest copy = request == null ? new PostRequest() : request.copy();
        copy.visibility = visibilityOf(copy.visibility, !copy.userIds.isEmpty());
        if (copy.visibility.length() == 0) {
            return blocked(copy, "invalid_visibility");
        }
        if (copy.kind == null || copy.kind.length() == 0) {
            copy.kind = "post";
        }
        if (!"post".equals(copy.kind) && !"article".equals(copy.kind) && !"story".equals(copy.kind)) {
            return blocked(copy, "invalid_kind");
        }
        if (!isClientNonce(copy.clientNonce)) {
            return blocked(copy, "invalid_client_nonce");
        }
        if (needsAudienceToken(copy.visibility)) {
            if (copy.userIds.isEmpty()) {
                return blocked(copy, "invalid_audience");
            }
            boolean tokenFailed = (tokenError != null && tokenError.length() > 0) || token == null || token.length() == 0;
            if (tokenFailed) {
                String reason = tokenError != null && tokenError.length() > 0 ? tokenError : "audience_token";
                return blocked(copy, reason);
            }
            copy.audienceToken = token;
        } else if (!copy.userIds.isEmpty()) {
            return blocked(copy, "invalid_audience");
        } else {
            copy.audienceToken = "";
        }
        String body = encodePost(copy);
        if (body == null) {
            return blocked(copy, "invalid_poll");
        }
        return ready(copy, body);
    }

    /** Visibility change. Same token rule as create. Content fields are not included. */
    public static PostBuild buildAudience(String visibility, List<Long> userIds, String token, String tokenError) {
        PostRequest request = new PostRequest();
        request.kind = "post";
        request.visibility = visibility == null ? "" : visibility;
        request.clientNonce = newClientNonce();
        if (userIds != null) {
            request.userIds.addAll(userIds);
        }
        PostBuild built = buildPost(request, token, tokenError);
        if (!built.send || built.body == null) {
            return built;
        }
        JsonObject full = object(built.body);
        JsonObject slim = new JsonObject();
        if (full != null && full.has("visibility")) {
            slim.add("visibility", full.get("visibility"));
        }
        if (full != null && full.has("audience")) {
            slim.add("audience", full.get("audience"));
        }
        return new PostBuild(true, "", slim.toString(), built.visibility, built.userIds, built.nonce);
    }

    private static PostBuild blocked(PostRequest request, String error) {
        return new PostBuild(false, error, null, request.visibility, request.userIds, request.clientNonce);
    }

    private static PostBuild ready(PostRequest request, String body) {
        return new PostBuild(true, "", body, request.visibility, request.userIds, request.clientNonce);
    }

    /** Whitelisted create body. Returns null when poll JSON is not an object. */
    private static String encodePost(PostRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("kind", request.kind);
        String text = clampText(request.text);
        if (text.length() > 0) {
            body.addProperty("text", text);
        }
        if (request.title != null && request.title.length() > 0) {
            body.addProperty("title", request.title);
        }
        body.addProperty("visibility", request.visibility);
        if (needsAudienceToken(request.visibility)) {
            JsonObject audience = new JsonObject();
            audience.addProperty("audience_token", request.audienceToken);
            body.add("audience", audience);
        }
        ArrayList<String> keys = limitMediaKeys(request.mediaKeys, request.mediaTypes);
        if (!keys.isEmpty()) {
            JsonArray mediaKeys = new JsonArray();
            for (int i = 0; i < keys.size(); i++) {
                mediaKeys.add(keys.get(i));
            }
            body.add("media_keys", mediaKeys);
        }
        if (request.city != null && request.city.length() > 0) {
            body.addProperty("city", request.city);
        }
        if (request.allowNearby && VIS_PUBLIC.equals(request.visibility)) {
            body.addProperty("allow_nearby", true);
        }
        if (request.placeId != null && request.placeId.length() > 0) {
            body.addProperty("place_id", request.placeId);
        }
        if (request.quoteOf != null && request.quoteOf.length() > 0) {
            body.addProperty("quote_of", request.quoteOf);
        }
        if (request.pollJson != null && request.pollJson.length() > 0) {
            JsonElement poll;
            try {
                poll = JsonParser.parseString(request.pollJson);
            } catch (Exception e) {
                return null;
            }
            if (poll == null || !poll.isJsonObject()) {
                return null;
            }
            body.add("poll", poll);
        }
        body.addProperty("client_nonce", request.clientNonce);
        return body.toString();
    }

    public static String draftJson(PostRequest request, List<String> paths, List<String> types) {
        JsonObject o = new JsonObject();
        PostRequest source = request == null ? new PostRequest() : request;
        o.addProperty("text", source.text == null ? "" : source.text);
        o.addProperty("visibility", source.visibility == null ? "" : source.visibility);
        JsonArray ids = new JsonArray();
        for (int i = 0; i < source.userIds.size(); i++) {
            Long id = source.userIds.get(i);
            if (id != null && id != 0L) {
                ids.add(id);
            }
        }
        o.add("user_ids", ids);
        o.addProperty("quote_of", source.quoteOf == null ? "" : source.quoteOf);
        o.addProperty("allow_nearby", source.allowNearby);
        o.addProperty("city", source.city == null ? "" : source.city);
        o.addProperty("city_name", source.cityName == null ? "" : source.cityName);
        o.addProperty("place_id", source.placeId == null ? "" : source.placeId);
        o.addProperty("place_name", source.placeName == null ? "" : source.placeName);
        if (source.pollJson != null && source.pollJson.length() > 0) {
            try {
                JsonElement poll = JsonParser.parseString(source.pollJson);
                if (poll != null && poll.isJsonObject()) {
                    o.add("poll", poll);
                }
            } catch (Exception ignore) {
            }
        }
        o.add("paths", stringArray(paths));
        o.add("types", stringArray(types));
        return o.toString();
    }

    /** Restores a composer draft. Old drafts that only stored a picker kind still parse. */
    public static PostRequest parseDraft(String json, List<String> paths, List<String> types) {
        JsonObject o = object(json);
        if (o == null) {
            return null;
        }
        PostRequest request = new PostRequest();
        request.text = string(o, "text");
        request.quoteOf = string(o, "quote_of");
        request.city = string(o, "city");
        request.cityName = string(o, "city_name");
        request.placeId = string(o, "place_id");
        request.placeName = string(o, "place_name");
        if (o.has("allow_nearby") && o.get("allow_nearby").isJsonPrimitive()) {
            request.allowNearby = o.get("allow_nearby").getAsBoolean();
        }
        JsonArray ids = o.has("user_ids") ? array(o.get("user_ids")) : null;
        if (ids != null) {
            for (int i = 0; i < ids.size(); i++) {
                JsonElement element = ids.get(i);
                if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    long id = element.getAsLong();
                    if (id != 0L) {
                        request.userIds.add(id);
                    }
                }
            }
        }
        String rawVisibility = string(o, "visibility");
        if (rawVisibility.length() == 0) {
            rawVisibility = string(o, "audience");
        }
        request.visibility = visibilityOf(rawVisibility, !request.userIds.isEmpty());
        if (o.has("poll") && o.get("poll").isJsonObject()) {
            request.pollJson = o.get("poll").toString();
        }
        fillStrings(paths, o.has("paths") ? array(o.get("paths")) : null);
        fillStrings(types, o.has("types") ? array(o.get("types")) : null);
        return request;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                array.add(value);
            }
        }
        return array;
    }

    private static void fillStrings(List<String> into, JsonArray values) {
        if (into == null) {
            return;
        }
        into.clear();
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            JsonElement element = values.get(i);
            if (element != null && element.isJsonPrimitive()) {
                into.add(element.getAsString());
            }
        }
    }

    /**
     * One comment attempt. Failure keeps the text, reply, attachment, and nonce.
     * Success clears them so the next comment gets a new nonce.
     */
    public static final class CommentDraft {
        public String text = "";
        public String parentId = "";
        public String attachmentType = "";
        public String attachmentKey = "";
        public String nonce = "";

        public void prepare(String text, String parentId, String attachmentType, String attachmentKey) {
            this.text = text == null ? "" : text;
            this.parentId = parentId == null ? "" : parentId;
            this.attachmentType = attachmentType == null ? "" : attachmentType;
            this.attachmentKey = attachmentKey == null ? "" : attachmentKey;
            if (!isClientNonce(nonce)) {
                nonce = newClientNonce();
            }
        }

        public void onSuccess() {
            text = "";
            parentId = "";
            attachmentType = "";
            attachmentKey = "";
            nonce = "";
        }

        public void onFailure() {
        }

        public String body() {
            return commentBody(text, parentId, attachmentType, attachmentKey, nonce);
        }
    }
}
