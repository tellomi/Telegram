package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedApi;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedSearchPage extends LinearLayout {

    private final DialogsActivity parent;
    private final ArrayList<Row> rows = new ArrayList<>();
    private final RecyclerListView listView;
    private String query = "";
    private String sort = "relevance";

    public FeedSearchPage(Context context, DialogsActivity parent) {
        super(context);
        this.parent = parent;
        setOrientation(VERTICAL);
        TextView toggle = new TextView(context);
        toggle.setText(LocaleController.getString("Moments_Sort_Relevance", R.string.Moments_Sort_Relevance));
        toggle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        toggle.setPadding(dp(16), dp(8), dp(16), dp(8));
        toggle.setOnClickListener(v -> {
            sort = "relevance".equals(sort) ? "latest" : "relevance";
            toggle.setText("relevance".equals(sort) ? LocaleController.getString("Moments_Sort_Relevance", R.string.Moments_Sort_Relevance) : LocaleController.getString("Moments_Sort_Latest", R.string.Moments_Sort_Latest));
            search(query);
        });
        addView(toggle);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        addView(listView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public RecyclerListView getListView() {
        return listView;
    }

    public void search(String text) {
        query = text == null ? "" : text;
        rows.clear();
        if (query.length() == 0) {
            ArrayList<String> history = FeedController.getInstance(parent.getCurrentAccount()).searchHistory();
            for (int i = 0; i < history.size(); i++) {
                rows.add(Row.history(history.get(i)));
            }
            if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
            return;
        }
        FeedController controller = FeedController.getInstance(parent.getCurrentAccount());
        controller.pushSearchHistory(query);
        String path = "/v1/feed/search?q=" + FeedApi.enc(query) + "&scope=all&sort=" + sort;
        controller.api.getJson(path, (json, error) -> {
            rows.clear();
            if (json == null) {
                if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
                return;
            }
            addTopics(json.optJSONArray("topics"));
            addUsers(json.optJSONArray("users"));
            org.telegram.messenger.feed.FeedContract.FeedList refs = org.telegram.messenger.feed.FeedContract.parseFeedList(json.toString());
            if (refs.needsCards) {
                JSONObject page = json;
                if (json.has("items")) {
                    page = new JSONObject();
                    JSONArray postsOnly = new JSONArray();
                    JSONArray items = json.optJSONArray("items");
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.optJSONObject(i);
                            if (item == null) continue;
                            String type = item.optString("type", "post");
                            if ("topic".equals(type)) rows.add(Row.topic(item.optString("name"), item.optInt("visible_count", 0)));
                            else if ("user".equals(type)) rows.add(Row.user(FeedModels.Author.parse(item)));
                            else {
                                try {
                                    postsOnly.put(item.has("post") ? item.getJSONObject("post") : item);
                                } catch (Exception ignore) {
                                }
                            }
                        }
                    }
                    try {
                        page.put("items", postsOnly);
                    } catch (Exception ignore) {
                    }
                }
                final JSONObject postPage = page;
                controller.resolvePostPage(postPage, (posts, pageError) -> {
                    if (posts != null) {
                        for (int i = 0; i < posts.size(); i++) {
                            rows.add(Row.post(posts.get(i)));
                        }
                    }
                    if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
                });
                return;
            }
            addPosts(json.optJSONArray("posts"));
            if (rows.isEmpty()) {
                JSONArray items = json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String type = item.optString("type", "post");
                        if ("topic".equals(type)) rows.add(Row.topic(item.optString("name"), item.optInt("visible_count", 0)));
                        else if ("user".equals(type)) rows.add(Row.user(FeedModels.Author.parse(item)));
                        else rows.add(Row.post(FeedModels.Post.parse(item.has("post") ? item.optJSONObject("post") : item)));
                    }
                }
            }
            if (listView.getAdapter() != null) listView.getAdapter().notifyDataSetChanged();
        });
    }

    private void addPosts(JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) rows.add(Row.post(FeedModels.Post.parse(item)));
        }
    }

    private void addTopics(JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) rows.add(Row.topic(item.optString("name"), item.optInt("visible_count", 0)));
        }
    }

    private void addUsers(JSONArray array) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) rows.add(Row.user(FeedModels.Author.parse(item)));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext());
            text.setPadding(dp(16), dp(12), dp(16), dp(12));
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            text.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            TextView text = (TextView) holder.itemView;
            text.setText(row.label);
            text.setOnClickListener(v -> {
                if (row.post != null && !row.post.unavailable) FeedCommentSheet.open(parent, row.post.id, false);
                else if (row.topic != null) parent.presentFragment(new FeedTopicActivity(row.topic));
                else if (row.author != null) FeedUi.openProfile(parent, row.author);
                else if (row.history != null) search(row.history);
            });
        }

        @Override
        public int getItemCount() { return rows.size(); }
    }

    private static class Row {
        String label;
        FeedModels.Post post;
        String topic;
        FeedModels.Author author;
        String history;
        static Row post(FeedModels.Post post) {
            Row row = new Row();
            row.post = post;
            row.label = post.unavailable ? LocaleController.getString("Moments_Unavailable", R.string.Moments_Unavailable) : FeedUi.displayName(post.author, UserSafe()) + "\n" + post.text;
            return row;
        }
        static Row topic(String name, int count) {
            Row row = new Row();
            row.topic = name;
            row.label = "#" + name + " · " + count;
            return row;
        }
        static Row user(FeedModels.Author author) {
            Row row = new Row();
            row.author = author;
            row.label = FeedUi.displayName(author, UserSafe());
            return row;
        }
        static Row history(String query) {
            Row row = new Row();
            row.history = query;
            row.label = query;
            return row;
        }
        private static int UserSafe() {
            return org.telegram.messenger.UserConfig.selectedAccount;
        }
    }
}
