package org.telegram.ui;

import java.util.Locale;

/**
 * Settings footer row that lets a normal account check for an app update.
 * The debug-menu item stays limited to standalone, beta, and private debug
 * builds. The row does not. A tap still goes through
 * {@code LaunchActivity.checkAppUpdate}; this class only decides who sees
 * the row and that the tap attaches progress so "no newer build" can be shown.
 */
public final class TellomiSettingsUpdate {

    /** Row on {@link SettingsActivity}, directly above the version footer. */
    public static final int ROW_ID = 41;

    private TellomiSettingsUpdate() {
    }

    /** Shown for every account. Debug, standalone, and beta flags do not hide it. */
    public static boolean rowVisible() {
        return true;
    }

    /**
     * Debug menu index 9 in {@link SettingsActivity}. A normal release account
     * does not see that item.
     */
    public static boolean debugMenuCheckVisible(boolean debugPrivate, boolean standalone, boolean beta) {
        return debugPrivate || standalone || beta;
    }

    /**
     * The settings tap passes a {@code Browser.Progress}. {@code checkAppUpdate}
     * shows {@code YourVersionIsLatest} only when that progress is non-null.
     */
    public static boolean attachProgressOnManualCheck() {
        return true;
    }

    public static final class Copy {
        public final String check;
        public final String latest;
        public final String failed;

        private Copy(String check, String latest, String failed) {
            this.check = check;
            this.latest = latest;
            this.failed = failed;
        }

        public static Copy resolve(String language) {
            if (language != null && language.toLowerCase(Locale.US).startsWith("zh")) {
                return new Copy("检查更新", "已是最新版本", "暂时无法检查更新，请稍后再试。");
            }
            return new Copy("Check for updates", "Already the latest version", "Could not check for updates. Try again.");
        }
    }
}
