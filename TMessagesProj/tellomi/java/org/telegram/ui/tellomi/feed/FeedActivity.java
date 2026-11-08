package org.telegram.ui.tellomi.feed;

import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.MainTabsActivityController;

import java.util.ArrayList;

/**
 * Discover tab. The live feed stays on the empty state until
 * {@code tellomi_discover_feed_enabled} is on and the account is logged in.
 * {@link #setPendingPostId} is the post-link hook used by the navigation work.
 */
public class FeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, NotificationCenter.NotificationCenterDelegate {

    public static final String ARG_POST_ID = "tellomi_post_id";
    static final int MENU_COMPOSE = 1;
    static final int MENU_NOTIFICATIONS = 2;

    private static String pendingPostId;

    TextView titleView;
    TextView messageView;
    private String openedPostId;
    private boolean hasMainTabs;
    private TellomiDiscoverScreen screen;
    private FragmentFloatingButton composeButton;
    private MainTabsActivityController tabsController;

    public FeedActivity(Bundle args) {
        super(args);
        if (args != null) {
            hasMainTabs = args.getBoolean("hasMainTabs", false);
            openedPostId = args.getString(ARG_POST_ID);
        }
    }

    /** A post link arrived before this screen existed. The feed reads it when the flag is on. */
    public static void setPendingPostId(String postId) {
        pendingPostId = postId;
    }

    public static String peekPendingPostId() {
        return pendingPostId;
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
            if (postId == null) {
                getArguments().remove(ARG_POST_ID);
            } else {
                getArguments().putString(ARG_POST_ID, postId);
            }
        }
        if (postId != null && screen != null) {
            screen.sync(false);
        }
    }

    void clearOpenedPost() {
        openedPostId = null;
        if (getArguments() != null) {
            getArguments().remove(ARG_POST_ID);
        }
    }

    public void setTabsController(MainTabsActivityController controller) {
        tabsController = controller;
    }

    void tellomiSetTabsVisible(boolean visible) {
        if (tabsController != null) {
            tabsController.setTabsVisible(visible);
        }
    }

    void showComposeButton(boolean show, String title, String notifications) {
        if (hasMainTabs && composeButton != null) {
            composeButton.setContentDescription(title);
            composeButton.setButtonVisible(show, false);
            return;
        }
        if (actionBar == null) {
            return;
        }
        actionBar.createMenu().clearItems();
        if (show) {
            actionBar.createMenu().addItem(MENU_NOTIFICATIONS, notifications);
            actionBar.createMenu().addItem(MENU_COMPOSE, title);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appConfigUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appConfigUpdated);
        if (screen != null) {
            screen.destroy();
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (screen != null) {
            screen.sync(false);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appConfigUpdated && screen != null) {
            screen.sync(false);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
        if (screen != null) {
            screen.onLocationPermission(requestCode, grantResults);
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
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1 && !hasMainTabs) {
                    finishFragment();
                } else if (id == MENU_COMPOSE && screen != null) {
                    screen.compose();
                } else if (id == MENU_NOTIFICATIONS && screen != null) {
                    screen.notifications();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        int top = ActionBar.getCurrentActionBarHeight() + org.telegram.messenger.AndroidUtilities.statusBarHeight;
        int bottom = hasMainTabs ? org.telegram.messenger.AndroidUtilities.dp(org.telegram.ui.DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS) : 0;
        root.setPadding(0, top, 0, bottom);
        fragmentView = root;
        if (screen != null) {
            screen.destroy();
        }
        // Feed rebuilds clear this child. The compose button stays on root.
        FrameLayout content = new FrameLayout(context);
        root.addView(content, org.telegram.ui.Components.LayoutHelper.createFrame(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.MATCH_PARENT));
        if (hasMainTabs) {
            composeButton = new FragmentFloatingButton(context, resourceProvider);
            composeButton.setImageResource(R.drawable.filled_fab_compose_32);
            composeButton.setButtonVisible(false, false);
            composeButton.setOnClickListener(v -> {
                if (screen != null) {
                    screen.compose();
                }
            });
            root.addView(composeButton, FragmentFloatingButton.createDefaultLayoutParams());
            composeButton.setTranslationY(-AndroidUtilities.navigationBarHeight);
        }
        screen = new TellomiDiscoverScreen(this, content, context);
        screen.sync(true);
        return fragmentView;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> list = new ArrayList<>();
        list.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        if (titleView != null) {
            list.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        }
        if (messageView != null) {
            list.add(new ThemeDescription(messageView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        }
        return list;
    }
}
