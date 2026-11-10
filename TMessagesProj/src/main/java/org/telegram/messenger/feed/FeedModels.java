package org.telegram.messenger.feed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class FeedModels {

    private FeedModels() {
    }

    public static final String STREAM_RECOMMEND = FeedContract.STREAM_RECOMMEND;
    public static final String STREAM_FOLLOWING = FeedContract.STREAM_FOLLOWING;
    public static final String STREAM_NEARBY = FeedContract.STREAM_NEARBY;

    /** Engine streams. friends is not a route. */
    public static final String[] STREAMS = new String[]{
            STREAM_RECOMMEND, STREAM_FOLLOWING, STREAM_NEARBY
    };

    public static class Author {
        public long userId;
        public String profileId = "";
        public String name = "";
        public String username = "";
        public String avatarUrl = "";

        public static Author parse(JSONObject o) {
            Author a = new Author();
            if (o == null) {
                return a;
            }
            a.userId = o.optLong("user_id", o.optLong("telegram_user_id", 0));
            a.profileId = o.optString("id", o.optString("profile_id", ""));
            a.name = o.optString("name", o.optString("display_name", ""));
            a.username = o.optString("username", o.optString("handle", ""));
            a.avatarUrl = o.optString("avatar", o.optString("avatar_url", ""));
            return a;
        }
    }

    public static class Media {
        public String type = "image";
        public String key = "";
        public String url = "";
        public String coverKey = "";
        public String name = "";
        public String mime = "";
        public int w;
        public int h;
        public int durationMs;
        public boolean original;

        static Media parse(JSONObject o) {
            Media m = new Media();
            if (o == null) {
                return m;
            }
            m.type = o.optString("type", o.optString("kind", "image"));
            m.key = o.optString("key", "");
            m.url = o.optString("url", o.optString("src", ""));
            m.coverKey = o.optString("cover_key", o.optString("cover", ""));
            m.name = o.optString("name", o.optString("filename", o.optString("file_name", "")));
            m.mime = o.optString("mime", o.optString("content_type", ""));
            m.w = o.optInt("w", o.optInt("width", 0));
            m.h = o.optInt("h", o.optInt("height", 0));
            m.durationMs = o.optInt("duration_ms", o.optInt("duration", 0));
            if (m.durationMs > 0 && m.durationMs < 1000 && o.has("duration") && !o.has("duration_ms")) {
                m.durationMs *= 1000;
            }
            m.original = o.optBoolean("is_original", false);
            return m;
        }

        public boolean isImage() {
            return "image".equals(type) || "photo".equals(type) || "picture".equals(type);
        }

        public boolean isVideo() {
            return "video".equals(type) || "gif".equals(type);
        }

        public boolean isFile() {
            return "file".equals(type) || "pdf".equals(type) || "document".equals(type) || "audio".equals(type) || "voice".equals(type);
        }

        /** Playable file. Relative keys use media_base_url from GET /v1/feed/config. */
        public String displayUrl(int account) {
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                return url;
            }
            String path = url != null && url.length() > 0 ? url : key;
            return FeedConfig.mediaUrl(account, path);
        }

        /** Still image. Video uses cover_key when the config published a media base. */
        public String thumbUrl(int account) {
            if (coverKey != null && coverKey.length() > 0) {
                String cover = FeedConfig.mediaUrl(account, coverKey);
                if (cover.length() > 0) {
                    return cover;
                }
            }
            return displayUrl(account);
        }
    }

    public static class LinkPreview {
        public String url = "";
        public String title = "";
        public String description = "";
        public String image = "";

        static LinkPreview parse(JSONObject o) {
            if (o == null) {
                return null;
            }
            LinkPreview p = new LinkPreview();
            p.url = o.optString("url", "");
            p.title = o.optString("title", "");
            p.description = o.optString("description", "");
            p.image = o.optString("image", "");
            if (p.url.length() == 0) {
                return null;
            }
            return p;
        }
    }

    public static class PollOption {
        public String id = "";
        public String text = "";
        public int votes;
        public boolean chosen;

        static PollOption parse(JSONObject o, int index) {
            PollOption op = new PollOption();
            op.id = o.optString("id", String.valueOf(index));
            op.text = o.optString("text", o.optString("option", ""));
            op.votes = o.optInt("votes", 0);
            op.chosen = o.optBoolean("chosen", false);
            return op;
        }
    }

    public static class Poll {
        public String question = "";
        public ArrayList<PollOption> options = new ArrayList<>();
        public boolean multiple;
        public boolean anonymous = true;
        public long closesAt;
        public long closedAt;
        public boolean closedByAuthor;
        public int totalVoters;
        public boolean voted;
        public boolean canSeeResults;

        static Poll parse(JSONObject o) {
            if (o == null) {
                return null;
            }
            Poll p = new Poll();
            p.question = o.optString("question", "");
            p.multiple = o.optBoolean("multiple", false);
            p.anonymous = o.optBoolean("anonymous", true);
            p.closesAt = o.optLong("closes_at", 0);
            p.closedAt = o.optLong("closed_at", 0);
            p.closedByAuthor = o.optBoolean("closed_by_author", false);
            p.totalVoters = o.optInt("total_voters", o.optInt("voters", 0));
            p.voted = o.optBoolean("voted", false);
            p.canSeeResults = o.optBoolean("can_see_results", p.voted || p.closedAt > 0);
            JSONArray arr = o.optJSONArray("options");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item != null) {
                        p.options.add(PollOption.parse(item, i));
                        if (p.options.get(p.options.size() - 1).chosen) {
                            p.voted = true;
                        }
                    }
                }
            }
            return p;
        }

        public boolean closed() {
            if (closedAt > 0) {
                return true;
            }
            return closesAt > 0 && System.currentTimeMillis() >= closesAt;
        }
    }

    public static class Post {
        public String id = "";
        public int version;
        public boolean unavailable;
        public String kind = "post";
        public String title = "";
        public Author author = new Author();
        public String text = "";
        public ArrayList<Media> media = new ArrayList<>();
        public Poll poll;
        public LinkPreview linkPreview;
        public String quoteId = "";
        public Post quote;
        public String audienceKind = "public";
        public String commentPolicy = "everyone_who_can_view";
        public String cityCode = "";
        public String cityName = "";
        public String placeName = "";
        public String distanceBand = "";
        public boolean allowNearby;
        public boolean pinned;
        public String state = "published";
        public long editedAt;
        public long createdAt;
        public int likes;
        public int comments;
        public int reposts;
        public boolean liked;
        public boolean saved;
        public boolean following;
        public boolean mutual;
        public String repostedByName = "";
        public String feedReason = "";
        public int albumPage;
        public boolean hiddenLocal;

        public boolean isPublic() {
            return "public".equals(audienceKind);
        }

        public boolean hasImages() {
            for (int i = 0; i < media.size(); i++) {
                if (media.get(i).isImage()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasVideo() {
            for (int i = 0; i < media.size(); i++) {
                if (media.get(i).isVideo()) {
                    return true;
                }
            }
            return false;
        }

        public static Post unavailable(String id) {
            Post p = new Post();
            p.id = id == null ? "" : id;
            p.unavailable = true;
            p.state = "unavailable";
            return p;
        }

        public static Post parse(JSONObject o) {
            Post p = new Post();
            if (o == null) {
                p.unavailable = true;
                return p;
            }
            p.id = o.optString("id", "");
            p.feedReason = o.optString("reason", "");
            if (bareRef(o)) {
                p.unavailable = true;
                p.state = "unavailable";
                return p;
            }
            p.version = o.optInt("version", 0);
            String state = o.optString("state", "published");
            p.state = state;
            p.unavailable = o.optBoolean("unavailable", false) || "unavailable".equals(state) || "deleted".equals(state);
            if (p.unavailable && o.optString("text", "").length() == 0 && !o.has("author")) {
                return p;
            }
            p.kind = o.optString("type", o.optString("kind", "post"));
            if (p.kind.length() == 0) {
                p.kind = "post";
            }
            p.title = o.optString("title", "");
            p.author = Author.parse(o.optJSONObject("author"));
            if (o.has("author_name") && p.author.name.length() == 0) {
                p.author.name = o.optString("author_name", "");
            }
            p.text = firstText(o, "text", "body", "caption", "excerpt", "summary");
            JSONObject content = o.optJSONObject("content");
            if (content != null) {
                if (p.title.length() == 0) {
                    p.title = content.optString("title", "");
                }
                if (p.text.length() == 0) {
                    p.text = firstText(content, "text", "body", "markdown", "html");
                }
                absorbMedia(p, content.optJSONArray("media"));
            }
            JSONObject article = o.optJSONObject("article");
            if (article != null) {
                p.kind = "article";
                if (p.title.length() == 0) {
                    p.title = article.optString("title", "");
                }
                if (p.text.length() == 0) {
                    p.text = firstText(article, "text", "body", "excerpt", "html", "markdown");
                }
                absorbMedia(p, article.optJSONArray("media"));
                if (article.has("cover")) {
                    p.media.add(0, Media.parse(article.optJSONObject("cover")));
                }
            }
            JSONObject story = o.optJSONObject("story");
            if (story != null) {
                p.kind = "story";
                if (p.text.length() == 0) {
                    p.text = firstText(story, "text", "caption");
                }
                absorbMedia(p, story.optJSONArray("media"));
            }
            absorbMedia(p, o.optJSONArray("media"));
            absorbMedia(p, o.optJSONArray("attachments"));
            if (o.has("file") && !o.isNull("file")) {
                p.media.add(Media.parse(o.optJSONObject("file")));
            }
            p.poll = Poll.parse(o.optJSONObject("poll"));
            p.linkPreview = LinkPreview.parse(o.optJSONObject("link_preview"));
            if (p.linkPreview == null) {
                p.linkPreview = LinkPreview.parse(o.optJSONObject("link"));
            }
            if (p.linkPreview == null) {
                p.linkPreview = LinkPreview.parse(o.optJSONObject("preview"));
            }
            if (o.has("quote_of") && !o.isNull("quote_of")) {
                Object q = o.opt("quote_of");
                if (q instanceof String) {
                    p.quoteId = (String) q;
                } else if (q instanceof JSONObject) {
                    p.quote = parse((JSONObject) q);
                    p.quoteId = p.quote.id;
                }
            }
            if (o.has("quote") && !o.isNull("quote")) {
                p.quote = parse(o.optJSONObject("quote"));
            }
            JSONObject audience = o.optJSONObject("audience");
            if (audience != null) {
                p.audienceKind = audience.optString("kind", "public");
            } else {
                p.audienceKind = o.optString("audience", "public");
            }
            p.commentPolicy = o.optString("comment_policy", "everyone_who_can_view");
            JSONObject location = o.optJSONObject("location");
            if (location != null) {
                p.cityCode = location.optString("city", "");
                p.cityName = location.optString("city_name", location.optString("city_label", ""));
                JSONObject place = location.optJSONObject("place");
                if (place != null) {
                    p.placeName = place.optString("name", "");
                } else {
                    p.placeName = location.optString("place_name", "");
                }
                p.distanceBand = location.optString("distance_band", "");
                p.allowNearby = location.optBoolean("allow_nearby", false);
            }
            p.pinned = o.optBoolean("pinned", o.optLong("pinned_at", 0) > 0);
            p.editedAt = FeedContract.epochMillis(o.optLong("edited_at", 0));
            p.createdAt = FeedContract.epochMillis(o.optLong("created_at", 0));
            JSONObject counts = o.optJSONObject("counts");
            if (counts == null) {
                counts = o.optJSONObject("stats");
            }
            if (counts != null) {
                p.likes = counts.optInt("likes", counts.optInt("like_count", 0));
                p.comments = counts.optInt("comments", counts.optInt("comment_count", 0));
                p.reposts = counts.optInt("reposts", counts.optInt("repost_count", 0));
            } else {
                p.likes = o.optInt("likes", o.optInt("like_count", 0));
                p.comments = o.optInt("comments", o.optInt("comment_count", 0));
                p.reposts = o.optInt("reposts", o.optInt("repost_count", 0));
            }
            JSONObject viewer = o.optJSONObject("viewer");
            if (viewer != null) {
                p.liked = viewer.optBoolean("liked", viewer.optBoolean("liked_by_me", false));
                p.saved = viewer.optBoolean("saved", false);
                p.following = viewer.optBoolean("following", false);
                p.mutual = viewer.optBoolean("mutual", false);
            } else {
                p.liked = o.optBoolean("liked", false);
                p.saved = o.optBoolean("saved", false);
            }
            JSONObject reposted = o.optJSONObject("reposted_by");
            if (reposted != null) {
                p.repostedByName = reposted.optString("name", "");
            } else {
                p.repostedByName = o.optString("reposted_by_name", "");
            }
            return p;
        }

        private static void absorbMedia(Post post, JSONArray media) {
            if (post == null || media == null) {
                return;
            }
            for (int i = 0; i < media.length(); i++) {
                JSONObject item = media.optJSONObject(i);
                if (item != null) {
                    post.media.add(Media.parse(item));
                }
            }
        }

        private static String firstText(JSONObject o, String... keys) {
            if (o == null || keys == null) {
                return "";
            }
            for (int i = 0; i < keys.length; i++) {
                if (!o.has(keys[i]) || o.isNull(keys[i])) {
                    continue;
                }
                Object value = o.opt(keys[i]);
                if (value instanceof String && ((String) value).length() > 0) {
                    return (String) value;
                }
            }
            return "";
        }

        /** `{id, reason}` from a feed list, before POST /v1/feed/cards. */
        private static boolean bareRef(JSONObject o) {
            if (o == null || o.optString("id", "").length() == 0) {
                return false;
            }
            if (o.has("type")) {
                String type = o.optString("type", "");
                if (type.length() > 0 && !"post".equals(type)) {
                    return false;
                }
            }
            java.util.Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!"id".equals(key) && !"reason".equals(key) && !"type".equals(key)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class Comment {
        public String id = "";
        public String postId = "";
        public String rootId = "";
        public String parentId = "";
        public Author author = new Author();
        public String replyToName = "";
        public String text = "";
        public Media attachment;
        public long createdAt;
        public int likes;
        public boolean liked;
        public boolean disliked;
        /** True only when the payload includes a dislike field. */
        public boolean showDislike;
        public String province = "";
        public int replyCount;
        public ArrayList<Comment> replies = new ArrayList<>();
        public boolean unavailable;

        public static Comment parse(JSONObject o) {
            Comment c = new Comment();
            if (o == null) {
                c.unavailable = true;
                return c;
            }
            c.id = o.optString("id", "");
            c.postId = o.optString("post_id", "");
            c.rootId = o.optString("root_id", "");
            c.parentId = o.optString("parent_id", "");
            c.author = Author.parse(o.optJSONObject("author"));
            JSONObject replyTo = o.optJSONObject("reply_to_user");
            if (replyTo != null) {
                c.replyToName = replyTo.optString("name", replyTo.optString("username", ""));
            } else {
                c.replyToName = o.optString("reply_to_name", "");
            }
            c.text = o.optString("text", "");
            if (o.has("attachment") && !o.isNull("attachment")) {
                c.attachment = Media.parse(o.optJSONObject("attachment"));
            } else if (o.has("media_keys") && !o.isNull("media_keys")) {
                c.attachment = voiceFromKeys(o.opt("media_keys"));
            }
            c.createdAt = o.optLong("created_at", 0);
            c.likes = o.optInt("likes", o.optInt("like_count", 0));
            c.liked = o.optBoolean("liked", false);
            c.showDislike = o.has("disliked") || o.has("dislikes");
            c.disliked = o.optBoolean("disliked", false);
            c.province = firstText(o, "province", "ip_province", "ip_location", "region");
            c.replyCount = o.optInt("reply_count", 0);
            c.unavailable = o.optBoolean("unavailable", false);
            JSONArray replies = o.optJSONArray("replies");
            if (replies != null) {
                for (int i = 0; i < replies.length(); i++) {
                    JSONObject item = replies.optJSONObject(i);
                    if (item != null) {
                        c.replies.add(parse(item));
                    }
                }
                if (c.replyCount < c.replies.size()) {
                    c.replyCount = c.replies.size();
                }
            }
            return c;
        }

        private static String firstText(JSONObject o, String... keys) {
            for (int i = 0; i < keys.length; i++) {
                String value = o.optString(keys[i], "");
                if (value.length() > 0 && !"null".equals(value)) {
                    return value;
                }
            }
            return "";
        }

        private static Media voiceFromKeys(Object raw) {
            if (raw instanceof JSONArray) {
                JSONArray keys = (JSONArray) raw;
                if (keys.length() == 0) {
                    return null;
                }
                Object first = keys.opt(0);
                if (first instanceof String) {
                    JSONObject media = new JSONObject();
                    try {
                        media.put("type", "audio");
                        media.put("key", first);
                    } catch (Exception ignore) {
                    }
                    return Media.parse(media);
                }
                if (first instanceof JSONObject) {
                    return Media.parse((JSONObject) first);
                }
            }
            return null;
        }
    }

    public static class NotificationItem {
        public String id = "";
        public String kind = "";
        public Author actor = new Author();
        public String postId = "";
        public String commentId = "";
        public String text = "";
        public long createdAt;
        public boolean read;
        public ArrayList<Author> actors = new ArrayList<>();
        public int extraCount;

        public static NotificationItem parse(JSONObject o) {
            NotificationItem n = new NotificationItem();
            if (o == null) {
                return n;
            }
            n.id = o.optString("id", "");
            n.kind = o.optString("kind", "");
            n.actor = Author.parse(o.optJSONObject("actor"));
            n.postId = o.optString("post_id", "");
            n.commentId = o.optString("comment_id", "");
            n.text = o.optString("text", o.optString("summary", ""));
            n.createdAt = o.optLong("created_at", 0);
            n.read = o.optBoolean("read", false);
            n.extraCount = o.optInt("extra_count", 0);
            JSONArray actors = o.optJSONArray("actors");
            if (actors != null) {
                for (int i = 0; i < actors.length(); i++) {
                    JSONObject item = actors.optJSONObject(i);
                    if (item != null) {
                        n.actors.add(Author.parse(item));
                    }
                }
            }
            if (n.actors.isEmpty() && n.actor.profileId.length() + n.actor.name.length() > 0) {
                n.actors.add(n.actor);
            }
            return n;
        }
    }

    public static class Page {
        public ArrayList<Post> items = new ArrayList<>();
        public String nextCursor = "";
    }

    public static Page parsePage(JSONObject o) {
        Page page = new Page();
        if (o == null) {
            return page;
        }
        JSONArray items = o.optJSONArray("items");
        if (items == null) {
            items = o.optJSONArray("cards");
        }
        if (items == null) {
            items = o.optJSONArray("posts");
        }
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) {
                    page.items.add(Post.parse(item));
                }
            }
        }
        page.nextCursor = FeedContract.normalizeCursor(o.optString("next_cursor", o.optString("cursor", "")));
        return page;
    }
}
