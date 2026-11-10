package org.telegram.ui.Feed;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedApi;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedTopicActivity extends BaseFragment {

    private final String name;
    private String sort = "hot";
    private int visibleCount;
    private final ArrayList<FeedModels.Post> posts = new ArrayList<>();
    private RecyclerListView listView;

    public FeedTopicActivity(String name) {
        this.name = name == null ? "" : name;
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle("#" + name);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) FeedReportSheet.open(thisFragment(), "topic", name);
            }
        });
        actionBar.createMenu().addItem(1, R.drawable.msg_report);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        fragmentView = listView;
        load();
        return fragmentView;
    }

    private BaseFragment thisFragment() {
        return this;
    }

    private void load() {
        FeedController controller = FeedController.getInstance(currentAccount);
        controller.api.getJson("/v1/feed/topics/" + FeedApi.enc(name) + "?sort=" + sort, (json, error) -> {
            posts.clear();
            if (json != null) {
                visibleCount = json.optInt("visible_count", 0);
            }
            controller.resolvePostPage(json, (list, pageError) -> {
                posts.clear();
                if (list != null) {
                    posts.addAll(list);
                }
                if (visibleCount == 0) visibleCount = posts.size();
                if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
            });
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return false; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                TextView text = new TextView(parent.getContext());
                text.setPadding(dp(16), dp(12), dp(16), dp(12));
                text.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(text);
            }
            FeedPostCell cell = new FeedPostCell(parent.getContext(), currentAccount, false, false, simple());
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                TextView text = (TextView) holder.itemView;
                text.setText("#" + name + " · " + visibleCount + " · " + ("hot".equals(sort) ? FeedUi.s("Moments.Sort.Hot", R.string.FeedSortHot) : FeedUi.s("Moments.Sort.Latest", R.string.FeedSortNew)));
                text.setOnClickListener(v -> {
                    sort = "hot".equals(sort) ? "latest" : "hot";
                    load();
                });
                return;
            }
            ((FeedPostCell) holder.itemView).setPost(posts.get(position - 1));
        }

        @Override
        public int getItemViewType(int position) { return position == 0 ? 0 : 1; }

        @Override
        public int getItemCount() { return posts.size() + 1; }
    }

    private FeedPostCell.Delegate simple() {
        return new FeedPostCell.Delegate() {
            @Override public void onOpen(FeedModels.Post post) { FeedCommentSheet.open(FeedTopicActivity.this, post.id, false); }
            @Override public void onLike(FeedModels.Post post) { FeedController.getInstance(currentAccount).toggleLike(post); }
            @Override public void onComment(FeedModels.Post post) { FeedCommentSheet.open(FeedTopicActivity.this, post.id, true); }
            @Override public void onRepost(FeedModels.Post post) {}
            @Override public void onShare(FeedModels.Post post) {}
            @Override public void onSave(FeedModels.Post post) { FeedController.getInstance(currentAccount).toggleSave(post); }
            @Override public void onMore(FeedModels.Post post, View anchor) {}
            @Override public void onFollow(FeedModels.Post post) {}
            @Override public void onAuthor(FeedModels.Post post) { FeedUi.openProfile(FeedTopicActivity.this, post.author); }
            @Override public void onVote(FeedModels.Post post, int option) {}
            @Override public void onMedia(FeedModels.Post post, int index) { FeedMediaViewer.open(FeedTopicActivity.this, post, index); }
        };
    }
}
