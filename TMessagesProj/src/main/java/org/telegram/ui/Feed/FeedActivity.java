package org.telegram.ui.Feed;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate, FeedController.Listener, FeedPostCell.Delegate, NotificationCenter.NotificationCenterDelegate {

    private ViewPagerFixed pager;
    private final FeedListView[] lists = new FeedListView[4];
    private final boolean[] loading = new boolean[4];
    private final boolean[] failed = new boolean[4];
    private final long[] loadedAt = new long[4];
    private TextView[] tabs;
    private TextView newPosts;
    private TextView uploadBanner;
    private int index;
    private boolean resetOnShow;
    private Runnable poll;
    private String appliedEngineBase = "";

    public FeedActivity(Bundle args) {
        super(args);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.FeedTitle));
        actionBar.setBackButtonDrawable(null);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setCastShadows(false);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem heart = menu.addItem(1, R.drawable.media_like);
        heart.setContentDescription(getString(R.string.FeedNotifications));
        heart.setOnClickListener(v -> presentFragment(new FeedNotificationsActivity()));

        FrameLayout content = new FrameLayout(context);
        LinearLayout tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        String[] titles = new String[]{
                getString(R.string.FeedRecommend),
                getString(R.string.FeedFollowing),
                getString(R.string.FeedFriends),
                getString(R.string.FeedNearby)
        };
        tabs = new TextView[4];
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            TextView view = new TextView(context);
            view.setText(titles[i]);
            view.setGravity(Gravity.CENTER);
            view.setTextSize(14);
            view.setTypeface(AndroidUtilities.bold());
            view.setOnClickListener(v -> pager.scrollToPosition(tab));
            tabs[i] = view;
            tabsRow.addView(view, LayoutHelper.createLinear(0, 40, 1f));
        }
        content.addView(tabsRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP));

        pager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                int next = Math.round(getPositionAnimated());
                if (next != index && next >= 0 && next < 4) {
                    index = next;
                    paintTabs();
                    ensure(index, false);
                }
            }
        };
        pager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 4;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public View createView(int viewType) {
                return lists[viewType];
            }

            @Override
            public void bindView(View view, int position, int viewType) {
            }
        });
        for (int i = 0; i < 4; i++) {
            final int tab = i;
            lists[i] = new FeedListView(context, new FeedListView.Host() {
                @Override
                public ArrayList<FeedModels.Post> posts() {
                    return controller().posts(FeedModels.STREAMS[tab]);
                }

                @Override
                public void onRefresh() {
                    ensure(tab, true);
                }

                @Override
                public void onLoadMore() {
                    if (!loading[tab] && controller().cursor(FeedModels.STREAMS[tab]).length() > 0) {
                        loading[tab] = true;
                        controller().load(FeedModels.STREAMS[tab], false, (ok, error) -> {
                            loading[tab] = false;
                            failed[tab] = !ok && controller().posts(FeedModels.STREAMS[tab]).isEmpty();
                            if (!ok && !controller().posts(FeedModels.STREAMS[tab]).isEmpty()) {
                                failed[tab] = true;
                            }
                            lists[tab].refresh();
                        });
                    }
                }

                @Override
                public boolean loading() {
                    return loading[tab];
                }

                @Override
                public boolean failed() {
                    return failed[tab];
                }

                @Override
                public boolean hasMore() {
                    return controller().cursor(FeedModels.STREAMS[tab]).length() > 0;
                }

                @Override
                public FeedPostCell.Delegate delegate() {
                    return FeedActivity.this;
                }

                @Override
                public int account() {
                    return currentAccount;
                }

                @Override
                public boolean showFollow() {
                    return tab == 0;
                }

                @Override
                public String emptyText() {
                    return emptyMessage(tab);
                }

                @Override
                public String emptyAction() {
                    return emptyActionFor(tab);
                }

                @Override
                public Runnable emptyClick() {
                    return () -> onEmpty(tab);
                }
            });
            lists[i].applyBottomInset(dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 72));
        }
        content.addView(pager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 40, 0, 0));

        newPosts = new TextView(context);
        newPosts.setText(getString(R.string.FeedNewPosts));
        newPosts.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        newPosts.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_featuredStickers_addButton)));
        newPosts.setPadding(dp(14), dp(8), dp(14), dp(8));
        newPosts.setVisibility(View.GONE);
        newPosts.setOnClickListener(v -> {
            newPosts.setVisibility(View.GONE);
            lists[index].scrollToTop();
            ensure(index, true);
        });
        content.addView(newPosts, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));

        uploadBanner = new TextView(context);
        uploadBanner.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        uploadBanner.setBackgroundColor(Theme.getColor(Theme.key_graySection));
        uploadBanner.setPadding(dp(16), dp(10), dp(16), dp(10));
        uploadBanner.setVisibility(View.GONE);
        content.addView(uploadBanner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 40, 0, 0));

        FragmentFloatingButton fab = new FragmentFloatingButton(context, resourceProvider);
        fab.setImageResource(R.drawable.filled_fab_compose_32);
        fab.setContentDescription(getString(R.string.FeedPublish));
        fab.setOnClickListener(v -> presentFragment(new FeedComposerActivity(null)));
        content.addView(fab, LayoutHelper.createFrame(56, 56, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 20, 0, 20, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16));

        fragmentView = content;
        paintTabs();
        controller().addListener(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appConfigUpdated);
        controller().resumeUploads();
        if (controller().debugFixtures() && controller().posts(FeedModels.STREAM_RECOMMEND).isEmpty()) {
            controller().setDebugFixtures(true);
        }
        appliedEngineBase = FeedConfig.engineBase(currentAccount);
        ensure(0, controller().posts(FeedModels.STREAM_RECOMMEND).isEmpty());
        onChanged();
        return fragmentView;
    }

    private void paintTabs() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(Theme.getColor(i == index ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
        }
        if (index == 3 && controller().cityName().length() > 0) {
            tabs[3].setText(getString(R.string.FeedNearby) + " · " + controller().cityName());
        }
    }

    private void ensure(int tab, boolean refresh) {
        if (tab == 3 && controller().cityCode().length() == 0 && !controller().askedLocation()) {
            presentFragment(new FeedCityActivity(true));
            return;
        }
        if (loading[tab]) {
            return;
        }
        if (!refresh && !controller().posts(FeedModels.STREAMS[tab]).isEmpty()) {
            lists[tab].refresh();
            return;
        }
        loading[tab] = true;
        failed[tab] = false;
        lists[tab].refresh();
        controller().load(FeedModels.STREAMS[tab], true, (ok, error) -> {
            loading[tab] = false;
            failed[tab] = !ok;
            loadedAt[tab] = System.currentTimeMillis();
            if (!ok && controller().debugFixtures()) {
                controller().posts(FeedModels.STREAMS[tab]).clear();
                controller().posts(FeedModels.STREAMS[tab]).addAll(FeedController.fixtures(FeedModels.STREAMS[tab]));
                failed[tab] = false;
            }
            lists[tab].refresh();
        });
    }

    private String emptyMessage(int tab) {
        if (tab == 0) return getString(R.string.FeedEmptyRecommend);
        if (tab == 1) return getString(R.string.FeedEmptyFollowing);
        if (tab == 2) return getString(R.string.FeedEmptyFriends);
        return getString(R.string.FeedEmptyNearby);
    }

    private String emptyActionFor(int tab) {
        if (tab == 0) return getString(R.string.FeedPublish);
        if (tab == 3) return getString(R.string.FeedChangeCity);
        return getString(R.string.FeedFindPeople);
    }

    private void onEmpty(int tab) {
        if (tab == 0) {
            presentFragment(new FeedComposerActivity(null));
        } else if (tab == 3) {
            presentFragment(new FeedCityActivity(false));
        } else {
            pager.scrollToPosition(0);
        }
    }

    public void consumeLeave() {
        resetOnShow = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (resetOnShow) {
            resetOnShow = false;
            index = 0;
            if (pager != null) {
                pager.scrollToPosition(0);
            }
            if (lists[0] != null) {
                lists[0].scrollToTop();
            }
            ensure(0, true);
            paintTabs();
        }
        schedulePoll();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (poll != null) {
            AndroidUtilities.cancelRunOnUIThread(poll);
        }
    }

    private void schedulePoll() {
        if (poll != null) {
            AndroidUtilities.cancelRunOnUIThread(poll);
        }
        poll = () -> {
            if (index == 0) {
                schedulePoll();
                return;
            }
            final int tab = index;
            controller().newCount(FeedModels.STREAMS[tab], loadedAt[tab], count -> {
                if (count != null && count > 0 && newPosts != null) {
                    newPosts.setVisibility(View.VISIBLE);
                }
            });
            schedulePoll();
        };
        AndroidUtilities.runOnUIThread(poll, FeedConfig.NEW_POSTS_POLL_MS);
    }

    @Override
    public void onFragmentDestroy() {
        controller().removeListener(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appConfigUpdated);
        if (poll != null) {
            AndroidUtilities.cancelRunOnUIThread(poll);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appConfigUpdated && account == currentAccount && lists != null && lists[index] != null) {
            String base = FeedConfig.engineBase(currentAccount);
            if (base.equals(appliedEngineBase)) {
                return;
            }
            appliedEngineBase = base;
            controller().forgetRemoteConfig();
            if (!loading[index]) {
                ensure(index, true);
            }
        }
    }

    @Override
    public void onChanged() {
        if (lists[0] == null) {
            return;
        }
        for (int i = 0; i < lists.length; i++) {
            lists[i].refresh();
        }
        ArrayList<FeedController.UploadTask> tasks = controller().uploads();
        if (tasks.isEmpty()) {
            uploadBanner.setVisibility(View.GONE);
        } else {
            FeedController.UploadTask task = tasks.get(0);
            uploadBanner.setVisibility(View.VISIBLE);
            if ("failed".equals(task.state)) {
                uploadBanner.setText(getString(R.string.FeedUploadFailed));
                uploadBanner.setOnClickListener(v -> controller().retry(task));
            } else {
                uploadBanner.setText(getString(R.string.FeedUploading) + " " + task.progress + "%");
                uploadBanner.setOnClickListener(v -> controller().cancel(task));
            }
        }
        paintTabs();
    }

    @Override
    public boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
        return false;
    }

    @Override
    public void onParentScrollToTop() {
        if (lists[index] != null) {
            lists[index].scrollToTop();
        }
    }

    @Override
    public void onOpen(FeedModels.Post post) {
        presentFragment(FeedDetailActivity.forPost(post.id));
    }

    @Override
    public void onLike(FeedModels.Post post) {
        controller().toggleLike(post);
    }

    @Override
    public void onComment(FeedModels.Post post) {
        FeedDetailActivity fragment = FeedDetailActivity.forPost(post.id);
        fragment.focusReply = true;
        presentFragment(fragment);
    }

    @Override
    public void onRepost(FeedModels.Post post) {
        if (!post.isPublic() && FeedConfig.ONLY_PUBLIC_CAN_REPOST) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.FeedRepostBlocked)).show();
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_forward, getString(R.string.FeedRepost), () -> controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/repost", new org.json.JSONObject(), (json, error) -> {
            if (error != null) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
            }
        }));
        options.add(R.drawable.msg_forward, getString(R.string.FeedQuote), () -> presentFragment(FeedComposerActivity.quote(post.id)));
        options.show();
    }

    @Override
    public void onShare(FeedModels.Post post) {
        String link = FeedUi.linkFor(post.id);
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_share, getString(R.string.FeedShareChat), () -> {
            ShareAlert alert = ShareAlert.createShareAlert(getContext(), null, link, false, link, false);
            showDialog(alert);
        });
        options.add(R.drawable.msg_link2, getString(R.string.FeedCopyLink), () -> {
            AndroidUtilities.addToClipboard(link);
            BulletinFactory.of(this).createCopyLinkBulletin().show();
        });
        options.show();
    }

    @Override
    public void onSave(FeedModels.Post post) {
        controller().toggleSave(post);
    }

    @Override
    public void onMore(FeedModels.Post post, View anchor) {
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        if (FeedUi.isSelf(currentAccount, post.author)) {
            options.add(R.drawable.msg_edit, getString(R.string.Edit), () -> presentFragment(FeedComposerActivity.edit(post.id)));
            options.add(R.drawable.msg_openin, getString(R.string.FeedChangeAudience), () -> FeedAudiencePicker.open(this, null, privacy -> {
                org.json.JSONObject body = new org.json.JSONObject();
                try {
                    body.put("kind", FeedAudiencePicker.kind(privacy.type));
                } catch (Exception ignore) {
                }
                controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/audience", body, (json, error) -> { });
            }));
            options.add(R.drawable.msg_delete, getString(R.string.Delete), () -> controller().deletePost(post.id));
        } else {
            options.add(R.drawable.msg_block, getString(R.string.FeedNotInterested), () -> controller().notInterested(post));
            options.add(R.drawable.msg_archive_hide, getString(R.string.FeedHidePost), () -> controller().hidePost(post));
            options.add(R.drawable.msg_block2, getString(R.string.FeedMute), () -> controller().mute(post.author.profileId, true));
            options.add(R.drawable.msg_report, getString(R.string.ReportChat), () -> FeedReportSheet.open(this, "post", post.id));
            if (post.isPublic()) {
                options.add(R.drawable.msg_link2, getString(R.string.FeedCopyLink), () -> AndroidUtilities.addToClipboard(FeedUi.linkFor(post.id)));
            }
        }
        options.show();
    }

    @Override
    public void onFollow(FeedModels.Post post) {
        boolean next = !post.following;
        post.following = next;
        controller().follow(post.author.profileId, next);
        onChanged();
    }

    @Override
    public void onAuthor(FeedModels.Post post) {
        FeedUi.openProfile(this, post.author);
    }

    @Override
    public void onVote(FeedModels.Post post, int option) {
        presentFragment(FeedDetailActivity.forPost(post.id));
    }

    @Override
    public void onMedia(FeedModels.Post post, int index) {
        FeedMediaViewer.open(this, post, index);
    }

    public static boolean autoplayAllowed() {
        return LiteMode.isEnabled(LiteMode.FLAG_AUTOPLAY_VIDEOS) && !FeedUi.reduceMotion();
    }

    @SuppressWarnings("unused")
    private void openExternal(String url) {
        Browser.openUrl(getContext(), url);
    }
}
