package org.telegram.ui.Feed;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Opened from Privacy and Security. Defaults match FR-123 and FR-038.
 */
public class FeedSettingsActivity extends BaseFragment {

    private static final String[] KEYS = new String[]{
            "comment", "reply", "mention", "follow", "like", "repost", "new_post"
    };
    private static final boolean[] DEFAULTS = new boolean[]{true, true, true, true, false, false, false};
    private final boolean[] values = new boolean[KEYS.length];
    private boolean followingPublic = true;
    private boolean followersPublic = true;

    public FeedSettingsActivity() {
        System.arraycopy(DEFAULTS, 0, values, 0, DEFAULTS.length);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(FeedUi.s("Moments.Title", R.string.FeedTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        RecyclerListView listView = new RecyclerListView(context);
        listView.setSections();
        listView.setLayoutManager(new LinearLayoutManager(context));
        ListAdapter adapter = new ListAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == 1) {
                followingPublic = !followingPublic;
                saveVisibility();
                adapter.notifyItemChanged(position);
            } else if (position == 2) {
                followersPublic = !followersPublic;
                saveVisibility();
                adapter.notifyItemChanged(position);
            } else if (position == 3) {
                presentFragment(new FeedCollectionActivity(FeedCollectionActivity.MUTES));
            } else if (position >= 5 && position < 5 + KEYS.length) {
                int index = position - 5;
                values[index] = !values[index];
                savePush();
                adapter.notifyItemChanged(position);
            }
        });
        fragmentView = listView;
        load();
        return fragmentView;
    }

    private void load() {
        String raw = controller().storage.getPref("push", "");
        if (raw.length() > 0) {
            String[] parts = raw.split(",");
            for (int i = 0; i < parts.length && i < values.length; i++) {
                values[i] = "1".equals(parts[i]);
            }
        }
        followingPublic = !"self".equals(controller().storage.getPref("following_list", "public"));
        followersPublic = !"self".equals(controller().storage.getPref("followers_list", "public"));
        controller().api.getJson("/v1/feed/me/notification-settings", (json, error) -> {
            if (json != null) {
                for (int i = 0; i < KEYS.length; i++) {
                    if (json.has(KEYS[i])) {
                        values[i] = json.optBoolean(KEYS[i], values[i]);
                    }
                }
                persistLocal();
                if (listViewAdapter() != null) listViewAdapter().notifyDataSetChanged();
            }
        });
        controller().api.getJson("/v1/feed/me/list-visibility", (json, error) -> {
            if (json != null) {
                followingPublic = !"self".equals(json.optString("following_list", "public"));
                followersPublic = !"self".equals(json.optString("followers_list", "public"));
                controller().storage.putPref("following_list", followingPublic ? "public" : "self");
                controller().storage.putPref("followers_list", followersPublic ? "public" : "self");
                if (listViewAdapter() != null) listViewAdapter().notifyDataSetChanged();
            }
        });
    }

    private RecyclerView.Adapter listViewAdapter() {
        return fragmentView instanceof RecyclerView ? ((RecyclerView) fragmentView).getAdapter() : null;
    }

    private void persistLocal() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(values[i] ? '1' : '0');
        }
        controller().storage.putPref("push", builder.toString());
    }

    private void savePush() {
        persistLocal();
        JSONObject body = new JSONObject();
        try {
            for (int i = 0; i < KEYS.length; i++) {
                body.put(KEYS[i], values[i]);
            }
        } catch (Exception ignore) {
        }
        controller().api.sendJson("PUT", "/v1/feed/me/notification-settings", body, (json, error) -> {});
    }

    private void saveVisibility() {
        controller().storage.putPref("following_list", followingPublic ? "public" : "self");
        controller().storage.putPref("followers_list", followersPublic ? "public" : "self");
        JSONObject body = new JSONObject();
        try {
            body.put("following_list", followingPublic ? "public" : "self");
            body.put("followers_list", followersPublic ? "public" : "self");
        } catch (Exception ignore) {
        }
        controller().api.sendJson("PUT", "/v1/feed/me/list-visibility", body, (json, error) -> {});
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) view = new HeaderCell(parent.getContext());
            else if (viewType == 2) view = new TextSettingsCell(parent.getContext());
            else view = new TextCheckCell(parent.getContext());
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                ((HeaderCell) holder.itemView).setText(FeedUi.s("Moments.Lists", R.string.FeedListsHeader));
            } else if (position == 1) {
                ((TextCheckCell) holder.itemView).setTextAndCheck(FeedUi.s("Moments.Settings.FollowingList", R.string.FeedFollowingPublic), followingPublic, true);
            } else if (position == 2) {
                ((TextCheckCell) holder.itemView).setTextAndCheck(FeedUi.s("Moments.Settings.FollowersList", R.string.FeedFollowersPublic), followersPublic, true);
            } else if (position == 3) {
                ((TextSettingsCell) holder.itemView).setTextAndValue(FeedUi.s("Moments.Mutes", R.string.FeedMutes), "", true);
            } else if (position == 4) {
                ((HeaderCell) holder.itemView).setText(FeedUi.s("Moments.Push", R.string.FeedPushHeader));
            } else {
                int index = position - 5;
                int[] titles = new int[]{R.string.FeedPushComment, R.string.FeedPushReply, R.string.FeedPushMention, R.string.FeedPushFollow, R.string.FeedPushLike, R.string.FeedPushRepost, R.string.FeedPushNewPost};
                String[] titleKeys = new String[]{"Moments.Push.Comments", "Moments.Push.Replies", "Moments.Push.Mentions", "Moments.Push.Follows", "Moments.Push.Likes", "Moments.Push.Reposts", "Moments.Push.NewPosts"};
                ((TextCheckCell) holder.itemView).setTextAndCheck(FeedUi.s(titleKeys[index], titles[index]), values[index], index != titles.length - 1);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 || position == 4) return 0;
            if (position == 3) return 2;
            return 1;
        }

        @Override
        public int getItemCount() {
            return 5 + KEYS.length;
        }
    }
}
