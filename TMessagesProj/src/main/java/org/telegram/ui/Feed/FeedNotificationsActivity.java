package org.telegram.ui.Feed;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

public class FeedNotificationsActivity extends BaseFragment {

    private final String[] tabs = new String[]{
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_INTERACTIONS,
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_FOLLOWS,
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_SYSTEM
    };
    private int tab;
    private final ArrayList<FeedModels.NotificationItem> items = new ArrayList<>();
    private RecyclerListView listView;

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(FeedUi.s("Moments.Notifications", R.string.FeedNotifications));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout filters = new LinearLayout(context);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        int[] labels = new int[]{R.string.FeedNotifInteractions, R.string.FeedNotifFollows, R.string.FeedNotifSystem};
        String[] labelKeys = new String[]{"Moments.Notif.Interactions", "Moments.Notif.Follows", "Moments.Notif.System"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(context);
            chip.setText(FeedUi.s(labelKeys[i], labels[i]));
            chip.setTextSize(13);
            chip.setPadding(dp(8), dp(8), dp(8), dp(8));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            chip.setOnClickListener(v -> {
                tab = index;
                load();
            });
            filters.addView(chip);
        }
        root.addView(filters, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = root;
        FeedController.getInstance(currentAccount).markNotificationsRead();
        load();
        return fragmentView;
    }

    private void load() {
        FeedController.getInstance(currentAccount).loadNotifications(tabs[tab], (list, error) -> {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setPadding(dp(16), dp(12), dp(16), dp(12));
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            text.setTextSize(15);
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            FeedModels.NotificationItem item = items.get(position);
            StringBuilder names = new StringBuilder();
            int shown = Math.min(2, item.actors.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) names.append(", ");
                names.append(FeedUi.displayName(item.actors.get(i), currentAccount));
            }
            if (item.actors.size() > 2) {
                names.append(" ").append(FeedUi.s("Moments.AndOthers", R.string.FeedAndOthers));
            }
            String line = names + " · " + kindLabel(item.kind);
            if (item.text != null && item.text.length() > 0) {
                line += "\n" + item.text;
            }
            ((TextView) holder.itemView).setText(line);
            holder.itemView.setOnClickListener(v -> open(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private String kindLabel(String kind) {
        if ("like".equals(kind)) return FeedUi.s("Moments.Filter.Likes", R.string.FeedFilterLikes);
        if ("comment".equals(kind) || "reply".equals(kind)) return FeedUi.s("Moments.Filter.Comments", R.string.FeedFilterComments);
        if ("mention".equals(kind)) return FeedUi.s("Moments.Filter.Mentions", R.string.FeedFilterMentions);
        if ("follow".equals(kind) || "mutual".equals(kind)) return FeedUi.s("Moments.Notif.Follows", R.string.FeedFilterFollows);
        if ("repost".equals(kind) || "quote".equals(kind)) return FeedUi.s("Moments.Filter.Reposts", R.string.FeedFilterReposts);
        if ("new_post".equals(kind)) return FeedUi.s("Moments.Profile.Posts", R.string.FeedFilterPosts);
        return kind == null ? "" : kind;
    }

    private void open(FeedModels.NotificationItem item) {
        if (item.postId != null && item.postId.length() > 0) {
            presentFragment(FeedDetailActivity.forPost(item.postId));
        } else if (item.actor != null && item.actor.userId != 0) {
            FeedUi.openProfile(this, item.actor);
        } else if (getParentActivity() != null) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setMessage(FeedUi.s("Moments.Unavailable", R.string.FeedUnavailable))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
        }
    }
}
