package org.telegram.ui;

/**
 * Shared top-end slot for the login language control and the day/night control.
 * Both use the same hit size and the same top edge on the welcome page and on
 * every later login page. A large font or a narrow window shrinks the hit
 * before the two controls overlap or leave the screen.
 */
public final class TellomiAuthChromeLayout {

    public static final int HIT_DP = 48;
    public static final int EDGE_DP = 4;
    public static final int GAP_DP = 4;
    /** Two controls still fit a 320dp window beside a back control. */
    public static final float MAX_FONT_SCALE = 1.3f;
    public static final int MIN_HIT_DP = 40;

    public final int top;
    public final int size;
    public final int languageLeft;
    public final int themeLeft;

    private TellomiAuthChromeLayout(int top, int size, int languageLeft, int themeLeft) {
        this.top = top;
        this.size = size;
        this.languageLeft = languageLeft;
        this.themeLeft = themeLeft;
    }

    /**
     * @param widthPx window width
     * @param density px per dp
     * @param fontScale system font scale
     * @param statusBarPx inset above the controls
     * @param proxyShown the shield occupies the top-end corner
     */
    public static TellomiAuthChromeLayout place(int widthPx, float density, float fontScale, int statusBarPx, boolean proxyShown) {
        float d = density > 0f ? density : 1f;
        float scale = fontScale > 0f ? Math.min(fontScale, MAX_FONT_SCALE) : 1f;
        int edge = Math.round(EDGE_DP * d);
        int gap = Math.round(GAP_DP * d);
        int size = Math.round(HIT_DP * d * scale);
        int proxy = proxyShown ? Math.round(HIT_DP * d) : 0;
        int back = Math.round(HIT_DP * d) + edge;
        int themeRight = widthPx - edge - proxy;
        int themeLeft = themeRight - size;
        int languageRight = themeLeft - gap;
        int languageLeft = languageRight - size;
        if (languageLeft < back && widthPx > 0) {
            int room = widthPx - edge - proxy - gap - back;
            int min = Math.round(MIN_HIT_DP * d);
            size = Math.max(min, room / 2);
            themeRight = widthPx - edge - proxy;
            themeLeft = themeRight - size;
            languageRight = themeLeft - gap;
            languageLeft = languageRight - size;
        }
        int top = Math.max(0, statusBarPx) + edge;
        return new TellomiAuthChromeLayout(top, size, languageLeft, themeLeft);
    }

    public int themeRightMargin(int widthPx) {
        return Math.max(0, widthPx - (themeLeft + size));
    }

    public int languageRightMargin(int widthPx) {
        return Math.max(0, widthPx - (languageLeft + size));
    }

    public boolean overlaps() {
        return languageLeft < themeLeft + size && themeLeft < languageLeft + size;
    }
}
