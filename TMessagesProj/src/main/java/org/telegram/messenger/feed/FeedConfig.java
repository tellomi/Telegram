package org.telegram.messenger.feed;

import android.content.SharedPreferences;

import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.net.URL;

/**
 * Suggested defaults from the feed spec (SG-*). Change them here.
 * Open decisions use the spec's recommended option. See the pull request.
 */
public final class FeedConfig {

    private FeedConfig() {
    }

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

    public static final String LINK_HOST = BuildVars.LINK_HOST;
    public static final String LINK_PREFIX = "https://" + LINK_HOST + "/m#";

    private static final String[] mediaBase = new String[UserConfig.MAX_ACCOUNT_COUNT];

    /**
     * True only when help.getAppConfig published both keys, or a debug build
     * has an explicit https origin in feed_engine_base. Never guesses a host.
     * discover.engineCredential.region is cn or global and is not consulted.
     */
    public static boolean enabled(int account) {
        return engineBase(account).length() > 0;
    }

    public static String engineBase(int account) {
        String debug = debugOverride(account);
        if (debug != null) {
            return debug;
        }
        AppGlobalConfig config = AppGlobalConfig.getInstance(account);
        if (config == null || !config.tellomiDiscoverFeedEnabled.get()) {
            return "";
        }
        String origin = httpsOrigin(config.tellomiDiscoverFeedBaseUrl.get());
        return origin == null ? "" : origin;
    }

    /** Debug builds only. Release builds ignore the preference. */
    public static void setEngineBase(int account, String url) {
        if (!BuildVars.DEBUG_VERSION) {
            return;
        }
        String origin = httpsOrigin(url);
        MessagesController.getInstance(account).getMainSettings().edit()
                .putString("feed_engine_base", origin == null ? "" : origin)
                .apply();
    }

    public static void rememberMediaBase(int account, String raw) {
        String prefix = httpsPrefix(raw);
        if (prefix == null || account < 0 || account >= mediaBase.length) {
            return;
        }
        mediaBase[account] = prefix;
        MessagesController.getInstance(account).getMainSettings().edit().putString("feed_media_base", prefix).apply();
    }

    /** Absolute http(s) stays as-is. A relative key is prefixed with media_base_url. */
    public static String mediaUrl(int account, String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.length() == 0) {
            return "";
        }
        if (keyOrUrl.startsWith("https://") || keyOrUrl.startsWith("http://")) {
            return keyOrUrl;
        }
        String base = mediaBase(account);
        if (base.length() == 0) {
            return "";
        }
        String key = keyOrUrl;
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        return base + "/" + key;
    }

    private static String mediaBase(int account) {
        if (account < 0 || account >= mediaBase.length) {
            return "";
        }
        if (mediaBase[account] == null) {
            String stored = MessagesController.getInstance(account).getMainSettings().getString("feed_media_base", "");
            mediaBase[account] = httpsPrefix(stored);
            if (mediaBase[account] == null) {
                mediaBase[account] = "";
            }
        }
        return mediaBase[account];
    }

    private static String debugOverride(int account) {
        if (!BuildVars.DEBUG_VERSION) {
            return null;
        }
        SharedPreferences prefs = MessagesController.getInstance(account).getMainSettings();
        return httpsOrigin(prefs.getString("feed_engine_base", ""));
    }

    /** https://host[:port] and nothing else. */
    static String httpsOrigin(String raw) {
        String value = trimSlash(raw);
        if (value == null) {
            return null;
        }
        try {
            URL url = new URL(value);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            if (url.getUserInfo() != null && url.getUserInfo().length() > 0) {
                return null;
            }
            if (url.getHost() == null || url.getHost().length() == 0) {
                return null;
            }
            String path = url.getPath();
            if (path != null && path.length() > 0 && !"/".equals(path)) {
                return null;
            }
            if (url.getQuery() != null || url.getRef() != null) {
                return null;
            }
            int port = url.getPort();
            if (port == 0 || port > 65535) {
                return null;
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    /** https origin plus an optional path. Used for media_base_url. */
    private static String httpsPrefix(String raw) {
        String value = trimSlash(raw);
        if (value == null) {
            return null;
        }
        try {
            URL url = new URL(value);
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            if (url.getUserInfo() != null && url.getUserInfo().length() > 0) {
                return null;
            }
            if (url.getHost() == null || url.getHost().length() == 0) {
                return null;
            }
            if (url.getQuery() != null || url.getRef() != null) {
                return null;
            }
            int port = url.getPort();
            if (port == 0 || port > 65535) {
                return null;
            }
            String path = url.getPath();
            if (path != null && path.indexOf(' ') >= 0) {
                return null;
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim();
        if (value.length() == 0) {
            return null;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.length() == 0 ? null : value;
    }
}
