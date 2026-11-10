package org.telegram.messenger.feed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * One controller per account. Switching accounts uses another instance (FR-162).
 */
public class FeedController {

    public interface Listener {
        void onChanged();
    }

    public static class UploadTask {
        public String nonce;
        public String text = "";
        public String audienceKind = "";
        public String commentPolicy = "everyone_who_can_view";
        public boolean allowNearby;
        public String cityCode = "";
        public String cityName = "";
        public String placeName = "";
        public ArrayList<String> paths = new ArrayList<>();
        public ArrayList<String> types = new ArrayList<>();
        public boolean original;
        public String extraJson = "";
        public String state = "uploading";
        public int progress;
        public String error = "";
        public int tries;
    }

    private static volatile FeedController[] instances = new FeedController[UserConfig.MAX_ACCOUNT_COUNT];

    public static FeedController getInstance(int account) {
        FeedController local = instances[account];
        if (local == null) {
            synchronized (FeedController.class) {
                local = instances[account];
                if (local == null) {
                    instances[account] = local = new FeedController(account);
                }
            }
        }
        return local;
    }

    public static void wipe(int account) {
        FeedController controller = instances[account];
        if (controller != null) {
            controller.storage.wipe();
            controller.pages.clear();
            controller.cursors.clear();
            controller.unread = 0;
            controller.notifyListeners();
        } else {
            new FeedStorage(account).wipe();
        }
    }

    public final int account;
    public final FeedApi api;
    public final FeedStorage storage;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private final HashMap<String, ArrayList<FeedModels.Post>> pages = new HashMap<>();
    private final HashMap<String, String> cursors = new HashMap<>();
    private final ArrayList<UploadTask> uploads = new ArrayList<>();
    private int unread;
    private boolean uploadsResumed;
    private boolean remoteConfigReady;
    private boolean remoteConfigLoading;
    private final ArrayList<String> tabs = new ArrayList<>();
    private final ArrayList<Runnable> configWaiters = new ArrayList<>();
    private final HashMap<String, FeedModels.Page> primed = new HashMap<>();
    private final HashMap<String, String> primedCursor = new HashMap<>();

    private FeedController(int account) {
        this.account = account;
        this.api = new FeedApi(account);
        this.storage = new FeedStorage(account);
        ArrayList<String> storedTabs = FeedContract.parseTabPref(storage.getPref("tabs", ""));
        if (storedTabs.isEmpty()) {
            storedTabs = FeedContract.defaultTabs();
        }
        tabs.addAll(storedTabs);
        for (int i = 0; i < tabs.size(); i++) {
            rememberPage(tabs.get(i));
        }
        try {
            unread = Integer.parseInt(storage.getPref("unread", "0"));
        } catch (Exception ignore) {
            unread = 0;
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onChanged();
        }
    }

    public ArrayList<FeedModels.Post> posts(String stream) {
        ArrayList<FeedModels.Post> list = pages.get(stream);
        if (list == null) {
            list = new ArrayList<>();
            pages.put(stream, list);
        }
        return list;
    }

    public String cursor(String stream) {
        String value = cursors.get(stream);
        return value == null ? "" : value;
    }

    public ArrayList<String> tabs() {
        return new ArrayList<>(tabs);
    }

    public boolean momentsTabVisible() {
        return true;
    }

    public int unreadCount() {
        return unread;
    }

    public void setUnread(int count) {
        unread = Math.max(0, count);
        storage.putPref("unread", String.valueOf(unread));
        notifyListeners();
    }

    public void load(final String stream, final boolean refresh, final Utilities.Callback2<Boolean, String> done) {
        if (!FeedConfig.enabled(account)) {
            if (done != null) {
                done.run(false, "unreachable");
            }
            return;
        }
        if (refresh) {
            primed.remove(stream);
            primedCursor.remove(stream);
        } else if (consumePrimed(stream, done)) {
            return;
        }
        if (remoteConfigReady && !refresh) {
            loadStream(stream, refresh, done);
            return;
        }
        ensureRemoteConfig(() -> loadStream(stream, refresh, done));
    }

    public void prefetchNext(String stream) {
        if (!FeedConfig.enabled(account) || primed.containsKey(stream)) {
            return;
        }
        String cursor = FeedContract.normalizeCursor(cursor(stream));
        if (cursor.length() == 0) {
            return;
        }
        String path = FeedContract.nextPagePath(stream, cursor, cityCode(), nearbySort());
        if (path == null) {
            return;
        }
        api.getJson(path, (json, error) -> {
            if (json == null || !cursor.equals(FeedContract.normalizeCursor(cursor(stream)))) {
                return;
            }
            FeedContract.FeedList refs = FeedContract.parseFeedList(json.toString());
            if (!refs.needsCards) {
                primed.put(stream, FeedModels.parsePage(json));
                primedCursor.put(stream, cursor);
                return;
            }
            hydrate(refs, (posts, hydrateError) -> {
                if (posts == null || !cursor.equals(FeedContract.normalizeCursor(cursor(stream)))) {
                    return;
                }
                FeedModels.Page page = new FeedModels.Page();
                page.items = posts;
                page.nextCursor = refs.cursor;
                primed.put(stream, page);
                primedCursor.put(stream, cursor);
            });
        });
    }

    private boolean consumePrimed(String stream, Utilities.Callback2<Boolean, String> done) {
        FeedModels.Page page = primed.get(stream);
        String cursor = primedCursor.get(stream);
        if (page == null || cursor == null || !cursor.equals(FeedContract.normalizeCursor(cursor(stream)))) {
            return false;
        }
        primed.remove(stream);
        primedCursor.remove(stream);
        applyStreamPage(stream, false, cursor, page, done);
        return true;
    }

    public void forgetRemoteConfig() {
        remoteConfigReady = false;
    }

    private void ensureRemoteConfig(final Runnable next) {
        if (remoteConfigReady) {
            next.run();
            return;
        }
        configWaiters.add(next);
        if (remoteConfigLoading) {
            return;
        }
        remoteConfigLoading = true;
        api.getJson(FeedContract.CAPABILITIES_PATH, (json, error) -> {
            if (json != null) {
                applyCapabilities(json.toString());
            }
        });
        api.getJson("/v1/feed/config", (json, error) -> {
            remoteConfigLoading = false;
            if (json != null) {
                FeedConfig.rememberMediaBase(account, json.optString("media_base_url", ""));
                remoteConfigReady = true;
                notifyListeners();
            }
            ArrayList<Runnable> waiters = new ArrayList<>(configWaiters);
            configWaiters.clear();
            for (int i = 0; i < waiters.size(); i++) {
                waiters.get(i).run();
            }
        });
    }

    private void applyCapabilities(String json) {
        ArrayList<String> next = FeedContract.tabsFromCapabilities(json);
        if (next.equals(tabs)) {
            return;
        }
        tabs.clear();
        tabs.addAll(next);
        storage.putPref("tabs", FeedContract.joinTabs(next));
        for (int i = 0; i < next.size(); i++) {
            rememberPage(next.get(i));
        }
        notifyListeners();
    }

    private void rememberPage(String stream) {
        if (pages.containsKey(stream)) {
            return;
        }
        FeedModels.Page cached = storage.loadPage(stream);
        pages.put(stream, cached.items);
        cursors.put(stream, FeedContract.normalizeCursor(cached.nextCursor));
    }

    private void loadStream(final String stream, final boolean refresh, final Utilities.Callback2<Boolean, String> done) {
        if (!FeedContract.isEngineStream(stream)) {
            if (done != null) {
                done.run(false, null);
            }
            return;
        }
        String cursor = refresh ? "" : FeedContract.normalizeCursor(cursor(stream));
        String path = refresh
                ? FeedContract.streamPath(stream, "", cityCode(), nearbySort())
                : FeedContract.nextPagePath(stream, cursor, cityCode(), nearbySort());
        if (path == null) {
            if (done != null) {
                done.run(true, null);
            }
            return;
        }
        api.getJson(path, (json, error) -> {
            if (json == null) {
                if (done != null) {
                    done.run(false, error);
                }
                return;
            }
            FeedContract.FeedList refs = FeedContract.parseFeedList(json.toString());
            if (!refs.needsCards) {
                applyStreamPage(stream, refresh, cursor, FeedModels.parsePage(json), done);
                return;
            }
            hydrate(refs, (posts, hydrateError) -> {
                if (posts == null) {
                    if (done != null) {
                        done.run(false, hydrateError);
                    }
                    return;
                }
                FeedModels.Page page = new FeedModels.Page();
                page.items = posts;
                page.nextCursor = refs.cursor;
                applyStreamPage(stream, refresh, cursor, page, done);
            });
        });
    }

    private void applyStreamPage(String stream, boolean refresh, String cursor, FeedModels.Page page, Utilities.Callback2<Boolean, String> done) {
        ArrayList<FeedModels.Post> list = posts(stream);
        ArrayList<FeedModels.Post> incoming = page == null || page.items == null ? new ArrayList<FeedModels.Post>() : page.items;
        FeedContract.PageApply apply = FeedContract.applyPage(idsOf(list), idsOf(incoming), refresh, page == null ? "" : page.nextCursor);
        if (apply.keptExisting) {
            notifyListeners();
            if (done != null) {
                done.run(true, FeedContract.KEPT_EXISTING);
            }
            return;
        }
        HashMap<String, FeedModels.Post> byId = new HashMap<>();
        if (!refresh) {
            for (int i = 0; i < list.size(); i++) {
                FeedModels.Post post = list.get(i);
                if (post != null && post.id != null && post.id.length() > 0) {
                    byId.put(post.id, post);
                }
            }
        }
        for (int i = 0; i < incoming.size(); i++) {
            FeedModels.Post post = incoming.get(i);
            if (post == null || post.id == null || post.id.length() == 0) {
                continue;
            }
            if (refresh || !byId.containsKey(post.id)) {
                byId.put(post.id, post);
            }
        }
        list.clear();
        for (int i = 0; i < apply.ids.size(); i++) {
            FeedModels.Post post = byId.get(apply.ids.get(i));
            if (post != null) {
                list.add(post);
            }
        }
        cursors.put(stream, apply.stopPaging ? "" : apply.nextCursor);
        if (refresh) {
            storage.savePage(stream, cursors.get(stream), list);
        }
        notifyListeners();
        if (done != null) {
            done.run(true, null);
        }
    }

    private static ArrayList<String> idsOf(ArrayList<FeedModels.Post> posts) {
        ArrayList<String> ids = new ArrayList<>();
        if (posts == null) {
            return ids;
        }
        for (int i = 0; i < posts.size(); i++) {
            FeedModels.Post post = posts.get(i);
            ids.add(post == null ? "" : post.id);
        }
        return ids;
    }

    /**
     * Feed lists return `{items:[{id,reason}],cursor}`. Full posts come from POST /v1/feed/cards.
     */
    public void resolvePostPage(JSONObject json, Utilities.Callback2<ArrayList<FeedModels.Post>, String> done) {
        if (json == null) {
            if (done != null) {
                done.run(new ArrayList<>(), "network");
            }
            return;
        }
        FeedContract.FeedList refs = FeedContract.parseFeedList(json.toString());
        if (!refs.needsCards) {
            if (done != null) {
                done.run(FeedModels.parsePage(json).items, null);
            }
            return;
        }
        hydrate(refs, done);
    }

    private void hydrate(FeedContract.FeedList refs, Utilities.Callback2<ArrayList<FeedModels.Post>, String> done) {
        JSONObject body;
        try {
            body = new JSONObject(FeedContract.cardsRequest(refs.idsToHydrate()));
        } catch (Exception e) {
            if (done != null) {
                done.run(null, "network");
            }
            return;
        }
        api.sendJson("POST", FeedContract.CARDS_PATH, body, (json, error) -> {
            if (json == null) {
                if (done != null) {
                    done.run(null, error);
                }
                return;
            }
            ArrayList<String> ordered = FeedContract.orderedCardJson(json.toString(), refs.refs);
            ArrayList<FeedModels.Post> posts = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                FeedContract.Ref ref = i < refs.refs.size() ? refs.refs.get(i) : null;
                FeedModels.Post post;
                try {
                    post = FeedModels.Post.parse(new JSONObject(ordered.get(i)));
                } catch (Exception e) {
                    post = FeedModels.Post.unavailable(ref == null ? "" : ref.id);
                }
                if ((post.feedReason == null || post.feedReason.length() == 0) && ref != null && ref.reason != null) {
                    post.feedReason = ref.reason;
                }
                posts.add(post);
            }
            if (done != null) {
                done.run(posts, null);
            }
        });
    }

    public FeedModels.Post find(String id) {
        for (int s = 0; s < FeedModels.STREAMS.length; s++) {
            ArrayList<FeedModels.Post> list = posts(FeedModels.STREAMS[s]);
            for (int i = 0; i < list.size(); i++) {
                if (id != null && id.equals(list.get(i).id)) {
                    return list.get(i);
                }
            }
        }
        return null;
    }

    public void removeFromStreams(String id) {
        for (int s = 0; s < FeedModels.STREAMS.length; s++) {
            ArrayList<FeedModels.Post> list = posts(FeedModels.STREAMS[s]);
            for (int i = list.size() - 1; i >= 0; i--) {
                if (id != null && id.equals(list.get(i).id)) {
                    list.remove(i);
                }
            }
        }
        notifyListeners();
    }

    public void toggleLike(final FeedModels.Post post) {
        if (post == null || post.unavailable) {
            return;
        }
        final boolean next = !post.liked;
        post.liked = next;
        post.likes += next ? 1 : -1;
        if (post.likes < 0) {
            post.likes = 0;
        }
        notifyListeners();
        String method = next ? "PUT" : "DELETE";
        api.sendJson(method, "/v1/feed/posts/" + FeedApi.enc(post.id) + "/like", null, (json, error) -> {
            if (error != null) {
                post.liked = !next;
                post.likes += next ? -1 : 1;
                if (post.likes < 0) {
                    post.likes = 0;
                }
                notifyListeners();
            }
        });
    }

    public void toggleSave(final FeedModels.Post post) {
        if (post == null || post.unavailable) {
            return;
        }
        final boolean next = !post.saved;
        post.saved = next;
        notifyListeners();
        api.sendJson(next ? "PUT" : "DELETE", "/v1/feed/posts/" + FeedApi.enc(post.id) + "/save", null, (json, error) -> {
            if (error != null) {
                post.saved = !next;
                notifyListeners();
            }
        });
    }

    public void hidePost(FeedModels.Post post) {
        if (post == null) {
            return;
        }
        removeFromStreams(post.id);
        api.sendJson("POST", "/v1/feed/posts/" + FeedApi.enc(post.id) + "/hide", new JSONObject(), (json, error) -> {
        });
    }

    public void notInterested(FeedModels.Post post) {
        if (post == null) {
            return;
        }
        removeFromStreams(post.id);
        JSONObject body = new JSONObject();
        try {
            body.put("content_id", post.id);
        } catch (Exception ignore) {
        }
        api.sendJson("POST", "/v1/feed/not-interested", body, (json, error) -> {
        });
    }

    public void mute(String profileId, boolean mute) {
        String path = "/v1/feed/me/feed-mutes/" + FeedApi.enc(profileId);
        api.sendJson(mute ? "PUT" : "DELETE", path, mute ? new JSONObject() : null, (json, error) -> notifyListeners());
    }

    public void follow(String profileId, boolean follow) {
        api.sendJson(follow ? "PUT" : "DELETE", "/v1/feed/follow/author/" + FeedApi.enc(profileId), follow ? new JSONObject() : null, (json, error) -> notifyListeners());
    }

    public void deletePost(String id) {
        removeFromStreams(id);
        api.sendJson("DELETE", "/v1/feed/posts/" + FeedApi.enc(id), null, (json, error) -> {
        });
    }

    public void loadComments(String postId, String sort, String cursor, Utilities.Callback2<ArrayList<FeedModels.Comment>, String> done) {
        String path = "hot".equals(sort)
                ? FeedContract.commentsPath(postId, cursor)
                : "/v1/feed/posts/" + FeedApi.enc(postId) + "/comments?sort=" + sort + (cursor == null || cursor.length() == 0 ? "" : "&cursor=" + FeedApi.enc(cursor));
        api.getJson(path, (json, error) -> {
            FeedModels.CommentPage page = FeedModels.CommentPage.parse(json);
            if (done != null) {
                done.run(page.comments, error == null ? page.cursor : null);
            }
        });
    }

    public void loadCommentThread(String postId, String cursor, Utilities.Callback<FeedModels.CommentPage> done) {
        api.getJson(FeedContract.commentsPath(postId, cursor), (json, error) -> {
            if (done != null) {
                done.run(error == null ? FeedModels.CommentPage.parse(json) : null);
            }
        });
    }

    public void loadReplies(String commentId, String cursor, Utilities.Callback<FeedModels.CommentPage> done) {
        api.getJson(FeedContract.repliesPath(commentId, cursor), (json, error) -> {
            if (done != null) {
                done.run(error == null ? FeedModels.CommentPage.parse(json) : null);
            }
        });
    }

    public void sendComment(String postId, String text, String parentId, JSONObject attachment, Utilities.Callback<String> done) {
        JSONObject body = new JSONObject();
        try {
            String type = attachment == null ? "" : attachment.optString("type", "");
            String key = attachment == null ? "" : attachment.optString("key", "");
            if (key.length() > 0 && (FeedContract.isVoice(type) || "image".equals(type) || "photo".equals(type) || "sticker".equals(type))) {
                body = new JSONObject(FeedContract.commentBody(text, parentId, type, key));
            } else {
                String value = text == null ? "" : text;
                if (value.length() > FeedContract.MAX_COMMENT) {
                    value = value.substring(0, FeedContract.MAX_COMMENT);
                }
                body.put("text", value);
                if (parentId != null && parentId.length() > 0) {
                    body.put("parent_id", parentId);
                }
                if (attachment != null) {
                    body.put("attachment", attachment);
                }
            }
        } catch (Exception ignore) {
        }
        api.sendJson("POST", "/v1/feed/posts/" + FeedApi.enc(postId) + "/comments", body, (json, error) -> {
            if (done != null) {
                done.run(error);
            }
        });
    }

    public void deleteComment(String postId, String commentId, Utilities.Callback<String> done) {
        api.sendJson("DELETE", "/v1/feed/posts/" + FeedApi.enc(postId) + "/comments/" + FeedApi.enc(commentId), null, (json, error) -> {
            if (done != null) {
                done.run(error);
            }
        });
    }

    public void vote(String postId, JSONArray options, boolean clear, Utilities.Callback<String> done) {
        if (clear) {
            api.sendJson("DELETE", "/v1/feed/posts/" + FeedApi.enc(postId) + "/vote", null, (json, error) -> {
                if (done != null) {
                    done.run(error);
                }
            });
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("options", options);
        } catch (Exception ignore) {
        }
        api.sendJson("PUT", "/v1/feed/posts/" + FeedApi.enc(postId) + "/vote", body, (json, error) -> {
            if (done != null) {
                done.run(error);
            }
        });
    }

    public void closePoll(String postId, Utilities.Callback<String> done) {
        api.sendJson("POST", "/v1/feed/posts/" + FeedApi.enc(postId) + "/poll/close", new JSONObject(), (json, error) -> {
            if (done != null) {
                done.run(error);
            }
        });
    }

    public void report(String targetType, String targetId, String reason, String detail, Utilities.Callback<String> done) {
        JSONObject body = new JSONObject();
        try {
            body.put("target_type", targetType);
            body.put("target_id", targetId);
            body.put("reason", reason);
            if (detail != null && detail.length() > 0) {
                body.put("detail", detail);
            }
        } catch (Exception ignore) {
        }
        api.sendJson("POST", "/v1/feed/reports", body, (json, error) -> {
            if ("post".equals(targetType) || "comment".equals(targetType)) {
                removeFromStreams(targetId);
            }
            if (done != null) {
                done.run(error);
            }
        });
    }

    public void markNotificationsRead() {
        setUnread(0);
        api.sendJson("POST", "/v1/feed/me/notifications/read", new JSONObject(), (json, error) -> {
        });
    }

    public void loadNotifications(String tab, Utilities.Callback2<ArrayList<FeedModels.NotificationItem>, String> done) {
        api.getJson(FeedContract.notificationsPath(tab), (json, error) -> {
            ArrayList<FeedModels.NotificationItem> list = new ArrayList<>();
            if (json != null) {
                JSONArray items = json.optJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item != null) {
                            list.add(FeedModels.NotificationItem.parse(item));
                        }
                    }
                }
                if (json.has("unread")) {
                    setUnread(json.optInt("unread", 0));
                }
            }
            if (done != null) {
                done.run(list, error);
            }
        });
    }

    public void refreshUnread() {
        api.getJson(FeedContract.notificationsPath(FeedContract.NOTIFICATION_INTERACTIONS), (json, error) -> {
            if (json != null && json.has("unread")) {
                setUnread(json.optInt("unread", unread));
            }
        });
    }

    public String cityCode() {
        return storage.getPref("city", "");
    }

    public String cityName() {
        return storage.getPref("city_name", "");
    }

    public void setCity(String code, String name) {
        storage.putPref("city", code == null ? "" : code);
        storage.putPref("city_name", name == null ? "" : name);
        notifyListeners();
    }

    public String nearbySort() {
        String sort = storage.getPref("nearby_sort", "latest");
        return sort == null || sort.length() == 0 ? "latest" : sort;
    }

    public void setNearbySort(String sort) {
        storage.putPref("nearby_sort", sort);
    }

    public boolean askedLocation() {
        return "1".equals(storage.getPref("asked_location", "0"));
    }

    public void setAskedLocation() {
        storage.putPref("asked_location", "1");
    }

    public ArrayList<UploadTask> uploads() {
        return uploads;
    }

    public void resumeUploads() {
        if (uploadsResumed) {
            return;
        }
        uploadsResumed = true;
        ArrayList<String[]> rows = storage.listUploads();
        for (int i = 0; i < rows.size(); i++) {
            try {
                JSONObject o = new JSONObject(rows.get(i)[1]);
                UploadTask task = taskFromJson(o);
                task.nonce = rows.get(i)[0];
                task.state = "failed";
                uploads.add(task);
            } catch (Exception ignore) {
            }
        }
        if (!uploads.isEmpty()) {
            notifyListeners();
        }
    }

    public void enqueue(UploadTask task) {
        if (task.nonce == null || task.nonce.length() == 0) {
            task.nonce = Utilities.random.nextLong() + "-" + System.currentTimeMillis();
        }
        task.state = "uploading";
        uploads.add(0, task);
        persist(task);
        notifyListeners();
        runUpload(task);
    }

    public void retry(UploadTask task) {
        task.state = "uploading";
        task.error = "";
        task.tries = 0;
        persist(task);
        notifyListeners();
        runUpload(task);
    }

    public void cancel(UploadTask task) {
        task.state = "cancelled";
        uploads.remove(task);
        storage.deleteUpload(task.nonce);
        notifyListeners();
    }

    private void persist(UploadTask task) {
        try {
            storage.saveUpload(task.nonce, taskToJson(task).toString(), task.state);
        } catch (Exception ignore) {
        }
    }

    private void runUpload(final UploadTask task) {
        uploadAt(task, 0, new JSONArray());
    }

    private void uploadAt(final UploadTask task, final int index, final JSONArray keys) {
        if ("cancelled".equals(task.state)) {
            return;
        }
        if (index >= task.paths.size()) {
            publishUploaded(task, keys);
            return;
        }
        File file = new File(task.paths.get(index));
        api.uploadFile(file, file.getName(), mime(task.types.get(index)), (key, error) -> {
            if ("cancelled".equals(task.state)) {
                return;
            }
            if (key == null) {
                task.tries++;
                if (task.tries < FeedConfig.UPLOAD_RETRIES) {
                    AndroidUtilities.runOnUIThread(() -> uploadAt(task, index, keys), (long) Math.pow(2, task.tries) * 1000L);
                } else {
                    task.state = "failed";
                    task.error = error == null ? "upload_failed" : error;
                    persist(task);
                    notifyListeners();
                }
                return;
            }
            try {
                JSONObject media = new JSONObject();
                media.put("key", key);
                media.put("type", task.types.get(index));
                media.put("is_original", task.original);
                keys.put(media);
            } catch (Exception ignore) {
            }
            task.progress = (index + 1) * 100 / Math.max(1, task.paths.size());
            notifyListeners();
            uploadAt(task, index + 1, keys);
        });
    }

    private void publishUploaded(final UploadTask task, JSONArray keys) {
        try {
            JSONObject body = new JSONObject();
            body.put("text", FeedContract.clampText(task.text));
            body.put("client_nonce", task.nonce);
            JSONObject audience = new JSONObject();
            audience.put("kind", task.audienceKind);
            body.put("audience", audience);
            body.put("comment_policy", task.commentPolicy);
            body.put("media", limitImages(keys));
            JSONObject location = new JSONObject();
            if (task.cityCode != null && task.cityCode.length() > 0) {
                location.put("city", task.cityCode);
                location.put("city_name", task.cityName);
            }
            if (task.placeName != null && task.placeName.length() > 0) {
                location.put("place", new JSONObject().put("name", task.placeName));
            }
            location.put("allow_nearby", task.allowNearby && "public".equals(task.audienceKind));
            body.put("location", location);
            if (task.extraJson != null && task.extraJson.length() > 0) {
                JSONObject extra = new JSONObject(task.extraJson);
                java.util.Iterator<String> keysIter = extra.keys();
                while (keysIter.hasNext()) {
                    String key = keysIter.next();
                    body.put(key, extra.get(key));
                }
            }
            api.sendJson("POST", "/v1/feed/posts", body, (json, error) -> {
                if (error != null) {
                    task.state = "failed";
                    task.error = error;
                    persist(task);
                } else {
                    task.state = "done";
                    uploads.remove(task);
                    storage.deleteUpload(task.nonce);
                    load(FeedModels.STREAM_FOLLOWING, true, null);
                }
                notifyListeners();
            });
        } catch (Exception e) {
            task.state = "failed";
            task.error = "upload_failed";
            persist(task);
            notifyListeners();
        }
    }

    private static JSONArray limitImages(JSONArray keys) {
        JSONArray limited = new JSONArray();
        int images = 0;
        if (keys == null) {
            return limited;
        }
        for (int i = 0; i < keys.length(); i++) {
            JSONObject media = keys.optJSONObject(i);
            if (media == null) {
                continue;
            }
            if ("image".equals(media.optString("type", ""))) {
                if (images >= FeedContract.MAX_IMAGES) {
                    continue;
                }
                images++;
            }
            limited.put(media);
        }
        return limited;
    }

    private static String mime(String type) {
        if ("video".equals(type)) {
            return "video/mp4";
        }
        if ("audio".equals(type)) {
            return "audio/ogg";
        }
        return "image/jpeg";
    }

    private JSONObject taskToJson(UploadTask task) throws Exception {
        JSONObject o = new JSONObject();
        o.put("nonce", task.nonce);
        o.put("text", task.text);
        o.put("audience", task.audienceKind);
        o.put("comment_policy", task.commentPolicy);
        o.put("allow_nearby", task.allowNearby);
        o.put("city", task.cityCode);
        o.put("city_name", task.cityName);
        o.put("place", task.placeName);
        o.put("original", task.original);
        o.put("extra", task.extraJson == null ? "" : task.extraJson);
        o.put("paths", new JSONArray(task.paths));
        o.put("types", new JSONArray(task.types));
        return o;
    }

    private UploadTask taskFromJson(JSONObject o) {
        UploadTask task = new UploadTask();
        task.nonce = o.optString("nonce", "");
        task.text = o.optString("text", "");
        task.audienceKind = o.optString("audience", "");
        task.commentPolicy = o.optString("comment_policy", "everyone_who_can_view");
        task.allowNearby = o.optBoolean("allow_nearby", false);
        task.cityCode = o.optString("city", "");
        task.cityName = o.optString("city_name", "");
        task.placeName = o.optString("place", "");
        task.original = o.optBoolean("original", false);
        task.extraJson = o.optString("extra", "");
        JSONArray paths = o.optJSONArray("paths");
        JSONArray types = o.optJSONArray("types");
        if (paths != null) {
            for (int i = 0; i < paths.length(); i++) {
                task.paths.add(paths.optString(i));
                task.types.add(types == null ? "image" : types.optString(i, "image"));
            }
        }
        return task;
    }

    public void pushSearchHistory(String query) {
        if (query == null || query.trim().length() == 0) {
            return;
        }
        String raw = storage.getPref("search_history", "");
        ArrayList<String> items = new ArrayList<>();
        items.add(query.trim());
        if (raw.length() > 0) {
            String[] parts = raw.split("\n");
            for (int i = 0; i < parts.length && items.size() < 20; i++) {
                if (!query.trim().equals(parts[i]) && parts[i].length() > 0) {
                    items.add(parts[i]);
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(items.get(i));
        }
        storage.putPref("search_history", builder.toString());
    }

    public ArrayList<String> searchHistory() {
        ArrayList<String> items = new ArrayList<>();
        String raw = storage.getPref("search_history", "");
        if (raw.length() == 0) {
            return items;
        }
        String[] parts = raw.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                items.add(parts[i]);
            }
        }
        return items;
    }

    public boolean debugFixtures() {
        return "1".equals(storage.getPref("debug_fixtures", "0"));
    }

    public void setDebugFixtures(boolean on) {
        storage.putPref("debug_fixtures", on ? "1" : "0");
        if (on) {
            for (int s = 0; s < FeedModels.STREAMS.length; s++) {
                String stream = FeedModels.STREAMS[s];
                if (posts(stream).isEmpty()) {
                    posts(stream).addAll(fixtures(stream));
                }
            }
            notifyListeners();
        }
    }

    public static ArrayList<FeedModels.Post> fixtures(String stream) {
        ArrayList<FeedModels.Post> list = new ArrayList<>();
        String[] texts = new String[]{
                "第一条动态。正文超过五行时会折叠，点全文进入详情。\n第二行\n第三行\n第四行\n第五行\n第六行才出现全文。",
                "互相关注就是好友。这条是公开帖，推荐流里可以关注。",
                "同城只展示作者打开了「在同城展示」的公开帖。",
                "点赞、评论、转发、分享、收藏都在底部这一排。"
        };
        String[] kinds = new String[]{"public", "mutual_followers", "public", "contacts"};
        for (int i = 0; i < texts.length; i++) {
            if (FeedModels.STREAM_RECOMMEND.equals(stream) && !"public".equals(kinds[i])) {
                continue;
            }
            if (FeedModels.STREAM_NEARBY.equals(stream) && !"public".equals(kinds[i])) {
                continue;
            }
            FeedModels.Post post = new FeedModels.Post();
            post.id = stream + "-fixture-" + i;
            post.text = texts[i];
            post.author.name = i % 2 == 0 ? "Ada" : "Bo";
            post.author.username = i % 2 == 0 ? "ada" : "bo";
            post.author.profileId = "p" + i;
            post.audienceKind = kinds[i];
            post.createdAt = System.currentTimeMillis() - (i + 1) * 3600_000L;
            post.likes = 12 - i;
            post.comments = i;
            post.cityName = "成都";
            post.allowNearby = FeedModels.STREAM_NEARBY.equals(stream);
            if (i == 2) {
                post.placeName = "某地点";
                post.distanceBand = "1–5 km";
            }
            list.add(post);
        }
        return list;
    }

}
