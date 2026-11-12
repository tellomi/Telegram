package org.telegram.ui.Feed;

/**
 * Which profile tabs the Moments extension shows. The shared-media row is laid
 * out after ProfileActivity's info rows, so the following and followers lines
 * start inside that row and cannot cover the info above it.
 */
public final class FeedProfileTabs {

    private FeedProfileTabs() {
    }

    /** Other user chats. Own-profile stories view, story search, and secret chats stay on the stock tabs. */
    public static boolean showsMoments(boolean userDialog, boolean encrypted, boolean searchingStories, boolean storiesView) {
        return userDialog && !encrypted && !searchingStories && !storiesView;
    }

    /**
     * Native Stories uses the same Simplified Chinese title as the Moments tab.
     * Bot previews reuse that slot with a different title, so they stay.
     */
    public static boolean hidesNativeStories(boolean showsMoments, boolean botPreviews) {
        return showsMoments && !botPreviews;
    }

    /**
     * @param infoBottom bottom of the profile info rows
     * @param sharedMediaTop top of the shared-media row
     * @param contentTop offset of the following line inside that row
     * @return true when the following line is drawn over the info rows
     */
    public static boolean followingCoversInfo(int infoBottom, int sharedMediaTop, int contentTop) {
        return sharedMediaTop + contentTop < infoBottom;
    }
}
