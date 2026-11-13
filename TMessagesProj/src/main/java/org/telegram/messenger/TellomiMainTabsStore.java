package org.telegram.messenger;

import android.content.SharedPreferences;

/** Device UI flags in {@link MessagesController#getGlobalMainSettings()} ({@code mainconfig}, the same file as {@link SharedConfig}). Not synced. */
public final class TellomiMainTabsStore {

    private TellomiMainTabsStore() {
    }

    public static TellomiMainTabs.Store device() {
        return new PrefStore(MessagesController.getGlobalMainSettings());
    }

    private static final class PrefStore implements TellomiMainTabs.Store {
        private final SharedPreferences preferences;

        private PrefStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public boolean contains(String key) {
            return preferences.contains(key);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return preferences.getBoolean(key, defaultValue);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            preferences.edit().putBoolean(key, value).apply();
        }
    }
}
