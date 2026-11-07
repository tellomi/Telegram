package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/** Local pin records. One file per account. Nothing here is uploaded. */
public final class TellomiChatLockStore {

    private TellomiChatLockStore() {
    }

    public static boolean locked(int account, long dialogId) {
        return load(account, dialogId) != null;
    }

    public static TellomiChatLock.Record load(int account, long dialogId) {
        return TellomiChatLock.parse(prefs(account).getString(key(dialogId), null));
    }

    public static void save(int account, long dialogId, TellomiChatLock.Record record) {
        prefs(account).edit().putString(key(dialogId), TellomiChatLock.serialize(record)).apply();
    }

    public static void clear(int account, long dialogId) {
        prefs(account).edit().remove(key(dialogId)).apply();
    }

    private static String key(long dialogId) {
        return "d" + dialogId;
    }

    private static SharedPreferences prefs(int account) {
        Context context = ApplicationLoader.applicationContext;
        return context.getSharedPreferences("tellomi_chat_lock_" + account, Context.MODE_PRIVATE);
    }
}
