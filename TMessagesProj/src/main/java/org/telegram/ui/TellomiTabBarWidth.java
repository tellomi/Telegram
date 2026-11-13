package org.telegram.ui;

/**
 * Phone tab-bar capsule width for the visible tabs.
 *
 * A titled slot is {@code titledSlotPx} (90dp at the call site). Two titled tabs
 * are {@code 2 * titledSlotPx + barPaddingPx}. Hiding titles uses {@code iconSlotPx}
 * (56dp) and ignores the measured label. A label wider than the titled slot keeps
 * that measured width. The result is never wider than {@code availableWidthPx}:
 * {@code -1} tells the caller to keep the upstream distribution, which also
 * applies the existing max-width cap.
 */
public final class TellomiTabBarWidth {

    public static final int TITLED_SLOT_DP = 90;
    public static final int ICON_SLOT_DP = 56;

    private TellomiTabBarWidth() {
    }

    /**
     * @param showLabels false uses the icon slot for every visible tab
     * @param labelWidthsPx measured label width per child; ignored when {@code showLabels} is false
     * @param slotWidthsPx in: a positive value marks a visible child. out, on success: that child's slot, hidden children 0
     * @param barPaddingPx padding included in the returned bar width (the bar's own horizontal padding)
     * @return bar width in px, or {@code -1} when the hug is wider than {@code availableWidthPx}. On {@code -1}, {@code slotWidthsPx} is left unchanged.
     */
    public static int hug(boolean showLabels, float[] labelWidthsPx, float[] slotWidthsPx, int titledSlotPx, int iconSlotPx, int availableWidthPx, int barPaddingPx) {
        if (labelWidthsPx == null || slotWidthsPx == null || labelWidthsPx.length != slotWidthsPx.length) {
            return -1;
        }
        int count = 0;
        int sum = 0;
        for (int i = 0; i < slotWidthsPx.length; i++) {
            if (slotWidthsPx[i] <= 0f) {
                continue;
            }
            count++;
            sum += slotWidth(showLabels, labelWidthsPx[i], titledSlotPx, iconSlotPx);
        }
        if (count == 0) {
            return -1;
        }
        int bar = sum + barPaddingPx;
        if (bar > availableWidthPx) {
            return -1;
        }
        for (int i = 0; i < slotWidthsPx.length; i++) {
            if (slotWidthsPx[i] <= 0f) {
                slotWidthsPx[i] = 0f;
                continue;
            }
            slotWidthsPx[i] = slotWidth(showLabels, labelWidthsPx[i], titledSlotPx, iconSlotPx);
        }
        return bar;
    }

    private static int slotWidth(boolean showLabels, float labelWidthPx, int titledSlotPx, int iconSlotPx) {
        if (!showLabels) {
            return iconSlotPx;
        }
        return Math.max(titledSlotPx, Math.round(labelWidthPx));
    }
}
