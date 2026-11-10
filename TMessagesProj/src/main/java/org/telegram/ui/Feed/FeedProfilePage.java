package org.telegram.ui.Feed;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Profile "动态" tab body. SharedMediaLayout hosts this view the same way it
 * hosts the saved-messages container.
 */
public class FeedProfilePage extends FrameLayout {

    public FeedProfilePage(Context context, BaseFragment fragment, long dialogId) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        boolean self = dialogId == UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
        TextView counts = new TextView(context);
        counts.setText(FeedUi.s("Moments.Segment.Following", R.string.FeedFollowing) + " · " + FeedUi.s("Moments.Followers", R.string.FeedFollowers));
        counts.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        counts.setPadding(dp(16), dp(12), dp(16), dp(8));
        counts.setOnClickListener(v -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWING, String.valueOf(dialogId))));
        column.addView(counts);
        if (self) {
            addLink(column, fragment, FeedUi.s("Moments.Profile.Saved", R.string.FeedSavedTitle), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.SAVED)));
            addLink(column, fragment, FeedUi.s("Moments.Profile.Liked", R.string.FeedLikedTitle), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.LIKED)));
        } else {
            addLink(column, fragment, FeedUi.s("Moments.Follow", R.string.FeedFollow), () -> org.telegram.messenger.feed.FeedController.getInstance(fragment.getCurrentAccount()).follow(String.valueOf(dialogId), true));
            addLink(column, fragment, FeedUi.s("Moments.Mute", R.string.FeedMute), () -> org.telegram.messenger.feed.FeedController.getInstance(fragment.getCurrentAccount()).mute(String.valueOf(dialogId), true));
            addLink(column, fragment, getString(R.string.ReportChat), () -> FeedReportSheet.open(fragment, "user", String.valueOf(dialogId)));
        }
        addLink(column, fragment, FeedUi.s("Moments.Profile.Posts", R.string.FeedProfileTab), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.PROFILE, String.valueOf(dialogId))));
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private void addLink(LinearLayout column, BaseFragment fragment, String title, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.setTextSize(16);
        view.setPadding(dp(16), dp(14), dp(16), dp(14));
        view.setOnClickListener(v -> click.run());
        column.addView(view);
    }
}
