package org.telegram.ui.Feed;

import java.util.List;

/**
 * Where the profile posts tab sits. SharedMediaLayout still decides stories
 * the native way ({@code stories_pinned_available}) and only asks this helper
 * whether posts are present and which slot they take.
 * {@link #STORIES_TAB} and {@link #MOMENTS_TAB} are SharedMediaLayout.TAB_STORIES
 * and TAB_MOMENTS.
 */
public final class FeedProfileTabs {

    public static final int STORIES_TAB = 8;
    public static final int MOMENTS_TAB = 16;

    private FeedProfileTabs() {
    }

    /** An eligible user profile with a confirmed non-empty post homepage. */
    public static boolean show(boolean eligible, boolean hasPosts) {
        return eligible && hasPosts;
    }

    /**
     * Index for the posts tab, or -1 when it stays hidden.
     * A stories tab keeps its place. Posts go in the next slot, the same
     * way a media tab is appended only after {@code hasMedia[i] > 0}.
     */
    public static int insertIndex(List<Integer> tabIds, boolean show) {
        if (!show || tabIds == null) {
            return -1;
        }
        for (int i = 0; i < tabIds.size(); i++) {
            Integer id = tabIds.get(i);
            if (id != null && id.intValue() == STORIES_TAB) {
                return i + 1;
            }
        }
        return 0;
    }
}
