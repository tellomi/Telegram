package org.telegram.messenger;

/**
 * Defaults for native Telegram features after the 2026-11-14 audit.
 *
 * Teamgram implements the MTProto methods and does not send the official
 * appConfig switches that DrKLO leaves off ({@code dialog_filters_enabled},
 * {@code background_connection}, {@code keep_alive_service},
 * {@code save_gifs_with_stickers}). Those defaults are on here.
 *
 * Purchase, Stars, Google Play billing, Fragment, and the TON wallet stay
 * off until {@code help.getAppConfig} says otherwise. Composer AI follows
 * {@code tellomi_ai_compose_enabled}. Translation follows
 * {@code translations_manual_enabled} / {@code translations_auto_enabled},
 * which start disabled until the server reports a healthy translator.
 */
public final class TellomiNativeFeatures {

    private TellomiNativeFeatures() {
    }

    /** {@code messages.getDialogFilters} is implemented. The server omits {@code dialog_filters_enabled}. */
    public static final boolean DIALOG_FILTERS_ENABLED = true;
    /** No RPC. Official clients turn this on from appConfig; Teamgram does not send the key. */
    public static final boolean BACKGROUND_CONNECTION = true;
    /** Same as {@link #BACKGROUND_CONNECTION} for the foreground notification service. */
    public static final boolean KEEP_ALIVE_SERVICE = true;
    /** Sticker and GIF methods are implemented. The server omits {@code save_gifs_with_stickers}. */
    public static final boolean SAVE_GIFS_WITH_STICKERS = true;

    /** {@code premium_purchase_blocked}. Purchase stays hidden when the key is absent. */
    public static final boolean PREMIUM_PURCHASE_BLOCKED = true;
    /** {@code stars_paid_messages_available}. */
    public static final boolean STARS_PAID_MESSAGES = false;
    /** {@code stars_gifts_enabled}. */
    public static final boolean STARS_GIFTS_ENABLED = false;
    /** {@code enable_gifts_in_profile}. */
    public static final boolean GIFTS_IN_PROFILE = false;
    /** {@code wallet_available}. TON and the Gram wallet stay hidden in debug builds too. */
    public static final boolean WALLET_AVAILABLE = false;
    /** Do not start Google Play Billing. */
    public static final boolean BILLING_UNAVAILABLE = true;
    /**
     * {@code translations_manual_enabled} and {@code translations_auto_enabled}.
     * The server sends {@code enabled} only when the self-hosted translator is healthy.
     */
    public static final String TRANSLATIONS = "disabled";

    /** Fragment and fragment.com are not a Tellomi service. */
    public static boolean fragmentLinksAllowed() {
        return false;
    }

    /**
     * Composer AI, rich-text AI, and the attach-caption AI button.
     * Pass {@code help.getAppConfig} {@code tellomi_ai_compose_enabled}.
     * Teamgram sets that key only when {@code TELLOMI_AI_COMPOSE=1} and an API key are present.
     */
    public static boolean aiComposeEnabled(boolean advertised) {
        return advertised;
    }
}
