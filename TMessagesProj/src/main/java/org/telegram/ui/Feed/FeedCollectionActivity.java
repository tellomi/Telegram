package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
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
import org.telegram.messenger.feed.FeedContract;
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

    private static final int PEOPLE_OK = 0;
    private static final int PEOPLE_PRIVATE = 1;
    private static final int PEOPLE_ERROR = 2;

    private final int mode;
    private final String profileId;
    private final ArrayList<FeedModels.Post> posts = new ArrayList<>();
    private final ArrayList<FeedModels.Author> people = new ArrayList<>();
    private final FeedContract.QueryGeneration peopleGeneration = new FeedContract.QueryGeneration();
    private RecyclerListView listView;
    private String filter = "posts";
    private String peopleCursor = "";
    private boolean peopleLoading;
    private int peopleState = PEOPLE_OK;

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
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || (mode != FOLLOWING && mode != FOLLOWERS) || peopleCursor.length() == 0 || peopleLoading || peopleState != PEOPLE_OK) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= listView.getAdapter().getItemCount() - 3) {
                    loadPeople(true);
                }
            }
        });
        fragmentView = listView;
        load();
        return fragmentView;
    }

    private String title() {
        if (mode == SAVED) return LocaleController.getString("Moments_Profile_Saved", R.string.Moments_Profile_Saved);
        if (mode == LIKED) return LocaleController.getString("Moments_Profile_Liked", R.string.Moments_Profile_Liked);
        if (mode == FOLLOWING) return LocaleController.getString("Moments_FollowingEntry", R.string.Moments_FollowingEntry);
        if (mode == FOLLOWERS) return LocaleController.getString("Moments_FollowersEntry", R.string.Moments_FollowersEntry);
        if (mode == MUTES) return LocaleController.getString("Moments_Mutes", R.string.Moments_Mutes);
        return LocaleController.getString("Moments_Profile_Posts", R.string.Moments_Profile_Posts);
    }

    private void load() {
        if (mode == FOLLOWING || mode == FOLLOWERS) {
            loadPeople(false);
            return;
        }
        String path;
        if (mode == SAVED) path = "/v1/feed/me/saved";
        else if (mode == LIKED) path = "/v1/feed/me/liked";
        else if (mode == MUTES) path = "/v1/feed/me/feed-mutes";
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
            if (mode == MUTES) {
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

    private void loadPeople(boolean more) {
        if (more && (peopleLoading || peopleCursor.length() == 0 || peopleState != PEOPLE_OK)) {
            return;
        }
        final int token = peopleGeneration.next();
        final boolean first = !more;
        final String requestCursor = first ? "" : peopleCursor;
        String path = FeedContract.relationPath(profileId, mode == FOLLOWERS, requestCursor);
        if (path == null) {
            return;
        }
        peopleLoading = true;
        FeedController.getInstance(currentAccount).api.getJson(path, (json, error) -> {
            if (!peopleGeneration.accept(token)) {
                return;
            }
            peopleLoading = false;
            FeedContract.RelationPage page = FeedContract.parseRelationPage(json == null ? null : json.toString(), error, mode == FOLLOWERS);
            if (!FeedContract.applyRelationPage(true, first, page.failed, page.hidden)) {
                return;
            }
            if (page.hidden) {
                peopleState = PEOPLE_PRIVATE;
                people.clear();
                peopleCursor = "";
                notifyPeople();
                return;
            }
            if (page.failed) {
                peopleState = PEOPLE_ERROR;
                people.clear();
                peopleCursor = "";
                notifyPeople();
                return;
            }
            peopleState = PEOPLE_OK;
            if (first) {
                people.clear();
            }
            if (json != null) {
                org.json.JSONArray items = json.optJSONArray("items");
                if (items == null) {
                    items = json.optJSONArray("users");
                }
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject item = items.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        FeedModels.Author author = FeedModels.Author.parse(item.has("author") ? item.optJSONObject("author") : item);
                        if (!containsPerson(author)) {
                            people.add(author);
                        }
                    }
                }
            }
            String next = page.cursor;
            if (!first && next.equals(requestCursor)) {
                next = "";
            }
            peopleCursor = next;
            notifyPeople();
        });
    }

    private boolean containsPerson(FeedModels.Author author) {
        if (author == null) {
            return true;
        }
        for (int i = 0; i < people.size(); i++) {
            FeedModels.Author have = people.get(i);
            if (author.profileId.length() > 0 && author.profileId.equals(have.profileId)) {
                return true;
            }
            if (author.userId != 0 && author.userId == have.userId) {
                return true;
            }
        }
        return false;
    }

    private void notifyPeople() {
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private FeedModels.Author privateRow() {
        FeedModels.Author author = new FeedModels.Author();
        author.name = LocaleController.getString("Moments_ListPrivate", R.string.Moments_ListPrivate);
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
                @Override public void onOpen(FeedModels.Post post) { FeedUi.openEntry(FeedCollectionActivity.this, post, FeedContract.ENTRY_PROFILE, false); }
                @Override public void onLike(FeedModels.Post post) { FeedController.getInstance(currentAccount).toggleLike(post); }
                @Override public void onComment(FeedModels.Post post) { FeedUi.openEntry(FeedCollectionActivity.this, post, FeedContract.ENTRY_COMMENT, true); }
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
                if (mode == FOLLOWING || mode == FOLLOWERS) {
                    TextView text = (TextView) holder.itemView;
                    if (peopleState == PEOPLE_PRIVATE) {
                        text.setText(LocaleController.getString("Moments_ListPrivate", R.string.Moments_ListPrivate));
                        text.setOnClickListener(null);
                        return;
                    }
                    if (peopleState == PEOPLE_ERROR) {
                        text.setText(LocaleController.getString("Moments_ListLoadFailed", R.string.Moments_ListLoadFailed));
                        text.setOnClickListener(v -> loadPeople(false));
                        return;
                    }
                }
                if (mode == PROFILE && position == 0) {
                    ((TextView) holder.itemView).setText(LocaleController.getString("Moments_Filter_Posts", R.string.Moments_Filter_Posts) + " / " + LocaleController.getString("Moments_Filter_Reposts", R.string.Moments_Filter_Reposts));
                    holder.itemView.setOnClickListener(v -> {
                        filter = "posts".equals(filter) ? "reposts" : "posts";
                        load();
                    });
                    return;
                }
                FeedModels.Author author = people.get(mode == PROFILE ? position : position);
                TextView text = (TextView) holder.itemView;
                text.setText(author.name.length() == 0 ? LocaleController.getString("Moments_ListPrivate", R.string.Moments_ListPrivate) : FeedUi.displayName(author, currentAccount));
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
            if (mode == FOLLOWING || mode == FOLLOWERS) {
                if (peopleState == PEOPLE_PRIVATE || peopleState == PEOPLE_ERROR) {
                    return 1;
                }
                return people.size();
            }
            if (mode == MUTES) return people.size();
            if (mode == PROFILE) return posts.size() + 1;
            return posts.size();
        }
    }
}
