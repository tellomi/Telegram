package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Device-local visibility for the main bottom bar.
 *
 * Indexes match the order {@code MainTabsActivity} adds tab views, so hiding
 * a tab does not rebuild the pager. Chats and Profile stay visible: Settings
 * is opened from the profile actions when its tab is hidden.
 *
 * <p>Studied, not copied: iMe's public Android tree (GPL-2.0,
 * https://github.com/imemessenger/iMe-Android, branch master, no tags, last
 * push 2021-02-13) only stores chat-list folder tabs in
 * {@code DialogsTabManager}. The 选单设定 screen in current iMe is not in that
 * tree. NagramX hides a caption by collapsing {@code GlassTabView} text
 * (GPL-2.0, commit f32ff4e47f3d11e8358c9a30e7bcfd37570633d0); the label switch
 * below follows that idea and is attributed on {@code GlassTabView}.
 * Cherrygram's {@code MainTabsManager} (GPL-2.0) was read and not reused:
 * its header asks other forks not to copy that organizer.
 */
public final class TellomiMainTabs {

    public static final int CONTACTS = 0;
    public static final int CALLS = 1;
    public static final int FEED = 2;
    public static final int CHATS = 3;
    public static final int SETTINGS = 4;
    public static final int PROFILE = 5;
    public static final int COUNT = 6;

    /** Left-to-right order of the existing bar. */
    public static final int[] BAR_ORDER = {
            CONTACTS, CALLS, FEED, CHATS, SETTINGS, PROFILE
    };

    /** Order of the switches on the menu-settings screen. */
    public static final int[] SETTINGS_ORDER = {
            FEED, CHATS, SETTINGS, PROFILE, CONTACTS, CALLS
    };

    public static final String HIDE_LABELS_KEY = "tellomi_main_tab_hide_labels";

    public interface Store {
        boolean contains(String key);

        boolean getBoolean(String key, boolean defaultValue);

        void putBoolean(String key, boolean value);
    }

    public interface Listener {
        void onTellomiMainTabsChanged();
    }

    private static final ArrayList<Listener> LISTENERS = new ArrayList<>();

    private TellomiMainTabs() {
    }

    public static String visibleKey(int tab) {
        return "tellomi_main_tab_visible_" + tab;
    }

    public static boolean isLocked(int tab) {
        return tab == CHATS || tab == PROFILE;
    }

    /** Fresh install: Moments, Chats, Profile. Settings, Contacts, and Calls are off. */
    public static boolean defaultVisible(int tab) {
        return tab == FEED || tab == CHATS || tab == PROFILE;
    }

    public static boolean isVisible(Store store, int tab) {
        if (tab < 0 || tab >= COUNT) {
            return false;
        }
        if (isLocked(tab)) {
            return true;
        }
        return store.getBoolean(visibleKey(tab), defaultVisible(tab));
    }

    /**
     * @return false when the tab cannot be changed (Chats, Profile, or an unknown index)
     */
    public static boolean setVisible(Store store, int tab, boolean visible) {
        if (tab < 0 || tab >= COUNT || isLocked(tab)) {
            return false;
        }
        store.putBoolean(visibleKey(tab), visible);
        return true;
    }

    public static boolean hideLabels(Store store) {
        return store.getBoolean(HIDE_LABELS_KEY, false);
    }

    public static void setHideLabels(Store store, boolean hide) {
        store.putBoolean(HIDE_LABELS_KEY, hide);
    }

    /** Visible tabs in bar order. */
    public static int[] visibleTabs(Store store) {
        int count = 0;
        int[] found = new int[COUNT];
        for (int tab : BAR_ORDER) {
            if (isVisible(store, tab)) {
                found[count++] = tab;
            }
        }
        return Arrays.copyOf(found, count);
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void notifyChanged() {
        Listener[] copy = LISTENERS.toArray(new Listener[0]);
        for (Listener listener : copy) {
            listener.onTellomiMainTabsChanged();
        }
    }
}
