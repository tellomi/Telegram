package org.telegram.ui.tellomi.feed;

/**
 * Bottom-tab visibility while the Discover list scrolls.
 * An empty page that cannot scroll stays visible. The top of the list
 * shows the bar again. Down and up each need their own distance so a
 * one-pixel reverse does not flicker.
 */
public final class TellomiDiscoverTabScroll {

    private final int hidePx;
    private final int showPx;
    private int down;
    private int up;
    private boolean hidden;

    public TellomiDiscoverTabScroll(int hidePx, int showPx) {
        this.hidePx = Math.max(1, hidePx);
        this.showPx = Math.max(1, showPx);
    }

    /** @return true when the tabs should be visible */
    public boolean onScroll(int scrollY, int dy, boolean canScroll) {
        if (!canScroll || scrollY <= 0) {
            down = 0;
            up = 0;
            hidden = false;
            return true;
        }
        if (dy > 0) {
            up = 0;
            down += dy;
            if (down >= hidePx) {
                hidden = true;
            }
        } else if (dy < 0) {
            down = 0;
            up += -dy;
            if (up >= showPx) {
                hidden = false;
            }
        }
        return !hidden;
    }

    public boolean visible() {
        return !hidden;
    }
}
