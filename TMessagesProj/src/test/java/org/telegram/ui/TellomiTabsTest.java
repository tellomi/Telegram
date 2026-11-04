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
        TellomiTabs.Tab.SETTINGS,
        TellomiTabs.Tab.PROFILE
    );

    /** Noto Sans Bold widths in dp, rounded up, plus 2dp. Order is the tab order. */
    private static final int[] EN_12 = {54, 36, 31, 55, 52, 42};
    private static final int[] EN_10 = {45, 31, 26, 46, 44, 35};
    private static final int[] ZH_12 = {27, 27, 27, 40, 27, 27};
    private static final int[] ZH_10 = {23, 23, 23, 33, 23, 23};

    @Test
    public void freshInstallShowsDiscoverChatsSettingsProfile() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(TellomiTabs.Visibility.fresh());
        assertEquals(Arrays.asList(
            TellomiTabs.Tab.FEED,
            TellomiTabs.Tab.CHATS,
            TellomiTabs.Tab.SETTINGS,
            TellomiTabs.Tab.PROFILE
        ), tabs);
    }

    @Test
    public void allOnIsDiscoverChatsCallsContactsSettingsProfile() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(new TellomiTabs.Visibility(true, true, true));
        assertEquals(ALL, tabs);
        assertEquals(6, tabs.size());
    }

    @Test
    public void chatsAndSettingsStayWhenOptionalsAreHidden() {
        List<TellomiTabs.Tab> tabs = TellomiTabs.visible(new TellomiTabs.Visibility(false, false, false));
        assertEquals(Arrays.asList(
            TellomiTabs.Tab.CHATS,
            TellomiTabs.Tab.SETTINGS,
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
    public void sixTabsFitOnA360dpScreenWithoutScaling() {
        assertTrue(TellomiTabs.barMaxWidthDp(6) >= 360);
        assertEquals(328 + 16, TellomiTabs.barMaxWidthDp(4));
        assertTrue(TellomiTabs.contentWidthDp(360, 6) > TellomiTabs.contentWidthDp(360, 4));
        assertTrue(TellomiTabs.labelsFit(360, 6, EN_12, EN_10));
        assertTrue(TellomiTabs.labelsFit(360, 6, ZH_12, ZH_10));
    }

    @Test
    public void hiddenVisualIndexIsNotAPagerPosition() {
        assertEquals(-1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CALLS));
        assertEquals(-1, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.CONTACTS));
        assertEquals(0, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.FEED));
        assertEquals(3, TellomiTabs.indexOf(TellomiTabs.visible(TellomiTabs.Visibility.fresh()), TellomiTabs.Tab.PROFILE));
    }
}
