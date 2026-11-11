package org.telegram.messenger.feed;

import org.json.JSONObject;

/**
 * Which post actions the viewer may take.
 *
 * A per-post flag from the engine wins. The card decoder reads
 * {@code can_repost}, {@code can_quote}, {@code can_chat_share},
 * {@code can_copy_link}, {@code can_external_share}, and {@code shareable}
 * on the post or on {@code viewer}, and the same names inside {@code actions}.
 * The iOS card decoder on release/ios only reads {@code viewer.can_comment},
 * so those flags are optional. When a flag is absent the decision mirrors
 * the engine visibility list the client already sends
 * ({@code public}, {@code friends}, {@code contacts}, {@code close_friends},
 * {@code include}, {@code exclude}, {@code only_me}) and the shareable rule:
 * only a public post may be reposted, quoted, copied, or shared outside a chat.
 * {@code friends} is that list's mutual tier. {@code mutual_followers} is the
 * card alias publish already maps to {@code friends}. Mutual posts may be
 * shared into a Telegram chat. {@code shareable: false} does not take that
 * chat action away unless {@code can_chat_share} is present and false.
 *
 * Author edit is {@code PATCH /v1/feed/posts/{id}}. Changing who can view is
 * {@code PUT /v1/feed/posts/{id}/audience}. Both stay on the viewer's own
 * post unless the card is unavailable or deleted.
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

    public static final class Flags {
        public static final Flags NONE = new Flags(false, null, null, null, null, null, null);

        public final boolean present;
        /** Null when the payload did not say. */
        public final Boolean repost;
        public final Boolean quote;
        public final Boolean chatShare;
        public final Boolean copyLink;
        public final Boolean externalShare;
        /** Broad engine bit. Does not by itself turn off mutual chat-share. */
        public final Boolean shareable;

        public Flags(boolean present, Boolean repost, Boolean quote, Boolean chatShare, Boolean copyLink, Boolean externalShare, Boolean shareable) {
            this.present = present;
            this.repost = repost;
            this.quote = quote;
            this.chatShare = chatShare;
            this.copyLink = copyLink;
            this.externalShare = externalShare;
            this.shareable = shareable;
        }
    }

    public static final class Decision {
        public final boolean allowed;
        /** Moments langpack key. Empty when {@link #allowed} is true. */
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

    public static Decision decide(String visibility, String state, boolean unavailable, boolean own, Action action, Flags flags) {
        if (action == Action.EDIT || action == Action.CHANGE_AUDIENCE) {
            if (tombstone(unavailable, state)) {
                return Decision.no(REASON_UNAVAILABLE);
            }
            return own ? Decision.yes() : Decision.no("");
        }
        if (flags == null) {
            flags = Flags.NONE;
        }
        Boolean specific = specific(flags, action);
        if (specific != null) {
            return specific ? Decision.yes() : Decision.no(denyReason(visibility, unavailable, state, action));
        }
        if (flags.shareable != null) {
            if (action == Action.CHAT_SHARE && !flags.shareable && mutualTier(visibility) && !tombstone(unavailable, state)) {
                return Decision.yes();
            }
            if (!flags.shareable || tombstone(unavailable, state)) {
                return Decision.no(denyReason(visibility, unavailable, state, action));
            }
            return Decision.yes();
        }
        if (tombstone(unavailable, state)) {
            return Decision.no(REASON_UNAVAILABLE);
        }
        return mirror(visibility, action);
    }

    /**
     * Engine error code to a Moments key. Empty when the client should keep
     * the server text. {@code quote_unavailable} and {@code invalid_quote}
     * are the quote validator's codes.
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
        if (code.contains("not_shareable") || code.contains("not shareable")) {
            return REASON_REPOST;
        }
        if ("not_found".equals(code) || code.contains("unavailable") || code.contains("deleted")) {
            return REASON_UNAVAILABLE;
        }
        return "";
    }

    public static Flags readFlags(JSONObject post) {
        if (post == null) {
            return Flags.NONE;
        }
        JSONObject viewer = post.optJSONObject("viewer");
        JSONObject actions = post.optJSONObject("actions");
        JSONObject viewerActions = viewer == null ? null : viewer.optJSONObject("actions");
        Boolean repost = first(bool(actions, "repost"), bool(viewerActions, "repost"), bool(post, "can_repost"), bool(viewer, "can_repost"));
        Boolean quote = first(bool(actions, "quote"), bool(viewerActions, "quote"), bool(post, "can_quote"), bool(viewer, "can_quote"));
        Boolean chat = first(bool(actions, "chat_share"), bool(viewerActions, "chat_share"), bool(post, "can_chat_share"), bool(viewer, "can_chat_share"));
        Boolean copy = first(bool(actions, "copy_link"), bool(viewerActions, "copy_link"), bool(post, "can_copy_link"), bool(viewer, "can_copy_link"));
        Boolean external = first(bool(actions, "external_share"), bool(viewerActions, "external_share"), bool(post, "can_external_share"), bool(viewer, "can_external_share"));
        Boolean shareable = first(bool(post, "shareable"), bool(viewer, "shareable"), bool(post, "can_share"), bool(viewer, "can_share"));
        boolean present = repost != null || quote != null || chat != null || copy != null || external != null || shareable != null;
        if (!present) {
            return Flags.NONE;
        }
        return new Flags(true, repost, quote, chat, copy, external, shareable);
    }

    private static Decision mirror(String visibility, Action action) {
        String kind = canonical(visibility);
        boolean mutual = "friends".equals(kind);
        boolean allowed;
        if ("public".equals(kind)) {
            allowed = true;
        } else if (mutual && action == Action.CHAT_SHARE) {
            allowed = true;
        } else {
            allowed = false;
        }
        if (allowed) {
            return Decision.yes();
        }
        return Decision.no(mirrorReason(kind, action));
    }

    private static String mirrorReason(String canonicalKind, Action action) {
        if ("friends".equals(canonicalKind) && (action == Action.REPOST || action == Action.QUOTE)) {
            return REASON_REPOST_MUTUAL;
        }
        if (action == Action.REPOST || action == Action.QUOTE) {
            return REASON_REPOST;
        }
        return REASON_SHARE;
    }

    private static String denyReason(String visibility, boolean unavailable, String state, Action action) {
        if (tombstone(unavailable, state)) {
            return REASON_UNAVAILABLE;
        }
        String kind = canonical(visibility);
        if ("public".equals(kind)) {
            return action == Action.REPOST || action == Action.QUOTE ? REASON_REPOST : REASON_SHARE;
        }
        return mirrorReason(kind, action);
    }

    private static Boolean specific(Flags flags, Action action) {
        if (action == Action.REPOST) {
            return flags.repost;
        }
        if (action == Action.QUOTE) {
            return flags.quote;
        }
        if (action == Action.CHAT_SHARE) {
            return flags.chatShare;
        }
        if (action == Action.COPY_LINK) {
            return flags.copyLink;
        }
        if (action == Action.EXTERNAL_SHARE) {
            return flags.externalShare;
        }
        return null;
    }

    private static Boolean first(Boolean... values) {
        if (values == null) {
            return null;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                return values[i];
            }
        }
        return null;
    }

    private static Boolean bool(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        Object value = object.opt(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value instanceof String) {
            String text = (String) value;
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
