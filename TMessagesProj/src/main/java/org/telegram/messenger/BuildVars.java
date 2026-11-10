/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.android.billingclient.api.ProductDetails;

import java.util.Objects;

public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG_VERSION;
    public static boolean LOGS_ENABLED = BuildConfig.DEBUG_VERSION;
    public static boolean DEBUG_PRIVATE_VERSION = BuildConfig.DEBUG_PRIVATE_VERSION;
    // langpack.* is MTProto to our server (teamgram-server), not translations.telegram.org.
    public static boolean USE_CLOUD_STRINGS = BuildConfig.TELLOMI_USE_CLOUD_STRINGS;
    public static boolean CHECK_UPDATES = true;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;

    // Not Telegram's published api_id 4 / api_hash. Requests go to the configured datacenter only.
    public static int APP_ID = BuildConfig.TELLOMI_API_ID;
    public static String APP_HASH = BuildConfig.TELLOMI_API_HASH;

    public static final boolean TELLOMI_CUSTOM_BACKEND = BuildConfig.TELLOMI_CUSTOM_BACKEND;
    public static final boolean TELLOMI_LOCK_DC_ADDRESSES = BuildConfig.TELLOMI_LOCK_DC_ADDRESSES;
    public static final String TELLOMI_DC_ADDRESSES = BuildConfig.TELLOMI_DC_ADDRESSES;
    public static final int TELLOMI_DEFAULT_DC_ID = BuildConfig.TELLOMI_DEFAULT_DC_ID;
    public static final String TELLOMI_SERVER_PUBLIC_KEY = BuildConfig.TELLOMI_SERVER_PUBLIC_KEY;
    /** System contacts account. Matches the installed application id, not the official Telegram id. */
    public static final String ACCOUNT_TYPE = "app.tellomi.chat";
    /** Display name of the installed package. Saved-media folders and contact row labels use it. */
    public static final String APP_NAME = "Tellomi";
    /** Public link host (tell.cc). Datacenter addresses stay IP literals; this is only for links. */
    public static final String LINK_HOST = BuildConfig.TELLOMI_LINK_HOST;
    /** Help, privacy, and terms. Distinct from {@link #LINK_HOST}. */
    public static final String SITE_HOST = "tellomi.cn";
    public static final String HELP_URL = "https://" + SITE_HOST + "/help";
    public static final String PRIVACY_URL = "https://" + SITE_HOST + "/legal/privacy/";
    public static final String TOS_URL = "https://" + SITE_HOST + "/legal/terms/";
    /** Custom scheme that mirrors tg:// routes, for example tellomi://resolve?domain=name. */
    public static final String LINK_SCHEME = BuildConfig.TELLOMI_LINK_SCHEME;
    public static final String LINK_BASE = "https://" + LINK_HOST + "/";
    public static final boolean TELLOMI_REBRAND_STRINGS = BuildConfig.TELLOMI_REBRAND_STRINGS;
    public static final String STORAGE_DIR_NAME = APP_NAME;
    public static final String STORAGE_IMAGES_DIR = APP_NAME + " Images";
    public static final String STORAGE_VIDEO_DIR = APP_NAME + " Video";
    public static final String STORAGE_AUDIO_DIR = APP_NAME + " Audio";
    public static final String STORAGE_DOCUMENTS_DIR = APP_NAME + " Documents";
    public static final String STORAGE_FILES_DIR = APP_NAME + " Files";
    public static final String STORAGE_STORIES_DIR = APP_NAME + " Stories";

    // Empty so this package does not present Telegram's SafetyNet or OAuth client.
    public static String SAFETYNET_KEY = "";
    public static String PLAYSTORE_APP_URL = "https://play.google.com/store/apps/details?id=org.telegram.messenger";
    public static String HUAWEI_STORE_URL = "https://appgallery.huawei.com/app/C101184875";
    public static String GOOGLE_AUTH_CLIENT_ID = "";

    public static String HUAWEI_APP_ID = "101184875";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = false;

    // works only on official app ids, disable on your forks
    public static boolean SUPPORTS_PASSKEYS = true;

    static {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", DEBUG_VERSION);
            if (LOGS_ENABLED) {
                final Thread.UncaughtExceptionHandler pastHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                    FileLog.fatal(exception, false);
                    if (pastHandler != null) {
                        pastHandler.uncaughtException(thread, exception);
                    }
                });
            }
        }
    }

    public static boolean useInvoiceBilling() {
        return BillingController.billingClientEmpty || DEBUG_VERSION && false || ApplicationLoader.isStandaloneBuild() || isBetaApp() && false || isHuaweiStoreApp() || hasDirectCurrency();
    }

    private static boolean hasDirectCurrency() {
        if (!BillingController.getInstance().isReady() || BillingController.PREMIUM_PRODUCT_DETAILS == null) {
            return false;
        }
        for (ProductDetails.SubscriptionOfferDetails offerDetails : BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails()) {
            for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                for (String cur : MessagesController.getInstance(UserConfig.selectedAccount).directPaymentsCurrency) {
                    if (Objects.equals(phase.getPriceCurrencyCode(), cur)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Boolean betaApp;
    public static boolean isBetaApp() {
        if (betaApp == null) {
            betaApp = ApplicationLoader.applicationContext != null && "org.telegram.messenger.beta".equals(ApplicationLoader.applicationContext.getPackageName());
        }
        return betaApp;
    }


    public static boolean isHuaweiStoreApp() {
        return ApplicationLoader.isHuaweiStoreBuild();
    }

    public static boolean isLegacyLinkHost(String host) {
        return TellomiLinks.isLegacyLinkHost(host);
    }

    /** tell.cc, plus t.me / telegram.me / telegram.dog so links already in chats still open. */
    public static boolean isLinkHost(String host) {
        return TellomiLinks.isLinkHost(host, LINK_HOST);
    }

    public static boolean isAppScheme(String scheme) {
        return scheme != null && ("tg".equalsIgnoreCase(scheme) || LINK_SCHEME != null && LINK_SCHEME.equalsIgnoreCase(scheme));
    }

    /** Rewrite tellomi: and tellomi:// to tg: / tg:// so the existing route parser can run. */
    public static String toTgUrl(String url) {
        if (url == null || LINK_SCHEME == null || LINK_SCHEME.isEmpty()) {
            return url;
        }
        return url.replaceFirst("(?i)^" + java.util.regex.Pattern.quote(LINK_SCHEME) + ":", "tg:");
    }

    /** Force generated links onto LINK_HOST. Telegram hosts from help.getConfig are rewritten. */
    public static String normalizeLinkPrefix(String prefix) {
        return TellomiLinks.normalizeLinkPrefix(prefix, LINK_HOST);
    }

    /** Host used for links the user copies or shares. Follows a Tellomi me_url_prefix when set. */
    public static String shareHost() {
        return TellomiLinks.displayHost(activeLinkPrefix(), LINK_HOST);
    }

    /** https://tell.cc/{path}, or the server's Tellomi me_url_prefix when that host is not Telegram's. */
    public static String shareLink(String path) {
        return TellomiLinks.https(activeLinkPrefix(), LINK_HOST, path);
    }

    public static String invitePlusTitle(String inviteLink) {
        return TellomiLinks.invitePlusTitle(activeLinkPrefix(), LINK_HOST, inviteLink);
    }

    public static String inviteJoinchatTitle(String inviteLink) {
        return TellomiLinks.inviteJoinchatTitle(LINK_HOST, inviteLink);
    }

    public static boolean isPublicChatInvite(String link) {
        return TellomiLinks.isPublicChatInvite(link, LINK_HOST);
    }

    public static boolean isMentionLink(String url, String visibleText) {
        return TellomiLinks.isMentionLink(url, visibleText, LINK_HOST);
    }

    public static boolean containsInstantView(String lowerUrl) {
        return TellomiLinks.containsInstantView(lowerUrl, LINK_HOST);
    }

    public static boolean isBusinessMessageLink(String link, String slug) {
        return TellomiLinks.isBusinessMessageLink(link, slug, LINK_HOST);
    }

    private static String activeLinkPrefix() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return LINK_HOST;
            }
            String prefix = MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix;
            return normalizeLinkPrefix(prefix);
        } catch (Throwable ignored) {
            return LINK_HOST;
        }
    }

    public static String getSmsHash() {
        return ApplicationLoader.isStandaloneBuild() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    }
}
