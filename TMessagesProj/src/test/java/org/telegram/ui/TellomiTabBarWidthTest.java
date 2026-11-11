package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TellomiTabBarWidthTest {

    private static final int TITLED = TellomiTabBarWidth.TITLED_SLOT_DP;
    private static final int ICON = TellomiTabBarWidth.ICON_SLOT_DP;

    /** Existing main-bar horizontal padding: dp(12) on each side. */
    private static final int BAR_PADDING = 24;

    @Test
    public void twoTitledTabs_areTwoSlotsPlusPadding() {
        float[] slots = present(40f, 36f);
        assertEquals(2 * TITLED + 8, TellomiTabBarWidth.hug(true, new float[]{40f, 36f}, slots, TITLED, ICON, 416, 8));
        assertSlots(slots, 90f, 90f);
    }

    @Test
    public void threeTitledTabs_hugInsteadOfTheFullWidth() {
        float[] slots = present(40f, 40f, 40f);
        assertEquals(3 * TITLED + BAR_PADDING, TellomiTabBarWidth.hug(true, new float[]{40f, 40f, 40f}, slots, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(slots, 90f, 90f, 90f);
    }

    @Test
    public void fourTitledTabs_hugWhenTheScreenFits() {
        float[] slots = present(40f, 40f, 40f, 40f);
        assertEquals(4 * TITLED + BAR_PADDING, TellomiTabBarWidth.hug(true, new float[]{40f, 40f, 40f, 40f}, slots, TITLED, ICON, 4 * TITLED + BAR_PADDING, BAR_PADDING));
        assertSlots(slots, 90f, 90f, 90f, 90f);
    }

    @Test
    public void longLabel_widensOnlyThatTab() {
        float[] slots = present(120f, 40f);
        assertEquals(120 + TITLED + 8, TellomiTabBarWidth.hug(true, new float[]{120f, 40f}, slots, TITLED, ICON, 416, 8));
        assertSlots(slots, 120f, 90f);
    }

    @Test
    public void fourTitledTabs_widerThanThePhone_keepUpstreamWidths() {
        float[] slots = present(80f, 70f, 70f, 70f);
        float[] labels = {40f, 40f, 40f, 40f};
        // 4 * 90 + 24 = 384, which is wider than a 360dp phone.
        assertEquals(-1, TellomiTabBarWidth.hug(true, labels, slots, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(slots, 80f, 70f, 70f, 70f);
    }

    @Test
    public void hiddenTab_isLeftOutOfTheHug() {
        float[] slots = present(40f, 0f, 36f);
        assertEquals(2 * TITLED + BAR_PADDING, TellomiTabBarWidth.hug(true, new float[]{40f, 100f, 36f}, slots, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(slots, 90f, 0f, 90f);
    }

    @Test
    public void hideLabels_twoTabs_useTheIconSlot() {
        float[] slots = present(80f, 80f);
        assertEquals(2 * ICON + 8, TellomiTabBarWidth.hug(false, new float[]{80f, 80f}, slots, TITLED, ICON, 416, 8));
        assertSlots(slots, 56f, 56f);
    }

    @Test
    public void hideLabels_threeAndFourTabs_stayOnTheIconSlot() {
        float[] three = present(80f, 80f, 80f);
        assertEquals(3 * ICON + BAR_PADDING, TellomiTabBarWidth.hug(false, new float[]{80f, 80f, 80f}, three, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(three, 56f, 56f, 56f);

        float[] four = present(140f, 140f, 140f, 140f);
        assertEquals(4 * ICON + BAR_PADDING, TellomiTabBarWidth.hug(false, new float[]{140f, 140f, 140f, 140f}, four, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(four, 56f, 56f, 56f, 56f);
    }

    @Test
    public void hideLabels_ignoresAWideMeasuredTitle() {
        float[] slots = present(140f, 140f);
        assertEquals(2 * ICON + BAR_PADDING, TellomiTabBarWidth.hug(false, new float[]{140f, 140f}, slots, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(slots, 56f, 56f);
    }

    @Test
    public void tabletWidth_stillHugs() {
        float[] slots = present(40f, 40f, 40f, 40f);
        assertEquals(4 * TITLED + BAR_PADDING, TellomiTabBarWidth.hug(true, new float[]{40f, 40f, 40f, 40f}, slots, TITLED, ICON, 800, BAR_PADDING));
    }

    @Test
    public void noVisibleTabs_doesNotHug() {
        float[] slots = present(0f, 0f);
        assertEquals(-1, TellomiTabBarWidth.hug(true, new float[]{40f, 40f}, slots, TITLED, ICON, 360, BAR_PADDING));
        assertSlots(slots, 0f, 0f);
    }

    private static float[] present(float... widths) {
        return widths.clone();
    }

    private static void assertSlots(float[] actual, float... expected) {
        assertEquals(expected.length, actual.length);
        assertArrayEquals(expected, actual, 0f);
    }
}
