package org.telegram.ui;

/**
 * Shared top-end slot for the welcome language control and, on the welcome
 * page only, the day/night control. Login keeps the language control and does
 * not add a theme control. The hit size stays 48dp. The system font scale is
 * not applied here: the sun animation was being fitted to the whole view, so a
 * larger hit box drew a larger moon.
 */
public final class TellomiAuthChromeLayout {

    public static final int HIT_DP = 48;
    public static final int EDGE_DP = 4;
    public static final int GAP_DP = 4;
    /** Upstream login back/close control: 32dp at 16dp from the top. */
    public static final int BACK_TOP_DP = 16;
    public static final int BACK_SIZE_DP = 32;

    public final int top;
    public final int size;
    public final int languageLeft;
    public final int themeLeft;
    private final int controls;

    private TellomiAuthChromeLayout(int top, int size, int languageLeft, int themeLeft, int controls) {
        this.top = top;
        this.size = size;
        this.languageLeft = languageLeft;
        this.themeLeft = themeLeft;
        this.controls = controls;
    }

    /**
     * @param widthPx window width
     * @param density px per dp
     * @param fontScale system font scale, accepted and not applied to this row
     * @param statusBarPx inset above the controls
     * @param proxyShown the shield occupies the top-end corner
     */
    public static TellomiAuthChromeLayout place(int widthPx, float density, float fontScale, int statusBarPx, boolean proxyShown) {
        return place(widthPx, density, fontScale, statusBarPx, proxyShown, 2);
    }

    /**
     * @param controls 2 on the welcome page (language and day/night), 1 on login (language only)
     */
    public static TellomiAuthChromeLayout place(int widthPx, float density, float fontScale, int statusBarPx, boolean proxyShown, int controls) {
        float d = density > 0f ? density : 1f;
        int count = controls <= 1 ? 1 : 2;
        int edge = Math.round(EDGE_DP * d);
        int gap = count == 2 ? Math.round(GAP_DP * d) : 0;
        // fontScale is intentionally unused. Text on the page still follows it.
        int preferred = Math.max(1, Math.round(HIT_DP * d));
        int proxy = proxyShown ? Math.round(HIT_DP * d) : 0;
        int back = Math.round(HIT_DP * d) + edge;
        int size = preferred;
        if (widthPx > 0) {
            int room = widthPx - edge - proxy - gap - back;
            if (room < preferred * count) {
                size = Math.max(1, room / count);
            }
        }
        int endRight = widthPx - edge - proxy;
        int endLeft = endRight - size;
        int themeLeft;
        int languageLeft;
        if (count == 1) {
            themeLeft = endLeft;
            languageLeft = endLeft;
        } else {
            themeLeft = endLeft;
            languageLeft = themeLeft - gap - size;
        }
        int top = Math.max(0, statusBarPx) + edge;
        return new TellomiAuthChromeLayout(top, size, languageLeft, themeLeft, count);
    }

    /** Same vertical center as the 32dp back/close control. */
    public int topAlignedWithBack(int statusBarPx, float density) {
        float d = density > 0f ? density : 1f;
        int backCenter = Math.round((BACK_TOP_DP + BACK_SIZE_DP / 2f) * d);
        return Math.max(0, statusBarPx) + backCenter - size / 2;
    }

    public int themeRightMargin(int widthPx) {
        return Math.max(0, widthPx - (themeLeft + size));
    }

    public int languageRightMargin(int widthPx) {
        return Math.max(0, widthPx - (languageLeft + size));
    }

    public boolean overlaps() {
        if (controls < 2) {
            return false;
        }
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
