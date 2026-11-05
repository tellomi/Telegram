package org.telegram.messenger;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiAppUpdateTest {

    @Test
    public void shippedAppChecksWhenTheFlagIsOn() {
        assertTrue(TellomiAppUpdate.shouldRequestUpdate(false, true, true, false));
    }

    @Test
    public void shippedAppSkipsWhenChecksAreOptedOut() {
        assertFalse(TellomiAppUpdate.shouldRequestUpdate(false, true, false, false));
    }

    @Test
    public void manualCheckStillRunsWhenOptedOut() {
        assertTrue(TellomiAppUpdate.shouldRequestUpdate(false, true, false, true));
    }

    @Test
    public void playBuildDoesNotCheck() {
        assertFalse(TellomiAppUpdate.shouldRequestUpdate(false, false, true, false));
    }

    @Test
    public void standaloneStillChecksWhenTheFlagIsOn() {
        assertTrue(TellomiAppUpdate.shouldRequestUpdate(true, false, true, false));
        assertFalse(TellomiAppUpdate.shouldRequestUpdate(true, false, false, false));
    }

    @Test
    public void softPopupIsDismissiblePathForTheShippedApp() {
        assertTrue(TellomiAppUpdate.shouldShowSoftPopup(false, false, true, false));
        assertFalse(TellomiAppUpdate.shouldShowSoftPopup(false, false, true, true));
        assertFalse(TellomiAppUpdate.shouldShowSoftPopup(false, false, false, false));
        assertTrue(TellomiAppUpdate.shouldShowSoftPopup(true, false, false, false));
        assertTrue(TellomiAppUpdate.shouldShowSoftPopup(false, true, false, false));
    }

    @Test
    public void urlOpensEvenWhenADocumentIsPresent() {
        assertEquals(TellomiAppUpdate.OPEN_URL, TellomiAppUpdate.primaryAction("https://www.tellomi.cn/dl/android/14.0.4-1801699/Tellomi.apk", true));
        assertEquals(TellomiAppUpdate.OPEN_URL, TellomiAppUpdate.primaryAction(" https://dl.tellomi.cn/android/Tellomi.apk ", false));
    }

    @Test
    public void documentInstallsOnlyWhenUrlIsMissing() {
        assertEquals(TellomiAppUpdate.INSTALL_APK, TellomiAppUpdate.primaryAction(null, true));
        assertEquals(TellomiAppUpdate.INSTALL_APK, TellomiAppUpdate.primaryAction("", true));
        assertEquals(TellomiAppUpdate.INSTALL_APK, TellomiAppUpdate.primaryAction("   ", true));
    }

    @Test
    public void apkLinkUsesTheSystemBrowser() {
        assertTrue(TellomiAppUpdate.preferSystemBrowser("https://dl.tellomi.cn/android/14.0.7-1802949/Tellomi.apk"));
        assertTrue(TellomiAppUpdate.preferSystemBrowser("https://dl.tellomi.cn/android/Tellomi.apk?x=1"));
        assertFalse(TellomiAppUpdate.preferSystemBrowser("https://www.tellomi.cn/download/"));
        assertFalse(TellomiAppUpdate.preferSystemBrowser(null));
    }

    @Test
    public void missingPayloadDoesNotOpenAStore() {
        assertEquals(TellomiAppUpdate.NONE, TellomiAppUpdate.primaryAction(null, false));
        assertEquals(TellomiAppUpdate.NONE, TellomiAppUpdate.primaryAction("", false));
    }

    @Test
    public void freshInstallDefaultsLastCheckToZeroAndChecks() {
        long now = 1_700_000_000_000L;
        long last = TellomiAppUpdate.lastCheckTimeOrFresh(false, now);
        assertEquals(0L, TellomiAppUpdate.FRESH_INSTALL_LAST_CHECK_MS);
        assertEquals(0L, last);
        assertEquals(0L, TellomiAppUpdate.lastCheckTimeOrFresh(true, 0L));
        assertEquals(now, TellomiAppUpdate.lastCheckTimeOrFresh(true, now));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(
                now,
                last,
                TellomiAppUpdate.DEFAULT_CHECK_DELAY_SECONDS,
                1800049,
                false,
                0));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(
                now,
                0L,
                TellomiAppUpdate.DEFAULT_CHECK_DELAY_SECONDS,
                1800049,
                true,
                1800049));
    }

    @Test
    public void versionChangeForcesOneCheckInsideTheDelay() {
        long now = 1_700_000_000_000L;
        long last = now - 60_000L;
        int delay = TellomiAppUpdate.DEFAULT_CHECK_DELAY_SECONDS;

        assertTrue(TellomiAppUpdate.versionChangedSinceLastRun(1800059, true, 1800049));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(now, last, delay, 1800059, true, 1800049));

        assertTrue(TellomiAppUpdate.versionChangedSinceLastRun(1800049, false, 0));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(now, last, delay, 1800049, false, 0));

        assertFalse(TellomiAppUpdate.versionChangedSinceLastRun(1800049, true, 1800049));
        assertTrue(TellomiAppUpdate.shouldSkipAutomaticCheck(now, last, delay, 1800049, true, 1800049));
    }

    @Test
    public void defaultIntervalIsSixHoursUnlessAppConfigSavedOne() {
        assertEquals(6 * 60 * 60, TellomiAppUpdate.DEFAULT_CHECK_DELAY_SECONDS);
        assertEquals(6 * 60 * 60, TellomiAppUpdate.resolveCheckDelaySeconds(false, 24 * 60 * 60));
        assertEquals(24 * 60 * 60, TellomiAppUpdate.resolveCheckDelaySeconds(true, 24 * 60 * 60));
        assertEquals(60, TellomiAppUpdate.resolveCheckDelaySeconds(true, 60));

        long now = 1_700_000_000_000L;
        long sixHours = 6L * 60L * 60L * 1000L;
        int sameVersion = 1800049;
        int defaultDelay = TellomiAppUpdate.resolveCheckDelaySeconds(false, 0);
        assertTrue(TellomiAppUpdate.shouldSkipAutomaticCheck(now, now - sixHours + 1L, defaultDelay, sameVersion, true, sameVersion));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(now, now - sixHours, defaultDelay, sameVersion, true, sameVersion));

        int serverDelay = TellomiAppUpdate.resolveCheckDelaySeconds(true, 24 * 60 * 60);
        assertTrue(TellomiAppUpdate.shouldSkipAutomaticCheck(now, now - sixHours, serverDelay, sameVersion, true, sameVersion));
        assertFalse(TellomiAppUpdate.shouldSkipAutomaticCheck(now, now - 24L * 60L * 60L * 1000L, serverDelay, sameVersion, true, sameVersion));
    }

    @Test
    public void scheduleHooksAreWhatTheClientCalls() throws Exception {
        String shared = read("TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java");
        String messages = read("TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java");
        String launch = read("TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java");
        assertTrue(shared.contains("TellomiAppUpdate.lastCheckTimeOrFresh("));
        assertFalse(shared.contains("getLong(\"appUpdateCheckTime\", System.currentTimeMillis())"));
        assertTrue(shared.contains("rememberUpdateCheckVersion"));
        assertTrue(messages.contains("TellomiAppUpdate.resolveCheckDelaySeconds("));
        assertTrue(messages.contains("case \"inapp_update_check_delay\":"));
        assertFalse(messages.contains("getInt(\"updateCheckDelay\", 24 * 60 * 60)"));
        assertTrue(launch.contains("TellomiAppUpdate.shouldSkipAutomaticCheck("));
        assertTrue(launch.contains("SharedConfig.rememberUpdateCheckVersion(installedVersion)"));
        assertTrue(launch.contains("checkAppUpdate(false, null)"));
        String dialog = read("TMessagesProj/tellomi/java/org/telegram/ui/TellomiSoftUpdateDialog.java");
        assertTrue(dialog.contains("TellomiAppUpdate.preferSystemBrowser("));
        assertTrue(dialog.contains("Browser.openUrlInSystemBrowser("));
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(find(relative).toPath()), StandardCharsets.UTF_8);
    }

    private static File find(String relative) {
        File direct = new File(relative);
        if (direct.isFile()) {
            return direct;
        }
        File nested = new File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        File fromModule = new File("..", relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative + " from " + new File(".").getAbsolutePath());
    }
}
