package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
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
        counts.setText(LocaleController.getString("Moments_Segment_Following", R.string.Moments_Segment_Following) + " · " + LocaleController.getString("Moments_Followers", R.string.Moments_Followers));
        counts.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        counts.setPadding(dp(16), dp(12), dp(16), dp(8));
        counts.setOnClickListener(v -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.FOLLOWING, String.valueOf(dialogId))));
        column.addView(counts);
        if (self) {
            addLink(column, fragment, LocaleController.getString("Moments_Profile_Saved", R.string.Moments_Profile_Saved), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.SAVED)));
            addLink(column, fragment, LocaleController.getString("Moments_Profile_Liked", R.string.Moments_Profile_Liked), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.LIKED)));
        } else {
            addLink(column, fragment, LocaleController.getString("Moments_Follow", R.string.Moments_Follow), () -> org.telegram.messenger.feed.FeedController.getInstance(fragment.getCurrentAccount()).follow(String.valueOf(dialogId), true));
            addLink(column, fragment, LocaleController.getString("Moments_Mute", R.string.Moments_Mute), () -> org.telegram.messenger.feed.FeedController.getInstance(fragment.getCurrentAccount()).mute(String.valueOf(dialogId), true));
            addLink(column, fragment, LocaleController.getString("Moments_Report", R.string.Moments_Report), () -> FeedReportSheet.open(fragment, "user", String.valueOf(dialogId)));
        }
        addLink(column, fragment, LocaleController.getString("Moments_Profile_Posts", R.string.Moments_Profile_Posts), () -> fragment.presentFragment(new FeedCollectionActivity(FeedCollectionActivity.PROFILE, String.valueOf(dialogId))));
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
