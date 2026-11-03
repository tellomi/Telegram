package org.telegram.messenger;

import org.junit.Test;

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
    public void missingPayloadDoesNotOpenAStore() {
        assertEquals(TellomiAppUpdate.NONE, TellomiAppUpdate.primaryAction(null, false));
        assertEquals(TellomiAppUpdate.NONE, TellomiAppUpdate.primaryAction("", false));
    }
}
