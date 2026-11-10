package org.telegram.messenger.feed;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;

/**
 * Per-account feed cache. The file lives next to the account files directory.
 * Logout and "clear local database" delete it, the same moments MessagesStorage
 * drops cache4.db. Photo and video bytes stay in the image cache, which the
 * storage-usage screen already clears.
 */
public class FeedStorage {

    private final int account;
    private SQLiteDatabase db;

    public FeedStorage(int account) {
        this.account = account;
        open();
    }

    private void open() {
        try {
            File dir = ApplicationLoader.getFilesDirFixed("feed");
            if (dir == null) {
                return;
            }
            File file = new File(dir, "feed_" + account + ".db");
            db = SQLiteDatabase.openOrCreateDatabase(file, null);
            db.execSQL("CREATE TABLE IF NOT EXISTS pages (stream TEXT PRIMARY KEY, cursor TEXT, json TEXT, saved_at INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts (id TEXT PRIMARY KEY, updated INTEGER, json TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS uploads (nonce TEXT PRIMARY KEY, json TEXT, state TEXT, updated INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS prefs (key TEXT PRIMARY KEY, value TEXT)");
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void wipe() {
        try {
            if (db != null) {
                db.close();
                db = null;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        File dir = ApplicationLoader.getFilesDirFixed("feed");
        if (dir == null) {
            return;
        }
        String name = "feed_" + account + ".db";
        new File(dir, name).delete();
        new File(dir, name + "-journal").delete();
        new File(dir, name + "-wal").delete();
        new File(dir, name + "-shm").delete();
        open();
    }

    public void savePage(String stream, String cursor, ArrayList<FeedModels.Post> posts) {
        if (db == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray();
            int limit = Math.min(posts.size(), FeedConfig.PAGE_SIZE);
            for (int i = 0; i < limit; i++) {
                array.put(new JSONObject().put("id", posts.get(i).id).put("raw", posts.get(i).id));
            }
            // Store the already-parsed cards as a compact replay of the last response text.
            ContentValues values = new ContentValues();
            values.put("stream", stream);
            values.put("cursor", cursor == null ? "" : cursor);
            values.put("json", pageJson(posts));
            values.put("saved_at", System.currentTimeMillis());
            db.insertWithOnConflict("pages", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private String pageJson(ArrayList<FeedModels.Post> posts) throws Exception {
        JSONArray items = new JSONArray();
        int limit = Math.min(posts.size(), FeedConfig.PAGE_SIZE);
        for (int i = 0; i < limit; i++) {
            items.put(serializePost(posts.get(i)));
        }
        JSONObject o = new JSONObject();
        o.put("items", items);
        return o.toString();
    }

    public static JSONObject serializePost(FeedModels.Post p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("version", p.version);
        o.put("state", p.unavailable ? "unavailable" : p.state);
        o.put("unavailable", p.unavailable);
        JSONObject author = new JSONObject();
        author.put("user_id", p.author.userId);
        author.put("id", p.author.profileId);
        author.put("name", p.author.name);
        author.put("username", p.author.username);
        author.put("avatar", p.author.avatarUrl);
        o.put("author", author);
        o.put("type", p.kind);
        o.put("title", p.title);
        o.put("text", p.text);
        JSONArray media = new JSONArray();
        for (int i = 0; i < p.media.size(); i++) {
            FeedModels.Media m = p.media.get(i);
            JSONObject item = new JSONObject();
            item.put("type", m.type);
            item.put("key", m.key);
            item.put("url", m.url);
            item.put("name", m.name);
            item.put("mime", m.mime);
            item.put("w", m.w);
            item.put("h", m.h);
            item.put("duration_ms", m.durationMs);
            media.put(item);
        }
        o.put("media", media);
        o.put("audience", new JSONObject().put("kind", p.audienceKind));
        o.put("comment_policy", p.commentPolicy);
        JSONObject location = new JSONObject();
        location.put("city", p.cityCode);
        location.put("city_name", p.cityName);
        location.put("place_name", p.placeName);
        location.put("distance_band", p.distanceBand);
        location.put("allow_nearby", p.allowNearby);
        o.put("location", location);
        o.put("pinned", p.pinned);
        o.put("edited_at", p.editedAt);
        o.put("created_at", p.createdAt);
        JSONObject counts = new JSONObject();
        counts.put("likes", p.likes);
        counts.put("comments", p.comments);
        counts.put("reposts", p.reposts);
        o.put("counts", counts);
        JSONObject viewer = new JSONObject();
        viewer.put("liked", p.liked);
        viewer.put("saved", p.saved);
        viewer.put("following", p.following);
        viewer.put("mutual", p.mutual);
        o.put("viewer", viewer);
        if (p.repostedByName != null && p.repostedByName.length() > 0) {
            o.put("reposted_by_name", p.repostedByName);
        }
        if (p.quote != null) {
            o.put("quote", serializePost(p.quote));
        } else if (p.quoteId != null && p.quoteId.length() > 0) {
            o.put("quote_of", p.quoteId);
        }
        if (p.linkPreview != null) {
            JSONObject link = new JSONObject();
            link.put("url", p.linkPreview.url);
            link.put("title", p.linkPreview.title);
            link.put("description", p.linkPreview.description);
            link.put("image", p.linkPreview.image);
            o.put("link_preview", link);
        }
        return o;
    }

    public FeedModels.Page loadPage(String stream) {
        FeedModels.Page page = new FeedModels.Page();
        if (db == null) {
            return page;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT cursor, json FROM pages WHERE stream=?", new String[]{stream});
            if (cursor.moveToFirst()) {
                page.nextCursor = cursor.getString(0);
                JSONObject o = new JSONObject(cursor.getString(1));
                page = FeedModels.parsePage(o);
                page.nextCursor = cursor.getString(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return page;
    }

    public void putPref(String key, String value) {
        if (db == null) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("key", key);
            values.put("value", value == null ? "" : value);
            db.insertWithOnConflict("prefs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String getPref(String key, String fallback) {
        if (db == null) {
            return fallback;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT value FROM prefs WHERE key=?", new String[]{key});
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fallback;
    }

    public void saveDraft(String id, long updated, String json) {
        if (db == null) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("id", id);
            values.put("updated", updated);
            values.put("json", json);
            db.insertWithOnConflict("drafts", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            trimDrafts();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String draftJson(String id) {
        if (id == null || id.length() == 0) {
            return null;
        }
        ArrayList<String[]> rows = listDrafts();
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row != null && row.length >= 3 && id.equals(row[0])) {
                return row[2];
            }
        }
        return null;
    }

    public void deleteDraft(String id) {
        if (db == null) {
            return;
        }
        try {
            db.delete("drafts", "id=?", new String[]{id});
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public ArrayList<String[]> listDrafts() {
        ArrayList<String[]> out = new ArrayList<>();
        if (db == null) {
            return out;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT id, updated, json FROM drafts ORDER BY updated DESC", null);
            while (cursor.moveToNext()) {
                out.add(new String[]{cursor.getString(0), String.valueOf(cursor.getLong(1)), cursor.getString(2)});
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return out;
    }

    private void trimDrafts() {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT id FROM drafts ORDER BY updated DESC", null);
            int index = 0;
            ArrayList<String> drop = new ArrayList<>();
            while (cursor.moveToNext()) {
                if (index >= FeedConfig.MAX_DRAFTS) {
                    drop.add(cursor.getString(0));
                }
                index++;
            }
            cursor.close();
            cursor = null;
            for (int i = 0; i < drop.size(); i++) {
                db.delete("drafts", "id=?", new String[]{drop.get(i)});
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void saveUpload(String nonce, String json, String state) {
        if (db == null) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put("nonce", nonce);
            values.put("json", json);
            values.put("state", state);
            values.put("updated", System.currentTimeMillis());
            db.insertWithOnConflict("uploads", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void deleteUpload(String nonce) {
        if (db == null) {
            return;
        }
        try {
            db.delete("uploads", "nonce=?", new String[]{nonce});
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public ArrayList<String[]> listUploads() {
        ArrayList<String[]> out = new ArrayList<>();
        if (db == null) {
            return out;
        }
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT nonce, json, state FROM uploads", null);
            while (cursor.moveToNext()) {
                out.add(new String[]{cursor.getString(0), cursor.getString(1), cursor.getString(2)});
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return out;
    }
}
