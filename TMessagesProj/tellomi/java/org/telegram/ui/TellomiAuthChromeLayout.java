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
        // The user's font scale is not capped. Hit targets grow with it.
        // They shrink, together, only when that size would cover the back
        // control or leave the window.
        float scale = fontScale > 0f ? fontScale : 1f;
        int edge = Math.round(EDGE_DP * d);
        int gap = Math.round(GAP_DP * d);
        int preferred = Math.max(1, Math.round(HIT_DP * d * scale));
        int proxy = proxyShown ? Math.round(HIT_DP * d) : 0;
        int back = Math.round(HIT_DP * d) + edge;
        int size = preferred;
        if (widthPx > 0) {
            int room = widthPx - edge - proxy - gap - back;
            if (room < preferred * 2) {
                size = Math.max(1, room / 2);
            }
        }
        int themeRight = widthPx - edge - proxy;
        int themeLeft = themeRight - size;
        int languageRight = themeLeft - gap;
        int languageLeft = languageRight - size;
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

    /**
     * Welcome carousel page after a redraw. A page the user already opened is
     * kept. The first visit still starts at the end in RTL and at the start
     * otherwise.
     */
    public static int welcomePage(boolean pageChosen, int lastPage, boolean rtl, int pageCount) {
        if (pageCount < 1) {
            return 0;
        }
        if (pageChosen) {
            if (lastPage < 0) {
                return 0;
            }
            if (lastPage >= pageCount) {
                return pageCount - 1;
            }
            return lastPage;
        }
        return rtl ? pageCount - 1 : 0;
    }
}
