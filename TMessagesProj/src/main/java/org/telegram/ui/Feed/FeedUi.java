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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public final class FeedUi {

    private static final WeakHashMap<BackupImageView, String> avatarKeys = new WeakHashMap<>();
    private static final WeakHashMap<BackupImageView, FeedModels.Author> avatarAuthors = new WeakHashMap<>();
    private static final WeakHashMap<BackupImageView, Integer> avatarAccounts = new WeakHashMap<>();
    private static final HashSet<Long> avatarReloads = new HashSet<>();
    private static final ConcurrentHashMap<String, TLRPC.User> storedResults = new ConcurrentHashMap<>();

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
        args.putBoolean("author_row_checked", true);
        fragment.presentFragment(new ProfileActivity(args));
    }

    /** Name and username from the card, only where the cached user is blank. */
    static void seedCachedUser(int account, FeedModels.Author author) {
        if (author == null || author.userId == 0) {
            return;
        }
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        TLRPC.User user = resolveAuthorForProfile(account, author.userId, author.accessHash);
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
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (changed && !user.min && user.id != selfId) {
            controller.putUser(user, false);
        }
    }

    /**
     * Profile open only. Joins an in-flight list read instead of starting a second one.
     * The bounded wait lives here, not in bindAvatar.
     */
    static TLRPC.User resolveAuthorForProfile(int account, long userId, long accessHash) {
        if (userId == 0) {
            return null;
        }
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        long selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.User memory = memoryAuthor(account, userId);
        boolean memoryPresent = memory != null;
        boolean memoryMin = memory != null && memory.min;
        String key = org.telegram.ui.ProfileOffline.storedKey(account, userId);
        TLRPC.User stored = null;
        boolean storedReadFinished = true;
        if (org.telegram.ui.ProfileOffline.needsStoredUser(memoryPresent, memoryMin)) {
            TLRPC.User resolved = ensureStoredUser(account, userId);
            storedReadFinished = org.telegram.ui.ProfileOffline.storedUsers().isDone(key);
            stored = storedResults.get(key);
            if (resolved != null) {
                memory = resolved;
                memoryPresent = true;
                memoryMin = resolved.min;
            }
        }
        org.telegram.ui.ProfileOffline.AuthorSeed action = org.telegram.ui.ProfileOffline.authorSeed(
                memoryPresent, memoryMin, stored != null, storedReadFinished, userId, selfId);
        if (action == org.telegram.ui.ProfileOffline.AuthorSeed.USE_STORED && stored != null) {
            controller.putUser(stored, true);
            TLRPC.User resolved = controller.getUser(userId);
            return resolved != null ? resolved : stored;
        }
        if (action == org.telegram.ui.ProfileOffline.AuthorSeed.INSERT_MIN_STUB) {
            TLRPC.TL_user stub = new TLRPC.TL_user();
            stub.id = userId;
            stub.access_hash = accessHash;
            stub.min = org.telegram.ui.ProfileOffline.authorStubIsMin();
            controller.putUser(stub, org.telegram.ui.ProfileOffline.authorStubFromCache());
            TLRPC.User resolved = controller.getUser(userId);
            return resolved != null ? resolved : stub;
        }
        return memory;
    }

    /** In-memory user, including the logged-in user record. Does not touch the users table. */
    private static TLRPC.User memoryAuthor(int account, long userId) {
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        TLRPC.User memory = controller.getUser(userId);
        if (memory == null && userId == UserConfig.getInstance(account).getClientUserId()) {
            TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
            if (self != null) {
                controller.putUser(self, true);
                memory = controller.getUser(userId);
                if (memory == null) {
                    memory = self;
                }
            }
        }
        return memory;
    }

    /**
     * One users-table read for this author. List binding and profile open share it.
     * A caller that did not start the read still waits at most STORED_USER_WAIT_MS.
     */
    public static TLRPC.User ensureStoredUser(int account, long userId) {
        if (userId == 0) {
            return null;
        }
        org.telegram.messenger.MessagesController controller = org.telegram.messenger.MessagesController.getInstance(account);
        String key = org.telegram.ui.ProfileOffline.storedKey(account, userId);
        org.telegram.ui.ProfileOffline.StoredRead decision = org.telegram.ui.ProfileOffline.storedUsers().begin(key, true);
        if (decision == org.telegram.ui.ProfileOffline.StoredRead.START) {
            postStoredRead(account, userId, key);
        }
        if (decision == org.telegram.ui.ProfileOffline.StoredRead.START || decision == org.telegram.ui.ProfileOffline.StoredRead.WAIT) {
            org.telegram.ui.ProfileOffline.storedUsers().await(key, org.telegram.ui.ProfileOffline.STORED_USER_WAIT_MS);
        }
        TLRPC.User found = storedResults.get(key);
        if (found != null) {
            controller.putUser(found, true);
        }
        TLRPC.User resolved = controller.getUser(userId);
        return resolved != null ? resolved : found;
    }

    /** List binding. Starts the shared read when needed and returns without waiting. */
    private static void scheduleStoredAuthor(int account, long userId) {
        if (userId == 0) {
            return;
        }
        String key = org.telegram.ui.ProfileOffline.storedKey(account, userId);
        if (org.telegram.ui.ProfileOffline.storedUsers().isDone(key)) {
            return;
        }
        if (org.telegram.ui.ProfileOffline.storedUsers().begin(key, false) != org.telegram.ui.ProfileOffline.StoredRead.START) {
            return;
        }
        postStoredRead(account, userId, key);
    }

    private static void postStoredRead(int account, long userId, String key) {
        org.telegram.messenger.MessagesStorage storage = org.telegram.messenger.MessagesStorage.getInstance(account);
        boolean queued = false;
        try {
            queued = storage.getStorageQueue().postRunnable(() -> {
                TLRPC.User loaded = null;
                try {
                    loaded = storage.getUser(userId);
                } finally {
                    if (loaded != null) {
                        storedResults.put(key, loaded);
                    } else {
                        storedResults.remove(key);
                    }
                    org.telegram.ui.ProfileOffline.storedUsers().complete(key);
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.User found = storedResults.get(key);
                        if (found != null) {
                            org.telegram.messenger.MessagesController.getInstance(account).putUser(found, true);
                        }
                        rebindAuthor(account, userId);
                    });
                }
            });
        } catch (Exception ignored) {
            queued = false;
        }
        if (!queued) {
            org.telegram.ui.ProfileOffline.storedUsers().complete(key);
        }
    }

    private static void rebindAuthor(int account, long userId) {
        ArrayList<BackupImageView> views = new ArrayList<>();
        for (Map.Entry<BackupImageView, FeedModels.Author> entry : avatarAuthors.entrySet()) {
            FeedModels.Author author = entry.getValue();
            BackupImageView view = entry.getKey();
            Integer boundAccount = avatarAccounts.get(view);
            if (author != null && author.userId == userId && boundAccount != null && boundAccount == account) {
                views.add(view);
            }
        }
        for (int i = 0; i < views.size(); i++) {
            BackupImageView view = views.get(i);
            avatarKeys.remove(view);
            bindAvatar(view, avatarAuthors.get(view), account);
        }
    }

    public static void bindAvatar(BackupImageView view, FeedModels.Author author, int account) {
        if (view == null) {
            return;
        }
        avatarAuthors.put(view, author);
        avatarAccounts.put(view, account);
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
        TLRPC.User user = memoryAuthor(account, author.userId);
        if (org.telegram.ui.ProfileOffline.needsStoredUser(user != null, user != null && user.min)) {
            scheduleStoredAuthor(account, author.userId);
        }
        boolean missingPhoto = user == null || user.photo == null || user.photo instanceof TLRPC.TL_userProfilePhotoEmpty;
        if (missingPhoto && user != null && !user.min && avatarReloads.add(author.userId)) {
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
