package org.telegram.ui.Feed;

import android.content.Context;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.util.HashSet;
import java.util.WeakHashMap;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public final class FeedUi {

    private static final WeakHashMap<BackupImageView, String> avatarKeys = new WeakHashMap<>();
    private static final HashSet<Long> avatarReloads = new HashSet<>();

    private FeedUi() {
    }

    public static void openEntry(BaseFragment fragment, FeedModels.Post post, String entry, boolean focus) {
        if (fragment == null || post == null || post.pending || post.unavailable) {
            return;
        }
        openEntry(fragment, post.id, entry, focus);
    }

    public static void openEntry(BaseFragment fragment, String postId, String entry, boolean focus) {
        openEntry(fragment, postId, "", entry, focus);
    }

    public static void openEntry(BaseFragment fragment, String postId, String commentId, String entry, boolean focus) {
        if (fragment == null || postId == null || postId.length() == 0) {
            return;
        }
        if (FeedContract.opensStandaloneDetail(entry)) {
            fragment.presentFragment(FeedDetailActivity.forPost(postId, commentId));
            return;
        }
        FeedCommentSheet.open(fragment, postId, focus);
    }

    public static boolean reduceMotion() {
        if (AndroidUtilities.getAnimatorDurationScale() == 0f) {
            return true;
        }
        return Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    public static String timeAgo(long millis) {
        FeedContract.RelativeBucket bucket = FeedContract.relativeBucket(System.currentTimeMillis(), millis);
        if (bucket.unit.length() == 0) {
            return "";
        }
        String language = "";
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        if (info != null && info.shortName != null) {
            language = info.shortName;
        }
        String shared = sharedTimer(bucket);
        if (shared.length() == 0 || shared.startsWith("LOC_ERR") || FeedContract.replaceEnglishTimer(language, shared, bucket.english)) {
            return momentsTimer(bucket);
        }
        return shared;
    }

    private static String sharedTimer(FeedContract.RelativeBucket bucket) {
        if ("now".equals(bucket.unit)) {
            return LocaleController.getString("RightNow", R.string.RightNow);
        }
        if ("minute".equals(bucket.unit)) {
            return LocaleController.formatPluralString("MinutesAgo", bucket.count);
        }
        if ("hour".equals(bucket.unit)) {
            return LocaleController.formatPluralString("HoursAgo", bucket.count);
        }
        if ("day".equals(bucket.unit)) {
            return LocaleController.formatPluralString("DaysAgo", bucket.count);
        }
        return "";
    }

    private static String momentsTimer(FeedContract.RelativeBucket bucket) {
        if ("now".equals(bucket.unit)) {
            return LocaleController.getString("Moments_Time_Now", R.string.Moments_Time_Now);
        }
        if ("minute".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Minutes", R.string.Moments_Time_Minutes, bucket.count);
        }
        if ("hour".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Hours", R.string.Moments_Time_Hours, bucket.count);
        }
        if ("day".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Days", R.string.Moments_Time_Days, bucket.count);
        }
        if ("week".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Weeks", R.string.Moments_Time_Weeks, bucket.count);
        }
        if ("month".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Months", R.string.Moments_Time_Months, bucket.count);
        }
        if ("year".equals(bucket.unit)) {
            return LocaleController.formatString("Moments_Time_Years", R.string.Moments_Time_Years, bucket.count);
        }
        return "";
    }

    public static String audienceLabel(String kind) {
        if (kind == null || kind.length() == 0 || "public".equals(kind) || "everyone".equals(kind)) {
            return "";
        }
        if ("mutual_followers".equals(kind) || "friends".equals(kind)) {
            return LocaleController.getString("Moments_Audience_Mutual", R.string.Moments_Audience_Mutual);
        }
        if ("contacts".equals(kind)) {
            return LocaleController.getString("Moments_Audience_Contacts", R.string.Moments_Audience_Contacts);
        }
        if ("close_friends".equals(kind)) {
            return LocaleController.getString("Moments_Audience_CloseFriends", R.string.Moments_Audience_CloseFriends);
        }
        if ("selected".equals(kind) || "include".equals(kind)) {
            return LocaleController.getString("Moments_Audience_Selected", R.string.Moments_Audience_Selected);
        }
        if ("private".equals(kind) || "only_me".equals(kind) || "self".equals(kind)) {
            return LocaleController.getString("Moments_Settings_OnlyMe", R.string.Moments_Settings_OnlyMe);
        }
        if ("followers".equals(kind) || "followers_only".equals(kind)) {
            return LocaleController.getString("Moments_Audience_Followers", R.string.Moments_Audience_Followers);
        }
        return LocaleController.getString("Moments_Audience_Custom", R.string.Moments_Audience_Custom);
    }

    public static void openProfile(BaseFragment fragment, FeedModels.Author author) {
        if (fragment == null || author == null || author.userId == 0) {
            return;
        }
        seedCachedUser(fragment.getCurrentAccount(), author);
        Bundle args = new Bundle();
        args.putLong("user_id", author.userId);
        args.putBoolean("open_moments", true);
        fragment.presentFragment(new ProfileActivity(args));
    }

    /** Name and username from the card, only where the cached user is blank. */
    static void seedCachedUser(int account, FeedModels.Author author) {
        if (author == null || author.userId == 0) {
            return;
        }
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(author.userId);
        if (user == null) {
            TLRPC.TL_user stub = new TLRPC.TL_user();
            stub.id = author.userId;
            stub.access_hash = author.accessHash;
            controller.putUser(stub, false);
            user = controller.getUser(author.userId);
        }
        if (user == null) {
            return;
        }
        boolean changed = false;
        String first = org.telegram.ui.ProfileOffline.firstNameIfBlank(user.first_name, user.last_name, author.name);
        if (first != null && !first.equals(user.first_name)) {
            user.first_name = first;
            changed = true;
        }
        String username = org.telegram.ui.ProfileOffline.usernameIfBlank(user.username, author.username);
        if (username != null && !username.equals(user.username)) {
            user.username = username;
            user.flags |= 8;
            changed = true;
        }
        long hash = org.telegram.ui.ProfileOffline.accessHashIfMissing(user.access_hash, author.accessHash);
        if (hash != user.access_hash) {
            user.access_hash = hash;
            changed = true;
        }
        if (changed) {
            controller.putUser(user, false);
        }
    }

    public static void bindAvatar(BackupImageView view, FeedModels.Author author, int account) {
        if (view == null) {
            return;
        }
        TLRPC.User user = telegramUser(author, account);
        String http = avatarHttp(author, account);
        boolean telegramPhoto = user != null && user.photo != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty);
        String source = FeedContract.avatarSource(telegramPhoto, http);
        long photoId = telegramPhoto ? user.photo.photo_id : 0;
        String key = source + ":" + (author == null ? 0 : author.userId) + ":" + photoId + ":" + http;
        if (key.equals(avatarKeys.get(view))) {
            return;
        }
        avatarKeys.put(view, key);
        if ("telegram".equals(source)) {
            view.setForUserOrChat(user, new AvatarDrawable(user));
            return;
        }
        AvatarDrawable drawable = user != null ? new AvatarDrawable(user) : new AvatarDrawable();
        if (user == null) {
            String name = author == null ? "" : (author.name.length() > 0 ? author.name : author.username);
            drawable.setInfo(author == null ? 0 : author.userId, name, null);
        }
        if ("http".equals(source)) {
            view.setImage(http, FeedContract.AVATAR_FILTER, drawable);
        } else if (user != null) {
            view.setForUserOrChat(user, drawable);
        } else {
            view.setImage(null, null, drawable);
        }
    }

    private static TLRPC.User telegramUser(FeedModels.Author author, int account) {
        if (author == null || author.userId == 0) {
            return null;
        }
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        TLRPC.User user = controller.getUser(author.userId);
        boolean missingPhoto = user == null || user.photo == null || user.photo instanceof TLRPC.TL_userProfilePhotoEmpty;
        if (user == null && author.accessHash != 0) {
            TLRPC.TL_user stub = new TLRPC.TL_user();
            stub.id = author.userId;
            stub.access_hash = author.accessHash;
            controller.putUser(stub, false);
            user = controller.getUser(author.userId);
        }
        if (missingPhoto && avatarReloads.add(author.userId)) {
            controller.reloadUser(author.userId);
        }
        return user;
    }

    static String avatarHttp(FeedModels.Author author, int account) {
        if (author == null) {
            return "";
        }
        if (author.avatarUrl != null && (author.avatarUrl.startsWith("https://") || author.avatarUrl.startsWith("http://"))) {
            return author.avatarUrl;
        }
        String key = author.avatarKey != null && author.avatarKey.length() > 0 ? author.avatarKey : author.avatarUrl;
        if (key == null || key.length() == 0) {
            return "";
        }
        return FeedConfig.mediaUrl(account, key);
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
        return LocaleController.getString("Moments_HiddenName", R.string.Moments_HiddenName);
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

    public static String mediaKindLabel(String kind) {
        if ("video".equals(kind)) {
            return getString(R.string.Moments_Tool_Video);
        }
        if ("audio".equals(kind) || "voice".equals(kind)) {
            return getString(R.string.Moments_Voice);
        }
        if ("sticker".equals(kind)) {
            return getString(R.string.Moments_Tool_Sticker);
        }
        if ("gif".equals(kind)) {
            return getString(R.string.AttachGif);
        }
        if ("image".equals(kind) || "photo".equals(kind)) {
            return getString(R.string.Moments_Photo);
        }
        return getString(R.string.Moments_File);
    }

    public static String linkFor(String id) {
        return FeedConfig.LINK_PREFIX + id;
    }

    public static boolean isSelf(int account, FeedModels.Author author) {
        return author != null && author.userId != 0 && author.userId == UserConfig.getInstance(account).getClientUserId();
    }

}
