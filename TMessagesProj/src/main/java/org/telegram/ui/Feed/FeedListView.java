package org.telegram.ui.Feed;

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
    private final TextView footer;
    private float downY;
    private boolean pulling;

    public FeedListView(Context context, Host host) {
        super(context);
        this.host = host;
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        listView.setOnItemClickListener((view, position) -> {
            ArrayList<org.telegram.messenger.feed.FeedModels.Post> posts = host.posts();
            if (position >= 0 && position < posts.size() && host.delegate() != null) {
                host.delegate().onOpen(posts.get(position));
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
                if (manager != null && host.hasMore() && !host.loading()) {
                    int last = manager.findLastVisibleItemPosition();
                    if (last >= host.posts().size() - 3) {
                        host.onLoadMore();
                    }
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

    public void refresh() {
        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        boolean empty = host.posts().isEmpty();
        footer.setVisibility(empty ? VISIBLE : GONE);
        if (!empty) {
            return;
        }
        if (host.loading()) {
            footer.setText(getString(R.string.Loading));
        } else if (host.failed()) {
            footer.setText(getString(R.string.FeedRetry));
        } else {
            String action = host.emptyAction();
            footer.setText(host.emptyText() + (action == null ? "" : "\n" + action));
        }
    }

    public void scrollToTop() {
        listView.scrollToPosition(0);
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
                retry.setText(getString(R.string.FeedRetry));
                retry.setGravity(android.view.Gravity.CENTER);
                retry.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                retry.setPadding(dp(16), dp(16), dp(16), dp(16));
                retry.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
                return new RecyclerListView.Holder(retry);
            }
            FeedPostCell cell = new FeedPostCell(parent.getContext(), host.account(), false, host.showFollow(), host.delegate());
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                holder.itemView.setOnClickListener(v -> host.onLoadMore());
                return;
            }
            ((FeedPostCell) holder.itemView).setPost(host.posts().get(position));
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
