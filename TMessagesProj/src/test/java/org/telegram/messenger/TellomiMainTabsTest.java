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
    public void freshInstallShowsMomentsChatsAndProfile() {
        int[] visible = TellomiMainTabs.visibleTabs(new MemoryStore());
        assertArrayEquals(new int[]{
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.PROFILE
        }, visible);
        assertFalse(TellomiMainTabs.isVisible(new MemoryStore(), TellomiMainTabs.SETTINGS));
        assertFalse(TellomiMainTabs.isVisible(new MemoryStore(), TellomiMainTabs.CONTACTS));
        assertFalse(TellomiMainTabs.isVisible(new MemoryStore(), TellomiMainTabs.CALLS));
        assertFalse(TellomiMainTabs.hideLabels(new MemoryStore()));
    }

    @Test
    public void chatsAndProfileStayVisibleEvenIfStoredOff() {
        MemoryStore store = new MemoryStore();
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.CHATS, false));
        assertFalse(TellomiMainTabs.setVisible(store, TellomiMainTabs.PROFILE, false));
        assertFalse(store.contains(TellomiMainTabs.visibleKey(TellomiMainTabs.CHATS)));
        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.CHATS), false);
        store.putBoolean(TellomiMainTabs.visibleKey(TellomiMainTabs.PROFILE), false);
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.isVisible(store, TellomiMainTabs.PROFILE));
        assertTrue(TellomiMainTabs.isLocked(TellomiMainTabs.CHATS));
        assertTrue(TellomiMainTabs.isLocked(TellomiMainTabs.PROFILE));
        assertFalse(TellomiMainTabs.isLocked(TellomiMainTabs.SETTINGS));
    }

    @Test
    public void otherTabsToggleAndSurviveANewStore() {
        MemoryStore store = new MemoryStore();
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.SETTINGS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CONTACTS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.CALLS, true));
        assertTrue(TellomiMainTabs.setVisible(store, TellomiMainTabs.FEED, false));
        TellomiMainTabs.setHideLabels(store, true);

        MemoryStore restored = store.copy();
        assertArrayEquals(new int[]{
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.CALLS,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.SETTINGS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(restored));
        assertTrue(TellomiMainTabs.hideLabels(restored));
        assertFalse(TellomiMainTabs.isVisible(restored, TellomiMainTabs.FEED));

        assertTrue(TellomiMainTabs.setVisible(restored, TellomiMainTabs.FEED, true));
        assertTrue(TellomiMainTabs.setVisible(restored, TellomiMainTabs.SETTINGS, false));
        TellomiMainTabs.setHideLabels(restored, false);
        assertArrayEquals(new int[]{
                TellomiMainTabs.CONTACTS,
                TellomiMainTabs.CALLS,
                TellomiMainTabs.FEED,
                TellomiMainTabs.CHATS,
                TellomiMainTabs.PROFILE
        }, TellomiMainTabs.visibleTabs(restored));
        assertFalse(TellomiMainTabs.hideLabels(restored));
    }

    @Test
    public void settingsOrderListsEveryTabOnce() {
        boolean[] seen = new boolean[TellomiMainTabs.COUNT];
        assertEquals(TellomiMainTabs.COUNT, TellomiMainTabs.SETTINGS_ORDER.length);
        assertEquals(TellomiMainTabs.COUNT, TellomiMainTabs.BAR_ORDER.length);
        for (int tab : TellomiMainTabs.SETTINGS_ORDER) {
            assertFalse(seen[tab]);
            seen[tab] = true;
        }
        assertTrue(Arrays.equals(seen, new boolean[]{true, true, true, true, true, true}));
    }

    @Test
    public void menuStringsExistInEveryBuiltInLanguage() throws Exception {
        Map<String, String> expected = new HashMap<>();
        expected.put("values", "Tools");
        expected.put("values-zh-rCN", "选单设定");
        expected.put("values-zh-rTW", "選單設定");
        expected.put("values-ru", "Настройки меню");
        expected.put("values-uk", "Налаштування меню");
        expected.put("values-de", "Menüeinstellungen");
        expected.put("values-es", "Ajustes del menú");
        expected.put("values-it", "Impostazioni menu");
        expected.put("values-ko", "메뉴 설정");
        expected.put("values-nl", "Menu-instellingen");
        expected.put("values-pt-rBR", "Ajustes do menu");
        expected.put("values-ar", "إعدادات القائمة");
        String[] keys = {
                "TellomiTools",
                "TellomiMenuSettings",
                "TellomiHideTabLabels",
                "TellomiNavBarButtons"
        };
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String xml = read(repoRoot().resolve("TMessagesProj/src/main/res/" + entry.getKey() + "/strings.xml"));
            for (String key : keys) {
                assertTrue(entry.getKey() + " missing " + key, xml.contains("name=\"" + key + "\""));
            }
            assertTrue(entry.getKey() + " missing menu title", xml.contains(">" + entry.getValue() + "<"));
            assertFalse(xml.contains("name=\"TellomiMenuSettings\">Menu settings<") && !"values".equals(entry.getKey()));
        }
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
