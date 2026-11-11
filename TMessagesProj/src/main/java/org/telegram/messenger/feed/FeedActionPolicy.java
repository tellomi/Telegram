package org.telegram.messenger.feed;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Repost, quote, and share for one Moments card.
 *
 * The card's only capability bit is {@code shareable} (content-engine
 * cards.go:63, filled by SQL {@code shareable()} at cards.go:210).
 * {@code shareable()} (migration 0035_feed_v1.sql:372) is true when the row
 * is not a comment or story, is not deleted, {@code state} is {@code published},
 * and {@code author_only_pending} is false. It does not look at visibility.
 * {@code repostable()} (0035:380) is that bit and {@code visibility = public}.
 * Repost rejects with 404 {@code not_found} (interactions.go:303). Quote
 * rejects with 400 {@code quote_unavailable} (posts.go:545-550).
 *
 * Copy link and external share follow {@code shareable} alone. Chat-share
 * also requires public or the friends / mutual tier. There is no engine
 * route for those three; the mutual chat exception is the product rule.
 *
 * Edit stays on the viewer's own post. {@code EditWindow} is 0 (posts.go:41),
 * so a missing or zero {@code limits.edit_window_seconds} does not hide it.
 * A positive window hides Edit after that many seconds from {@code created_at}.
 */
public final class FeedActionPolicy {

    public enum Action {
        REPOST,
        QUOTE,
        CHAT_SHARE,
        COPY_LINK,
        EXTERNAL_SHARE,
        EDIT,
        CHANGE_AUDIENCE
    }

    public static final String REASON_REPOST = "Moments_RepostBlocked";
    public static final String REASON_REPOST_MUTUAL = "Moments_RepostBlocked_Mutual";
    public static final String REASON_SHARE = "Moments_ShareBlocked";
    public static final String REASON_UNAVAILABLE = "Moments_Unavailable";
    public static final String REASON_QUOTE_UNAVAILABLE = "Moments_QuoteUnavailable";
    public static final String REASON_QUOTE_INVALID = "Moments_QuoteInvalid";

    /** 0 matches posts.go EditWindow: edit is not limited. */
    private static volatile int editWindowSeconds;

    public static final class Input {
        public String visibility = "";
        public String state = "published";
        public String kind = "post";
        public boolean unavailable;
        public boolean authorOnlyPending;
        /** Null when the card omitted {@code shareable}. */
        public Boolean shareable;
        public boolean own;
        public long createdAtMillis;
        public long nowMillis;
        public int editWindowSeconds;
    }

    public static final class Decision {
        public final boolean allowed;
        /** Moments langpack key. Empty when {@link #allowed} is true, or when the row is simply hidden. */
        public final String reasonKey;

        public Decision(boolean allowed, String reasonKey) {
            this.allowed = allowed;
            this.reasonKey = reasonKey == null ? "" : reasonKey;
        }

        static Decision yes() {
            return new Decision(true, "");
        }

        static Decision no(String reasonKey) {
            return new Decision(false, reasonKey);
        }
    }

    private FeedActionPolicy() {
    }

    public static int editWindowSeconds() {
        return editWindowSeconds;
    }

    /** {@code GET /v1/feed/config} {@code limits.edit_window_seconds}. A body without the key keeps the last value. */
    public static void noteConfig(String configJson) {
        JsonObject config = object(configJson);
        if (config == null) {
            return;
        }
        if (config.has("limits") && config.get("limits").isJsonObject()) {
            JsonObject limits = config.getAsJsonObject("limits");
            Integer window = integer(limits, "edit_window_seconds");
            if (window != null) {
                editWindowSeconds = window;
                return;
            }
        }
        Integer top = integer(config, "edit_window_seconds");
        if (top != null) {
            editWindowSeconds = top;
        }
    }

    /** Test hook. Production leaves the value at 0 until config arrives. */
    public static void setEditWindowSecondsForTest(int seconds) {
        editWindowSeconds = Math.max(0, seconds);
    }

    public static boolean tombstone(boolean unavailable, String state) {
        return unavailable || "unavailable".equals(state) || "deleted".equals(state);
    }

    /** Engine visibility, or the card alias mapped onto it. */
    public static String canonical(String visibility) {
        if (visibility == null) {
            return "";
        }
        if ("everyone".equals(visibility) || "all".equals(visibility)) {
            return "public";
        }
        if ("mutual".equals(visibility) || "mutual_followers".equals(visibility)) {
            return "friends";
        }
        if ("selected".equals(visibility)) {
            return "include";
        }
        if ("excluded".equals(visibility)) {
            return "exclude";
        }
        if ("private".equals(visibility) || "self".equals(visibility)) {
            return "only_me";
        }
        return visibility;
    }

    public static boolean mutualTier(String visibility) {
        return "friends".equals(canonical(visibility));
    }

    public static boolean publicTier(String visibility) {
        return "public".equals(canonical(visibility));
    }

    /**
     * Top-level card field only. Nested copies and other names are ignored.
     * Null when the card did not include {@code shareable}.
     */
    public static Boolean readShareable(String postJson) {
        JsonObject post = object(postJson);
        if (post == null || !post.has("shareable") || post.get("shareable").isJsonNull()) {
            return null;
        }
        return bool(post.get("shareable"));
    }

    public static Decision decide(Input input, Action action) {
        Input card = input == null ? new Input() : input;
        boolean gone = tombstone(card.unavailable, card.state);
        if (action == Action.EDIT || action == Action.CHANGE_AUDIENCE) {
            if (gone) {
                return Decision.no(REASON_UNAVAILABLE);
            }
            if (!card.own) {
                return Decision.no("");
            }
            if (action == Action.EDIT && !editOpen(card)) {
                return Decision.no("");
            }
            return Decision.yes();
        }
        if (gone || !shareable(card)) {
            if (gone) {
                return Decision.no(REASON_UNAVAILABLE);
            }
            if (action == Action.REPOST || action == Action.QUOTE) {
                return Decision.no(repostReason(card.visibility));
            }
            return Decision.no(REASON_SHARE);
        }
        if (action == Action.COPY_LINK || action == Action.EXTERNAL_SHARE) {
            return Decision.yes();
        }
        if (action == Action.CHAT_SHARE) {
            if (publicTier(card.visibility) || mutualTier(card.visibility)) {
                return Decision.yes();
            }
            return Decision.no(REASON_SHARE);
        }
        if (publicTier(card.visibility) && "published".equals(card.state)) {
            return Decision.yes();
        }
        return Decision.no(repostReason(card.visibility));
    }

    /** 404 from {@code PUT /repost} is {@code not_found} (interactions.go:303), not a shareable code. */
    public static String reasonKeyForRepostError(String error) {
        if (error == null || error.length() == 0) {
            return "";
        }
        if ("not_found".equals(error) || "http_404".equals(error)) {
            return REASON_REPOST;
        }
        return reasonKeyForError(error);
    }

    /**
     * Quote body errors. {@code quote_unavailable} is posts.go:545-550.
     * {@code not_found} stays the unavailable string; repost uses
     * {@link #reasonKeyForRepostError}.
     */
    public static String reasonKeyForError(String error) {
        if (error == null || error.length() == 0) {
            return "";
        }
        String code = error.toLowerCase(java.util.Locale.US);
        if (code.contains("invalid_quote")) {
            return REASON_QUOTE_INVALID;
        }
        if (code.contains("quote_unavailable")) {
            return REASON_QUOTE_UNAVAILABLE;
        }
        if ("not_found".equals(code) || code.contains("unavailable") || code.contains("deleted")) {
            return REASON_UNAVAILABLE;
        }
        return "";
    }

    /**
     * SQL {@code shareable()} when the card omitted the bit. A present bit wins, including false.
     * The author's own only-me card is {@code ownOnlyMe} (cards.go:324) and does not send
     * {@code shareable:true}. For that published post the SQL predicate is still true, so copy
     * link and external share stay allowed. Repost and quote still need public visibility.
     */
    static boolean shareable(Input card) {
        if (tombstone(card.unavailable, card.state)) {
            return false;
        }
        if (card.shareable != null) {
            return card.shareable;
        }
        if (card.authorOnlyPending) {
            return false;
        }
        if (!"published".equals(card.state)) {
            return false;
        }
        if ("comment".equals(card.kind) || "story".equals(card.kind)) {
            return false;
        }
        return true;
    }

    private static boolean editOpen(Input card) {
        if (card.editWindowSeconds <= 0) {
            return true;
        }
        if (card.createdAtMillis <= 0) {
            return true;
        }
        long now = card.nowMillis > 0 ? card.nowMillis : card.createdAtMillis;
        return now - card.createdAtMillis <= card.editWindowSeconds * 1000L;
    }

    private static String repostReason(String visibility) {
        if (mutualTier(visibility)) {
            return REASON_REPOST_MUTUAL;
        }
        return REASON_REPOST;
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
        } catch (RuntimeException ignore) {
        }
        return null;
    }

    private static Integer integer(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement value = object.get(key);
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                return Math.max(0, value.getAsInt());
            }
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                return Math.max(0, Integer.parseInt(value.getAsString().trim()));
            }
        } catch (RuntimeException ignore) {
        }
        return null;
    }

    private static Boolean bool(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return value.getAsInt() != 0;
        }
        if (value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
