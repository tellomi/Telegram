package org.telegram.ui.tellomi.feed;

import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The compose button is a sibling of the feed content. Rebuilding the empty
 * state, the city list, or the posts clears only the content. Clearing the
 * page removes the button with it.
 */
public final class TellomiDiscoverComposeMount {

    public static final String CONTENT = "content";
    public static final String COMPOSE = "compose";

    private TellomiDiscoverComposeMount() {
    }

    /** What the feed, the empty state, and the city list do to their parent. */
    public static void clear(ViewGroup content) {
        if (content != null) {
            content.removeAllViews();
        }
    }

    /**
     * Page membership the feed actually uses: content is rebuilt, compose stays
     * on the page. {@link #clearPage()} is the bug where both share one parent.
     */
    public static final class Page {
        private final List<String> page = new ArrayList<>();
        private final List<String> content = new ArrayList<>();

        public Page(boolean compose) {
            page.add(CONTENT);
            if (compose) {
                page.add(COMPOSE);
            }
        }

        public void rebuildContent(String surface) {
            content.clear();
            if (surface != null) {
                content.add(surface);
            }
        }

        public void clearPage() {
            page.clear();
            content.clear();
        }

        public boolean composeAttached() {
            return page.contains(COMPOSE);
        }

        public List<String> content() {
            return Collections.unmodifiableList(content);
        }

        public List<String> pageChildren() {
            return Collections.unmodifiableList(page);
        }
    }
}
