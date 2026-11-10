package org.telegram.ui.Feed;

import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.ui.LaunchActivity;

/**
 * https://tell.cc/m#&lt;id&gt; and tellomi://tell.cc/m#&lt;id&gt; (SG-13).
 */
public final class FeedLinks {

    private FeedLinks() {
    }

    public static boolean open(Uri uri) {
        String id = idOf(uri);
        if (id == null) {
            return false;
        }
        LaunchActivity activity = LaunchActivity.instance;
        if (activity == null) {
            return false;
        }
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.ui.ActionBar.BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null) {
                FeedCommentSheet.open(fragment, id, false);
            }
        });
        return true;
    }

    public static boolean open(String url) {
        if (url == null) {
            return false;
        }
        return open(Uri.parse(url));
    }

    public static String idOf(Uri uri) {
        if (uri == null) {
            return null;
        }
        String host = uri.getHost();
        String scheme = uri.getScheme();
        boolean hostOk = FeedConfig.LINK_HOST.equalsIgnoreCase(host);
        boolean schemeOk = "tellomi".equalsIgnoreCase(scheme) && hostOk;
        boolean web = ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && hostOk;
        if (!hostOk && !schemeOk && !web) {
            return null;
        }
        String fragment = uri.getFragment();
        if (fragment != null && fragment.length() > 0) {
            return fragment;
        }
        String path = uri.getPath();
        if (path != null && path.startsWith("/m/")) {
            return path.substring(3);
        }
        return null;
    }

    public static boolean handleIntent(android.content.Intent intent) {
        if (intent == null || intent.getData() == null) {
            return false;
        }
        if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() == null) {
            return false;
        }
        return open(intent.getData());
    }
}
