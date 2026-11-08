package org.telegram.ui;

/**
 * Size rules for the Tellomi login and sign-up screens.
 * Inputs are pixels or a raw font scale. No Android types, so the rules can be
 * checked without a device. See UPSTREAM_PATCHES.md.
 */
public final class TellomiLoginFit {

    /** Form column on a tablet, unfolded foldable, or other wide window. */
    public static final int MAX_CONTENT_DP = 420;
    /** Dialog card. Wider windows keep the same measure as a phone. */
    public static final int DIALOG_MAX_DP = 400;
    /**
     * System font scale applied to login text. 1.6 is "largest" on many phones
     * without turning a 48dp control into two unreadable lines. Smaller settings
     * pass through unchanged.
     */
    public static final float MAX_FONT_SCALE = 1.6f;
    /** {@link android.view.View.MeasureSpec#EXACTLY}. Kept here so tests do not need the SDK. */
    public static final int MEASURE_EXACTLY = 1073741824;

    private TellomiLoginFit() {
    }

    /**
     * Horizontal padding that centers a column of {@code maxContentPx} inside
     * {@code availablePx}. Zero on a phone.
     */
    public static int extraSidePadPx(int availablePx, int maxContentPx) {
        if (maxContentPx <= 0 || availablePx <= maxContentPx) {
            return 0;
        }
        return (availablePx - maxContentPx) / 2;
    }

    public static int dialogWidthPx(int windowWidthPx, int sideMarginPx, int maxWidthPx) {
        int inner = windowWidthPx - Math.max(0, sideMarginPx) * 2;
        if (inner < 1) {
            inner = 1;
        }
        if (maxWidthPx > 0 && inner > maxWidthPx) {
            inner = maxWidthPx;
        }
        return inner;
    }

    public static int dialogMaxHeightPx(int windowHeightPx, int reservedPx) {
        int max = windowHeightPx - Math.max(0, reservedPx);
        return Math.max(1, max);
    }

    /**
     * Value for {@code setTextSize(COMPLEX_UNIT_SP, ...)}. The framework multiplies
     * by the font scale again, so the painted size is {@code baseSp * min(scale, maxScale)}.
     */
    public static float cappedSp(float baseSp, float fontScale, float maxScale) {
        float scale = fontScale > 0f ? fontScale : 1f;
        float cap = maxScale > 0f ? maxScale : scale;
        return baseSp * Math.min(scale, cap) / scale;
    }

    /** Outline height. Never shorter than the design size. Grows when the glyph no longer fits. */
    public static int fieldHeightPx(int basePx, int textPx, int verticalPadPx) {
        int needed = Math.max(0, textPx) + Math.max(0, verticalPadPx);
        return Math.max(Math.max(1, basePx), needed);
    }

    /** Digit glyph inside a code square. Stays inside the box when the square has to shrink. */
    public static float digitPx(int boxPx, float preferredPx) {
        if (boxPx <= 1) {
            return 1f;
        }
        float max = boxPx * 0.62f;
        float preferred = preferredPx > 0f ? preferredPx : max;
        return Math.max(1f, Math.min(preferred, max));
    }

    /** A weighted spacer can push the last row down only when the page is taller than the form. */
    public static boolean expandToViewport(int contentHeightPx, int viewportMode, int viewportSizePx) {
        return viewportMode == MEASURE_EXACTLY && viewportSizePx > contentHeightPx;
    }

    /** Viewport the form may fill once the IME covers the bottom of the window. */
    public static int visibleAboveKeyboard(int viewportPx, int keyboardPx) {
        int visible = viewportPx - Math.max(0, keyboardPx);
        return Math.max(1, visible);
    }

    /** The submit control is off the visible page and the page cannot scroll to it. */
    public static boolean submitClipped(int submitBottomPx, int visiblePx, boolean canScroll) {
        return submitBottomPx > visiblePx && !canScroll;
    }
}
