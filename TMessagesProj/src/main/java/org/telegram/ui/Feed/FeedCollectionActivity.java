package org.telegram.ui.Feed;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedCollectionActivity extends BaseFragment {

    public static final int SAVED = 0;
    public static final int LIKED = 1;
    public static final int FOLLOWING = 2;
    public static final int FOLLOWERS = 3;
    public static final int MUTES = 4;
    public static final int PROFILE = 5;

    private final int mode;
    private final String profileId;
    private final ArrayList<FeedModels.Post> posts = new ArrayList<>();
    private final ArrayList<FeedModels.Author> people = new ArrayList<>();
    private RecyclerListView listView;
    private String filter = "posts";

    public FeedCollectionActivity(int mode) {
        this(mode, null);
    }

    public FeedCollectionActivity(int mode, String profileId) {
        super();
        this.mode = mode;
        this.profileId = profileId;
    }

    public static FeedCollectionActivity profile(long userId) {
        Bundle args = new Bundle();
        args.putLong("user_id", userId);
        return new FeedCollectionActivity(PROFILE, String.valueOf(userId));
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(title());
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        fragmentView = listView;
        load();
        return fragmentView;
    }

    private String title() {
        if (mode == SAVED) return FeedUi.s("Moments.Profile.Saved", R.string.FeedSavedTitle);
        if (mode == LIKED) return FeedUi.s("Moments.Profile.Liked", R.string.FeedLikedTitle);
        if (mode == FOLLOWING) return FeedUi.s("Moments.Segment.Following", R.string.FeedFollowing);
        if (mode == FOLLOWERS) return FeedUi.s("Moments.Followers", R.string.FeedFollowers);
        if (mode == MUTES) return FeedUi.s("Moments.Mutes", R.string.FeedMutes);
        return FeedUi.s("Moments.Profile.Posts", R.string.FeedProfileTab);
    }

    private void load() {
        String path;
        if (mode == SAVED) path = "/v1/feed/me/saved";
        else if (mode == LIKED) path = "/v1/feed/me/liked";
        else if (mode == MUTES) path = "/v1/feed/me/feed-mutes";
        else if (mode == FOLLOWING) path = "/v1/feed/profiles/" + profileId + "/following";
        else if (mode == FOLLOWERS) path = "/v1/feed/profiles/" + profileId + "/followers";
        else path = "/v1/feed/profiles/" + profileId + "/posts?filter=" + filter;
        FeedController controller = FeedController.getInstance(currentAccount);
        controller.api.getJson(path, (json, error) -> {
            posts.clear();
            people.clear();
            if (json != null && "list_private".equals(json.optString("error"))) {
                people.add(privateRow());
                if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
                return;
            }
            if (mode == FOLLOWING || mode == FOLLOWERS || mode == MUTES) {
                if (json != null) {
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            people.add(FeedModels.Author.parse(item.has("author") ? item.optJSONObject("author") : item));
                        }
                    }
                }
                if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
                return;
            }
            controller.resolvePostPage(json, (list, pageError) -> {
                posts.clear();
                if (list != null) {
                    posts.addAll(list);
                }
                if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
            });
        });
    }

    private FeedModels.Author privateRow() {
        FeedModels.Author author = new FeedModels.Author();
        author.name = FeedUi.s("Moments.ListPrivate", R.string.FeedListPrivate);
        return author;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                TextView text = new TextView(parent.getContext());
                text.setPadding(dp(16), dp(12), dp(16), dp(12));
                text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(text);
            }
            FeedPostCell cell = new FeedPostCell(parent.getContext(), currentAccount, false, false, new FeedPostCell.Delegate() {
                @Override public void onOpen(FeedModels.Post post) { if (!post.unavailable) FeedCommentSheet.open(FeedCollectionActivity.this, post.id, false); }
                @Override public void onLike(FeedModels.Post post) { FeedController.getInstance(currentAccount).toggleLike(post); }
                @Override public void onComment(FeedModels.Post post) { FeedCommentSheet.open(FeedCollectionActivity.this, post.id, true); }
                @Override public void onRepost(FeedModels.Post post) {}
                @Override public void onShare(FeedModels.Post post) {}
                @Override public void onSave(FeedModels.Post post) { FeedController.getInstance(currentAccount).toggleSave(post); }
                @Override public void onMore(FeedModels.Post post, View anchor) {}
                @Override public void onFollow(FeedModels.Post post) {}
                @Override public void onAuthor(FeedModels.Post post) { FeedUi.openProfile(FeedCollectionActivity.this, post.author); }
                @Override public void onVote(FeedModels.Post post, int option) {}
                @Override public void onMedia(FeedModels.Post post, int index) { FeedMediaViewer.open(FeedCollectionActivity.this, post, index); }
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 0) {
                if (mode == PROFILE && position == 0) {
                    ((TextView) holder.itemView).setText(FeedUi.s("Moments.Profile.Posts", R.string.FeedFilterPosts) + " / " + FeedUi.s("Moments.Filter.Reposts", R.string.FeedFilterReposts));
                    holder.itemView.setOnClickListener(v -> {
                        filter = "posts".equals(filter) ? "reposts" : "posts";
                        load();
                    });
                    return;
                }
                FeedModels.Author author = people.get(mode == PROFILE ? position : position);
                TextView text = (TextView) holder.itemView;
                text.setText(author.name.length() == 0 ? FeedUi.s("Moments.ListPrivate", R.string.FeedListPrivate) : FeedUi.displayName(author, currentAccount));
                text.setOnClickListener(v -> {
                    if (mode == MUTES && author.profileId.length() > 0) {
                        FeedController.getInstance(currentAccount).mute(author.profileId, false);
                        load();
                    } else {
                        FeedUi.openProfile(FeedCollectionActivity.this, author);
                    }
                });
                return;
            }
            int offset = mode == PROFILE ? 1 : 0;
            ((FeedPostCell) holder.itemView).setPost(posts.get(position - offset));
        }

        @Override
        public int getItemViewType(int position) {
            if (mode == FOLLOWING || mode == FOLLOWERS || mode == MUTES) return 0;
            if (mode == PROFILE && position == 0) return 0;
            return 1;
        }

        @Override
        public int getItemCount() {
            if (mode == FOLLOWING || mode == FOLLOWERS || mode == MUTES) return people.size();
            if (mode == PROFILE) return posts.size() + 1;
            return posts.size();
        }
    }
}
