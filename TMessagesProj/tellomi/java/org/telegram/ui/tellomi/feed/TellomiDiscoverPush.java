package org.telegram.ui.tellomi.feed;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.TellomiPack;

/**
 * System banner for a discover interaction. {@code from_id} stays the official
 * account and is not opened as a chat. The tap carries the post id.
 */
public final class TellomiDiscoverPush {

    public static final String EXTRA_POST = "tellomi_discover_post";

    public static final class Event {
        public final String kind;
        public final String postId;
        public final String actorName;
        public final String text;

        public Event(String kind, String postId, String actorName, String text) {
            this.kind = kind;
            this.postId = postId;
            this.actorName = actorName;
            this.text = text;
        }
    }

    private TellomiDiscoverPush() {
    }

    public static Event from(String locKey, String discover, String kind, String postId, String actorName, String text) {
        boolean flagged = "1".equals(discover) || (locKey != null && locKey.startsWith("DISCOVER_"));
        if (!flagged) {
            return null;
        }
        String resolved = kind == null || kind.isEmpty() ? kindFromLoc(locKey) : kind;
        if (resolved == null) {
            return null;
        }
        String id = TellomiDiscover.postId(postId);
        if (!"follow".equals(resolved) && id == null) {
            return null;
        }
        return new Event(resolved, id, actorName == null ? "" : actorName, text == null ? "" : text);
    }

    public static String kindFromLoc(String locKey) {
        if (locKey == null) {
            return null;
        }
        if (locKey.startsWith("DISCOVER_LIKE")) {
            return "like";
        }
        if (locKey.startsWith("DISCOVER_COMMENT")) {
            return "comment";
        }
        if (locKey.startsWith("DISCOVER_REPLY")) {
            return "reply";
        }
        if (locKey.startsWith("DISCOVER_MENTION")) {
            return "mention";
        }
        if (locKey.startsWith("DISCOVER_FOLLOW")) {
            return "follow";
        }
        if (locKey.startsWith("DISCOVER_REPOST")) {
            return "repost";
        }
        return null;
    }

    public static String phraseKey(String kind) {
        if ("like".equals(kind)) {
            return "Tellomi.Discover.NotifyLike";
        }
        if ("comment".equals(kind)) {
            return "Tellomi.Discover.NotifyComment";
        }
        if ("reply".equals(kind)) {
            return "Tellomi.Discover.NotifyReply";
        }
        if ("mention".equals(kind)) {
            return "Tellomi.Discover.NotifyMention";
        }
        if ("follow".equals(kind)) {
            return "Tellomi.Discover.NotifyFollow";
        }
        if ("repost".equals(kind)) {
            return "Tellomi.Discover.NotifyRepost";
        }
        return null;
    }

    /** Comment and reply text stays out of the banner when previews are off. */
    public static String banner(String kind, String text, boolean showPreview, String phrase) {
        String line = phrase == null ? "" : phrase;
        boolean preview = showPreview && text != null && !text.isEmpty() && ("comment".equals(kind) || "reply".equals(kind));
        if (!preview) {
            return line;
        }
        if (line.isEmpty()) {
            return text;
        }
        return line + "：" + text;
    }

    public static Uri openUri(String postId) {
        String url = TellomiDiscoverShare.publicUrl(BuildVars.LINK_HOST, postId);
        if (url == null) {
            return null;
        }
        return Uri.parse(url);
    }

    public static boolean present(int account, String locKey, JSONObject custom, JSONObject json, long when) {
        String discover = custom == null ? "" : custom.optString("discover", "");
        boolean flagged = "1".equals(discover) || (locKey != null && locKey.startsWith("DISCOVER_"));
        if (!flagged) {
            return false;
        }
        String kind = custom == null ? "" : custom.optString("kind", "");
        String postId = custom == null ? "" : custom.optString("post_id", "");
        String actor = "";
        if (json != null) {
            JSONArray args = json.optJSONArray("loc_args");
            if (args != null && args.length() > 0) {
                actor = args.optString(0, "");
            }
        }
        String text = json == null ? "" : json.optString("message", "");
        Event event = from(locKey, discover.isEmpty() ? null : discover, kind, postId, actor, text);
        if (event == null || !TellomiDiscoverNotify.enabled(account)) {
            return true;
        }
        show(account, event, when);
        return true;
    }

    private static void show(int account, Event event, long when) {
        try {
            android.content.Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            String language = "en";
            try {
                LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
                if (info != null && info.shortName != null) {
                    language = info.shortName;
                }
            } catch (Throwable ignored) {
            }
            String key = phraseKey(event.kind);
            String phrase = key == null ? "" : TellomiPack.text(key, language, TellomiPack.device());
            boolean preview = true;
            try {
                android.content.SharedPreferences preferences = org.telegram.messenger.MessagesController.getNotificationsSettings(account);
                if (preferences != null) {
                    long peer = preferences.getLong(TellomiDiscoverNotify.PEER_PREF, TellomiDiscoverNotify.OFFICIAL_USER_ID);
                    preview = preferences.getBoolean("content_preview_" + peer, true);
                }
            } catch (Throwable ignored) {
            }
            String body = banner(event.kind, event.text, preview, phrase);
            String title = event.actorName == null || event.actorName.isEmpty()
                ? TellomiPack.text("Tellomi.Discover.NotifySwitch", language, TellomiPack.device())
                : event.actorName;
            String channelId = "tellomi_discover_" + account;
            NotificationManager manager = (NotificationManager) context.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
            }
            Intent intent = new Intent(context, LaunchActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra("currentAccount", account);
            if (event.postId != null) {
                intent.putExtra(EXTRA_POST, event.postId);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, 770000 + account, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(when > 0 ? when : System.currentTimeMillis());
            manager.notify("tellomi_discover", account + 1, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
