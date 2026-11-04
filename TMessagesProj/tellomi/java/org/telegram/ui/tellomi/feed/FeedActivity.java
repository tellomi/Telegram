package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

import java.util.ArrayList;

/**
 * Placeholder for the Discover tab. The feed client arrives with M3.
 * The tab stays visible and shows an empty state when there is nothing to load (D-37).
 */
public class FeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {

    public static final String ARG_POST_ID = "tellomi_post_id";

    private static String pendingPostId;

    private TextView titleView;
    private TextView messageView;
    private String openedPostId;
    private boolean hasMainTabs;

    public FeedActivity(Bundle args) {
        super(args);
        if (args != null) {
            hasMainTabs = args.getBoolean("hasMainTabs", false);
            openedPostId = args.getString(ARG_POST_ID);
        }
    }

    /** A post link arrived before this screen existed. M3 reads it from the fragment. */
    public static void setPendingPostId(String postId) {
        pendingPostId = postId;
    }

    public static String takePendingPostId() {
        String id = pendingPostId;
        pendingPostId = null;
        return id;
    }

    public String getOpenedPostId() {
        return openedPostId;
    }

    public void openPost(String postId) {
        openedPostId = postId;
        if (getArguments() != null) {
            getArguments().putString(ARG_POST_ID, postId);
        }
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return true;
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;
        actionBar.setTitle(LocaleController.getString(R.string.TellomiTabFeed));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setCastShadows(false);
        if (!hasMainTabs) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        titleView = new TextView(context);
        titleView.setText(LocaleController.getString(R.string.TellomiTabFeed));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        column.addView(titleView, LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT));

        messageView = new TextView(context);
        messageView.setText(LocaleController.getString(R.string.TellomiFeedEmptyMessage));
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        messageView.setGravity(Gravity.CENTER);
        messageView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        LinearLayout.LayoutParams messageLp = LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT);
        messageLp.topMargin = dp(8);
        column.addView(messageView, messageLp);

        int top = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.statusBarHeight;
        int bottom = hasMainTabs ? dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        column.setPadding(dp(32), 0, dp(32), 0);
        root.setPadding(0, top, 0, bottom);
        root.addView(column, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ));
        return fragmentView = root;
    }

    private static LinearLayout.LayoutParams LayoutParams(int width) {
        return new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        list.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        list.add(new ThemeDescription(messageView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        return list;
    }
}
