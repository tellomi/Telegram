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
    // TELLOMI: cloud langpack and store update checks talk to Telegram infrastructure.
    public static boolean USE_CLOUD_STRINGS = BuildConfig.TELLOMI_USE_CLOUD_STRINGS;
    public static boolean CHECK_UPDATES = BuildConfig.TELLOMI_CHECK_UPDATES;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;

    // TELLOMI: api_id / api_hash placeholders. Not Telegram's published app credentials.
    public static int APP_ID = BuildConfig.TELLOMI_API_ID;
    public static String APP_HASH = BuildConfig.TELLOMI_API_HASH;

    public static final String APP_NAME = BuildConfig.TELLOMI_APP_NAME;
    public static final String STORAGE_DIR_NAME = APP_NAME;
    public static final String STORAGE_IMAGES_DIR = APP_NAME + " Images";
    public static final String STORAGE_VIDEO_DIR = APP_NAME + " Video";
    public static final String STORAGE_AUDIO_DIR = APP_NAME + " Audio";
    public static final String STORAGE_DOCUMENTS_DIR = APP_NAME + " Documents";
    public static final String STORAGE_FILES_DIR = APP_NAME + " Files";
    public static final String STORAGE_STORIES_DIR = APP_NAME + " Stories";
    public static final boolean TELLOMI_CUSTOM_BACKEND = BuildConfig.TELLOMI_CUSTOM_BACKEND;
    public static final boolean TELLOMI_LOCK_DC_ADDRESSES = BuildConfig.TELLOMI_LOCK_DC_ADDRESSES;
    public static final boolean TELLOMI_REBRAND_STRINGS = BuildConfig.TELLOMI_REBRAND_STRINGS;
    public static final String TELLOMI_DC_ADDRESSES = BuildConfig.TELLOMI_DC_ADDRESSES;
    public static final int TELLOMI_DEFAULT_DC_ID = BuildConfig.TELLOMI_DEFAULT_DC_ID;
    public static final String TELLOMI_SERVER_PUBLIC_KEY = BuildConfig.TELLOMI_SERVER_PUBLIC_KEY;
    public static final String DOWNLOAD_URL = BuildConfig.TELLOMI_DOWNLOAD_URL;
    public static final String ACCOUNT_TYPE = BuildConfig.TELLOMI_ACCOUNT_TYPE;

    // Empty disables SafetyNet. Telegram's key is rejected for this applicationId.
    public static String SAFETYNET_KEY = "";
    public static String PLAYSTORE_APP_URL = DOWNLOAD_URL;
    public static String HUAWEI_STORE_URL = DOWNLOAD_URL;
    // Placeholder. Google sign-in stays in the UI but does not use Telegram's OAuth client.
    public static String GOOGLE_AUTH_CLIENT_ID = "000000000000-tellomi.apps.googleusercontent.com";

    public static String HUAWEI_APP_ID = "101184875";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = BuildConfig.TELLOMI_BILLING_UNAVAILABLE;

    // works only on official app ids, disable on your forks
    public static boolean SUPPORTS_PASSKEYS = false;

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

    public static String getSmsHash() {
        return ApplicationLoader.isStandaloneBuild() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    }
}
