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
