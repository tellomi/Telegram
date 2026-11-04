package org.telegram.ui;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Langpack keys for Tellomi Discover, tabs, and sign-up.
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

        Entry(String key, String en, String zh) {
            this.key = key;
            this.en = en;
            this.zh = zh;
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
        entry("Tellomi.Discover.AiGenerated", "AI generated", "AI 生成"),
        entry("Tellomi.Discover.QuoteDeleted", "The original post was deleted by its author", "原动态已被作者删除"),
        entry("Tellomi.Discover.Publish", "Post", "发布"),
        entry("Tellomi.Discover.LegacyEmptyTitle", "Nothing here yet", "还没有内容"),
        entry("Tellomi.Discover.LegacyEmptyText", "Posts will show up here.", "帖子会显示在这里。"),
        entry("Tellomi.Discover.LegacyPostUnavailable", "This post can't be opened yet.", "这条帖子暂时无法打开。"),
        entry("Tellomi.Discover.Likes", "%d likes", "%d 赞"),
        entry("Tellomi.Discover.Comments", "%d comments", "%d 评论"),
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
        entry("Tellomi.Discover.Tab.Feed", "Discover", "发现"),
        entry("Tellomi.Discover.Tab.HideFeed", "Hide Discover", "隐藏发现"),
        entry("Tellomi.Discover.Tab.HideContacts", "Hide Contacts", "隐藏联系人"),
        entry("Tellomi.Discover.Tab.Navigation", "Navigation Bar", "导航栏"),
        entry("Tellomi.Discover.Tab.NavigationFooter", "Chats and Settings always stay in the bar.", "聊天和设置始终显示，不能关闭。"),
        entry("Tellomi.Discover.Tab.EmptyTitle", "Nothing here yet", "还没有内容"),
        entry("Tellomi.Discover.Tab.EmptyText", "Posts will show up here.", "帖子会显示在这里。"),
        entry("Tellomi.Discover.Tab.PostUnavailable", "This post can't be opened yet.", "这条帖子暂时无法打开。"),
        entry("Tellomi.Signup.AvatarFailed", "Couldn't upload the profile photo", "头像上传失败"),
        entry("Tellomi.Signup.AvatarRetry", "Retry", "重试")
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

    /** Android resource name for a langpack key. Dots and hyphens are not legal in {@code strings.xml}. */
    public static String resourceName(String key) {
        if (key == null) {
            return "";
        }
        return key.replace('.', '_').replace('-', '_').replace(':', '_');
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
        return new Entry(key, en, zh);
    }
}
