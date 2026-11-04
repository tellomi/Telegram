package org.telegram.messenger;

import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.tellomi.feed.TellomiDiscover;

/**
 * {@code tellomi_discover_feed_enabled} from help.getAppConfig.
 * A missing key is off, including when an older config had turned it on.
 * {@code tellomi_discover_feed_base_url} is optional; an http URL does not enable the client.
 */
public final class TellomiDiscoverFlags {

    static final String KEY_ENABLED = TellomiDiscover.ENABLED_KEY;
    static final String KEY_BASE = TellomiDiscover.BASE_URL_KEY;

    private static final boolean[] enabled = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private static final String[] base = new String[UserConfig.MAX_ACCOUNT_COUNT];
    private static final boolean[] loaded = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private TellomiDiscoverFlags() {
    }

    public static boolean capture(int account, SharedPreferences.Editor editor, SharedPreferences preferences, TLRPC.TL_jsonObject object) {
        if (account < 0 || account >= enabled.length || editor == null || preferences == null) {
            return false;
        }
        TellomiDiscover.Field enabledField = TellomiDiscover.Field.missing();
        TellomiDiscover.Field baseField = TellomiDiscover.Field.missing();
        if (object != null && object.value != null) {
            for (int i = 0; i < object.value.size(); i++) {
                TLRPC.TL_jsonObjectValue entry = object.value.get(i);
                if (entry == null || entry.key == null) {
                    continue;
                }
                if (KEY_ENABLED.equals(entry.key)) {
                    enabledField = field(entry.value);
                } else if (KEY_BASE.equals(entry.key)) {
                    baseField = field(entry.value);
                }
            }
        }
        TellomiDiscover.Configuration configuration = TellomiDiscover.Configuration.resolve(enabledField, baseField);
        String nextBase = configuration.baseUrl == null ? "" : configuration.baseUrl;
        boolean wasEnabled = preferences.getBoolean(KEY_ENABLED, false);
        String wasBase = preferences.getString(KEY_BASE, "");
        if (wasBase == null) {
            wasBase = "";
        }
        editor.putBoolean(KEY_ENABLED, configuration.enabled);
        editor.putString(KEY_BASE, nextBase);
        enabled[account] = configuration.enabled;
        base[account] = nextBase;
        loaded[account] = true;
        return wasEnabled != configuration.enabled || !wasBase.equals(nextBase);
    }

    public static TellomiDiscover.Configuration configuration(int account) {
        if (account < 0 || account >= enabled.length) {
            return new TellomiDiscover.Configuration(false, null);
        }
        if (!loaded[account]) {
            SharedPreferences preferences = MessagesController.getMainSettings(account);
            enabled[account] = preferences != null && preferences.getBoolean(KEY_ENABLED, false);
            String stored = preferences == null ? "" : preferences.getString(KEY_BASE, "");
            base[account] = stored == null ? "" : stored;
            loaded[account] = true;
        }
        if (!enabled[account]) {
            return new TellomiDiscover.Configuration(false, null);
        }
        String stored = base[account];
        if (stored == null || stored.isEmpty()) {
            return TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.bool(true), TellomiDiscover.Field.missing());
        }
        return TellomiDiscover.Configuration.resolve(TellomiDiscover.Field.bool(true), TellomiDiscover.Field.string(stored));
    }

    private static TellomiDiscover.Field field(TLRPC.JSONValue value) {
        if (value instanceof TLRPC.TL_jsonBool) {
            return TellomiDiscover.Field.bool(((TLRPC.TL_jsonBool) value).value);
        }
        if (value instanceof TLRPC.TL_jsonNumber) {
            return TellomiDiscover.Field.number(((TLRPC.TL_jsonNumber) value).value);
        }
        if (value instanceof TLRPC.TL_jsonString) {
            return TellomiDiscover.Field.string(((TLRPC.TL_jsonString) value).value);
        }
        return TellomiDiscover.Field.missing();
    }
}
