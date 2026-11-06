package org.telegram.ui;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiTabsTest {

    private static final List<TellomiTabs.Tab> ALL = Arrays.asList(
        TellomiTabs.Tab.FEED,
        TellomiTabs.Tab.CHATS,
        TellomiTabs.Tab.CALLS,
        TellomiTabs.Tab.CONTACTS,
        TellomiTabs.Tab.PROFILE
    );

    /** Widths without the removed Settings tab. Order is the remaining tab order. */
    private static final int[] EN_12 = {54, 36, 31, 55, 42};
    private static final int[] EN_10 = {45, 31, 26, 46, 35};
    private static final int[] ZH_12 = {27, 27, 27, 40, 27};
    private static final int[] ZH_10 = {23, 23, 23, 33, 23};

    @Test
    public void freshInstallShowsDiscoverChatsProfile() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(TellomiTabs.Visibility.fresh());
        assertEquals(Arrays.asList(
            TellomiTabs.Tab.FEED,
            TellomiTabs.Tab.CHATS,
            TellomiTabs.Tab.PROFILE
        ), tabs);
        assertFalse(tabs.contains(TellomiTabs.Tab.SETTINGS));
    }

    @Test
    public void allOnIsDiscoverChatsCallsContactsProfile() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(new TellomiTabs.Visibility(true, true, true));
        assertEquals(ALL, tabs);
        assertEquals(5, tabs.size());
        assertFalse(tabs.contains(TellomiTabs.Tab.SETTINGS));
    }

    @Test
    public void chatsAndProfileStayWhenOptionalsAreHidden() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(new TellomiTabs.Visibility(false, false, false));
        assertEquals(Arrays.asList(
            TellomiTabs.Tab.CHATS,
            TellomiTabs.Tab.PROFILE
        ), tabs);
        assertFalse(tabs.contains(TellomiTabs.Tab.FEED));
        assertFalse(tabs.contains(TellomiTabs.Tab.CALLS));
        assertFalse(tabs.contains(TellomiTabs.Tab.CONTACTS));
    }

    @Test
    public void startIndexIsAlwaysChats() {
        assertEquals(1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CHATS));
        assertEquals(1, TellomiTabs.indexOf(ALL, TellomiTabs.Tab.CHATS));
        assertEquals(0, TellomiTabs.indexOf(
            TellomiTabs.visible(new TellomiTabs.Visibility(false, false, false)),
            TellomiTabs.Tab.CHATS
        ));
        List<TellomiTabs.Tab> callsOnly = TellomiTabs.visible(new TellomiTabs.Visibility(false, true, false));
        assertEquals(0, TellomiTabs.indexOf(callsOnly, TellomiTabs.Tab.CHATS));
        assertEquals(TellomiTabs.Tab.CALLS, callsOnly.get(1));
    }

    @Test
    public void fiveTabsFitOnA360dpScreenWithoutScaling() {
        assertTrue(TellomiTabs.barMaxWidthDp(5) >= 360);
        assertEquals(328 + 16, TellomiTabs.barMaxWidthDp(4));
        assertTrue(TellomiTabs.contentWidthDp(360, 5) > TellomiTabs.contentWidthDp(360, 4));
        assertTrue(TellomiTabs.labelsFit(360, 5, EN_12, EN_10));
        assertTrue(TellomiTabs.labelsFit(360, 5, ZH_12, ZH_10));
    }

    @Test
    public void settingsSelectionIsNotRewrittenAndANewLaunchStartsOnChats() throws Exception {
        assertEquals(TellomiTabs.Tab.SETTINGS, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.SETTINGS, false));
        assertEquals(TellomiTabs.Tab.FEED, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.SETTINGS, true));
        assertEquals(TellomiTabs.Tab.CHATS, TellomiTabNavigation.tabToKeep(null, false));
        assertEquals(1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CHATS));

        String pager = read("TMessagesProj/src/main/java/org/telegram/ui/ViewPagerActivity.java");
        int create = pager.indexOf("initialFragmentPosition = getStartPosition()");
        int memory = pager.indexOf("initialFragmentPosition = viewPager.getCurrentPosition()");
        assertTrue(create > 0);
        assertTrue(memory > 0);
        assertFalse(pager.contains("restoredPagerPosition"));
        String tabs = read("TMessagesProj/tellomi/java/org/telegram/ui/TellomiTabs.java");
        assertFalse(tabs.contains("restoredPagerPosition"));
        String main = read("TMessagesProj/src/main/java/org/telegram/ui/MainTabsActivity.java");
        assertTrue(main.contains("return TellomiTabs.startPosition(currentAccount, POSITION_CHATS)"));
        assertFalse(main.contains("saveSelfArgs"));
    }

    @Test
    public void hiddenVisualIndexIsNotAPagerPosition() {
        assertEquals(-1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CALLS));
        assertEquals(-1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CONTACTS));
        assertEquals(0, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.FEED));
        assertEquals(2, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.PROFILE));
    }

    @Test
    public void profileSettingsButtonStillOpensSettings() throws Exception {
        String profile = read("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java");
        int settings = profile.indexOf("case ProfileActionsView.KEY_SETTINGS:");
        assertTrue(settings > 0);
        assertTrue(profile.substring(settings, settings + 120).contains("new SettingsActivity()"));
    }

    private static String read(String relative) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(find(relative).toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static java.io.File find(String relative) {
        java.io.File direct = new java.io.File(relative);
        if (direct.isFile()) {
            return direct;
        }
        java.io.File nested = new java.io.File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        String stripped = relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative;
        java.io.File fromModule = new java.io.File("..", stripped);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative);
    }
}
