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
            limit
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
        post.shareable = card.shareable;
        post.poll = card.poll;
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
        card.comments = comments(json.objectValue.get("comments"));
        card.poll = poll(body == null ? null : body.objectValue.get("poll"));
        return card;
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

    private static TellomiDiscover.Poll poll(TellomiJson json) {
        TellomiJson object = objectValue(json);
        if (object == null) {
            return null;
        }
        TellomiJson options = object.objectValue.get("options");
        if (options == null || options.type != TellomiJson.Type.ARRAY || options.arrayValue.isEmpty()) {
            return null;
        }
        java.util.List<TellomiDiscover.PollOption> parsed = new java.util.ArrayList<>();
        for (int i = 0; i < options.arrayValue.size(); i++) {
            TellomiJson item = options.arrayValue.get(i);
            if (item == null) {
                continue;
            }
            String text;
            int votes = 0;
            boolean voted = false;
            if (item.type == TellomiJson.Type.STRING) {
                text = item.stringValue;
            } else {
                TellomiJson option = objectValue(item);
                if (option == null) {
                    continue;
                }
                text = stringValue(option.objectValue.get("text"));
                if (text == null) {
                    text = stringValue(option.objectValue.get("label"));
                }
                Integer count = intValue(option.objectValue.get("votes"));
                if (count == null) {
                    count = intValue(option.objectValue.get("vote_count"));
                }
                votes = count == null ? 0 : count;
                Boolean mine = boolValue(option.objectValue.get("voted"));
                if (mine == null) {
                    mine = boolValue(option.objectValue.get("voted_by_me"));
                }
                voted = mine != null && mine;
            }
            if (text == null || text.isEmpty()) {
                continue;
            }
            parsed.add(new TellomiDiscover.PollOption(text, votes, voted));
        }
        if (parsed.isEmpty()) {
            return null;
        }
        return new TellomiDiscover.Poll(parsed);
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
        if (json == null || json.type != TellomiJson.Type.ARRAY) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < json.arrayValue.size(); i++) {
            TellomiJson item = objectValue(json.arrayValue.get(i));
            if (item == null) {
                continue;
            }
            String kind = stringValue(item.objectValue.get("type"));
            if (kind != null && "image".equals(kind.toLowerCase(java.util.Locale.US))) {
                count++;
            }
        }
        return count;
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
