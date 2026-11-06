package org.telegram.ui.tellomi.feed;

import java.util.ArrayList;
import java.util.List;

/** Card, feed, and config parsers. Field names follow the content-engine API. */
public final class TellomiDiscoverParse {

    private TellomiDiscoverParse() {
    }

    public static TellomiDiscover.Capabilities capabilities(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        Integer version = intValue(json.objectValue.get("version"));
        if (version == null || version != 1) {
            return null;
        }
        TellomiJson features = json.objectValue.get("features");
        if (features == null || features.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < features.arrayValue.size(); i++) {
            String name = stringValue(features.arrayValue.get(i));
            if (name == null) {
                return null;
            }
            names.add(name);
        }
        return new TellomiDiscover.Capabilities(version, names);
    }

    public static Boolean status(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        return boolValue(json.objectValue.get("visible"));
    }

    public static TellomiDiscover.EngineConfig engineConfig(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        Boolean enabled = boolValue(json.objectValue.get("enabled"));
        if (enabled == null) {
            return null;
        }
        List<TellomiDiscover.FeedKind> tabs = new ArrayList<>();
        TellomiJson tabValues = json.objectValue.get("tabs");
        if (tabValues != null && tabValues.type == TellomiJson.Type.ARRAY) {
            for (int i = 0; i < tabValues.arrayValue.size(); i++) {
                String name = stringValue(tabValues.arrayValue.get(i));
                TellomiDiscover.FeedKind kind = name == null ? null : TellomiDiscover.FeedKind.fromConfig(name);
                if (kind != null && !tabs.contains(kind)) {
                    tabs.add(kind);
                }
            }
        }
        if (tabs.isEmpty()) {
            tabs.add(TellomiDiscover.FeedKind.RECOMMEND);
            tabs.add(TellomiDiscover.FeedKind.FOLLOWING);
        }
        TellomiJson limits = json.objectValue.get("limits");
        Integer textChars = null;
        if (limits != null && limits.type == TellomiJson.Type.OBJECT) {
            textChars = intValue(limits.objectValue.get("post_text_chars"));
        }
        int limit = textChars == null ? TellomiDiscover.POST_TEXT_LIMIT : textChars;
        if (limit <= 0) {
            limit = TellomiDiscover.POST_TEXT_LIMIT;
        }
        Boolean personalization = boolValue(json.objectValue.get("personalization_toggle_visible"));
        return new TellomiDiscover.EngineConfig(
            enabled,
            stringValue(json.objectValue.get("region")),
            tabs,
            stringValue(json.objectValue.get("recommend_mode")),
            personalization != null && personalization,
            limit,
            mediaBase(stringValue(json.objectValue.get("media_base_url")))
        );
    }

    public static TellomiDiscover.FeedPage feedPage(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        TellomiJson items = json.objectValue.get("items");
        if (items == null || items.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        List<TellomiDiscover.FeedItem> parsed = new ArrayList<>();
        for (int i = 0; i < items.arrayValue.size(); i++) {
            TellomiJson item = items.arrayValue.get(i);
            if (item == null || item.type != TellomiJson.Type.OBJECT) {
                return null;
            }
            String id = TellomiDiscover.postId(stringValue(item.objectValue.get("id")));
            if (id == null) {
                return null;
            }
            String reason = stringValue(item.objectValue.get("reason"));
            parsed.add(new TellomiDiscover.FeedItem(id, reason == null ? "" : reason, stringValue(item.objectValue.get("reposted_by"))));
        }
        String cursor = stringValue(json.objectValue.get("cursor"));
        if (cursor != null && cursor.isEmpty()) {
            cursor = null;
        }
        return new TellomiDiscover.FeedPage(parsed, cursor);
    }

    public static List<TellomiDiscover.Card> cards(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        TellomiJson values = json.objectValue.get("cards");
        if (values == null || values.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        List<TellomiDiscover.Card> parsed = new ArrayList<>();
        for (int i = 0; i < values.arrayValue.size(); i++) {
            TellomiDiscover.Card card = card(values.arrayValue.get(i));
            if (card == null) {
                return null;
            }
            parsed.add(card);
        }
        return parsed;
    }

    public static TellomiDiscover.CreatedPost createdPost(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        String id = TellomiDiscover.postId(stringValue(json.objectValue.get("id")));
        String state = stringValue(json.objectValue.get("state"));
        Integer version = intValue(json.objectValue.get("version"));
        if (id == null || state == null || state.isEmpty() || version == null) {
            return null;
        }
        return new TellomiDiscover.CreatedPost(id, state, version);
    }

    public static String errorCode(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        return stringValue(json.objectValue.get("error"));
    }

    /**
     * {@code 201}/{@code 200 {"id","state","version","held"?}}. {@code held} may be absent.
     * A missing id, state, or version is not a created comment.
     */
    public static TellomiDiscover.CommentCreated commentCreated(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        String id = TellomiDiscover.postId(stringValue(json.objectValue.get("id")));
        String state = stringValue(json.objectValue.get("state"));
        Integer version = intValue(json.objectValue.get("version"));
        if (id == null || state == null || state.isEmpty() || version == null) {
            return null;
        }
        Boolean held = null;
        if (json.objectValue.containsKey("held")) {
            held = boolValue(json.objectValue.get("held"));
            if (held == null) {
                return null;
            }
        }
        return new TellomiDiscover.CommentCreated(id, state, version, held);
    }

    /**
     * Public comments, oldest first. Pending rows are omitted. A comment whose
     * {@code post_id} is another post is omitted. More than 50 rows is unreadable.
     */
    public static TellomiDiscover.CommentPage commentPage(byte[] data, String postId) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        TellomiJson values = json.objectValue.get("comments");
        if (values == null || values.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        if (values.arrayValue.size() > TellomiDiscoverReact.COMMENT_PAGE) {
            return null;
        }
        String cursor = null;
        if (json.objectValue.containsKey("cursor")) {
            TellomiJson rawCursor = json.objectValue.get("cursor");
            if (rawCursor == null || rawCursor.type != TellomiJson.Type.STRING) {
                return null;
            }
            cursor = rawCursor.stringValue;
            if (cursor.isEmpty()) {
                cursor = null;
            }
        }
        java.util.List<TellomiDiscover.Comment> parsed = new java.util.ArrayList<>();
        for (int i = 0; i < values.arrayValue.size(); i++) {
            TellomiJson item = values.arrayValue.get(i);
            if (item == null || item.type != TellomiJson.Type.OBJECT) {
                return null;
            }
            TellomiDiscover.Comment comment = commentCard(item, postId);
            if (comment == null) {
                continue;
            }
            parsed.add(comment);
        }
        return new TellomiDiscover.CommentPage(parsed, cursor);
    }

    private static TellomiDiscover.Comment commentCard(TellomiJson item, String expectedPost) {
        String id = TellomiDiscover.postId(stringValue(item.objectValue.get("id")));
        if (id == null) {
            return null;
        }
        String state = stringValue(item.objectValue.get("state"));
        if (state != null && !"ok".equals(state)) {
            return null;
        }
        String post = stringValue(item.objectValue.get("post_id"));
        if (post != null) {
            post = TellomiDiscover.postId(post);
            if (post == null || (expectedPost != null && !expectedPost.equals(post))) {
                return null;
            }
        }
        String root = uuidOrNull(item.objectValue.get("root_id"));
        String parent = uuidOrNull(item.objectValue.get("parent_id"));
        if (item.objectValue.containsKey("root_id") && root == null) {
            return null;
        }
        if (item.objectValue.containsKey("parent_id") && parent == null) {
            return null;
        }
        TellomiJson body = objectValue(item.objectValue.get("card"));
        TellomiJson author = body == null ? null : objectValue(body.objectValue.get("author"));
        String text = body == null ? null : stringValue(body.objectValue.get("text"));
        if (text == null) {
            text = stringValue(item.objectValue.get("text"));
        }
        String authorName = author == null ? "" : empty(stringValue(author.objectValue.get("display_name")));
        TellomiJson me = objectValue(item.objectValue.get("me"));
        Boolean own = me == null ? null : boolValue(me.objectValue.get("author"));
        return new TellomiDiscover.Comment(id, text == null ? "" : text, state == null ? "ok" : state, authorName, post, root, parent, own != null && own);
    }

    private static String uuidOrNull(TellomiJson json) {
        String value = stringValue(json);
        if (value == null || !TellomiDiscover.clientNonce(value)) {
            return null;
        }
        return value;
    }

    public static TellomiDiscover.Post displayPost(TellomiDiscover.Card card) {
        TellomiDiscover.Post post = new TellomiDiscover.Post(
            card.id,
            card.text,
            card.authorName,
            card.authorHandle,
            card.tellomiNumber,
            !"ok".equals(card.state),
            card.aiGenerated,
            card.badge,
            card.likeCount,
            card.commentCount,
            card.repostCount,
            card.imageCount,
            card.quoteState,
            card.quoteText,
            card.ipRegionLabel,
            card.city
        );
        post.comments = card.comments == null ? java.util.Collections.emptyList() : card.comments;
        post.liked = card.liked;
        post.likesHidden = card.likesHidden;
        post.shareable = card.shareable;
        post.poll = card.poll;
        post.linkPreview = card.linkPreview;
        post.images = card.images == null ? java.util.Collections.<TellomiDiscover.Image>emptyList() : card.images;
        return post;
    }

    private static TellomiDiscover.Card card(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.OBJECT) {
            return null;
        }
        String id = TellomiDiscover.postId(stringValue(json.objectValue.get("id")));
        if (id == null) {
            return null;
        }
        String state = stringValue(json.objectValue.get("state"));
        if (state == null) {
            state = "ok";
        }
        TellomiJson body = objectValue(json.objectValue.get("card"));
        TellomiJson author = body == null ? null : objectValue(body.objectValue.get("author"));
        TellomiJson stats = objectValue(json.objectValue.get("stats"));
        TellomiJson quoted = objectValue(json.objectValue.get("quoted"));
        TellomiJson quotedCard = quoted == null ? null : objectValue(quoted.objectValue.get("card"));
        String city = TellomiDiscoverCity.code(stringValue(body == null ? null : body.objectValue.get("city")));
        if (city == null) {
            city = TellomiDiscoverCity.code(stringValue(json.objectValue.get("city")));
        }
        Boolean ai = body == null ? null : boolValue(body.objectValue.get("ai_generated"));
        Boolean edited = body == null ? null : boolValue(body.objectValue.get("edited"));
        Boolean shareable = boolValue(json.objectValue.get("shareable"));
        TellomiDiscover.Card card = new TellomiDiscover.Card(
            id,
            state,
            intValue(json.objectValue.get("version")),
            body == null ? "" : empty(stringValue(body.objectValue.get("text"))),
            author == null ? "" : empty(stringValue(author.objectValue.get("display_name"))),
            author == null ? null : stringValue(author.objectValue.get("handle")),
            author == null ? null : stringValue(author.objectValue.get("tellomi_number")),
            author == null ? null : stringValue(author.objectValue.get("id")),
            author != null && stringValue(author.objectValue.get("badge")) != null
                ? stringValue(author.objectValue.get("badge"))
                : (body == null ? null : stringValue(body.objectValue.get("badge"))),
            ai != null && ai,
            edited != null && edited,
            shareable != null && shareable,
            stats == null ? 0 : zero(intValue(stats.objectValue.get("likes"))),
            stats == null ? 0 : zero(intValue(stats.objectValue.get("comments"))),
            stats == null ? 0 : zero(intValue(stats.objectValue.get("reposts"))),
            body == null ? 0 : imageCount(body.objectValue.get("media")),
            quoted == null ? null : stringValue(quoted.objectValue.get("state")),
            quotedCard == null ? null : stringValue(quotedCard.objectValue.get("text")),
            body == null ? null : stringValue(body.objectValue.get("ip_region_label")),
            city
        );
        TellomiJson me = objectValue(json.objectValue.get("me"));
        Boolean liked = me == null ? null : boolValue(me.objectValue.get("liked"));
        Boolean likesHidden = stats == null ? null : boolValue(stats.objectValue.get("likes_hidden"));
        card.liked = liked != null && liked;
        card.likesHidden = likesHidden != null && likesHidden;
        card.comments = comments(json.objectValue.get("comments"));
        card.poll = poll(body == null ? null : body.objectValue.get("poll"), json.objectValue.get("poll"));
        card.linkPreview = TellomiDiscoverLink.stored(body, json);
        card.images = images(body == null ? null : body.objectValue.get("media"));
        return card;
    }

    /**
     * {@code 200 {"counts":[Int],"voted":[Int]}}. An empty body or {@code {}} is not a tally.
     * A negative number rejects the whole response.
     */
    public static TellomiDiscover.VoteTally voteTally(byte[] data) {
        TellomiJson json = object(data);
        if (json == null || !json.objectValue.containsKey("counts") || !json.objectValue.containsKey("voted")) {
            return null;
        }
        java.util.List<Integer> counts = intList(json.objectValue.get("counts"));
        java.util.List<Integer> voted = intList(json.objectValue.get("voted"));
        if (counts == null || voted == null) {
            return null;
        }
        for (int i = 0; i < counts.size(); i++) {
            if (counts.get(i) == null || counts.get(i) < 0) {
                return null;
            }
        }
        for (int i = 0; i < voted.size(); i++) {
            if (voted.get(i) == null || voted.get(i) < 0) {
                return null;
            }
        }
        return new TellomiDiscover.VoteTally(counts, voted);
    }

    public static TellomiDiscover.NoticePage notices(byte[] data) {
        TellomiJson json = object(data);
        if (json == null) {
            return null;
        }
        TellomiJson items = json.objectValue.get("items");
        if (items == null) {
            items = json.objectValue.get("notifications");
        }
        if (items == null || items.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        java.util.List<TellomiDiscover.Notice> parsed = new java.util.ArrayList<>();
        for (int i = 0; i < items.arrayValue.size(); i++) {
            TellomiJson item = objectValue(items.arrayValue.get(i));
            if (item == null) {
                continue;
            }
            String id = stringValue(item.objectValue.get("id"));
            if (id == null || id.isEmpty()) {
                id = stringValue(item.objectValue.get("event_id"));
            }
            String kind = stringValue(item.objectValue.get("kind"));
            if (id == null || id.isEmpty() || kind == null || kind.isEmpty()) {
                continue;
            }
            String actor = stringValue(item.objectValue.get("actor_name"));
            if (actor == null) {
                TellomiJson actorObject = objectValue(item.objectValue.get("actor"));
                actor = actorObject == null ? null : stringValue(actorObject.objectValue.get("display_name"));
            }
            String text = stringValue(item.objectValue.get("text"));
            if (text == null) {
                text = stringValue(item.objectValue.get("body"));
            }
            String postId = TellomiDiscover.postId(stringValue(item.objectValue.get("post_id")));
            parsed.add(new TellomiDiscover.Notice(id, kind, actor == null ? "" : actor, postId, text == null ? "" : text));
        }
        String cursor = stringValue(json.objectValue.get("cursor"));
        if (cursor != null && cursor.isEmpty()) {
            cursor = null;
        }
        return new TellomiDiscover.NoticePage(parsed, cursor);
    }

    /**
     * Labels are {@code card.poll.options} as strings. Counts and this account's
     * choice are the sibling {@code poll.counts} / {@code poll.voted}. An object
     * option ({@code text}/{@code label}/{@code votes}) is not a poll.
     */
    private static TellomiDiscover.Poll poll(TellomiJson labels, TellomiJson tally) {
        TellomiJson labelObject = objectValue(labels);
        if (labelObject == null) {
            return null;
        }
        TellomiJson options = labelObject.objectValue.get("options");
        if (options == null || options.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        java.util.List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < options.arrayValue.size(); i++) {
            String text = stringValue(options.arrayValue.get(i));
            if (text == null) {
                return null;
            }
            text = TellomiDiscover.trim(text);
            if (text.isEmpty()) {
                return null;
            }
            texts.add(text);
        }
        if (texts.size() < TellomiDiscover.POLL_OPTION_MIN) {
            return null;
        }
        TellomiJson tallyObject = objectValue(tally);
        java.util.List<Integer> counts = intListOrEmpty(tallyObject, "counts");
        java.util.List<Integer> votedRaw = intListOrEmpty(tallyObject, "voted");
        java.util.List<TellomiDiscover.PollOption> parsed = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            int votes = i < counts.size() && counts.get(i) != null ? counts.get(i) : 0;
            if (votes < 0) {
                votes = 0;
            }
            parsed.add(new TellomiDiscover.PollOption(texts.get(i), votes));
        }
        java.util.List<Integer> voted = new java.util.ArrayList<>();
        for (int i = 0; i < votedRaw.size(); i++) {
            Integer index = votedRaw.get(i);
            if (index != null && index >= 0 && index < parsed.size()) {
                voted.add(index);
            }
        }
        return new TellomiDiscover.Poll(parsed, voted);
    }

    private static java.util.List<Integer> intListOrEmpty(TellomiJson object, String key) {
        if (object == null || !object.objectValue.containsKey(key)) {
            return java.util.Collections.emptyList();
        }
        java.util.List<Integer> values = intList(object.objectValue.get(key));
        return values == null ? java.util.Collections.<Integer>emptyList() : values;
    }

    private static java.util.List<Integer> intList(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.ARRAY) {
            return null;
        }
        java.util.List<Integer> values = new java.util.ArrayList<>();
        for (int i = 0; i < json.arrayValue.size(); i++) {
            Integer value = intValue(json.arrayValue.get(i));
            if (value == null) {
                return null;
            }
            values.add(value);
        }
        return values;
    }

    private static java.util.List<TellomiDiscover.Comment> comments(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.ARRAY) {
            return java.util.Collections.emptyList();
        }
        java.util.List<TellomiDiscover.Comment> list = new java.util.ArrayList<>();
        for (int i = 0; i < json.arrayValue.size(); i++) {
            TellomiJson item = objectValue(json.arrayValue.get(i));
            if (item == null) {
                continue;
            }
            String id = TellomiDiscover.postId(stringValue(item.objectValue.get("id")));
            if (id == null) {
                continue;
            }
            String text = stringValue(item.objectValue.get("text"));
            list.add(new TellomiDiscover.Comment(id, text == null ? "" : text));
        }
        return list;
    }

    private static int imageCount(TellomiJson json) {
        return images(json).size() + skippedImages(json);
    }

    /** Images with a usable {@code key}. A typed image with no key still counts, and is not shown. */
    private static java.util.List<TellomiDiscover.Image> images(TellomiJson json) {
        java.util.List<TellomiDiscover.Image> list = new java.util.ArrayList<>();
        if (json == null || json.type != TellomiJson.Type.ARRAY) {
            return list;
        }
        for (int i = 0; i < json.arrayValue.size(); i++) {
            TellomiJson item = objectValue(json.arrayValue.get(i));
            if (!imageItem(item)) {
                continue;
            }
            String key = stringValue(item.objectValue.get("key"));
            boolean usableKey = TellomiDiscoverMedia.validKey(key);
            String url = TellomiDiscoverMedia.cardUrl(stringValue(item.objectValue.get("url")));
            if (usableKey || url != null) {
                list.add(new TellomiDiscover.Image(usableKey ? key : null, url));
            }
        }
        return list;
    }

    private static int skippedImages(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.ARRAY) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < json.arrayValue.size(); i++) {
            TellomiJson item = objectValue(json.arrayValue.get(i));
            if (!imageItem(item)) {
                continue;
            }
            String key = stringValue(item.objectValue.get("key"));
            String url = TellomiDiscoverMedia.cardUrl(stringValue(item.objectValue.get("url")));
            if (!TellomiDiscoverMedia.validKey(key) && url == null) {
                count++;
            }
        }
        return count;
    }

    /** Empty is the same as a missing {@code media_base_url}. */
    private static String mediaBase(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean imageItem(TellomiJson item) {
        if (item == null) {
            return false;
        }
        String kind = stringValue(item.objectValue.get("type"));
        return kind != null && "image".equals(kind.toLowerCase(java.util.Locale.US));
    }

    private static TellomiJson object(byte[] data) {
        TellomiJson json = TellomiJson.parse(data);
        if (json == null || json.type != TellomiJson.Type.OBJECT) {
            return null;
        }
        return json;
    }

    private static TellomiJson objectValue(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.OBJECT) {
            return null;
        }
        return json;
    }

    static String stringValue(TellomiJson json) {
        if (json == null) {
            return null;
        }
        if (json.type == TellomiJson.Type.STRING) {
            return json.stringValue;
        }
        if (json.type == TellomiJson.Type.NUMBER) {
            double value = json.numberValue;
            if (value == Math.rint(value) && value <= Long.MAX_VALUE && value >= Long.MIN_VALUE) {
                return Long.toString((long) value);
            }
        }
        return null;
    }

    private static Boolean boolValue(TellomiJson json) {
        if (json == null) {
            return null;
        }
        if (json.type == TellomiJson.Type.BOOL) {
            return json.boolValue;
        }
        if (json.type == TellomiJson.Type.NUMBER) {
            return json.numberValue != 0;
        }
        if (json.type == TellomiJson.Type.STRING) {
            String value = json.stringValue.toLowerCase(java.util.Locale.US);
            if ("1".equals(value) || "true".equals(value) || "yes".equals(value)) {
                return true;
            }
            if ("0".equals(value) || "false".equals(value) || "no".equals(value)) {
                return false;
            }
        }
        return null;
    }

    private static Integer intValue(TellomiJson json) {
        Long value = int64Value(json);
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private static Long int64Value(TellomiJson json) {
        if (json == null) {
            return null;
        }
        if (json.type == TellomiJson.Type.NUMBER) {
            double value = json.numberValue;
            if (value != Math.rint(value) || value > Long.MAX_VALUE || value < Long.MIN_VALUE) {
                return null;
            }
            return (long) value;
        }
        if (json.type == TellomiJson.Type.STRING) {
            try {
                return Long.parseLong(json.stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }
}
