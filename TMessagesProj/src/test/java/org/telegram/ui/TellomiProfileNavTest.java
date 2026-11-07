package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiProfileNavTest {

    @Test
    public void chineseNavLabelIsMe() {
        assertEquals("我", TellomiProfileNav.label("zh"));
        assertEquals("我", TellomiProfileNav.label("zh-Hans"));
        assertEquals("我", TellomiProfileNav.label("zh-CN"));
        assertEquals("我", TellomiProfileNav.label("zh_SG"));
        assertEquals("我", TellomiProfileNav.label("zh-Hant"));
        assertEquals("我", TellomiProfileNav.label("zh-TW"));
        assertEquals("我", TellomiProfileNav.label("zh-HK"));
        assertEquals("我", TellomiProfileNav.label("zh-MO"));
    }

    @Test
    public void otherLanguagesKeepTheStockTabString() {
        assertNull(TellomiProfileNav.label("en"));
        assertNull(TellomiProfileNav.label("en-US"));
        assertNull(TellomiProfileNav.label("ja"));
        assertNull(TellomiProfileNav.label("de"));
        assertNull(TellomiProfileNav.label(null));
        assertNull(TellomiProfileNav.label(""));
        assertNull(TellomiProfileNav.label("  "));
    }

    @Test
    public void oneCharacterLabelFitsANarrowBar() {
        int[] at12 = {27, 27, 27, 40, 14};
        int[] at10 = {23, 23, 23, 33, 12};
        int[] enlarged = {27, 27, 27, 40, 23};
        assertTrue(fits(360, 5, at12, 16) || fits(360, 5, at12, 8) || fits(360, 5, at10, 4));
        assertTrue(fits(320, 5, enlarged, 16) || fits(320, 5, enlarged, 8) || fits(320, 5, at10, 4));
        assertTrue(fits(360, 3, new int[]{27, 27, 14}, 16));
    }

    /** Same budget as {@code TellomiTabs.labelsFit}: content width minus side padding. */
    private static boolean fits(int screenDp, int count, int[] textDp, int sidePadDp) {
        int bar = count <= 4 ? 328 + 16 : 376;
        int side = count <= 4 ? 12 : 4;
        int content = Math.min(screenDp, bar) - side * 2;
        int total = 0;
        for (int width : textDp) {
            total += width + sidePadDp * 2;
        }
        return total <= content;
    }
}
