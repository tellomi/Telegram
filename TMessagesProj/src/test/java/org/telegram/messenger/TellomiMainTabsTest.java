package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiMainTabsTest {

    @Test
    public void freshInstallShowsDiscoverChatsSettingsAndProfile() {
        MemoryStore store = new MemoryStore();
        int[] visible = TellomiMainTabs.visibleTabs(store);
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, visible);
        assertTrue(TellomiMainTabs.defaultVisible(TellomiMainTabs.FEED));
        assertTrue(TellomiMainTabs.defaultVisible(TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.defaultVisible(TellomiMainTabs.SETTINGS));
        assertTrue(TellomiMainTabs.defaultVisible(TellomiMainTabs.PROFILE));
        assertFalse(TellomiMainTabs.defaultVisible(TellomiMainTabs.CONTACTS));
        assertFalse(TellomiMainTabs.defaultVisible(TellomiMainTabs.CALLS));
        assertFalse(store.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS)));
        assertFalse(TellomiMainTabs.isVisible(new MemoryStore(), TellomiMainTabs.CONTACTS));
        assertFalse(TellomiMainTabs.isVisible(new MemoryStore(), TellomiMainTabs.CALLS));
        assertFalse(TellomiMainTabs.hideLabels(new MemoryStore()));
    }

    @Test
    public void storedSettingsOffStaysOnTheBar() {
        MemoryStore hidden = new MemoryStore();
        hidden.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS), false);
        assertFalse(TellomiMainTabs.setVisible(hidden, TellomiMainTabs.SETTINGS, false));
        assertFalse(hidden.getBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS), true));
        assertTrue(TellomiMainTabs.isVisible(hidden, TellomiMainTabs.SETTINGS));
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(hidden.copy()));

        MemoryStore legacyOff = new MemoryStore();
        legacyOff.putBoolean(TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.SETTINGS), false);
        assertTrue(TellomiMainTabs.isVisible(legacyOff, TellomiMainTabs.SETTINGS));
        assertFalse(legacyOff.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS)));
    }

    @Test
    public void allOnOrderIsDiscoverChatsCallsContactsSettingsProfile() {
        assertEquals(6, TellomiMainTabs.COUNT);
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.CALLS,
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.BAR_ORDER);
        for (int index = 0; index < TellomiMainTabs.BAR_ORDER.length; index++) {
            assertEquals(index, TellomiMainTabs.BAR_ORDER[index]);
        }
        assertEquals(0, TellomiMainTabs.FEED);
        assertEquals(1, TellomiMainTabs.CHATS);
        assertEquals(2, TellomiMainTabs.CALLS);
        assertEquals(3, TellomiMainTabs.CONTACTS);
        assertEquals(4, TellomiMainTabs.SETTINGS);
        assertEquals(5, TellomiMainTabs.PROFILE);

        MemoryStore store = new MemoryStore();
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CALLS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CONTACTS, true));
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.SETTINGS, false));
        assertArrayEquals(TellomiMainTabs.BAR_ORDER, TellomiMainTabs.visibleTabs(store));
    }

    @Test
    public void coldStartStaysOnTheChatsTab() throws Exception {
        assertEquals(TellomiMainTabs.CHATS, TellomiMainTabs.BAR_ORDER[1]);
        String activity = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java"));
        assertTrue(activity.contains("private static final int POSITION_CHATS = TellomiMainTabs.CHATS;"));
        assertTrue(activity.contains("protected int getStartPosition() {\n        return POSITION_CHATS;\n    }"));
        assertTrue(activity.contains("tabsView.addView(tabs[index]);"));
        assertTrue(activity.contains("GlassTabView.TabAnimation.FEED, R.string.TellomiTabFeed"));
        assertFalse(activity.contains("R.string.Moments_Title"));
    }

    @Test
    public void settingsChatsSearchLinkOpensTheFeedTab() throws Exception {
        assertEquals(0, TellomiMainTabs.FEED);
        assertEquals(2, TellomiMainTabs.CALLS);
        String links = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/LinkManager.java"));
        assertTrue(links.contains("\"chats\".equalsIgnoreCase(first)"));
        assertTrue(links.contains("\"search\".equalsIgnoreCase(second)"));
        assertTrue(links.contains("mainFragment.viewPager.scrollToPosition(TellomiMainTabs.FEED)"));
        assertFalse(links.contains("scrollToPosition(2)"));
        assertFalse(links.contains("scrollToPosition(TellomiMainTabs.CALLS)"));
        assertFalse(links.contains("scrollToPosition(TellomiMainTabs.CHATS)"));
    }

    @Test
    public void chatsSettingsAndProfileStayVisibleEvenIfStoredOff() {
        MemoryStore store = new MemoryStore();
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.CHATS, false));
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.SETTINGS, false));
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.PROFILE, false));
        assertFalse(store.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.CHATS)));
        assertFalse(store.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS)));
        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.CHATS), false);
        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS), false);
        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.PROFILE), false);
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.SETTINGS));
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.PROFILE));
        assertTrue(TellomiMainTabs.isLocked(TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.isLocked(TellomiMainTabs.SETTINGS));
        assertTrue(TellomiMainTabs.isLocked(TellomiMainTabs.PROFILE));
        assertFalse(TellomiMainTabs.isLocked(TellomiMainTabs.FEED));
        assertFalse(TellomiMainTabs.isLocked(TellomiMainTabs.CALLS));
        assertFalse(TellomiMainTabs.isLocked(TellomiMainTabs.CONTACTS));
    }

    @Test
    public void chatsAndSettingsLongPressDoesNotHideThem() throws Exception {
        String activity = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java"));
        String menu = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/TellomiMenuSettingsActivity.java"));
        assertTrue(activity.contains("tabs[INDEX_CHATS].setOnLongClickListener(this::openFoldersSelector)"));
        assertFalse(activity.contains("tabs[INDEX_SETTINGS].setOnLongClickListener"));
        assertFalse(activity.contains("tabs[INDEX_CHATS].setOnLongClickListener(this::openCallsSelector)"));
        assertTrue(activity.contains("getString(R.string.HideCallTab)"));
        assertTrue(menu.contains("boolean locked = TellomiMainTabs.isLocked(tab)"));
        assertTrue(menu.contains(".setEnabled(!locked)"));
        assertTrue(menu.contains("if (TellomiMainTabs.isLocked(tab))"));
    }

    @Test
    public void otherTabsToggleAndSurviveANewStore() {
        MemoryStore store = new MemoryStore();
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.SETTINGS, false));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CONTACTS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CALLS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.FEED, false));
        TellomiMainTabs.setHideLabels(store, true);

        MemoryStore restored = store.copy();
        assertArrayEquals(new int[]{
                TellomiMainTabs.CHATS,
                TellomiMainTabs.CALLS,
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(restored));
        assertTrue(TellomiMainTabs.hideLabels(restored));
        assertFalse(TellomiMainTabs.isVisible(restored, TellomiMainTabs.FEED));

        assertTrue(TellomiMainTabs.setVisible(restored, TellomiMainTabs.FEED, true));
        assertFalse(TellomiMainTabs.setVisible(restored, TellomiMainTabs.SETTINGS, false));
        TellomiMainTabs.setHideLabels(restored, false);
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.CALLS,
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(restored));
        assertFalse(TellomiMainTabs.hideLabels(restored));
    }

    @Test
    public void settingsOrderListsEveryTabOnce() {
        boolean[] seen = new boolean[TellomiMainTabs.COUNT];
        assertEquals(TellomiMainTabs.COUNT, TellomiMainTabs.SETTINGS_ORDER.length);
        assertEquals(TellomiMainTabs.COUNT, TellomiMainTabs.BAR_ORDER.length);
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE,
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.CALLS
        }, TellomiMainTabs.SETTINGS_ORDER);
        for (int tab : TellomiMainTabs.SETTINGS_ORDER) {
            assertFalse(seen[tab]);
            seen[tab] = true;
        }
        assertTrue(Arrays.equals(seen, new boolean[]{true, true, true, true, true, true}));
    }

    @Test
    public void legacyPreferenceKeysAreCopiedForward() {
        MemoryStore store = new MemoryStore();
        store.putBoolean(TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.FEED), false);
        store.putBoolean(TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.SETTINGS), false);
        store.putBoolean("tellomi_main_tab_hide_labels", true);

        assertFalse(TellomiMainTabs.isVisible(store, TellomiMainTabs.FEED));
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.SETTINGS));
        assertTrue(TellomiMainTabs.hideLabels(store));
        assertFalse(store.getBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.FEED), true));
        assertFalse(store.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS)));
        assertTrue(store.getBoolean(TellomiMainTabs.HIDE_LABELS_KEY, false));

        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.FEED), true);
        store.putBoolean(TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.FEED), false);
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.FEED));
    }

    @Test
    public void namedAndHistoricalVisibilityKeysStayOnTheSameTabs() {
        assertEquals("mainTabsFeed", TellomiMainTabs.visibleKey(TellomiMainTabs.FEED));
        assertEquals("mainTabsChats", TellomiMainTabs.visibleKey(TellomiMainTabs.CHATS));
        assertEquals("mainTabsCalls", TellomiMainTabs.visibleKey(TellomiMainTabs.CALLS));
        assertEquals("mainTabsContacts", TellomiMainTabs.visibleKey(TellomiMainTabs.CONTACTS));
        assertEquals("mainTabsSettings", TellomiMainTabs.visibleKey(TellomiMainTabs.SETTINGS));
        assertEquals("mainTabsProfile", TellomiMainTabs.visibleKey(TellomiMainTabs.PROFILE));
        assertEquals("tellomi_main_tab_visible_0", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.CONTACTS));
        assertEquals("tellomi_main_tab_visible_1", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.CALLS));
        assertEquals("tellomi_main_tab_visible_2", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.FEED));
        assertEquals("tellomi_main_tab_visible_3", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.CHATS));
        assertEquals("tellomi_main_tab_visible_4", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.SETTINGS));
        assertEquals("tellomi_main_tab_visible_5", TellomiMainTabs.legacyVisibleKey(TellomiMainTabs.PROFILE));

        MemoryStore named = new MemoryStore();
        named.putBoolean("mainTabsContacts", true);
        named.putBoolean("mainTabsCalls", false);
        named.putBoolean("mainTabsFeed", false);
        named.putBoolean("mainTabsSettings", true);
        assertTrue(TellomiMainTabs.isVisible(named, TellomiMainTabs.CONTACTS));
        assertFalse(TellomiMainTabs.isVisible(named, TellomiMainTabs.CALLS));
        assertFalse(TellomiMainTabs.isVisible(named, TellomiMainTabs.FEED));
        assertTrue(TellomiMainTabs.isVisible(named, TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.isVisible(named, TellomiMainTabs.SETTINGS));
        assertTrue(TellomiMainTabs.isVisible(named, TellomiMainTabs.PROFILE));
        assertArrayEquals(new int[]{
                TellomiMainTabs.CHATS,
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(named));

        MemoryStore legacy = new MemoryStore();
        legacy.putBoolean("tellomi_main_tab_visible_0", true);
        legacy.putBoolean("tellomi_main_tab_visible_1", false);
        legacy.putBoolean("tellomi_main_tab_visible_2", false);
        legacy.putBoolean("tellomi_main_tab_visible_4", true);
        assertTrue(TellomiMainTabs.isVisible(legacy, TellomiMainTabs.CONTACTS));
        assertFalse(TellomiMainTabs.isVisible(legacy, TellomiMainTabs.CALLS));
        assertFalse(TellomiMainTabs.isVisible(legacy, TellomiMainTabs.FEED));
        assertTrue(TellomiMainTabs.isVisible(legacy, TellomiMainTabs.SETTINGS));
        assertTrue(legacy.getBoolean("mainTabsContacts", false));
        assertFalse(legacy.getBoolean("mainTabsCalls", true));
        assertFalse(legacy.getBoolean("mainTabsFeed", true));
        assertFalse(legacy.contains("mainTabsSettings"));
        assertFalse(legacy.contains("mainTabs" + TellomiMainTabs.FEED));
        assertArrayEquals(TellomiMainTabs.visibleTabs(named), TellomiMainTabs.visibleTabs(legacy));
    }

    @Test
    public void freshInstallThirdTabIsMeNotTheOfficialProfileString() throws Exception {
        int[] visible = TellomiMainTabs.visibleTabs(new MemoryStore());
        assertEquals(4, visible.length);
        assertEquals(TellomiMainTabs.SETTINGS, visible[2]);
        assertEquals(TellomiMainTabs.PROFILE, visible[3]);

        String activity = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java"));
        String menu = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/TellomiMenuSettingsActivity.java"));
        assertTrue(activity.contains("createAvatar(context, resourceProvider, currentAccount, R.string.TellomiTabMe)"));
        assertFalse(activity.contains("R.string.MainTabsProfile"));
        assertTrue(menu.contains("return R.string.TellomiTabMe;"));
        assertTrue(menu.contains("return R.string.TellomiTabFeed;"));
        assertFalse(menu.contains("R.string.MainTabsProfile"));
        assertFalse(menu.contains("R.string.Moments_Title"));

        String english = read(repoRoot().resolve("TMessagesProj/src/main/res/values/strings.xml"));
        String simplified = read(repoRoot().resolve("TMessagesProj/src/main/res/values-zh-rCN/strings.xml"));
        String traditional = read(repoRoot().resolve("TMessagesProj/src/main/res/values-zh-rTW/strings.xml"));
        assertTrue(english.contains("<string name=\"TellomiTabMe\">Me</string>"));
        assertTrue(english.contains("<string name=\"MainTabsProfile\">Profile</string>"));
        assertTrue(simplified.contains("<string name=\"TellomiTabMe\">我</string>"));
        assertTrue(traditional.contains("<string name=\"TellomiTabMe\">我</string>"));
        String zh = read(repoRoot().resolve("TMessagesProj/src/main/res/values-zh/strings.xml"));
        assertTrue(english.contains("<string name=\"TellomiTabFeed\">Discover</string>"));
        assertTrue(simplified.contains("<string name=\"TellomiTabFeed\">发现</string>"));
        assertTrue(traditional.contains("<string name=\"TellomiTabFeed\">发现</string>"));
        assertTrue(simplified.contains("name=\"Moments_Title\">动态<"));
        assertTrue(zh.contains("<string name=\"TellomiTabMe\">我</string>"));
        assertTrue(zh.contains("<string name=\"TellomiTabFeed\">发现</string>"));
        assertFalse(simplified.contains("name=\"MainTabsProfile\""));
        assertFalse(traditional.contains("name=\"MainTabsProfile\""));
        assertFalse(zh.contains("name=\"MainTabsProfile\""));
    }

    @Test
    public void menuStringsMatchLanguagePackKeys() throws Exception {
        String[] keys = {
                "SettingsTools",
                "MainTabsMenuSettings",
                "MainTabsHideLabels",
                "MainTabsButtons"
        };
        Map<String, String[]> exact = new HashMap<>();
        exact.put("values", new String[]{"Tools", "Menu settings", "Hide button labels", "Navigation bar buttons"});
        exact.put("values-zh-rCN", new String[]{"工具", "选单设定", "隐藏按钮文字", "导航栏按钮"});
        exact.put("values-zh-rTW", new String[]{"工具", "選單設定", "隱藏按鈕文字", "導覽列按鈕"});
        for (Map.Entry<String, String[]> entry : exact.entrySet()) {
            String xml = read(repoRoot().resolve("TMessagesProj/src/main/res/" + entry.getKey() + "/strings.xml"));
            for (int i = 0; i < keys.length; i++) {
                assertTrue(entry.getKey() + " " + keys[i],
                        xml.contains("<string name=\"" + keys[i] + "\">" + entry.getValue()[i] + "</string>"));
            }
            assertFalse(xml.contains("TellomiMenuSettings"));
            assertFalse(xml.contains("TellomiTools"));
            assertFalse(xml.contains("TellomiHideTabLabels"));
            assertFalse(xml.contains("TellomiNavBarButtons"));
        }
        Map<String, String> menuTitle = new HashMap<>();
        menuTitle.put("values-ru", "Настройки меню");
        menuTitle.put("values-uk", "Налаштування меню");
        menuTitle.put("values-de", "Menüeinstellungen");
        menuTitle.put("values-es", "Ajustes del menú");
        menuTitle.put("values-it", "Impostazioni menu");
        menuTitle.put("values-ko", "메뉴 설정");
        menuTitle.put("values-nl", "Menu-instellingen");
        menuTitle.put("values-pt-rBR", "Ajustes do menu");
        menuTitle.put("values-ar", "إعدادات القائمة");
        for (Map.Entry<String, String> entry : menuTitle.entrySet()) {
            String xml = read(repoRoot().resolve("TMessagesProj/src/main/res/" + entry.getKey() + "/strings.xml"));
            for (String key : keys) {
                assertTrue(entry.getKey() + " missing " + key, xml.contains("name=\"" + key + "\""));
            }
            assertTrue(xml.contains("<string name=\"MainTabsMenuSettings\">" + entry.getValue() + "</string>"));
            assertFalse(xml.contains("<string name=\"MainTabsMenuSettings\">Menu settings</string>"));
        }
    }

    @Test
    public void menuScreenUsesResourceGetStringAndGlobalNotification() throws Exception {
        String activity = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/TellomiMenuSettingsActivity.java"));
        String settings = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java"));
        String tabs = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java"));
        String center = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java"));
        assertFalse(activity.contains("getString(\""));
        assertTrue(activity.contains("getString(R.string.MainTabsMenuSettings)"));
        assertTrue(activity.contains("getString(R.string.MainTabsHideLabels)"));
        assertTrue(activity.contains("getString(R.string.MainTabsButtons)"));
        assertTrue(activity.contains("NotificationCenter.mainTabsSettingsUpdated"));
        assertFalse(activity.contains("TellomiMainTabs.Listener"));
        assertTrue(settings.contains("getString(R.string.SettingsTools)"));
        assertTrue(settings.contains("getString(R.string.MainTabsMenuSettings)"));
        assertFalse(settings.contains("\"TellomiMenuSettings\""));
        assertFalse(settings.contains("\"TellomiTools\""));
        assertFalse(settings.contains("R.string.TellomiMenuSettings"));
        assertFalse(settings.contains("R.string.TellomiTools"));
        assertTrue(tabs.contains("NotificationCenter.mainTabsSettingsUpdated"));
        assertFalse(tabs.contains("tellomiTabsListener"));
        assertFalse(tabs.contains("getString(\"Moments_Title\""));
        assertTrue(center.contains("mainTabsSettingsUpdated = totalEvents++"));
    }

    @Test
    public void ownProfileStillOpensSettingsWhenTheTabIsHidden() throws Exception {
        String profile = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java"));
        String actions = read(repoRoot().resolve("TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileActionsView.java"));
        assertTrue(profile.contains("actionsView.addSettings()"));
        assertTrue(profile.contains("case ProfileActionsView.KEY_SETTINGS:"));
        assertTrue(profile.contains("presentFragment(new SettingsActivity())"));
        assertTrue(profile.contains("imageUpdater = new ImageUpdater"));
        assertTrue(actions.contains("KEY_SETTINGS = 17"));
        assertTrue(actions.contains("mode == MODE_MY_PROFILE"));
    }

    private static final class MemoryStore implements TellomiMainTabs.Store {
        private final Map<String, Boolean> values = new HashMap<>();

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Boolean value = values.get(key);
            return value == null ? defaultValue : value;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }

        private MemoryStore copy() {
            MemoryStore copy = new MemoryStore();
            copy.values.putAll(values);
            return copy;
        }
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("TMessagesProj"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
