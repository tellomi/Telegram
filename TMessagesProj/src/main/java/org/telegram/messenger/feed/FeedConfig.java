package org.telegram.messenger.feed;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

/**
 * Suggested defaults from the feed spec (SG-*). Change them here.
 * Open decisions use the spec's recommended option. See the pull request.
 */
public final class FeedConfig {

    private FeedConfig() {
    }

    /** TBD-15 option A. Host is one line so it can move without touching call sites. */
    public static final String DEFAULT_ENGINE_BASE = "https://content.tellomi.cn";

    public static final int PAGE_SIZE = 20;
    public static final long NEW_POSTS_POLL_MS = 60_000L;
    public static final int MAX_DRAFTS = 20;
    public static final int MAX_TEXT = 2000;
    public static final int TEXT_COUNTER_FROM = 1800;
    public static final int MAX_COMMENT = 1000;
    public static final int MAX_IMAGES = 12;
    public static final int MAX_VIDEOS = 3;
    public static final int MAX_VIDEO_SEC = 60;
    public static final int MAX_VOICE_SEC = 180;
    public static final int MAX_MENTIONS = 10;
    public static final int MAX_TOPICS = 3;
    public static final int TOPIC_NAME_MAX = 32;
    public static final int MAX_CONTENT_WIDTH_DP = 640;
    public static final int COLLAPSE_LINES = 5;
    public static final int UPLOAD_RETRIES = 3;
    public static final int MAX_PINNED = 3;
    public static final int COMMENT_PREVIEW_REPLIES = 2;

    /** SG-12. Full-screen viewer keeps pinch-zoom. */
    public static final boolean DOUBLE_TAP_LIKE = true;
    /** TBD-09. Comment hearts show a count and do not notify. */
    public static final boolean COMMENT_LIKES = true;
    /** TBD-06. Only public posts can be reposted or quoted. */
    public static final boolean ONLY_PUBLIC_CAN_REPOST = true;
    /** TBD-10. Direct reposts appear in following, with a "reposted" line. */
    public static final boolean FOLLOWING_INCLUDES_REPOSTS = true;
    /** SG-01. Dot, not a number. */
    public static final boolean UNREAD_DOT = true;

    public static final String LINK_HOST = "tell.cc";
    public static final String LINK_PREFIX = "https://tell.cc/m#";

    public static String engineBase(int account) {
        SharedPreferences prefs = MessagesController.getInstance(account).getMainSettings();
        String override = prefs.getString("feed_engine_base", "");
        if (override != null && override.startsWith("http")) {
            return trimSlash(override);
        }
        return DEFAULT_ENGINE_BASE;
    }

    public static void setEngineBase(int account, String url) {
        MessagesController.getInstance(account).getMainSettings().edit().putString("feed_engine_base", url == null ? "" : url).apply();
    }

    private static String trimSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
