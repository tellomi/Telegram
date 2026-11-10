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
import org.telegram.messenger.feed.FeedContract;
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

    private FrameLayout content;
    private ViewPagerFixed pager;
    private LinearLayout tabsRow;
    private String[] streams = new String[0];
    private FeedListView[] lists = new FeedListView[0];
    private boolean[] loading = new boolean[0];
    private boolean[] failed = new boolean[0];
    private long[] loadedAt = new long[0];
    private TextView[] tabs = new TextView[0];
    private TextView uploadBanner;
    private int index;
    private boolean resetOnShow;
    private String appliedEngineBase = "";
    private String mountedTabs = "";

    public FeedActivity(Bundle args) {
        super(args);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(FeedUi.s("Moments.Title", R.string.FeedTitle));
        actionBar.setBackButtonDrawable(null);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setCastShadows(false);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem heart = menu.addItem(1, R.drawable.media_like);
        heart.setContentDescription(FeedUi.s("Moments.Notifications", R.string.FeedNotifications));
        heart.setOnClickListener(v -> presentFragment(new FeedNotificationsActivity()));

        content = new FrameLayout(context);
        installStreams();

        uploadBanner = new TextView(context);
        uploadBanner.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        uploadBanner.setBackgroundColor(Theme.getColor(Theme.key_graySection));
        uploadBanner.setPadding(dp(16), dp(10), dp(16), dp(10));
        uploadBanner.setVisibility(View.GONE);
        content.addView(uploadBanner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 40, 0, 0));

        FragmentFloatingButton fab = new FragmentFloatingButton(context, resourceProvider);
        fab.setImageResource(R.drawable.filled_fab_compose_32);
        fab.setContentDescription(FeedUi.s("Moments.Publish", R.string.FeedPublish));
        fab.setOnClickListener(v -> presentFragment(new FeedComposerActivity(null)));
        content.addView(fab, LayoutHelper.createFrame(56, 56, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 20, 0, 20, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16));

        fragmentView = content;
        paintTabs();
        controller().addListener(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appConfigUpdated);
        controller().resumeUploads();
        controller().refreshGate();
        if (controller().debugFixtures() && streams.length > 0 && controller().posts(streams[0]).isEmpty()) {
            controller().setDebugFixtures(true);
        }
        appliedEngineBase = FeedConfig.engineBase(currentAccount);
        if (streams.length > 0) {
            ensure(0, controller().posts(streams[0]).isEmpty());
        }
        onChanged();
        return fragmentView;
    }

    private void installStreams() {
        ArrayList<String> next = controller().tabs();
        if (next.isEmpty()) {
            next = FeedContract.defaultTabs();
        }
        String signature = FeedContract.joinTabs(next);
        if (signature.equals(mountedTabs) && pager != null) {
            return;
        }
        mountedTabs = signature;
        streams = next.toArray(new String[0]);
        index = 0;
        loading = new boolean[streams.length];
        failed = new boolean[streams.length];
        loadedAt = new long[streams.length];
        lists = new FeedListView[streams.length];
        tabs = new TextView[streams.length];
        if (tabsRow != null) {
            content.removeView(tabsRow);
        }
        if (pager != null) {
            content.removeView(pager);
        }
        tabsRow = new LinearLayout(content.getContext());
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        for (int i = 0; i < streams.length; i++) {
            final int tab = i;
            TextView view = new TextView(content.getContext());
            view.setText(titleFor(streams[i]));
            view.setGravity(Gravity.CENTER);
            view.setTextSize(14);
            view.setTypeface(AndroidUtilities.bold());
            view.setOnClickListener(v -> {
                if (pager != null) {
                    pager.scrollToPosition(tab);
                }
            });
            tabs[i] = view;
            tabsRow.addView(view, LayoutHelper.createLinear(0, 40, 1f));
        }
        content.addView(tabsRow, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP));
        for (int i = 0; i < streams.length; i++) {
            final int tab = i;
            lists[i] = new FeedListView(content.getContext(), new FeedListView.Host() {
                @Override
                public ArrayList<FeedModels.Post> posts() {
                    return controller().posts(streams[tab]);
                }

                @Override
                public void onRefresh() {
                    ensure(tab, true);
                }

                @Override
                public void onLoadMore() {
                    if (tab >= loading.length) {
                        return;
                    }
                    if (!loading[tab] && FeedContract.normalizeCursor(controller().cursor(streams[tab])).length() > 0) {
                        loading[tab] = true;
                        controller().load(streams[tab], false, (ok, error) -> {
                            loading[tab] = false;
                            failed[tab] = !ok;
                            if (tab < lists.length && lists[tab] != null) {
                                lists[tab].refresh();
                            }
                        });
                    }
                }

                @Override
                public boolean loading() {
                    return tab < loading.length && loading[tab];
                }

                @Override
                public boolean failed() {
                    return tab < failed.length && failed[tab];
                }

                @Override
                public boolean hasMore() {
                    return FeedContract.normalizeCursor(controller().cursor(streams[tab])).length() > 0;
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
                    return FeedModels.STREAM_RECOMMEND.equals(streams[tab]);
                }

                @Override
                public String emptyText() {
                    return emptyMessage(streams[tab]);
                }

                @Override
                public String emptyAction() {
                    return emptyActionFor(streams[tab]);
                }

                @Override
                public Runnable emptyClick() {
                    return () -> onEmpty(streams[tab]);
                }
            });
            lists[i].applyBottomInset(dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 72));
        }
        pager = new ViewPagerFixed(content.getContext()) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                int next = Math.round(getPositionAnimated());
                if (next != index && next >= 0 && next < streams.length) {
                    index = next;
                    paintTabs();
                    ensure(index, false);
                }
            }
        };
        pager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return streams.length;
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }

            @Override
            public View createView(int viewType) {
                if (viewType < 0 || viewType >= lists.length) {
                    return new View(content.getContext());
                }
                return lists[viewType];
            }

            @Override
            public void bindView(View view, int position, int viewType) {
            }
        });
        content.addView(pager, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 40, 0, 0));
    }

    private String titleFor(String stream) {
        if (FeedModels.STREAM_FOLLOWING.equals(stream)) {
            return FeedUi.s("Moments.Segment.Following", R.string.FeedFollowing);
        }
        if (FeedModels.STREAM_NEARBY.equals(stream)) {
            return FeedUi.s("Moments.Segment.Nearby", R.string.FeedNearby);
        }
        return FeedUi.s("Moments.Segment.Recommend", R.string.FeedRecommend);
    }

    private void paintTabs() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(Theme.getColor(i == index ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
            tabs[i].setText(titleFor(streams[i]));
        }
        if (index >= 0 && index < streams.length && FeedModels.STREAM_NEARBY.equals(streams[index]) && controller().cityName().length() > 0) {
            tabs[index].setText(FeedUi.s("Moments.Segment.Nearby", R.string.FeedNearby) + " · " + controller().cityName());
        }
    }

    private void ensure(int tab, boolean refresh) {
        if (tab < 0 || tab >= streams.length || lists.length <= tab) {
            return;
        }
        if (FeedModels.STREAM_NEARBY.equals(streams[tab]) && controller().cityCode().length() == 0 && !controller().askedLocation()) {
            presentFragment(new FeedCityActivity(true));
            return;
        }
        if (loading[tab]) {
            return;
        }
        if (!refresh && !controller().posts(streams[tab]).isEmpty()) {
            lists[tab].refresh();
            return;
        }
        loading[tab] = true;
        failed[tab] = false;
        lists[tab].refresh();
        final String stream = streams[tab];
        controller().load(stream, true, (ok, error) -> {
            if (tab >= loading.length) {
                return;
            }
            loading[tab] = false;
            failed[tab] = !ok;
            loadedAt[tab] = System.currentTimeMillis();
            if (!ok && controller().debugFixtures()) {
                controller().posts(stream).clear();
                controller().posts(stream).addAll(FeedController.fixtures(stream));
                failed[tab] = false;
            }
            if (tab < lists.length && lists[tab] != null) {
                lists[tab].refresh();
            }
            if (FeedContract.KEPT_EXISTING.equals(error)) {
                BulletinFactory.of(FeedActivity.this).createSimpleBulletin(R.raw.chats_infotip, FeedUi.s("Moments.RefreshKept", R.string.FeedRefreshKept)).show();
            }
        });
    }

    private String emptyMessage(String stream) {
        if (FeedModels.STREAM_FOLLOWING.equals(stream)) return FeedUi.s("Moments.Empty.Following", R.string.FeedEmptyFollowing);
        if (FeedModels.STREAM_NEARBY.equals(stream)) return FeedUi.s("Moments.Empty.Nearby", R.string.FeedEmptyNearby);
        return FeedUi.s("Moments.Empty.Recommend", R.string.FeedEmptyRecommend);
    }

    private String emptyActionFor(String stream) {
        if (FeedModels.STREAM_NEARBY.equals(stream)) return FeedUi.s("Moments.SwitchCity", R.string.FeedChangeCity);
        if (FeedModels.STREAM_FOLLOWING.equals(stream)) return FeedUi.s("Moments.FindPeople", R.string.FeedFindPeople);
        return null;
    }

    private void onEmpty(String stream) {
        if (FeedModels.STREAM_NEARBY.equals(stream)) {
            presentFragment(new FeedCityActivity(false));
        } else if (FeedModels.STREAM_FOLLOWING.equals(stream)) {
            if (pager != null) {
                pager.scrollToPosition(0);
            }
        } else {
            presentFragment(new FeedComposerActivity(null));
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
            if (lists.length > 0 && lists[0] != null) {
                lists[0].scrollToTop();
            }
            ensure(0, true);
            paintTabs();
        }
        controller().refreshGate();
    }

    @Override
    public void onFragmentDestroy() {
        controller().removeListener(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appConfigUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appConfigUpdated && account == currentAccount) {
            controller().refreshGate();
            if (lists == null || index < 0 || index >= lists.length || lists[index] == null) {
                return;
            }
            String base = FeedConfig.engineBase(currentAccount);
            if (base.equals(appliedEngineBase)) {
                return;
            }
            appliedEngineBase = base;
            controller().forgetRemoteConfig();
            if (index < loading.length && !loading[index]) {
                ensure(index, true);
            }
        }
    }

    @Override
    public void onChanged() {
        if (content != null) {
            String signature = FeedContract.joinTabs(controller().tabs());
            if (signature.length() > 0 && !signature.equals(mountedTabs)) {
                installStreams();
                if (streams.length > 0) {
                    ensure(0, controller().posts(streams[0]).isEmpty());
                }
            }
        }
        if (lists.length == 0 || lists[0] == null) {
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
                uploadBanner.setText(FeedUi.s("Moments.FailedRetry", R.string.FeedUploadFailed));
                uploadBanner.setOnClickListener(v -> controller().retry(task));
            } else {
                uploadBanner.setText(FeedUi.s("Moments.Uploading", R.string.FeedUploading) + " " + task.progress + "%");
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
        if (index >= 0 && index < lists.length && lists[index] != null) {
            lists[index].scrollToTop();
        }
    }

    @Override
    public void onOpen(FeedModels.Post post) {
        FeedCommentSheet.open(this, post.id, false);
    }

    @Override
    public void onLike(FeedModels.Post post) {
        controller().toggleLike(post);
    }

    @Override
    public void onComment(FeedModels.Post post) {
        FeedCommentSheet.open(this, post.id, true);
    }

    @Override
    public void onRepost(FeedModels.Post post) {
        if (!post.isPublic() && FeedConfig.ONLY_PUBLIC_CAN_REPOST) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, FeedUi.s("Moments.RepostBlocked", R.string.FeedRepostBlocked)).show();
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_saved, post.saved ? FeedUi.s("Moments.Saved", R.string.FeedSaved) : FeedUi.s("Moments.Save", R.string.FeedSave), () -> controller().toggleSave(post));
        options.add(R.drawable.msg_forward, FeedUi.s("Moments.Repost", R.string.FeedRepost), () -> controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/repost", new org.json.JSONObject(), (json, error) -> {
            if (error != null) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
            }
        }));
        options.add(R.drawable.msg_forward, FeedUi.s("Moments.Quote", R.string.FeedQuote), () -> presentFragment(FeedComposerActivity.quote(post.id)));
        options.show();
    }

    @Override
    public void onShare(FeedModels.Post post) {
        String link = FeedUi.linkFor(post.id);
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_share, FeedUi.s("Moments.ShareChat", R.string.FeedShareChat), () -> {
            ShareAlert alert = ShareAlert.createShareAlert(getContext(), null, link, false, link, false);
            showDialog(alert);
        });
        options.add(R.drawable.msg_link2, FeedUi.s("Moments.CopyLink", R.string.FeedCopyLink), () -> {
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
            options.add(R.drawable.msg_openin, FeedUi.s("Moments.WhoCanSee", R.string.FeedChangeAudience), () -> FeedAudiencePicker.open(this, null, privacy -> {
                org.json.JSONObject body = new org.json.JSONObject();
                try {
                    body.put("kind", FeedAudiencePicker.kind(privacy.type));
                } catch (Exception ignore) {
                }
                controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/audience", body, (json, error) -> { });
            }));
            options.add(R.drawable.msg_delete, getString(R.string.Delete), () -> controller().deletePost(post.id));
        } else {
            options.add(R.drawable.msg_block, FeedUi.s("Moments.NotInterested", R.string.FeedNotInterested), () -> controller().notInterested(post));
            options.add(R.drawable.msg_archive_hide, FeedUi.s("Moments.HidePost", R.string.FeedHidePost), () -> controller().hidePost(post));
            options.add(R.drawable.msg_block2, FeedUi.s("Moments.Mute", R.string.FeedMute), () -> controller().mute(post.author.profileId, true));
            options.add(R.drawable.msg_report, getString(R.string.ReportChat), () -> FeedReportSheet.open(this, "post", post.id));
            if (post.isPublic()) {
                options.add(R.drawable.msg_link2, FeedUi.s("Moments.CopyLink", R.string.FeedCopyLink), () -> AndroidUtilities.addToClipboard(FeedUi.linkFor(post.id)));
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
        FeedCommentSheet.open(this, post.id, false);
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
