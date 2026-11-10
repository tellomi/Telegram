package org.telegram.ui.Feed;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.util.Locale;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

public final class FeedUi {

    private FeedUi() {
    }

    /**
     * Langpack key shared with iOS ({@code Moments.*}), then the Android
     * resource for the active language. A Chinese UI does not stay on an
     * English langpack value.
     */
    public static String s(String key, int res) {
        String value = LocaleController.getString(key, res);
        if (value == null || value.startsWith("LOC_ERR")) {
            value = LocaleController.getString(res);
        }
        if (prefersChinese() && value != null && value.equals(resourceString(Locale.ENGLISH, res))) {
            String zh = resourceString(Locale.SIMPLIFIED_CHINESE, res);
            if (zh != null && zh.length() > 0) {
                return zh;
            }
        }
        return value == null ? "" : value;
    }

    private static boolean prefersChinese() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        String language = locale.getLanguage();
        return language != null && language.toLowerCase(Locale.US).startsWith("zh");
    }

    /**
     * Main tabs call this while the first screen is built. Creating a
     * configuration context can throw (NotFoundException or NPE inside
     * AssetManager on some devices). That must not kill the process.
     */
    private static String resourceString(Locale locale, int res) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || res == 0 || locale == null) {
            return null;
        }
        try {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            if (Build.VERSION.SDK_INT >= 24) {
                config.setLocales(new android.os.LocaleList(locale));
            }
            return context.createConfigurationContext(config).getResources().getString(res);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean reduceMotion() {
        if (AndroidUtilities.getAnimatorDurationScale() == 0f) {
            return true;
        }
        return Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    public static String timeAgo(long millis) {
        if (millis <= 0) {
            return "";
        }
        long delta = Math.max(0, System.currentTimeMillis() - millis);
        if (delta < 60_000L) {
            return getString(R.string.RightNow);
        }
        if (delta < 3600_000L) {
            return formatPluralString("MinutesAgo", (int) (delta / 60_000L));
        }
        if (delta < 86400_000L) {
            return formatPluralString("HoursAgo", (int) (delta / 3600_000L));
        }
        return LocaleController.formatDateChat(millis / 1000L, false);
    }

    public static String audienceLabel(String kind) {
        if (kind == null || kind.length() == 0 || "public".equals(kind) || "everyone".equals(kind)) {
            return "";
        }
        if ("mutual_followers".equals(kind) || "friends".equals(kind)) {
            return FeedUi.s("Moments.Audience.Mutual", R.string.FeedAudienceMutual);
        }
        if ("contacts".equals(kind)) {
            return getString(R.string.StoryPrivacyOptionContacts);
        }
        if ("close_friends".equals(kind)) {
            return getString(R.string.StoryPrivacyOptionCloseFriends);
        }
        if ("selected".equals(kind) || "include".equals(kind)) {
            return getString(R.string.StoryPrivacyOptionSelectedContacts);
        }
        if ("private".equals(kind) || "only_me".equals(kind) || "self".equals(kind)) {
            return FeedUi.s("Moments.Settings.OnlyMe", R.string.FeedAudiencePrivate);
        }
        if ("followers".equals(kind) || "followers_only".equals(kind)) {
            return FeedUi.s("Moments.Audience.Followers", R.string.FeedAudienceFollowers);
        }
        return FeedUi.s("Moments.Audience.Custom", R.string.FeedAudienceCustom);
    }

    public static void openProfile(BaseFragment fragment, FeedModels.Author author) {
        if (fragment == null || author == null || author.userId == 0) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", author.userId);
        args.putBoolean("open_moments", true);
        fragment.presentFragment(new ProfileActivity(args));
    }

    public static void bindAvatar(BackupImageView view, FeedModels.Author author, int account) {
        TLRPC.User user = author != null && author.userId != 0 ? org.telegram.messenger.MessagesController.getInstance(account).getUser(author.userId) : null;
        if (user != null) {
            AvatarDrawable drawable = new AvatarDrawable(user);
            view.setForUserOrChat(user, drawable);
            return;
        }
        AvatarDrawable drawable = new AvatarDrawable();
        String name = author == null ? "" : (author.name.length() > 0 ? author.name : author.username);
        drawable.setInfo(author == null ? 0 : author.userId, name, null);
        String avatar = author == null ? "" : author.avatarUrl;
        if ((avatar == null || avatar.length() == 0) && author != null && author.avatarKey != null && author.avatarKey.length() > 0) {
            avatar = FeedConfig.mediaUrl(account, author.avatarKey);
        }
        if (avatar != null && avatar.length() > 0) {
            view.setImage(avatar, "40_40", drawable);
        } else {
            view.setImage(null, null, drawable);
        }
    }

    public static String displayName(FeedModels.Author author, int account) {
        if (author == null) {
            return "";
        }
        if (author.userId != 0) {
            TLRPC.User user = org.telegram.messenger.MessagesController.getInstance(account).getUser(author.userId);
            if (user != null) {
                return UserObject.getUserName(user);
            }
        }
        if (!TextUtils.isEmpty(author.name)) {
            return author.name;
        }
        if (!TextUtils.isEmpty(author.username)) {
            return "@" + author.username;
        }
        return getString(R.string.HiddenName);
    }

    public static TextView text(Context context, int size, int colorKey) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(colorKey));
        view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    public static ImageView icon(Context context, int res, String description) {
        ImageView view = new ImageView(context);
        view.setImageResource(res);
        view.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.SRC_IN));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        view.setMinimumWidth(dp(48));
        view.setMinimumHeight(dp(48));
        return view;
    }

    public static View empty(Context context, String message, String action, Runnable click) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(32), dp(48), dp(32), dp(32));
        TextView text = new TextView(context);
        text.setText(message);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(SharedConfig.fontSize);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (action != null) {
            TextView button = new TextView(context);
            button.setText(action);
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            button.setTextSize(16);
            button.setTypeface(AndroidUtilities.bold());
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(16), dp(12), dp(16), dp(12));
            button.setOnClickListener(v -> {
                if (click != null) {
                    click.run();
                }
            });
            box.addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 12, 0, 0));
        }
        return box;
    }

    public static void playHeart(FrameLayout host) {
        ImageView heart = new ImageView(host.getContext());
        heart.setImageResource(R.drawable.media_like_active);
        heart.setColorFilter(new PorterDuffColorFilter(0xFFFF2E38, PorterDuff.Mode.SRC_IN));
        host.addView(heart, LayoutHelper.createFrame(64, 64, Gravity.CENTER));
        if (reduceMotion()) {
            heart.setAlpha(0f);
            heart.animate().alpha(1f).setDuration(120).withEndAction(() -> heart.animate().alpha(0f).setDuration(180).withEndAction(() -> host.removeView(heart)).start()).start();
            return;
        }
        heart.setScaleX(0.6f);
        heart.setScaleY(0.6f);
        heart.animate().scaleX(1f).scaleY(1f).setDuration(180).withEndAction(() -> heart.animate().alpha(0f).setDuration(220).withEndAction(() -> host.removeView(heart)).start()).start();
    }

    public static String linkFor(String id) {
        return FeedConfig.LINK_PREFIX + id;
    }

    public static boolean isSelf(int account, FeedModels.Author author) {
        return author != null && author.userId != 0 && author.userId == UserConfig.getInstance(account).getClientUserId();
    }

}
