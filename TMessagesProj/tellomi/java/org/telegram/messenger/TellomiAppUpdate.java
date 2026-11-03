package org.telegram.messenger;

/**
 * Gates for the shipped App module's {@code help.getAppUpdate} flow.
 * Standalone and beta builds keep their existing checks. The App module
 * opts in with {@link ApplicationLoader#supportsInAppUpdates()} and still
 * honors {@code TELLOMI_CHECK_UPDATES} so {@code -PTELLOMI_CHECK_UPDATES=false}
 * skips the automatic request.
 */
public final class TellomiAppUpdate {

    public static final int NONE = 0;
    /** Open {@code help.appUpdate#url} in the browser. */
    public static final int OPEN_URL = 1;
    /** Install {@code help.appUpdate#document} when no url is set. */
    public static final int INSTALL_APK = 2;

    private TellomiAppUpdate() {
    }

    public static boolean shouldRequestUpdate(boolean standaloneOrBeta, boolean inAppUpdates, boolean checkUpdates, boolean force) {
        if (!standaloneOrBeta && !inAppUpdates) {
            return false;
        }
        return force || checkUpdates;
    }

    public static boolean shouldShowSoftPopup(boolean standalone, boolean debugVersion, boolean inAppUpdates, boolean canNotSkip) {
        if (canNotSkip) {
            return false;
        }
        return standalone || debugVersion || inAppUpdates;
    }

    /**
     * A non-empty url wins, including when a document is also present.
     * A document alone installs. Neither opens a store listing.
     */
    public static int primaryAction(String url, boolean hasDocument) {
        if (url != null && url.trim().length() > 0) {
            return OPEN_URL;
        }
        if (hasDocument) {
            return INSTALL_APK;
        }
        return NONE;
    }
}
