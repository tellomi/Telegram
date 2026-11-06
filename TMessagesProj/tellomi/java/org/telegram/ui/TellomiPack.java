package org.telegram.ui;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Langpack keys for Tellomi Discover, settings, tabs, and sign-up.
 * A downloaded pack is read by key ({@code Tellomi.Discover.*}). The bundled
 * English and Chinese values match {@code tellomi/res/values} and {@code values-zh}.
 */
public final class TellomiPack {

    public interface Source {
        String get(String key);
    }

    public static final class Entry {
        public final String key;
        public final String en;
        public final String zh;
        /** Traditional Chinese. Null keeps the simplified string for zh-Hant. */
        public final String zhHant;

        Entry(String key, String en, String zh) {
            this(key, en, zh, null);
        }

        Entry(String key, String en, String zh, String zhHant) {
            this.key = key;
            this.en = en;
            this.zh = zh;
            this.zhHant = zhHant;
        }
    }

    private static final Entry[] ENTRIES = {
        entry("Tellomi.Discover.Recommend", "For You", "推荐"),
        entry("Tellomi.Discover.Following", "Following", "关注"),
        entry("Tellomi.Discover.Nearby", "Nearby", "同城"),
        entry("Tellomi.Discover.Compose", "Post", "发布"),
        entry("Tellomi.Discover.EmptyTitle", "No posts yet", "还没有帖子"),
        entry("Tellomi.Discover.EmptyText", "Posts from people you follow, and recommendations, show up here.", "关注的人和推荐会显示在这里。"),
        entry("Tellomi.Discover.Unavailable", "This post isn't available", "这条动态已不可见"),
        entry("Tellomi.Discover.Failed", "Couldn't load this. Pull to try again.", "暂时打不开，下拉再试一次。"),
        entry("Tellomi.Discover.CityPrompt", "Choose a city", "选择城市"),
        entry("Tellomi.Discover.CityPlaceholder", "City", "输入城市"),
        entry("Tellomi.Discover.UseLocation", "Use current location", "使用当前位置"),
        entry("Tellomi.Discover.ConfirmCity", "Done", "确定"),
        entry("Tellomi.Discover.CityDenied", "You can pick a city without location. Only the city is kept.", "可以不定位，直接从列表里选。只保存城市，不保存坐标。"),
        entry("Tellomi.Discover.Visibility", "Who can see this", "谁可以看"),
        entry("Tellomi.Discover.Everyone", "Everyone", "所有人"),
        entry("Tellomi.Discover.Contacts", "My Contacts", "我的联系人"),
        entry("Tellomi.Discover.CloseFriends", "Close Friends", "密友"),
        entry("Tellomi.Discover.Selected", "Selected People", "选定的用户"),
        entry("Tellomi.Discover.Exclude", "Exclude", "不给谁看"),
        entry("Tellomi.Discover.AudienceInvalid", "That list can't be used. Choose again.", "名单无效，请重新选择。"),
        entry("Tellomi.Discover.UserInvalid", "Someone on the list can't be added.", "名单里有人无法使用。"),
        entry("Tellomi.Discover.UserBlocked", "Someone on the list is blocked.", "名单里有人已互相拉黑。"),
        entry("Tellomi.Discover.Disabled", "Discover isn't available right now.", "发现暂时不可用。"),
        entry("Tellomi.Discover.NearbyEmpty", "No posts in this city yet.", "这个城市还没有帖子。"),
        entry("Tellomi.Discover.InvalidCity", "That city can't be used. Choose another.", "这个城市不能用，请重新选择。"),
        entry("Tellomi.Discover.NearbyUnavailable", "City posts are not available yet.", "同城暂时还不能刷新。"),
        entry("Tellomi.Discover.NearbyUnavailableHint", "You can still pick a city. A post keeps only the city.", "仍可以选城市。帖子只记录城市，不记录位置。"),
        entry("Tellomi.Discover.ChangeCity", "Change city", "更换城市"),
        entry("Tellomi.Discover.ComposeCity", "Posting in %s", "发布到 %s"),
        entry("Tellomi.Discover.ComposeCityNone", "No city on this post", "这条不标注城市"),
        entry("Tellomi.Discover.AiGenerated", "AI generated", "AI 生成"),
        entry("Tellomi.Discover.QuoteDeleted", "The original post was deleted by its author", "原动态已被作者删除"),
        entry("Tellomi.Discover.Publish", "Post", "发布"),
        entry("Tellomi.Discover.Mention", "Mention", "提及"),
        entry("Tellomi.Discover.MentionHint", "Write something. Use @ to mention a username.", "写点什么，用 @ 提及用户"),
        entry("Tellomi.Discover.MentionInvalid", "%s is not a valid username, so they won't be notified.", "%s 不是有效用户名，对方收不到提及。"),
        entry("Tellomi.Discover.MentionEmpty", "No matching username", "没有匹配的用户名"),
        entry("Tellomi.Discover.Feed", "Discover", "发现"),
        entry("Tellomi.Discover.HideFeed", "Hide Discover", "隐藏发现"),
        entry("Tellomi.Discover.HideContacts", "Hide Contacts", "隐藏联系人"),
        entry("Tellomi.Discover.NavigationTitle", "Navigation Bar", "导航栏"),
        entry("Tellomi.Discover.NavigationFooter", "Chats always stays in the bar.", "聊天始终显示，不能关闭。"),
        entry("Tellomi.Discover.FeedEmptyTitle", "Nothing here yet", "还没有内容"),
        entry("Tellomi.Discover.FeedEmptyText", "Posts will show up here.", "帖子会显示在这里。"),
        entry("Tellomi.Discover.FeedPostUnavailable", "This post can't be opened yet.", "这条帖子暂时无法打开。"),
        entry("Tellomi.Discover.Likes", "%d likes", "%d 赞"),
        entry("Tellomi.Discover.Like", "Like", "赞"),
        entry("Tellomi.Discover.Liked", "Liked", "已赞"),
        entry("Tellomi.Discover.Comments", "%d comments", "%d 评论"),
        entry("Tellomi.Discover.Comment", "Comment", "评论"),
        entry("Tellomi.Discover.CommentHint", "Write a comment", "写一条评论"),
        entry("Tellomi.Discover.Reply", "Reply", "回复"),
        entry("Tellomi.Discover.DeleteComment", "Delete", "删除"),
        entry("Tellomi.Discover.CommentPending", "Pending review. Not public yet.", "审核中，尚未公开"),
        entry("Tellomi.Discover.LoadMore", "More comments", "更多评论"),
        entry("Tellomi.Discover.ProfileRequired", "Complete your profile before commenting.", "评论前需要先完善个人资料。"),
        entry("Tellomi.Discover.InvalidText", "A comment needs 1 to 500 characters.", "评论需要 1 到 500 字。"),
        entry("Tellomi.Discover.InvalidNonce", "This comment was not sent. Try again.", "这条评论没能提交，请再试一次。"),
        entry("Tellomi.Discover.InvalidParent", "That comment can't be replied to.", "这条回复的对象不可用。"),
        entry("Tellomi.Discover.CommentsClosed", "Comments are closed.", "评论已关闭。"),
        entry("Tellomi.Discover.ReplyNotAllowed", "Replies are not allowed on that comment.", "不能回复这条评论。"),
        entry("Tellomi.Discover.HarassmentGuard", "This comment was blocked.", "这条评论被拦住了。"),
        entry("Tellomi.Discover.NotCommentable", "This post can't be commented on.", "这条动态不能评论。"),
        entry("Tellomi.Discover.BadCursor", "This comment page expired. Open the post again.", "评论列表过期了，请重新打开。"),
        entry("Tellomi.Discover.CommentNotFound", "That comment is no longer there.", "这条评论已经不在了。"),
        entry("Tellomi.Discover.Photos", "%d photos", "%d 张图片"),
        entry("Tellomi.Discover.TellomiId", "Tellomi ID: %s", "Tellomi 号：%s"),
        entry("Tellomi.Discover.Report", "Report", "举报"),
        entry("Tellomi.Discover.ReportSent", "Report sent", "举报已发送"),
        entry("Tellomi.Discover.ReportFailed", "Couldn't send the report", "举报没有发出去"),
        entry("Tellomi.Discover.Report.Spam", "Spam", "垃圾信息"),
        entry("Tellomi.Discover.Report.Violence", "Violence", "暴力"),
        entry("Tellomi.Discover.Report.Pornography", "Pornography", "色情"),
        entry("Tellomi.Discover.Report.Child", "Child abuse", "虐待儿童"),
        entry("Tellomi.Discover.Report.Drugs", "Illegal drugs", "毒品"),
        entry("Tellomi.Discover.Report.Personal", "Personal details", "侵犯隐私"),
        entry("Tellomi.Discover.Report.Other", "Other", "其他"),
        entry("Tellomi.Discover.Forward", "Forward", "转发"),
        entry("Tellomi.Discover.Repost", "Direct forward", "直接转发"),
        entry("Tellomi.Discover.Quote", "Quote", "引用转发"),
        entry("Tellomi.Discover.ShareChat", "Share in Tellomi", "分享到 tellomi 聊天"),
        entry("Tellomi.Discover.ShareExternal", "Share to another app", "分享到外部 App"),
        entry("Tellomi.Discover.Poll", "Poll", "投票"),
        entry("Tellomi.Discover.PollAdd", "Add an option", "添加选项"),
        entry("Tellomi.Discover.VideoAdd", "Add a video", "添加视频"),
        entry("Tellomi.Discover.VideoReplace", "Replace", "更换"),
        entry("Tellomi.Discover.VideoRemove", "Remove", "移除"),
        entry("Tellomi.Discover.VideoHint", "Videos can be up to 60 seconds.", "视频最长 60 秒。"),
        entry("Tellomi.Discover.VideoTooLong", "This video is longer than 60 seconds. Pick a shorter one.", "这段视频超过 60 秒，请换一段更短的。"),
        entry("Tellomi.Discover.VideoUnknown", "Couldn't read how long this video is. Pick another.", "读不到这段视频的时长，请换一个。"),
        entry("Tellomi.Discover.VideoSelected", "Selected · %s", "已选择 · %s"),
        entry("Tellomi.Discover.VideoNotUploaded", "Video upload isn't available yet. Remove it to post the text.", "发现还不能上传视频，移除后可以发布文字。"),
        entry("Tellomi.Discover.VideoReading", "Reading the length…", "正在读取时长…"),
        entry("Tellomi.Discover.PhotoAdd", "Add photos", "添加图片", "添加圖片"),
        entry("Tellomi.Discover.PhotoHint", "Up to 10 photos. JPEG, PNG, or WebP. Each one up to 20 MB.", "最多 10 张，JPEG、PNG 或 WebP，单张不超过 20MB。", "最多 10 張，JPEG、PNG 或 WebP，單張不超過 20MB。"),
        entry("Tellomi.Discover.PhotoCount", "%d selected", "已选 %d 张", "已選 %d 張"),
        entry("Tellomi.Discover.PhotoRemove", "Remove", "移除", "移除"),
        entry("Tellomi.Discover.PhotoLimit", "You can add up to 10 photos.", "一次最多 10 张图片。", "一次最多 10 張圖片。"),
        entry("Tellomi.Discover.PhotoTooLarge", "This photo is larger than 20 MB.", "这张图片超过 20MB。", "這張圖片超過 20MB。"),
        entry("Tellomi.Discover.PhotoType", "Use a JPEG, PNG, or WebP photo.", "只支持 JPEG、PNG 或 WebP。", "只支援 JPEG、PNG 或 WebP。"),
        entry("Tellomi.Discover.PhotoUploadFailed", "Couldn't upload the photo.", "图片上传失败。", "圖片上傳失敗。"),
        entry("Tellomi.Discover.PhotoNetwork", "The network failed while uploading.", "网络不好，图片没有传上去。", "網路不好，圖片沒有傳上去。"),
        entry("Tellomi.Discover.PhotoUploading", "Uploading photos…", "正在上传图片…", "正在上傳圖片…"),
        entry("Tellomi.Discover.Notifications", "Notifications", "通知"),
        entry("Tellomi.Discover.NotificationsEmpty", "No notifications yet", "还没有通知"),
        entry("Tellomi.Discover.NotifyLike", "liked your post", "赞了你的帖子"),
        entry("Tellomi.Discover.NotifyComment", "commented on your post", "评论了你的帖子"),
        entry("Tellomi.Discover.NotifyReply", "replied to your comment", "回复了你的评论"),
        entry("Tellomi.Discover.NotifyMention", "mentioned you in a post", "在帖子里提到了你"),
        entry("Tellomi.Discover.NotifyFollow", "followed you", "关注了你"),
        entry("Tellomi.Discover.NotifyRepost", "forwarded your post", "转发了你的帖子"),
        entry("Tellomi.Discover.NotifySwitch", "Discover", "发现"),
        entry("Tellomi.Discover.NotifySwitchInfo", "Likes, comments, mentions, follows, and forwards", "赞、评论、提及、关注和转发"),
        entry("Tellomi.Discover.Reposted", "Forwarded", "已转发"),
        entry("Tellomi.Discover.LinkPreviewLoading", "Fetching the link preview.", "正在获取链接预览。"),
        entry("Tellomi.SignUp.AvatarFailed", "Couldn't upload the profile photo (%s)", "头像上传失败（%s）"),
        entry("Tellomi.SignUp.AvatarRetry", "Retry", "重试"),
        entry("Tellomi.Settings.Group.Account", "Account", "账号"),
        entry("Tellomi.Settings.Group.General", "General", "通用"),
        entry("Tellomi.Settings.Group.Support", "Support", "支持"),
        entry("Tellomi.Settings.Help", "Help Center", "帮助中心"),
        entry("Tellomi.Settings.Card.Phone", "Phone", "手机号"),
        entry("Tellomi.Settings.Card.Email", "Email", "邮箱"),
        entry("Tellomi.Settings.Card.Username", "Username", "用户名")
    };

    private TellomiPack() {
    }

    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        Collections.addAll(list, ENTRIES);
        return list;
    }

    public static Entry find(String key) {
        if (key == null) {
            return null;
        }
        for (int i = 0; i < ENTRIES.length; i++) {
            if (ENTRIES[i].key.equals(key)) {
                return ENTRIES[i];
            }
        }
        return null;
    }

    /**
     * Android resource name for a langpack key. {@code .}, {@code -}, and
     * {@code :} become {@code _}. City lookup keys already use underscores
     * ({@code Tellomi.Discover.City.cn_hong_kong}).
     */
    public static String resourceName(String key) {
        if (key == null) {
            return "";
        }
        return key.replace('.', '_').replace('-', '_').replace(':', '_');
    }

    /** Hant, or TW / HK / MO without an explicit Hans tag. */
    public static boolean traditionalChinese(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return false;
        }
        String value = languageCode.toLowerCase(Locale.US).replace('_', '-');
        if (value.contains("hans")) {
            return false;
        }
        if (value.contains("hant")) {
            return true;
        }
        return value.endsWith("-tw") || value.endsWith("-hk") || value.endsWith("-mo");
    }

    public static boolean chinese(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            return false;
        }
        String value = languageCode.toLowerCase(Locale.US).replace('_', '-');
        return "zh".equals(value) || value.startsWith("zh-");
    }

    public static String bundled(String key, String languageCode) {
        Entry entry = find(key);
        if (entry == null) {
            return null;
        }
        if (traditionalChinese(languageCode) && entry.zhHant != null && !entry.zhHant.isEmpty()) {
            return entry.zhHant;
        }
        return chinese(languageCode) ? entry.zh : entry.en;
    }

    /** A pack hit. Null when the source has no real value for the key. */
    public static String override(Source source, String key) {
        if (source == null || key == null) {
            return null;
        }
        try {
            String value = source.get(key);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("LOC_ERR")) {
                return null;
            }
            return trimmed;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String text(String key, String languageCode, Source source) {
        String packed = override(source, key);
        if (packed != null) {
            return packed;
        }
        String bundled = bundled(key, languageCode);
        return bundled == null ? "" : bundled;
    }

    /**
     * The active langpack, then the flavor string resource. A failure returns null
     * so the caller can use the bundled value for that key.
     */
    public static Source device() {
        return new Source() {
            @Override
            public String get(String key) {
                try {
                    return readDevice(key);
                } catch (Throwable ignored) {
                    return null;
                }
            }
        };
    }

    private static String readDevice(String key) {
        android.content.Context context = ApplicationLoader.applicationContext;
        int res = 0;
        if (context != null) {
            res = context.getResources().getIdentifier(resourceName(key), "string", context.getPackageName());
        }
        String value = LocaleController.getString(key, res);
        if (value == null || value.startsWith("LOC_ERR")) {
            return null;
        }
        return value;
    }

    private static Entry entry(String key, String en, String zh) {
        return new Entry(key, en, zh, null);
    }

    private static Entry entry(String key, String en, String zh, String zhHant) {
        return new Entry(key, en, zh, zhHant);
    }
}
