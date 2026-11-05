package org.telegram.ui.tellomi.feed;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local 「个性化推荐」 switch (D-58).
 *
 * Stored per account in {@link MessagesController#getMainSettings(int)}
 * ({@code mainconfig} / {@code mainconfigN}), the same file as
 * {@code tellomi_show_feed_tab} and {@code tellomi_discover_city}.
 * A missing key is on, so a fresh install keeps today's recommend URL:
 * {@link TellomiDiscover#feedUrl} omits {@code personalized}.
 *
 * The row is always on Privacy and Security. {@code personalization_toggle_visible}
 * stays on {@link TellomiDiscover.EngineConfig} and is not a gate: the parser
 * stores false both when the key is omitted and when it is false, and the
 * configs this client already parses would hide the only control that can
 * send {@code personalized=false}.
 */
public final class TellomiDiscoverPersonalization {

    /** Account main settings. Default {@link #DEFAULT_ON}. */
    public static final String PREF_KEY = "tellomi_discover_personalized";

    public static final boolean DEFAULT_ON = true;

    /** One boolean, so tests can round-trip without Android. */
    public interface BoolPrefs {
        boolean getBoolean(String key, boolean def);

        void putBoolean(String key, boolean value);
    }

    public interface Listener {
        void onChanged(int account, boolean on);
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();

    private TellomiDiscoverPersonalization() {
    }

    /** Missing stored value is on. */
    public static boolean enabled(Boolean stored) {
        return stored == null || stored;
    }

    /**
     * Recommend follows the switch. Following keeps the production call
     * ({@code personalized} is ignored for that path inside {@link TellomiDiscover#feedUrl}).
     */
    public static boolean forFeed(TellomiDiscover.FeedKind kind, boolean switchOn) {
        if (kind == TellomiDiscover.FeedKind.RECOMMEND) {
            return switchOn;
        }
        return true;
    }

    /** The open recommend tab refetches as soon as the switch changes. */
    public static boolean reloadRecommendImmediately(TellomiDiscover.FeedKind showing) {
        return showing == TellomiDiscover.FeedKind.RECOMMEND;
    }

    public static boolean read(BoolPrefs prefs) {
        if (prefs == null) {
            return DEFAULT_ON;
        }
        return prefs.getBoolean(PREF_KEY, DEFAULT_ON);
    }

    public static void write(BoolPrefs prefs, boolean on) {
        if (prefs == null) {
            return;
        }
        prefs.putBoolean(PREF_KEY, on);
    }

    public static boolean read(int account) {
        return read(prefs(MessagesController.getMainSettings(account)));
    }

    /** Writes the switch and tells a live discover screen to reload recommend. */
    public static void setEnabled(int account, boolean on) {
        write(prefs(MessagesController.getMainSettings(account)), on);
        notifyListeners(account, read(account));
    }

    public static boolean toggle(int account) {
        boolean next = !read(account);
        setEnabled(account, next);
        return next;
    }

    public static void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(listener)) {
                LISTENERS.add(listener);
            }
        }
    }

    public static void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }

    public static BoolPrefs prefs(final SharedPreferences preferences) {
        if (preferences == null) {
            return null;
        }
        return new BoolPrefs() {
            @Override
            public boolean getBoolean(String key, boolean def) {
                return preferences.getBoolean(key, def);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                preferences.edit().putBoolean(key, value).apply();
            }
        };
    }

    /** In-memory stand-in for the account {@code mainconfig} boolean. */
    public static final class MapPrefs implements BoolPrefs {
        private final Map<String, Boolean> values = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean def) {
            Boolean stored = values.get(key);
            return stored == null ? def : stored;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }
    }

    /** Test hook. Production calls this from {@link #setEnabled}. */
    static void notifyForTest(int account, boolean on) {
        notifyListeners(account, on);
    }

    private static void notifyListeners(int account, boolean on) {
        Listener[] copy;
        synchronized (LISTENERS) {
            copy = LISTENERS.toArray(new Listener[0]);
        }
        for (int i = 0; i < copy.length; i++) {
            copy[i].onChanged(account, on);
        }
    }
}
