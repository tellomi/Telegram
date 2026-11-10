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
import org.telegram.tgnet.ConnectionsManager;
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

import org.json.JSONArray;

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
    private TextView newPosts;
    private TextView nearbySortView;
    private int index;
    private boolean resetOnShow;
    private String appliedEngineBase = "";
    private String mountedTabs = "";
    private final Runnable probeNewPosts = new Runnable() {
        @Override
        public void run() {
            probeVisibleHead();
            AndroidUtilities.runOnUIThread(this, FeedConfig.NEW_POSTS_POLL_MS);
        }
    };

    public FeedActivity(Bundle args) {
        super(args);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("Moments_Title", R.string.Moments_Title));
        actionBar.setBackButtonDrawable(null);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setCastShadows(false);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem heart = menu.addItem(1, R.drawable.media_like);
        heart.setContentDescription(LocaleController.getString("Moments_Notifications", R.string.Moments_Notifications));
        heart.setOnClickListener(v -> presentFragment(new FeedNotificationsActivity()));

        content = new FrameLayout(context);
        installStreams();

        uploadBanner = new TextView(context);
        uploadBanner.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        uploadBanner.setBackgroundColor(Theme.getColor(Theme.key_graySection));
        uploadBanner.setPadding(dp(16), dp(10), dp(16), dp(10));
        uploadBanner.setVisibility(View.GONE);
        content.addView(uploadBanner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 40, 0, 0));

        newPosts = new TextView(context);
        newPosts.setText(LocaleController.getString("Moments_NewPosts", R.string.Moments_NewPosts));
        newPosts.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        newPosts.setTextSize(14);
        newPosts.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_featuredStickers_addButton)));
        newPosts.setPadding(dp(14), dp(6), dp(14), dp(6));
        newPosts.setVisibility(View.GONE);
        newPosts.setOnClickListener(v -> {
            newPosts.setVisibility(View.GONE);
            ensure(index, true);
        });
        content.addView(newPosts, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 48, 0, 0));

        nearbySortView = new TextView(context);
        nearbySortView.setTextSize(13);
        nearbySortView.setTypeface(AndroidUtilities.bold());
        nearbySortView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        nearbySortView.setPadding(dp(12), dp(4), dp(12), dp(4));
        nearbySortView.setVisibility(View.GONE);
        nearbySortView.setOnClickListener(v -> {
            controller().setNearbySort(FeedContract.nextNearbySort(controller().nearbySort()));
            paintTabs();
            ensure(index, true);
        });
        content.addView(nearbySortView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));

        FragmentFloatingButton fab = new FragmentFloatingButton(context, resourceProvider);
        fab.setImageResource(R.drawable.filled_fab_compose_32);
        fab.setContentDescription(LocaleController.getString("Moments_Publish", R.string.Moments_Publish));
        fab.setOnClickListener(v -> presentFragment(new FeedComposerActivity(null)));
        content.addView(fab, LayoutHelper.createFrame(56, 56, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 20, 0, 20, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16));

        fragmentView = content;
        paintTabs();
        controller().addListener(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appConfigUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);
        applyConnectionTitle();
        controller().resumeUploads();
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
                public void onPrefetchPage() {
                    if (!loading[tab] && org.telegram.messenger.feed.FeedContract.normalizeCursor(controller().cursor(streams[tab])).length() > 0) {
                        controller().prefetchNext(streams[tab]);
                    }
                }

                @Override
                public void onLoadMore() {
                    if (tab >= loading.length) {
                        return;
                    }
                    if (!loading[tab] && FeedContract.normalizeCursor(controller().cursor(streams[tab])).length() > 0) {
                        loading[tab] = true;
                        final String stream = streams[tab];
                        controller().load(stream, false, (ok, error) -> finishLoad(stream, ok, error));
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
            return LocaleController.getString("Moments_Segment_Following", R.string.Moments_Segment_Following);
        }
        if (FeedModels.STREAM_NEARBY.equals(stream)) {
            return LocaleController.getString("Moments_Segment_Nearby", R.string.Moments_Segment_Nearby);
        }
        return LocaleController.getString("Moments_Segment_Recommend", R.string.Moments_Segment_Recommend);
    }

    private void paintTabs() {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(Theme.getColor(i == index ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText));
            tabs[i].setText(titleFor(streams[i]));
        }
        if (index >= 0 && index < streams.length && FeedModels.STREAM_NEARBY.equals(streams[index]) && controller().cityName().length() > 0) {
            tabs[index].setText(LocaleController.getString("Moments_Segment_Nearby", R.string.Moments_Segment_Nearby) + " · " + controller().cityName());
        }
        boolean nearby = index >= 0 && index < streams.length && FeedModels.STREAM_NEARBY.equals(streams[index]);
        if (nearbySortView != null) {
            nearbySortView.setVisibility(nearby ? View.VISIBLE : View.GONE);
            if (nearby) {
                boolean mixed = "mixed".equals(controller().nearbySort());
                nearbySortView.setText(mixed
                        ? LocaleController.getString("Moments_Nearby_Mixed", R.string.Moments_Nearby_Mixed)
                        : LocaleController.getString("Moments_Sort_Latest", R.string.Moments_Sort_Latest));
                nearbySortView.setContentDescription(nearbySortView.getText());
            }
        }
        if (pager != null && pager.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pager.getLayoutParams();
            int top = dp(nearby ? 72 : 40);
            if (lp.topMargin != top) {
                lp.topMargin = top;
                pager.setLayoutParams(lp);
            }
        }
    }

    private void applyConnectionTitle() {
        if (actionBar == null) {
            return;
        }
        String key = FeedContract.connectionTitleKey(ConnectionsManager.getInstance(currentAccount).getConnectionState());
        if (key == null) {
            actionBar.setTitleOverlayText(null, 0, null);
            return;
        }
        int res = titleRes(key);
        actionBar.setTitleOverlayText(key, res, null);
    }

    private static int titleRes(String key) {
        if ("WaitingForNetwork".equals(key)) {
            return R.string.WaitingForNetwork;
        }
        if ("Updating".equals(key)) {
            return R.string.Updating;
        }
        if ("ConnectingToProxyWithDots".equals(key)) {
            return R.string.ConnectingToProxyWithDots;
        }
        return R.string.Connecting;
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
            if (!ok && !"cancelled".equals(error) && controller().debugFixtures()) {
                controller().posts(stream).clear();
                controller().posts(stream).addAll(FeedController.fixtures(stream));
                ok = true;
            }
            finishLoad(stream, ok, error);
            if (FeedContract.KEPT_EXISTING.equals(error)) {
                BulletinFactory.of(FeedActivity.this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString("Moments_RefreshKept", R.string.Moments_RefreshKept)).show();
            }
        });
    }

    private int streamIndex(String stream) {
        if (stream == null) {
            return -1;
        }
        for (int i = 0; i < streams.length; i++) {
            if (stream.equals(streams[i])) {
                return i;
            }
        }
        return -1;
    }

    private void finishLoad(String stream, boolean ok, String error) {
        int live = streamIndex(stream);
        if (live < 0 || live >= loading.length) {
            return;
        }
        loading[live] = false;
        if ("cancelled".equals(error)) {
            return;
        }
        failed[live] = !ok;
        if (live < loadedAt.length) {
            loadedAt[live] = System.currentTimeMillis();
        }
        if (live < lists.length && lists[live] != null) {
            lists[live].refresh();
        }
    }

    private String emptyMessage(String stream) {
        if (FeedModels.STREAM_FOLLOWING.equals(stream)) return LocaleController.getString("Moments_Empty_Following", R.string.Moments_Empty_Following);
        if (FeedModels.STREAM_NEARBY.equals(stream)) return LocaleController.getString("Moments_Empty_Nearby", R.string.Moments_Empty_Nearby);
        return LocaleController.getString("Moments_Empty_Recommend", R.string.Moments_Empty_Recommend);
    }

    private String emptyActionFor(String stream) {
        if (FeedModels.STREAM_NEARBY.equals(stream)) return LocaleController.getString("Moments_SwitchCity", R.string.Moments_SwitchCity);
        if (FeedModels.STREAM_FOLLOWING.equals(stream)) return LocaleController.getString("Moments_FindPeople", R.string.Moments_FindPeople);
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
        AndroidUtilities.cancelRunOnUIThread(probeNewPosts);
        AndroidUtilities.runOnUIThread(probeNewPosts, FeedConfig.NEW_POSTS_POLL_MS);
        applyConnectionTitle();
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
    }

    @Override
    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(probeNewPosts);
        super.onPause();
    }

    private void probeVisibleHead() {
        if (newPosts == null || streams.length == 0 || index < 0 || index >= streams.length) {
            return;
        }
        String stream = streams[index];
        if (FeedModels.STREAM_RECOMMEND.equals(stream) || controller().posts(stream).isEmpty()) {
            return;
        }
        controller().probeHead(stream, newer -> AndroidUtilities.runOnUIThread(() -> {
            if (newPosts == null || index < 0 || index >= streams.length || !stream.equals(streams[index])) {
                return;
            }
            newPosts.setVisibility(Boolean.TRUE.equals(newer) ? View.VISIBLE : View.GONE);
        }));
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(probeNewPosts);
        controller().removeListener(this);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appConfigUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.appConfigUpdated && account == currentAccount) {
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
        } else if (id == NotificationCenter.didUpdateConnectionState && account == currentAccount) {
            applyConnectionTitle();
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
                uploadBanner.setText(LocaleController.getString("Moments_FailedRetry", R.string.Moments_FailedRetry));
                uploadBanner.setOnClickListener(v -> controller().retry(task));
            } else {
                uploadBanner.setText(LocaleController.getString("Moments_Uploading", R.string.Moments_Uploading) + " " + task.progress + "%");
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
        if (post == null || post.pending) {
            return;
        }
        FeedDetailActivity detail = FeedDetailActivity.forPost(post.id);
        detail.focusReply = false;
        presentFragment(detail);
    }

    @Override
    public void onPostUpdated(String postId) {
        for (int i = 0; i < lists.length; i++) {
            if (lists[i] != null) {
                lists[i].notifyPost(postId);
            }
        }
    }

    @Override
    public void onLike(FeedModels.Post post) {
        controller().toggleLike(post);
    }

    @Override
    public void onComment(FeedModels.Post post) {
        FeedCommentSheet.open(this, post, true);
    }

    @Override
    public void onRepost(FeedModels.Post post) {
        if (!post.isPublic() && FeedConfig.ONLY_PUBLIC_CAN_REPOST) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString("Moments_RepostBlocked", R.string.Moments_RepostBlocked)).show();
            return;
        }
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_saved, post.saved ? LocaleController.getString("Moments_Saved", R.string.Moments_Saved) : LocaleController.getString("Moments_Save", R.string.Moments_Save), () -> controller().toggleSave(post));
        options.add(R.drawable.msg_forward, LocaleController.getString("Moments_Repost", R.string.Moments_Repost), () -> controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/repost", new org.json.JSONObject(), (json, error) -> {
            if (error != null) {
                BulletinFactory.of(this).createErrorBulletin(error).show();
            }
        }));
        options.add(R.drawable.msg_forward, LocaleController.getString("Moments_Quote", R.string.Moments_Quote), () -> presentFragment(FeedComposerActivity.quote(post.id)));
        options.show();
    }

    @Override
    public void onShare(FeedModels.Post post) {
        String link = FeedUi.linkFor(post.id);
        ItemOptions options = ItemOptions.makeOptions(this, fragmentView);
        options.add(R.drawable.msg_share, LocaleController.getString("Moments_ShareChat", R.string.Moments_ShareChat), () -> {
            ShareAlert alert = ShareAlert.createShareAlert(getContext(), null, link, false, link, false);
            showDialog(alert);
        });
        options.add(R.drawable.msg_link2, LocaleController.getString("Moments_CopyLink", R.string.Moments_CopyLink), () -> {
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
            options.add(R.drawable.msg_openin, LocaleController.getString("Moments_ChangeAudience", R.string.Moments_ChangeAudience), () -> FeedAudiencePicker.open(this, null, privacy -> {
                org.json.JSONObject body = new org.json.JSONObject();
                try {
                    body.put("kind", FeedAudiencePicker.kind(privacy.type));
                } catch (Exception ignore) {
                }
                controller().api.sendJson("PUT", "/v1/feed/posts/" + post.id + "/audience", body, (json, error) -> { });
            }));
            options.add(R.drawable.msg_delete, getString(R.string.Delete), () -> controller().deletePost(post.id));
        } else {
            options.add(R.drawable.msg_block, LocaleController.getString("Moments_NotInterested", R.string.Moments_NotInterested), () -> controller().notInterested(post));
            options.add(R.drawable.msg_archive_hide, LocaleController.getString("Moments_HidePost", R.string.Moments_HidePost), () -> controller().hidePost(post));
            options.add(R.drawable.msg_block2, LocaleController.getString("Moments_Mute", R.string.Moments_Mute), () -> controller().mute(post.author.profileId, true));
            options.add(R.drawable.msg_report, getString(R.string.ReportChat), () -> FeedReportSheet.open(this, "post", post.id));
            if (post.isPublic()) {
                options.add(R.drawable.msg_link2, LocaleController.getString("Moments_CopyLink", R.string.Moments_CopyLink), () -> AndroidUtilities.addToClipboard(FeedUi.linkFor(post.id)));
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
        if (post == null || post.poll == null || post.poll.closed() || option < 0 || option >= post.poll.options.size()) {
            return;
        }
        FeedModels.PollOption chosen = post.poll.options.get(option);
        JSONArray options = new JSONArray();
        options.put(chosen.id);
        boolean clear = chosen.chosen && !post.poll.multiple;
        controller().vote(post.id, options, clear, error -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                BulletinFactory.of(FeedActivity.this).createErrorBulletin(error).show();
                return;
            }
            if (clear) {
                chosen.chosen = false;
                post.poll.voted = false;
            } else if (!post.poll.multiple) {
                for (int i = 0; i < post.poll.options.size(); i++) {
                    post.poll.options.get(i).chosen = i == option;
                }
                post.poll.voted = true;
                post.poll.canSeeResults = true;
            } else {
                chosen.chosen = true;
                post.poll.voted = true;
                post.poll.canSeeResults = true;
            }
            onChanged();
        }));
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
