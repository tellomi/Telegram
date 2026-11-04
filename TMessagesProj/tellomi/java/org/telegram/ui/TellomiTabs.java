package org.telegram.ui;

import android.content.SharedPreferences;
import android.graphics.drawable.ShapeDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.tellomi.feed.FeedActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Visible bottom tabs for Tellomi (M2, D-4, D-5, D-7, D-8, D-36).
 *
 * Order when everything is on: Discover, Chats, Calls, Contacts, Settings, Profile.
 * A fresh install shows Discover, Chats, Settings, and Profile. Chats and Settings
 * cannot be removed. The pager starts on Chats.
 *
 * Visual indices match {@code MainTabsActivity}'s tab array: chats 0, contacts 1,
 * settings 2, calls 3, profile 4, discover 5.
 */
public final class TellomiTabs {

    public enum Tab {
        FEED,
        CHATS,
        CALLS,
        CONTACTS,
        SETTINGS,
        PROFILE
    }

    public static final int VISUAL_CHATS = 0;
    public static final int VISUAL_CONTACTS = 1;
    public static final int VISUAL_SETTINGS = 2;
    public static final int VISUAL_CALLS = 3;
    public static final int VISUAL_PROFILE = 4;
    public static final int VISUAL_FEED = 5;

    static final String PREF_SHOW_FEED = "tellomi_show_feed_tab";
    static final String PREF_SHOW_CONTACTS = "tellomi_show_contacts_tab";

    /** Upstream bar cap: 328dp plus two 8dp margins. */
    private static final int UPSTREAM_BAR_MAX_DP = 328 + 16;
    /** Wider than a 360dp phone so that cap does not shrink six tabs. */
    private static final int WIDE_BAR_MAX_DP = 376;
    /** {@code MAIN_TABS_MARGIN + 4}, the upstream horizontal inset. */
    private static final int UPSTREAM_SIDE_PAD_DP = 12;
    private static final int WIDE_SIDE_PAD_DP = 4;

    /** Same side paddings as {@code MainTabsLayout}'s three measure passes. */
    private static final int[] PASS_PAD_DP = {16, 8, 4};

    private TellomiTabs() {
    }

    /**
     * When false, {@link MainTabsActivity} keeps the upstream four positions
     * (chats, contacts, calls-or-settings, profile).
     */
    public static boolean enabled() {
        return true;
    }

    public static final class Visibility {
        public final boolean feed;
        public final boolean calls;
        public final boolean contacts;

        public Visibility(boolean feed, boolean calls, boolean contacts) {
            this.feed = feed;
            this.calls = calls;
            this.contacts = contacts;
        }

        /** D-5. Calls stay on {@code UserConfig.showCallsTab}, which defaults to false. */
        public static Visibility fresh() {
            return new Visibility(true, false, false);
        }
    }

    /** D-4 / D-36 / D-7. Chats, Settings, and Profile are always present. At most six. */
    public static List<Tab> visible(Visibility visibility) {
        Visibility value = visibility == null ? Visibility.fresh() : visibility;
        ArrayList<Tab> tabs = new ArrayList<>(6);
        if (value.feed) {
            tabs.add(Tab.FEED);
        }
        tabs.add(Tab.CHATS);
        if (value.calls) {
            tabs.add(Tab.CALLS);
        }
        if (value.contacts) {
            tabs.add(Tab.CONTACTS);
        }
        tabs.add(Tab.SETTINGS);
        tabs.add(Tab.PROFILE);
        return tabs;
    }

    public static int indexOf(List<Tab> tabs, Tab tab) {
        return tabs == null ? -1 : tabs.indexOf(tab);
    }

    /** D-8. Pager index of Chats, or {@code fallback} when the flag is off. */
    public static int startPosition(int account, int fallback) {
        if (!enabled()) {
            return fallback;
        }
        int index = indexOf(visible(read(account)), Tab.CHATS);
        return index < 0 ? fallback : index;
    }

    public static int fragmentCount(int account, int fallback) {
        if (!enabled()) {
            return fallback;
        }
        return visible(read(account)).size();
    }

    public static int profilePosition(int account, int fallback) {
        if (!enabled()) {
            return fallback;
        }
        int index = indexOf(visible(read(account)), Tab.PROFILE);
        return index < 0 ? fallback : index;
    }

    /**
     * Maps a visual tab index onto a pager position. Hidden tabs return -1.
     * With the flag off this is the upstream {@code index > 2 ? index - 1 : index}.
     */
    public static int positionForVisualIndex(int account, int visualIndex) {
        if (!enabled()) {
            return visualIndex > 2 ? visualIndex - 1 : visualIndex;
        }
        Tab tab = tabForVisual(visualIndex);
        if (tab == null) {
            return -1;
        }
        return indexOf(visible(read(account)), tab);
    }

    public static int barMaxWidthDp(int visibleCount) {
        return visibleCount <= 4 ? UPSTREAM_BAR_MAX_DP : WIDE_BAR_MAX_DP;
    }

    public static int barSidePaddingDp(int visibleCount) {
        return visibleCount <= 4 ? UPSTREAM_SIDE_PAD_DP : WIDE_SIDE_PAD_DP;
    }

    public static int contentWidthDp(int screenDp, int visibleCount) {
        return Math.min(screenDp, barMaxWidthDp(visibleCount)) - barSidePaddingDp(visibleCount) * 2;
    }

    /**
     * True when some {@link org.telegram.ui.MainTabsLayout} text pass fits in
     * {@code contentWidthDp} without the scale-down that ellipsizes labels.
     * {@code textAt12} and {@code textAt10} are label widths in dp.
     */
    public static boolean labelsFit(int screenDp, int visibleCount, int[] textAt12, int[] textAt10) {
        int content = contentWidthDp(screenDp, visibleCount);
        int[][] widths = {textAt12, textAt12, textAt10};
        for (int pass = 0; pass < PASS_PAD_DP.length; pass++) {
            if (fitsWithoutScaling(content, widths[pass], PASS_PAD_DP[pass])) {
                return true;
            }
        }
        return false;
    }

    public static boolean fitsWithoutScaling(int contentDp, int[] textWidthDp, int sidePaddingDp) {
        if (textWidthDp == null || textWidthDp.length == 0 || contentDp <= 0) {
            return false;
        }
        int total = 0;
        for (int width : textWidthDp) {
            if (width <= 0) {
                return false;
            }
            total += width + sidePaddingDp * 2;
        }
        return total <= contentDp;
    }

    public static Visibility read(int account) {
        SharedPreferences prefs = MessagesController.getMainSettings(account);
        return new Visibility(
            prefs.getBoolean(PREF_SHOW_FEED, true),
            UserConfig.getInstance(account).showCallsTab,
            prefs.getBoolean(PREF_SHOW_CONTACTS, false)
        );
    }

    public static boolean install(MainTabsActivity activity) {
        if (!enabled() || activity == null || activity.tabs == null || activity.tabsView == null) {
            return false;
        }
        ensureFeedTab(activity);
        applyChrome(activity, false);
        if (FeedActivity.peekPendingPostId() != null) {
            AndroidUtilities.runOnUIThread(() -> selectFeed(activity));
        }
        return true;
    }

    /** Existing calls toggle and later feed/contacts toggles all post {@code callTabsVisibleToggled}. */
    public static boolean onVisibilityChanged(MainTabsActivity activity) {
        if (!enabled() || activity == null || activity.tabsView == null) {
            return false;
        }
        ensureFeedTab(activity);
        Tab keep = currentTab(activity);
        applyChrome(activity, true);
        remap(activity, keep);
        return true;
    }

    public static BaseFragment createFragment(MainTabsActivity activity, int position) {
        if (!enabled() || activity == null) {
            return null;
        }
        List<Tab> tabs = visible(read(activity.getCurrentAccount()));
        if (position < 0 || position >= tabs.size()) {
            return null;
        }
        switch (tabs.get(position)) {
            case FEED:
                return newFeed();
            case CONTACTS:
                return newContacts();
            case CALLS:
                return newCalls();
            case SETTINGS:
                return newSettings();
            case CHATS:
                return activity.tellomiCreateChats();
            case PROFILE:
                return newProfile(activity.getCurrentAccount());
            default:
                return null;
        }
    }

    private static BaseFragment newFeed() {
        Bundle args = new Bundle();
        args.putBoolean("hasMainTabs", true);
        String pending = FeedActivity.takePendingPostId();
        if (pending != null) {
            args.putString(FeedActivity.ARG_POST_ID, pending);
        }
        return new FeedActivity(args);
    }

    private static BaseFragment newContacts() {
        Bundle args = new Bundle();
        args.putBoolean("needPhonebook", true);
        args.putBoolean("needFinishFragment", false);
        args.putBoolean("hasMainTabs", true);
        return new ContactsActivity(args);
    }

    private static BaseFragment newCalls() {
        Bundle args = new Bundle();
        args.putBoolean("needFinishFragment", false);
        args.putBoolean("hasMainTabs", true);
        return new CallLogActivity(args);
    }

    private static BaseFragment newSettings() {
        Bundle args = new Bundle();
        args.putBoolean("hasMainTabs", true);
        return new SettingsActivity(args);
    }

    private static BaseFragment newProfile(int account) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(account).getClientUserId());
        args.putBoolean("my_profile", true);
        args.putBoolean("hasMainTabs", true);
        return new ProfileActivity(args);
    }

    private static void ensureFeedTab(MainTabsActivity activity) {
        if (activity.tabs == null || activity.tabsView == null || activity.getContext() == null) {
            return;
        }
        if (activity.tabs.length > VISUAL_FEED && activity.tabs[VISUAL_FEED] != null) {
            return;
        }
        GlassTabView[] next = new GlassTabView[VISUAL_FEED + 1];
        System.arraycopy(activity.tabs, 0, next, 0, Math.min(activity.tabs.length, VISUAL_FEED));
        GlassTabView feed = GlassTabView.createMainTab(
            activity.getContext(),
            activity.getResourceProvider(),
            GlassTabView.TabAnimation.FEED,
            R.string.TellomiTabFeed
        );
        feed.setOnClickListener(v -> activity.tellomiTabClicked(VISUAL_FEED));
        feed.setOnLongClickListener(v -> hideTab(activity, v, Tab.FEED));
        next[VISUAL_FEED] = feed;
        activity.tabs = next;
        activity.tabsView.addTabToIgnoreClick(feed);
        activity.tabsView.addView(feed);
    }

    private static void applyChrome(MainTabsActivity activity, boolean animated) {
        if (activity.tabsView == null || activity.tabs == null) {
            return;
        }
        Visibility visibility = read(activity.getCurrentAccount());
        int count = visible(visibility).size();
        activity.tabsView.setMaxWidth(AndroidUtilities.dp(barMaxWidthDp(count)));
        int side = AndroidUtilities.dp(barSidePaddingDp(count));
        int vertical = AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4);
        activity.tabsView.setPadding(side, vertical, side, vertical);
        if (activity.tabs.length > VISUAL_PROFILE && activity.tabs[VISUAL_PROFILE] != null) {
            activity.tabs[VISUAL_PROFILE].setLabelHorizontalPadding(AndroidUtilities.dp(count >= 5 ? 0 : 8));
        }
        setPriority(activity, VISUAL_FEED, 0);
        setPriority(activity, VISUAL_CHATS, 1);
        setPriority(activity, VISUAL_CALLS, 2);
        setPriority(activity, VISUAL_CONTACTS, 3);
        setPriority(activity, VISUAL_SETTINGS, 4);
        setPriority(activity, VISUAL_PROFILE, 5);
        show(activity, VISUAL_FEED, visibility.feed, animated);
        show(activity, VISUAL_CHATS, true, animated);
        show(activity, VISUAL_CALLS, visibility.calls, animated);
        show(activity, VISUAL_CONTACTS, visibility.contacts, animated);
        show(activity, VISUAL_SETTINGS, true, animated);
        show(activity, VISUAL_PROFILE, true, animated);
        activity.tabsView.requestLayout();
    }

    private static void setPriority(MainTabsActivity activity, int visualIndex, int priority) {
        GlassTabView tab = tabAt(activity, visualIndex);
        if (tab != null) {
            activity.tabsView.setPriority(tab, priority);
        }
    }

    private static void show(MainTabsActivity activity, int visualIndex, boolean visible, boolean animated) {
        GlassTabView tab = tabAt(activity, visualIndex);
        if (tab != null) {
            activity.tabsView.setViewVisible(tab, visible, animated);
        }
    }

    private static GlassTabView tabAt(MainTabsActivity activity, int visualIndex) {
        if (activity.tabs == null || visualIndex < 0 || visualIndex >= activity.tabs.length) {
            return null;
        }
        return activity.tabs[visualIndex];
    }

    private static Tab currentTab(MainTabsActivity activity) {
        if (activity.viewPager == null) {
            return Tab.CHATS;
        }
        ViewPagerActivity.FragmentState state = activity.fragmentsArr.get(activity.viewPager.getCurrentPosition());
        if (state == null) {
            return Tab.CHATS;
        }
        Tab tab = identify(state.fragment);
        return tab == null ? Tab.CHATS : tab;
    }

    private static void remap(MainTabsActivity activity, Tab keep) {
        if (activity.viewPager == null) {
            return;
        }
        List<Tab> next = visible(read(activity.getCurrentAccount()));
        int keepPos = indexOf(next, keep);
        if (keepPos < 0) {
            keepPos = indexOf(next, Tab.CHATS);
        }
        if (keepPos < 0) {
            keepPos = 0;
        }
        ArrayList<Integer> dropKeys = new ArrayList<>();
        for (int i = 0; i < activity.fragmentsArr.size(); i++) {
            ViewPagerActivity.FragmentState state = activity.fragmentsArr.valueAt(i);
            Tab tab = state == null ? null : identify(state.fragment);
            if (tab == null || indexOf(next, tab) < 0) {
                dropKeys.add(activity.fragmentsArr.keyAt(i));
            }
        }
        for (int i = 0; i < dropKeys.size(); i++) {
            activity.dropFragmentAtPosition(dropKeys.get(i));
        }
        SparseArray<ViewPagerActivity.FragmentState> kept = new SparseArray<>();
        for (int i = 0; i < activity.fragmentsArr.size(); i++) {
            ViewPagerActivity.FragmentState state = activity.fragmentsArr.valueAt(i);
            if (state == null) {
                continue;
            }
            Tab tab = identify(state.fragment);
            int pos = indexOf(next, tab);
            if (pos >= 0) {
                kept.put(pos, state);
            }
        }
        activity.fragmentsArr.clear();
        for (int i = 0; i < kept.size(); i++) {
            activity.fragmentsArr.put(kept.keyAt(i), kept.valueAt(i));
        }
        activity.viewPager.currentPosition = keepPos;
        View[] pages = activity.viewPager.getViewPages();
        if (pages != null && pages.length > 0 && pages[0] != null) {
            activity.viewPager.rebuild(false);
        }
        activity.selectTab(keepPos, true);
    }

    static Tab identify(BaseFragment fragment) {
        if (fragment instanceof FeedActivity) {
            return Tab.FEED;
        }
        if (fragment instanceof DialogsActivity) {
            return Tab.CHATS;
        }
        if (fragment instanceof CallLogActivity) {
            return Tab.CALLS;
        }
        if (fragment instanceof ContactsActivity) {
            return Tab.CONTACTS;
        }
        if (fragment instanceof SettingsActivity) {
            return Tab.SETTINGS;
        }
        if (fragment instanceof ProfileActivity) {
            return Tab.PROFILE;
        }
        return null;
    }

    public static TellomiTabNavigation.Copy currentCopy() {
        String code = "";
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null) {
                code = info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return TellomiTabNavigation.Copy.resolve(code, TellomiPack.device());
    }

    /** Writes Discover, Calls, and Contacts, then refreshes the bar. */
    public static void commit(int account, Visibility visibility) {
        Visibility value = visibility == null ? Visibility.fresh() : visibility;
        MessagesController.getMainSettings(account).edit()
            .putBoolean(PREF_SHOW_FEED, value.feed)
            .putBoolean(PREF_SHOW_CONTACTS, value.contacts)
            .apply();
        UserConfig.getInstance(account).setShowCallsTab(value.calls);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.callTabsVisibleToggled);
    }

    /** One extra row on the contacts long-press, same shape as Hide Calls (D-38). */
    public static void appendHideContacts(MainTabsActivity activity, ItemOptions options) {
        if (!enabled() || activity == null || options == null || !read(activity.getCurrentAccount()).contacts) {
            return;
        }
        options.add(R.drawable.msg_archive_hide, currentCopy().hideContacts, () ->
            commit(activity.getCurrentAccount(), TellomiTabNavigation.hide(read(activity.getCurrentAccount()), Tab.CONTACTS))
        );
    }

    /**
     * {@code https://tell.cc/m#<id>} (D-57). Returns false when the bar is off
     * or the link is not a post, so {@link LaunchActivity} keeps it.
     * {@code /m/<slug>} is still a message link.
     */
    public static boolean consumeFeedPostLink(LaunchActivity activity, Uri uri, int account) {
        if (uri == null) {
            return false;
        }
        boolean barOn = enabled();
        String postId = TellomiTabNavigation.feedPostIdIfHandled(uri.getHost(), uri.getPath(), uri.getEncodedFragment(), barOn);
        if (postId == null) {
            postId = TellomiTabNavigation.feedPostIdIfHandled(uri.toString(), barOn);
        }
        if (postId == null) {
            return false;
        }
        FeedActivity.setPendingPostId(postId);
        revealFeed(activity, account);
        return true;
    }

    private static void revealFeed(LaunchActivity activity, int account) {
        MainTabsActivity tabs = popToMainTabs(activity);
        Visibility current = read(account);
        commit(account, new Visibility(true, current.calls, current.contacts));
        selectFeed(tabs);
    }

    private static void selectFeed(MainTabsActivity activity) {
        if (activity == null || activity.viewPager == null) {
            return;
        }
        int position = indexOf(visible(read(activity.getCurrentAccount())), Tab.FEED);
        if (position < 0) {
            return;
        }
        activity.selectTab(position, false);
        activity.viewPager.scrollToPosition(position);
        FeedActivity feed = findFeed(activity);
        String pending = FeedActivity.peekPendingPostId();
        if (feed != null && pending != null) {
            feed.openPost(pending);
            FeedActivity.setPendingPostId(null);
        }
    }

    private static FeedActivity findFeed(MainTabsActivity activity) {
        if (activity == null || activity.fragmentsArr == null) {
            return null;
        }
        for (int i = 0; i < activity.fragmentsArr.size(); i++) {
            ViewPagerActivity.FragmentState state = activity.fragmentsArr.valueAt(i);
            if (state != null && state.fragment instanceof FeedActivity) {
                return (FeedActivity) state.fragment;
            }
        }
        return null;
    }

    private static MainTabsActivity popToMainTabs(LaunchActivity activity) {
        if (activity == null || activity.actionBarLayout == null) {
            return null;
        }
        java.util.List<BaseFragment> stack = activity.actionBarLayout.getFragmentStack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        MainTabsActivity tabs = null;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i) instanceof MainTabsActivity) {
                tabs = (MainTabsActivity) stack.get(i);
                break;
            }
            if (i > 0) {
                activity.actionBarLayout.removeFragmentFromStack(i);
            }
        }
        return tabs;
    }

    private static boolean hideTab(MainTabsActivity activity, View anchor, Tab tab) {
        if (!enabled() || !TellomiTabNavigation.canLongPressHide(tab) || activity == null || anchor == null) {
            return false;
        }
        if (activity.getContext() == null || activity.getParentActivity() == null) {
            return false;
        }
        String title = tab == Tab.CONTACTS ? currentCopy().hideContacts : currentCopy().hideFeed;
        ItemOptions options = ItemOptions.makeOptions(activity, anchor);
        options.add(R.drawable.msg_archive_hide, title, () ->
            commit(activity.getCurrentAccount(), TellomiTabNavigation.hide(read(activity.getCurrentAccount()), tab))
        );
        options.setBlur(true);
        options.translate(0, -AndroidUtilities.dp(4));
        ShapeDrawable bg = Theme.createRoundRectDrawable(AndroidUtilities.dp(28), activity.getThemedColor(Theme.key_windowBackgroundWhite));
        bg.getPaint().setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(1), Theme.multAlpha(0xFF000000, 0.15f));
        options.setScrimViewBackground(bg);
        options.show();
        return true;
    }

    private static Tab tabForVisual(int visualIndex) {
        switch (visualIndex) {
            case VISUAL_CHATS:
                return Tab.CHATS;
            case VISUAL_CONTACTS:
                return Tab.CONTACTS;
            case VISUAL_SETTINGS:
                return Tab.SETTINGS;
            case VISUAL_CALLS:
                return Tab.CALLS;
            case VISUAL_PROFILE:
                return Tab.PROFILE;
            case VISUAL_FEED:
                return Tab.FEED;
            default:
                return null;
        }
    }
}
