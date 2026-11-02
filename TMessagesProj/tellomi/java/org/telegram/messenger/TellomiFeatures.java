package org.telegram.messenger;

/**
 * Gates for Telegram features Teamgram does not implement.
 *
 * Official appConfig keys are used where they exist
 * ({@code premium_purchase_blocked}, {@code stars_purchase_blocked},
 * {@code wallet_available}). Tellomi-only keys default to off when the
 * server omits them, so a missing config hides the feature.
 * Stories stay available.
 */
public final class TellomiFeatures {

    private TellomiFeatures() {
    }

    public static MessagesController controller(int account) {
        return MessagesController.getInstance(account);
    }

    /** {@code tellomi_ai_compose_enabled}, default false. */
    public static boolean aiComposeEnabled(int account) {
        return controller(account).config.tellomiAiComposeEnabled.get();
    }

    public static boolean aiComposeEnabled() {
        return aiComposeEnabled(UserConfig.selectedAccount);
    }

    /** {@code tellomi_translate_enabled}, default false. Also hides Cocoon. */
    public static boolean translateEnabled(int account) {
        return controller(account).config.tellomiTranslateEnabled.get();
    }

    public static boolean translateEnabled() {
        return translateEnabled(UserConfig.selectedAccount);
    }

    /**
     * Premium shop and upsells. Official {@code premium_purchase_blocked}
     * defaults to blocked when the key has never been stored.
     */
    public static boolean premiumEntryEnabled(int account) {
        return !controller(account).premiumPurchaseBlocked();
    }

    /** Official {@code stars_purchase_blocked}. Defaults to blocked. */
    public static boolean starsEnabled(int account) {
        return controller(account).starsPurchaseAvailable();
    }

    /** {@code tellomi_gifts_enabled}, default false. */
    public static boolean giftsEnabled(int account) {
        return controller(account).config.tellomiGiftsEnabled.get();
    }

    /**
     * Gram / TON balances and the TON intro. {@code tellomi_ton_enabled},
     * default false. The wallet screen itself follows {@code wallet_available}.
     */
    public static boolean tonEnabled(int account) {
        return controller(account).config.tellomiTonEnabled.get();
    }

    /** Official {@code wallet_available}. Default false when the key is missing. */
    public static boolean walletEnabled(int account) {
        return controller(account).config.walletAvailable.get();
    }

    public static boolean walletEnabled() {
        return walletEnabled(UserConfig.selectedAccount);
    }

    /** {@code tellomi_fragment_enabled}, default false. */
    public static boolean fragmentEnabled(int account) {
        return controller(account).config.tellomiFragmentEnabled.get();
    }

    public static boolean fragmentEnabled() {
        return fragmentEnabled(UserConfig.selectedAccount);
    }

    /** Channel ads and revenue. {@code tellomi_channel_revenue_enabled}, default false. */
    public static boolean channelRevenueEnabled(int account) {
        return controller(account).config.tellomiChannelRevenueEnabled.get();
    }

    /** {@code tellomi_business_enabled}, default false. */
    public static boolean businessEnabled(int account) {
        return controller(account).config.tellomiBusinessEnabled.get();
    }

    /** {@code tellomi_boosts_enabled}, default false. */
    public static boolean boostsEnabled(int account) {
        return controller(account).config.tellomiBoostsEnabled.get();
    }

    /** {@code tellomi_passport_enabled}, default false. */
    public static boolean passportEnabled(int account) {
        return controller(account).config.tellomiPassportEnabled.get();
    }

    /** {@code tellomi_ai_summary_enabled}, default false. */
    public static boolean aiSummaryEnabled(int account) {
        return controller(account).config.tellomiAiSummaryEnabled.get();
    }
}
