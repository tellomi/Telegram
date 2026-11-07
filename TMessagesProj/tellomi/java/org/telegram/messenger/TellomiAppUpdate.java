package org.telegram.messenger;

/**
 * Gates for the shipped App module's update check.
 * That module reads the release feeds in {@link TellomiReleaseUpdate}.
 * Standalone and beta builds keep {@code help.getAppUpdate}. The App module
 * opts in with {@link ApplicationLoader#supportsInAppUpdates()} and still
 * honors {@code TELLOMI_CHECK_UPDATES} so {@code -PTELLOMI_CHECK_UPDATES=false}
 * skips the automatic request.
 */
public final class TellomiAppUpdate {

    public static final int NONE = 0;
    /** Open a non-APK {@code help.appUpdate#url} in the browser. */
    public static final int OPEN_URL = 1;
    /** Install {@code help.appUpdate#document} when no url is set. */
    public static final int INSTALL_APK = 2;
    /** Download an allowlisted https APK inside the app, then open the system installer. */
    public static final int DOWNLOAD_IN_APP = 3;

    private TellomiAppUpdate() {
    }

    /** Seconds between automatic checks when {@code inapp_update_check_delay} has not been saved. */
    public static final int DEFAULT_CHECK_DELAY_SECONDS = 6 * 60 * 60;

    /**
     * Missing {@code appUpdateCheckTime} on a fresh install. Zero is already outside
     * any delay, so the next resume sends {@code help.getAppUpdate} instead of
     * treating the first launch as the last check.
     */
    public static final long FRESH_INSTALL_LAST_CHECK_MS = 0L;

    public static boolean shouldRequestUpdate(boolean standaloneOrBeta, boolean inAppUpdates, boolean checkUpdates, boolean force) {
        if (!standaloneOrBeta && !inAppUpdates) {
            return false;
        }
        return force || checkUpdates;
    }

    /**
     * A saved check time is kept, including zero. No saved time is a fresh install.
     */
    public static long lastCheckTimeOrFresh(boolean hasSavedCheckTime, long savedCheckTimeMs) {
        return hasSavedCheckTime ? savedCheckTimeMs : FRESH_INSTALL_LAST_CHECK_MS;
    }

    /**
     * {@code help.getAppConfig} {@code inapp_update_check_delay} is stored as
     * {@code updateCheckDelay} and wins. Otherwise the client default is 6 hours.
     */
    public static int resolveCheckDelaySeconds(boolean hasSavedDelay, int savedDelaySeconds) {
        return hasSavedDelay ? savedDelaySeconds : DEFAULT_CHECK_DELAY_SECONDS;
    }

    /**
     * First launch after an upgrade, or the first launch that has no recorded
     * version code yet. A later launch of the same code is not a change.
     */
    public static boolean versionChangedSinceLastRun(int currentVersionCode, boolean hasSavedVersionCode, int savedVersionCode) {
        if (!hasSavedVersionCode) {
            return true;
        }
        return savedVersionCode != currentVersionCode;
    }

    /**
     * Automatic {@code checkAppUpdate(false)} throttle. A version change forces
     * one check even when the previous check is still inside the delay.
     */
    public static boolean shouldSkipAutomaticCheck(long nowMs, long lastCheckMs, int delaySeconds, int currentVersionCode, boolean hasSavedVersionCode, int savedVersionCode) {
        if (versionChangedSinceLastRun(currentVersionCode, hasSavedVersionCode, savedVersionCode)) {
            return false;
        }
        return Math.abs(nowMs - lastCheckMs) < (long) delaySeconds * 1000L;
    }

    public static boolean shouldShowSoftPopup(boolean standalone, boolean debugVersion, boolean inAppUpdates, boolean canNotSkip) {
        if (canNotSkip) {
            return false;
        }
        return standalone || debugVersion || inAppUpdates;
    }

    /**
     * An allowlisted https APK is downloaded in the app. Any other non-empty
     * url still opens in the browser. A document with no url installs that
     * file. Neither path opens a store listing.
     */
    public static int primaryAction(String url, boolean hasDocument) {
        String trimmed = url == null ? "" : url.trim();
        if (TellomiReleaseUpdate.acceptableApkUrl(trimmed) != null) {
            return DOWNLOAD_IN_APP;
        }
        if (trimmed.length() > 0) {
            return OPEN_URL;
        }
        if (hasDocument) {
            return INSTALL_APK;
        }
        return NONE;
    }

    /**
     * The website APK is no longer handed to the system browser.
     * {@link #primaryAction} returns {@link #DOWNLOAD_IN_APP} for that link.
     */
    public static boolean preferSystemBrowser(String url) {
        return false;
    }
}
