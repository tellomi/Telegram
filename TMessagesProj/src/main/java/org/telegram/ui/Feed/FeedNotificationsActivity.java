package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

public class FeedNotificationsActivity extends BaseFragment {

    private final String[] tabs = new String[]{
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_INTERACTIONS,
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_FOLLOWS,
            org.telegram.messenger.feed.FeedContract.NOTIFICATION_SYSTEM
    };
    private int tab;
    private final ArrayList<FeedModels.NotificationItem> items = new ArrayList<>();
    private final FeedContract.QueryGeneration generation = new FeedContract.QueryGeneration();
    private RecyclerListView listView;
    private FeedContract.NotificationPlace place = FeedContract.notificationPlace(org.telegram.messenger.feed.FeedContract.NOTIFICATION_INTERACTIONS, 0, 0);
    private boolean pendingRestore;
    private String cursor = "";
    private boolean loading;

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(LocaleController.getString("Moments_Notifications", R.string.Moments_Notifications));
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
        int[] labels = new int[]{R.string.Moments_Notif_Interactions, R.string.Moments_Notif_Follows, R.string.Moments_Notif_System};
        String[] labelKeys = new String[]{"Moments_Notif_Interactions", "Moments_Notif_Follows", "Moments_Notif_System"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(context);
            chip.setText(LocaleController.getString(labelKeys[i], labels[i]));
            chip.setTextSize(13);
            chip.setPadding(dp(8), dp(8), dp(8), dp(8));
            chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            chip.setOnClickListener(v -> {
                if (tab == index) {
                    return;
                }
                tab = index;
                place = FeedContract.notificationPlace(tabs[tab], 0, 0);
                pendingRestore = true;
                load(false);
            });
            filters.addView(chip);
        }
        root.addView(filters, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || cursor.length() == 0) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= items.size() - 3) {
                    load(true);
                }
            }
        });
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = root;
        load(false);
        return fragmentView;
    }

    @Override
    public void onPause() {
        rememberPlace();
        pendingRestore = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        restorePlace();
    }

    private void rememberPlace() {
        int position = 0;
        int offset = 0;
        if (listView != null && listView.getLayoutManager() instanceof LinearLayoutManager) {
            LinearLayoutManager manager = (LinearLayoutManager) listView.getLayoutManager();
            position = manager.findFirstVisibleItemPosition();
            View child = manager.findViewByPosition(position);
            offset = child == null ? 0 : child.getTop();
        }
        place = FeedContract.notificationPlace(tabs[tab], position, offset);
    }

    private void restorePlace() {
        place = FeedContract.restoreNotifications(place);
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i].equals(place.tab)) {
                tab = i;
                break;
            }
        }
        if (!pendingRestore || listView == null || items.isEmpty() || !(listView.getLayoutManager() instanceof LinearLayoutManager)) {
            return;
        }
        pendingRestore = false;
        ((LinearLayoutManager) listView.getLayoutManager()).scrollToPositionWithOffset(place.position, place.offset);
    }

    private void load(boolean more) {
        if (more && (loading || cursor.length() == 0)) {
            return;
        }
        final int token = generation.next();
        final boolean first = !more;
        final String requestCursor = first ? "" : cursor;
        final String tabName = tabs[tab];
        loading = true;
        if (first) {
            cursor = "";
            items.clear();
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            FeedController.getInstance(currentAccount).markNotificationsRead(tabName);
        }
        FeedController.getInstance(currentAccount).loadNotifications(tabName, requestCursor, (list, next, error) -> {
            if (!generation.accept(token)) {
                return;
            }
            loading = false;
            if (error != null && !first) {
                return;
            }
            if (first) {
                items.clear();
            }
            if (list != null) {
                for (int i = 0; i < list.size(); i++) {
                    FeedModels.NotificationItem item = list.get(i);
                    if (!containsNotice(item)) {
                        items.add(item);
                    }
                }
            }
            String page = next == null ? "" : next;
            if (!first && page.equals(requestCursor)) {
                page = "";
            }
            cursor = error != null ? "" : page;
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            if (first) {
                AndroidUtilities.runOnUIThread(this::restorePlace);
            }
        });
    }

    private boolean containsNotice(FeedModels.NotificationItem item) {
        if (item == null || item.id.length() == 0) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (item.id.equals(items.get(i).id)) {
                return true;
            }
        }
        return false;
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
                names.append(" ").append(LocaleController.getString("Moments_AndOthers", R.string.Moments_AndOthers));
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
        if ("like".equals(kind)) return LocaleController.getString("Moments_Filter_Likes", R.string.Moments_Filter_Likes);
        if ("comment".equals(kind) || "reply".equals(kind)) return LocaleController.getString("Moments_Filter_Comments", R.string.Moments_Filter_Comments);
        if ("mention".equals(kind)) return LocaleController.getString("Moments_Filter_Mentions", R.string.Moments_Filter_Mentions);
        if ("follow".equals(kind) || "mutual".equals(kind)) return LocaleController.getString("Moments_Notif_Follows", R.string.Moments_Notif_Follows);
        if ("repost".equals(kind) || "quote".equals(kind)) return LocaleController.getString("Moments_Filter_Reposts", R.string.Moments_Filter_Reposts);
        if ("new_post".equals(kind)) return LocaleController.getString("Moments_Filter_Posts", R.string.Moments_Filter_Posts);
        return LocaleController.getString("Moments_Notif_Other", R.string.Moments_Notif_Other);
    }

    private void open(FeedModels.NotificationItem item) {
        long userId = item.actor == null ? 0 : item.actor.userId;
        if (userId == 0 && !item.actors.isEmpty()) {
            userId = item.actors.get(0).userId;
        }
        int target = FeedContract.notificationOpen(item.kind, item.postId, item.commentId, userId, item.unavailable);
        if (target == FeedContract.OPEN_PROFILE) {
            FeedModels.Author author = item.actor != null && item.actor.userId != 0 ? item.actor : (item.actors.isEmpty() ? item.actor : item.actors.get(0));
            FeedUi.openProfile(this, author);
            return;
        }
        if (target == FeedContract.OPEN_POST && FeedContract.opensStandaloneDetail(FeedContract.ENTRY_NOTIFICATION)) {
            rememberPlace();
            pendingRestore = true;
            FeedUi.openEntry(this, item.postId, FeedContract.notificationCommentId(target, item.commentId), FeedContract.ENTRY_NOTIFICATION, false);
            return;
        }
        if (getParentActivity() != null) {
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                    .setMessage(LocaleController.getString("Moments_PostGone", R.string.Moments_PostGone))
                    .setPositiveButton(LocaleController.getString("Moments_OK", R.string.Moments_OK), null)
                    .show();
        }
    }
}
