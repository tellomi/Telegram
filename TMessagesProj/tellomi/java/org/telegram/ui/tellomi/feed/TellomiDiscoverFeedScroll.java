package org.telegram.ui.tellomi.feed;

/**
 * One Discover list scroll. The feed used to install the tab-bar listener and
 * then replace it with a listener that only asks for the next page.
 */
public final class TellomiDiscoverFeedScroll {

    private TellomiDiscoverFeedScroll() {
    }

    public static boolean nearEnd(int scrollY, int viewHeight, int contentHeight, int tailPx) {
        return contentHeight > 0 && scrollY + viewHeight >= contentHeight - tailPx;
    }

    /**
     * What one listener must do. {@code pageFeed} is false for the city list.
     * A replacement listener that only returns the page flag drops the tabs.
     */
    public static Effects onScroll(boolean pageFeed, int scrollY, int viewHeight, int contentHeight, int tailPx) {
        return new Effects(true, pageFeed && nearEnd(scrollY, viewHeight, contentHeight, tailPx));
    }

    /** A scroll view keeps one listener. The second call is what dropped the tab bar. */
    public static final class ListenerSlot {
        private String active;

        public void set(String name) {
            active = name;
        }

        public String active() {
            return active;
        }
    }

    public static final class Effects {
        public final boolean tabs;
        public final boolean page;

        public Effects(boolean tabs, boolean page) {
            this.tabs = tabs;
            this.page = page;
        }
    }
}
