package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedListView extends FrameLayout {

    public interface Host {
        ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts();
        void onRefresh();
        void onLoadMore();
        void onPrefetchPage();
        boolean loading();
        boolean failed();
        boolean hasMore();
        FeedPostCell.Delegate delegate();
        int account();
        boolean showFollow();
        String emptyText();
        String emptyAction();
        Runnable emptyClick();
    }

    private final Host host;
    public final RecyclerListView listView;
    private final FeedPreloader preloader;
    private final TextView footer;
    private int scrollDirection = 1;
    private long lastScrollAt;
    private View emptyView;
    private float downY;
    private boolean pulling;
    private boolean refreshPosted;

    public FeedListView(Context context, Host host) {
        super(context);
        this.host = host;
        preloader = new FeedPreloader(host.account());
        listView = new RecyclerListView(context);
        preloader.setParent(listView);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setItemAnimator(null);
        Adapter adapter = new Adapter();
        adapter.setHasStableIds(true);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((View view, int position, float x, float y) -> {
            ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts = host.posts();
            if (!(view instanceof FeedPostCell) || position < 0 || position >= posts.size() || host.delegate() == null) {
                return;
            }
            if (!((FeedPostCell) view).tapOpensDetail(x, y)) {
                return;
            }
            host.delegate().onOpen(posts.get(position));
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) {
                    scrollDirection = dy > 0 ? 1 : -1;
                }
                consider(dy, listView.getScrollState() == RecyclerView.SCROLL_STATE_SETTLING);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                consider(0, false);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    postDelayed(() -> host.onPrefetchPage(), 1000);
                }
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        footer = new TextView(context);
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        footer.setTextSize(15);
        footer.setPadding(dp(24), dp(24), dp(24), dp(24));
        addView(footer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER));
        footer.setOnClickListener(v -> host.onRefresh());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preloader.setAttached(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        preloader.setAttached(false);
        super.onDetachedFromWindow();
    }

    private void consider(int dy, boolean settling) {
        LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
        FeedPlayback.choose();
        if (manager == null) {
            return;
        }
        int first = manager.findFirstVisibleItemPosition();
        int last = manager.findLastVisibleItemPosition();
        int count = host.posts().size();
        if (last >= 0 && count > 0 && org.telegram.messenger.feed.FeedContract.shouldLoadMore(count - 1 - last, host.hasMore(), host.loading())) {
            host.onLoadMore();
        }
        long now = android.os.SystemClock.uptimeMillis();
        boolean fling = false;
        if (settling && dy != 0 && lastScrollAt > 0) {
            float seconds = Math.max(0.016f, (now - lastScrollAt) / 1000f);
            float screens = Math.abs(dy) / (float) Math.max(1, listView.getHeight());
            fling = screens / seconds > org.telegram.messenger.feed.FeedContract.FLING_PAUSE_SPEED;
        }
        lastScrollAt = now;
        preloader.update(host.posts(), first, last, scrollDirection, fling);
    }

    public void refresh() {
        if (listView.isComputingLayout()) {
            if (!refreshPosted) {
                refreshPosted = true;
                listView.post(() -> {
                    refreshPosted = false;
                    refresh();
                });
            }
            return;
        }
        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        boolean empty = host.posts().isEmpty();
        if (!empty) {
            footer.setVisibility(GONE);
            if (emptyView != null) {
                emptyView.setVisibility(GONE);
            }
            return;
        }
        if (host.loading()) {
            showStatus(getString(R.string.Loading), null);
        } else if (host.failed()) {
            showStatus(LocaleController.getString("Moments_LoadFailed", R.string.Moments_LoadFailed), v -> host.onRefresh());
        } else {
            footer.setVisibility(GONE);
            if (emptyView != null) {
                removeView(emptyView);
            }
            String action = host.emptyAction();
            if (action != null && action.equals(LocaleController.getString("Moments_Publish", R.string.Moments_Publish))) {
                action = null;
            }
            emptyView = FeedUi.empty(getContext(), host.emptyText(), action, host.emptyClick());
            addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER));
        }
    }

    private void showStatus(String text, android.view.View.OnClickListener click) {
        if (emptyView != null) {
            emptyView.setVisibility(GONE);
        }
        footer.setVisibility(VISIBLE);
        footer.setText(text);
        footer.setOnClickListener(click);
    }

    public void scrollToTop() {
        listView.scrollToPosition(0);
    }

    public static final String PAYLOAD_LIKE = "like";

    public void notifyPost(String postId) {
        if (postId == null || listView.getAdapter() == null) {
            return;
        }
        ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts = host.posts();
        for (int i = 0; i < posts.size(); i++) {
            org.telegram.messenger.feed.FeedModels.Post post = posts.get(i);
            if (post != null && postId.equals(post.id)) {
                listView.getAdapter().notifyItemChanged(i, PAYLOAD_LIKE);
                return;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            downY = ev.getY();
            pulling = !listView.canScrollVertically(-1);
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE && pulling) {
            if (ev.getY() - downY > dp(72)) {
                pulling = false;
                host.onRefresh();
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) {
                TextView retry = new TextView(parent.getContext());
                retry.setText(LocaleController.getString("Moments_LoadFailed", R.string.Moments_LoadFailed));
                retry.setGravity(android.view.Gravity.CENTER);
                retry.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                retry.setPadding(dp(16), dp(16), dp(16), dp(16));
                retry.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
                return new RecyclerListView.Holder(retry);
            }
            FeedPostCell cell = new FeedPostCell(parent.getContext(), host.account(), false, host.showFollow(), host.delegate());
            cell.setPreloadReceivers(preloader.receivers());
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            onBindViewHolder(holder, position, java.util.Collections.emptyList());
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
            if (holder.getItemViewType() == 1) {
                holder.itemView.setOnClickListener(v -> host.onLoadMore());
                return;
            }
            ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts = host.posts();
            if (!(holder.itemView instanceof FeedPostCell) || position < 0 || position >= posts.size()) {
                return;
            }
            FeedPostCell cell = (FeedPostCell) holder.itemView;
            org.telegram.messenger.feed.FeedModels.Post post = posts.get(position);
            if (payloads != null && payloads.contains(PAYLOAD_LIKE)) {
                cell.bindLike(post);
                return;
            }
            cell.setPost(post);
        }

        @Override
        public long getItemId(int position) {
            ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts = host.posts();
            if (position < 0 || position >= posts.size() || posts.get(position) == null || posts.get(position).id == null) {
                return RecyclerView.NO_ID;
            }
            return posts.get(position).id.hashCode() & 0xffffffffL;
        }

        @Override
        public int getItemViewType(int position) {
            return position >= host.posts().size() ? 1 : 0;
        }

        @Override
        public int getItemCount() {
            int n = host.posts().size();
            if (n > 0 && host.failed()) {
                return n + 1;
            }
            return n;
        }
    }

    public void applyBottomInset(int px) {
        listView.setPadding(0, 0, 0, px);
        listView.setClipToPadding(false);
    }
}
