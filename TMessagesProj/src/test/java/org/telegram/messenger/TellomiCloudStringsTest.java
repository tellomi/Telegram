package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiCloudStringsTest {

    @Test
    public void downloadedPackIsReadForAnyLanguage() {
        assertTrue(TellomiDefaults.readDownloadedPack(true, false));
        assertTrue(TellomiDefaults.readDownloadedPack(false, true));
    }

    @Test
    public void bundledRowDoesNotPretendToBeADownload() {
        assertFalse(TellomiDefaults.readDownloadedPack(false, false));
    }
}
