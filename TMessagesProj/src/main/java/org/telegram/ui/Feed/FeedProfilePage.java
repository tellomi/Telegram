package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import static org.telegram.messenger.AndroidUtilities.dp;

/**
 * Profile "动态" tab body. SharedMediaLayout hosts this view the same way it
 * hosts the saved-messages container.
 */
public class FeedProfilePage extends FrameLayout {

    private final FeedContract.QueryGeneration generation = new FeedContract.QueryGeneration();
    private final int account;
    private final String profileId;
    private int token;
    private TextView followingView;
    private TextView followersView;

    public FeedProfilePage(Context context, BaseFragment fragment, long dialogId) {
        super(context);
        account = fragment.getCurrentAccount();
        profileId = String.valueOf(dialogId);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        boolean self = dialogId == UserConfig.getInstance(account).getClientUserId();
        followingView = addLink(column, entry(false, null), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWING, profileId)));
        followersView = addLink(column, entry(true, null), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWERS, profileId)));
        if (self) {
            addLink(column, LocaleController.getString(R.string.Moments_Profile_Saved), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.SAVED)));
            addLink(column, LocaleController.getString(R.string.Moments_Profile_Liked), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.LIKED)));
        } else {
            addLink(column, LocaleController.getString(R.string.Moments_Follow), () -> FeedController.getInstance(account).follow(profileId, true));
            addLink(column, LocaleController.getString(R.string.Moments_Mute), () -> FeedController.getInstance(account).mute(profileId, true));
            addLink(column, LocaleController.getString(R.string.Moments_Report), () -> FeedReportSheet.open(fragment, "user", profileId));
        }
        addLink(column, LocaleController.getString(R.string.Moments_Profile_Posts), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.PROFILE, profileId)));
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadCounts();
    }

    @Override
    protected void onDetachedFromWindow() {
        generation.next();
        super.onDetachedFromWindow();
    }

    private void loadCounts() {
        token = generation.next();
        FeedController controller = FeedController.getInstance(account);
        String following = FeedContract.relationPath(profileId, false, "");
        String followers = FeedContract.relationPath(profileId, true, "");
        if (following != null) {
            controller.api.getJson(following, (json, error) -> {
                if (!generation.accept(token)) {
                    return;
                }
                followingView.setText(entry(false, FeedContract.parseRelationPage(json == null ? null : json.toString(), error, false)));
            });
        }
        if (followers != null) {
            controller.api.getJson(followers, (json, error) -> {
                if (!generation.accept(token)) {
                    return;
                }
                followersView.setText(entry(true, FeedContract.parseRelationPage(json == null ? null : json.toString(), error, true)));
            });
        }
    }

    private static String entry(boolean followers, FeedContract.RelationPage page) {
        if (page != null && page.hidden) {
            return LocaleController.getString(followers ? R.string.Moments_FollowersPrivate : R.string.Moments_FollowingPrivate);
        }
        if (page == null || page.failed || page.total < 0) {
            return LocaleController.getString(followers ? R.string.Moments_FollowersEntry : R.string.Moments_FollowingEntry);
        }
        if (followers) {
            return LocaleController.formatString(R.string.Moments_FollowersWithCount, page.total);
        }
        return LocaleController.formatString(R.string.Moments_FollowingWithCount, page.total);
    }

    private TextView addLink(LinearLayout column, String title, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.setTextSize(16);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setOnClickListener(v -> click.run());
        column.addView(view);
        return view;
    }
}
