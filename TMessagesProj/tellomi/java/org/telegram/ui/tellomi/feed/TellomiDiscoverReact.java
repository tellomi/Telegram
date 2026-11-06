package org.telegram.ui.tellomi.feed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Discover likes and public text comments.
 *
 * A like keeps one desired state. A response whose generation does not match
 * the post on screen is dropped, the same way a chat comment page is dropped
 * when {@code commentMessagesLoadingGuid} moves on. This does not call
 * {@code stories.sendReaction} or {@code SendMessagesHelper}.
 */
public final class TellomiDiscoverReact {

    public static final int COMMENT_TEXT_LIMIT = 500;
    public static final int COMMENT_PAGE = 50;

    private TellomiDiscoverReact() {
    }

    public static boolean feature(TellomiDiscover.Capabilities capabilities, String name) {
        return capabilities != null && name != null && capabilities.features != null && capabilities.features.contains(name);
    }

    /** {@code recommend} unless the value is one of the engine surfaces. Nearby is not one of them. */
    public static String surface(String raw) {
        if ("following".equals(raw) || "hot".equals(raw) || "search".equals(raw) || "profile".equals(raw)) {
            return raw;
        }
        return "recommend";
    }

    public static String surface(TellomiDiscover.FeedKind kind) {
        if (kind == TellomiDiscover.FeedKind.FOLLOWING) {
            return "following";
        }
        return "recommend";
    }

    /** A 401 with {@code credential_expired}, or a 401 that names no error, may refresh the discover token once. */
    public static boolean refetchCredential(TellomiDiscover.HttpResult result) {
        if (result == null || result.status != 401) {
            return false;
        }
        String code = TellomiDiscoverParse.errorCode(result.body);
        return code == null || code.isEmpty() || "credential_expired".equals(code);
    }

    public static TellomiDiscover.ClientError commentError(String text, String parentId, String nonce) {
        String body = text == null ? "" : text.trim();
        int length = body.codePointCount(0, body.length());
        if (length < 1 || length > COMMENT_TEXT_LIMIT) {
            return TellomiDiscover.ClientError.INVALID_TEXT;
        }
        if (parentId != null && !parentId.isEmpty() && !TellomiDiscover.clientNonce(parentId)) {
            return TellomiDiscover.ClientError.INVALID_PARENT;
        }
        if (!TellomiDiscover.clientNonce(nonce)) {
            return TellomiDiscover.ClientError.INVALID_NONCE;
        }
        return null;
    }

    /** {@code text}, optional {@code parent_id}, {@code client_nonce}. No {@code media_keys} and no {@code operation_id}. */
    public static byte[] commentBody(String text, String parentId, String nonce) {
        String parent = parentId == null ? null : parentId.trim();
        if (parent != null && parent.isEmpty()) {
            parent = null;
        }
        String body = text == null ? "" : text.trim();
        if (commentError(body, parent, nonce) != null) {
            return null;
        }
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("text", body);
        if (parent != null) {
            object.put("parent_id", parent);
        }
        object.put("client_nonce", nonce);
        return TellomiJson.write(object);
    }

    public static TellomiDiscover.Outcome decode(TellomiDiscover.Operation operation, TellomiDiscover.HttpResult result) {
        if (operation == null || !react(operation.kind)) {
            return null;
        }
        if (result == null) {
            return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAVAILABLE);
        }
        String code = TellomiDiscoverParse.errorCode(result.body);
        TellomiDiscover.ClientError mapped = mapped(result.status, code);
        if (mapped != null) {
            return TellomiDiscover.Outcome.failure(mapped);
        }
        if (result.status == 401) {
            return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAUTHORIZED);
        }
        if (result.status == 403) {
            return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAUTHORIZED);
        }
        if (operation.kind == TellomiDiscover.Operation.Kind.LIKE || operation.kind == TellomiDiscover.Operation.Kind.UNLIKE || operation.kind == TellomiDiscover.Operation.Kind.DELETE_COMMENT) {
            if (result.status == 204) {
                return TellomiDiscover.Outcome.success(TellomiDiscover.Decoded.done());
            }
            if (result.status == 404) {
                return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.NOT_FOUND);
            }
            return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAVAILABLE);
        }
        if (operation.kind == TellomiDiscover.Operation.Kind.COMMENTS) {
            if (result.status != 200) {
                return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAVAILABLE);
            }
            String postId = operation.ids == null || operation.ids.isEmpty() ? null : operation.ids.get(0);
            TellomiDiscover.CommentPage page = TellomiDiscoverParse.commentPage(result.body, postId);
            return page == null ? TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNREADABLE) : TellomiDiscover.Outcome.success(TellomiDiscover.Decoded.commentPage(page));
        }
        if (result.status != 200 && result.status != 201) {
            return TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAVAILABLE);
        }
        TellomiDiscover.CommentCreated created = TellomiDiscoverParse.commentCreated(result.body);
        return created == null ? TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNREADABLE) : TellomiDiscover.Outcome.success(TellomiDiscover.Decoded.commentCreated(created));
    }

    public static String messageKey(TellomiDiscover.ClientError error) {
        if (error == TellomiDiscover.ClientError.PROFILE_REQUIRED) {
            return "Tellomi.Discover.ProfileRequired";
        }
        if (error == TellomiDiscover.ClientError.INVALID_TEXT) {
            return "Tellomi.Discover.InvalidText";
        }
        if (error == TellomiDiscover.ClientError.INVALID_NONCE) {
            return "Tellomi.Discover.InvalidNonce";
        }
        if (error == TellomiDiscover.ClientError.INVALID_PARENT) {
            return "Tellomi.Discover.InvalidParent";
        }
        if (error == TellomiDiscover.ClientError.COMMENTS_CLOSED) {
            return "Tellomi.Discover.CommentsClosed";
        }
        if (error == TellomiDiscover.ClientError.REPLY_NOT_ALLOWED) {
            return "Tellomi.Discover.ReplyNotAllowed";
        }
        if (error == TellomiDiscover.ClientError.HARASSMENT_GUARD) {
            return "Tellomi.Discover.HarassmentGuard";
        }
        if (error == TellomiDiscover.ClientError.NOT_COMMENTABLE) {
            return "Tellomi.Discover.NotCommentable";
        }
        if (error == TellomiDiscover.ClientError.BAD_CURSOR) {
            return "Tellomi.Discover.BadCursor";
        }
        if (error == TellomiDiscover.ClientError.NOT_FOUND) {
            return "Tellomi.Discover.Unavailable";
        }
        if (error == TellomiDiscover.ClientError.HIDDEN) {
            return "Tellomi.Discover.Disabled";
        }
        return "Tellomi.Discover.Failed";
    }

    private static boolean react(TellomiDiscover.Operation.Kind kind) {
        return kind == TellomiDiscover.Operation.Kind.LIKE
            || kind == TellomiDiscover.Operation.Kind.UNLIKE
            || kind == TellomiDiscover.Operation.Kind.COMMENTS
            || kind == TellomiDiscover.Operation.Kind.COMMENT
            || kind == TellomiDiscover.Operation.Kind.DELETE_COMMENT;
    }

    private static TellomiDiscover.ClientError mapped(int status, String code) {
        if (code == null) {
            return null;
        }
        if (status == 404 && "feed_hidden".equals(code)) {
            return TellomiDiscover.ClientError.HIDDEN;
        }
        if (status == 409 && "profile_required".equals(code)) {
            return TellomiDiscover.ClientError.PROFILE_REQUIRED;
        }
        if (status == 400 && "invalid_text".equals(code)) {
            return TellomiDiscover.ClientError.INVALID_TEXT;
        }
        if (status == 400 && "client_nonce".equals(code)) {
            return TellomiDiscover.ClientError.INVALID_NONCE;
        }
        if (status == 400 && "parent".equals(code)) {
            return TellomiDiscover.ClientError.INVALID_PARENT;
        }
        if (status == 400 && ("cursor".equals(code) || "invalid_cursor".equals(code))) {
            return TellomiDiscover.ClientError.BAD_CURSOR;
        }
        if (status == 403 && "comments_closed".equals(code)) {
            return TellomiDiscover.ClientError.COMMENTS_CLOSED;
        }
        if (status == 403 && "reply_not_allowed".equals(code)) {
            return TellomiDiscover.ClientError.REPLY_NOT_ALLOWED;
        }
        if (status == 403 && "harassment_guard".equals(code)) {
            return TellomiDiscover.ClientError.HARASSMENT_GUARD;
        }
        if (status == 403 && "not_commentable".equals(code)) {
            return TellomiDiscover.ClientError.NOT_COMMENTABLE;
        }
        if (status == 404 && "not_found".equals(code)) {
            return TellomiDiscover.ClientError.NOT_FOUND;
        }
        return null;
    }

    /** One desired like. A failed call restores the last confirmed count. */
    public static final class Like {
        public static final class View {
            public final boolean liked;
            public final boolean hidden;
            public final int count;

            View(boolean liked, boolean hidden, int count) {
                this.liked = liked;
                this.hidden = hidden;
                this.count = count;
            }
        }

        public static final class Request {
            public final int generation;
            public final int version;
            public final String postId;
            public final boolean on;
            public final String surface;
            /** Tap count when this request was built. Later taps make a newer intent. */
            final int intentVersion;
            /** One idempotent follow-up after an uncertain failure. Never a 403 or 404 retry. */
            final boolean automatic;

            Request(int generation, int version, String postId, boolean on, String surface, int intentVersion, boolean automatic) {
                this.generation = generation;
                this.version = version;
                this.postId = postId;
                this.on = on;
                this.surface = surface;
                this.intentVersion = intentVersion;
                this.automatic = automatic;
            }
        }

        private String postId;
        private String surface = "recommend";
        private int generation = 1;
        private int nextVersion = 1;
        private int intentVersion;
        private boolean confirmedLiked;
        private boolean targetLiked;
        private int confirmedCount;
        private boolean hidden;
        private Request inflight;
        private TellomiDiscover.ClientError failure;

        public static Like start(String postId, boolean liked, int count, boolean hidden, String surface) {
            Like like = new Like();
            like.postId = postId;
            like.confirmedLiked = liked;
            like.targetLiked = liked;
            like.confirmedCount = Math.max(0, count);
            like.hidden = hidden;
            like.surface = TellomiDiscoverReact.surface(surface);
            return like;
        }

        public boolean idle() {
            return inflight == null && targetLiked == confirmedLiked;
        }

        public TellomiDiscover.ClientError failure() {
            return failure;
        }

        public int generation() {
            return generation;
        }

        /** The page went away. A response for the old generation does not change this like. */
        public void close() {
            generation++;
            inflight = null;
            targetLiked = confirmedLiked;
            failure = null;
        }

        /** A freshly loaded card may replace an idle like. An in-flight tap is left alone. */
        public void adopt(boolean liked, int count, boolean likesHidden) {
            if (!idle()) {
                return;
            }
            confirmedLiked = liked;
            targetLiked = liked;
            confirmedCount = Math.max(0, count);
            hidden = likesHidden;
        }

        public void bind(String postId, boolean liked, int count, boolean likesHidden, String surface) {
            this.surface = TellomiDiscoverReact.surface(surface);
            if (postId != null && postId.equals(this.postId)) {
                adopt(liked, count, likesHidden);
                return;
            }
            generation++;
            inflight = null;
            this.postId = postId;
            confirmedLiked = liked;
            targetLiked = liked;
            confirmedCount = Math.max(0, count);
            hidden = likesHidden;
            this.surface = TellomiDiscoverReact.surface(surface);
        }

        public View view() {
            int count = confirmedCount;
            if (!hidden) {
                if (targetLiked && !confirmedLiked) {
                    count++;
                }
                if (!targetLiked && confirmedLiked) {
                    count = Math.max(0, count - 1);
                }
            }
            return new View(targetLiked, hidden, count);
        }

        public Request tap() {
            intentVersion++;
            targetLiked = !targetLiked;
            failure = null;
            if (inflight != null) {
                return null;
            }
            return send(false);
        }

        public Request complete(int generation, int version, boolean ok) {
            return finish(generation, version, ok ? null : TellomiDiscover.ClientError.UNAVAILABLE);
        }

        /**
         * {@code error} null is success. A newer tap during the flight is kept.
         * An uncertain failure of that newer intent sends one idempotent like or unlike.
         * 403 and 404 do not send again. The confirmed count moves only on success.
         */
        public Request finish(int generation, int version, TellomiDiscover.ClientError error) {
            if (this.generation != generation || inflight == null || inflight.version != version) {
                return null;
            }
            Request sent = inflight;
            boolean sentOn = sent.on;
            inflight = null;
            if (error == null) {
                if (!hidden) {
                    if (sentOn && !confirmedLiked) {
                        confirmedCount++;
                    }
                    if (!sentOn && confirmedLiked) {
                        confirmedCount = Math.max(0, confirmedCount - 1);
                    }
                }
                confirmedLiked = sentOn;
                failure = null;
                if (targetLiked != confirmedLiked) {
                    return send(false);
                }
                return null;
            }
            if (permanent(error) || sent.automatic || sent.intentVersion == intentVersion) {
                targetLiked = confirmedLiked;
                failure = error;
                return null;
            }
            failure = null;
            return send(true);
        }

        private Request send(boolean automatic) {
            inflight = new Request(generation, nextVersion++, postId, targetLiked, surface, intentVersion, automatic);
            return inflight;
        }
    }

    /** List and detail both apply a like response through here. */
    public static Like.Request onLikeHttp(Like like, Like.Request request, TellomiDiscover.Outcome outcome) {
        if (like == null || request == null || request.generation != like.generation()) {
            return null;
        }
        TellomiDiscover.ClientError error = null;
        if (outcome == null || !outcome.ok()) {
            error = outcome == null || outcome.error == null ? TellomiDiscover.ClientError.UNAVAILABLE : outcome.error;
        }
        return like.finish(request.generation, request.version, error);
    }

    public static boolean permanentLikeFailure(TellomiDiscover.ClientError error) {
        return permanent(error);
    }

    private static boolean permanent(TellomiDiscover.ClientError error) {
        return error == TellomiDiscover.ClientError.NOT_FOUND
            || error == TellomiDiscover.ClientError.HIDDEN
            || error == TellomiDiscover.ClientError.UNAUTHORIZED
            || error == TellomiDiscover.ClientError.PROFILE_REQUIRED
            || error == TellomiDiscover.ClientError.INVALID_TEXT
            || error == TellomiDiscover.ClientError.INVALID_NONCE
            || error == TellomiDiscover.ClientError.INVALID_PARENT
            || error == TellomiDiscover.ClientError.COMMENTS_CLOSED
            || error == TellomiDiscover.ClientError.REPLY_NOT_ALLOWED
            || error == TellomiDiscover.ClientError.HARASSMENT_GUARD
            || error == TellomiDiscover.ClientError.NOT_COMMENTABLE
            || error == TellomiDiscover.ClientError.BAD_CURSOR;
    }

    /** Public comments for one post. Pending rows stay out of {@link #publicCount()}. */
    public static final class Comments {
        public static final class Load {
            public final int generation;
            public final int version;
            public final String postId;
            public final String cursor;

            Load(int generation, int version, String postId, String cursor) {
                this.generation = generation;
                this.version = version;
                this.postId = postId;
                this.cursor = cursor;
            }
        }

        public static final class Send {
            public final int generation;
            public final int version;
            public final String postId;
            public final String text;
            public final String parentId;
            public final String nonce;
            public final TellomiDiscover.ClientError error;

            private Send(int generation, int version, String postId, String text, String parentId, String nonce, TellomiDiscover.ClientError error) {
                this.generation = generation;
                this.version = version;
                this.postId = postId;
                this.text = text;
                this.parentId = parentId;
                this.nonce = nonce;
                this.error = error;
            }

            public boolean ready() {
                return error == null;
            }

            static Send local(TellomiDiscover.ClientError error) {
                return new Send(0, 0, null, null, null, null, error);
            }
        }

        public static final class Delete {
            public final int generation;
            public final int version;
            public final String commentId;

            Delete(int generation, int version, String commentId) {
                this.generation = generation;
                this.version = version;
                this.commentId = commentId;
            }
        }

        private String postId;
        private int generation = 1;
        private int nextVersion = 1;
        private boolean loading;
        private int loadVersion;
        private String cursor;
        private boolean ended;
        private boolean writing;
        private int writeVersion;
        private String lastText = "";
        private String lastParent;
        private String lastNonce;
        private boolean lastSucceeded = true;
        private final Map<String, TellomiDiscover.Comment> visible = new LinkedHashMap<>();
        private final Map<String, TellomiDiscover.Comment> pending = new LinkedHashMap<>();
        private final Set<String> own = new LinkedHashSet<>();

        public Load open(String postId) {
            generation++;
            this.postId = postId;
            visible.clear();
            pending.clear();
            own.clear();
            cursor = null;
            ended = false;
            loading = false;
            writing = false;
            lastText = "";
            lastParent = null;
            lastNonce = null;
            lastSucceeded = true;
            return load(null);
        }

        public Load more() {
            if (loading || ended || cursor == null) {
                return null;
            }
            return load(cursor);
        }

        private Load load(String pageCursor) {
            loading = true;
            loadVersion = nextVersion++;
            return new Load(generation, loadVersion, postId, pageCursor);
        }

        public boolean accept(int generation, int version, TellomiDiscover.CommentPage page) {
            if (this.generation != generation || !loading || version != loadVersion || page == null) {
                return false;
            }
            loading = false;
            for (int i = 0; i < page.comments.size(); i++) {
                TellomiDiscover.Comment comment = page.comments.get(i);
                if (comment == null || visible.containsKey(comment.id) || pending.containsKey(comment.id)) {
                    continue;
                }
                if (comment.postId != null && this.postId != null && !this.postId.equals(comment.postId)) {
                    continue;
                }
                if (comment.state != null && !"ok".equals(comment.state)) {
                    continue;
                }
                visible.put(comment.id, comment);
                if (comment.own) {
                    own.add(comment.id);
                }
            }
            if (page.cursor == null || page.cursor.isEmpty()) {
                ended = true;
                this.cursor = null;
            } else {
                this.cursor = page.cursor;
                ended = false;
            }
            return true;
        }

        public void failLoad(int generation, int version) {
            if (this.generation != generation || version != loadVersion) {
                return;
            }
            loading = false;
        }

        public int publicCount() {
            return visible.size();
        }

        public List<TellomiDiscover.Comment> visible() {
            return new ArrayList<>(visible.values());
        }

        public List<TellomiDiscover.Comment> pendingRows() {
            return new ArrayList<>(pending.values());
        }

        public boolean hasMore() {
            return !ended && cursor != null && !loading;
        }

        public boolean canDelete(String commentId) {
            return commentId != null && own.contains(commentId);
        }

        public String replyTo(TellomiDiscover.Comment comment) {
            if (comment == null || comment.id == null || !visible.containsKey(comment.id)) {
                return null;
            }
            if (comment.postId != null && postId != null && !postId.equals(comment.postId)) {
                return null;
            }
            if (comment.state != null && !"ok".equals(comment.state)) {
                return null;
            }
            return comment.id;
        }

        public Send submit(String text, String parentId, String freshNonce) {
            if (writing) {
                return null;
            }
            String parent = parentId == null ? null : parentId.trim();
            if (parent != null && parent.isEmpty()) {
                parent = null;
            }
            String body = text == null ? "" : text.trim();
            if (parent != null && !visible.containsKey(parent)) {
                return Send.local(TellomiDiscover.ClientError.INVALID_PARENT);
            }
            boolean same = body.equals(lastText) && (parent == null ? lastParent == null : parent.equals(lastParent));
            String nonce = !lastSucceeded && same && lastNonce != null ? lastNonce : freshNonce;
            TellomiDiscover.ClientError error = commentError(body, parent, nonce);
            if (error != null) {
                return Send.local(error);
            }
            writing = true;
            writeVersion = nextVersion++;
            lastText = body;
            lastParent = parent;
            lastNonce = nonce;
            lastSucceeded = false;
            return new Send(generation, writeVersion, postId, body, parent, nonce, null);
        }

        public boolean finishSend(int generation, int version, TellomiDiscover.CommentCreated created) {
            if (this.generation != generation || !writing || version != writeVersion || created == null) {
                return false;
            }
            writing = false;
            lastSucceeded = true;
            String text = lastText;
            String parent = lastParent;
            lastNonce = null;
            own.add(created.id);
            if (!"ok".equals(created.state)) {
                pending.put(created.id, new TellomiDiscover.Comment(created.id, text, created.state, "", postId, null, parent, true));
                return true;
            }
            if (!visible.containsKey(created.id)) {
                visible.put(created.id, new TellomiDiscover.Comment(created.id, text, "ok", "", postId, null, parent, true));
            }
            return true;
        }

        public void failSend(int generation, int version) {
            if (this.generation != generation || version != writeVersion) {
                return;
            }
            writing = false;
        }

        public Delete delete(String commentId) {
            if (writing || !canDelete(commentId)) {
                return null;
            }
            writing = true;
            writeVersion = nextVersion++;
            return new Delete(generation, writeVersion, commentId);
        }

        public boolean finishDelete(int generation, int version, String commentId, boolean ok) {
            if (this.generation != generation || !writing || version != writeVersion) {
                return false;
            }
            writing = false;
            if (!ok || commentId == null) {
                return false;
            }
            visible.remove(commentId);
            pending.remove(commentId);
            own.remove(commentId);
            return true;
        }
    }
}
