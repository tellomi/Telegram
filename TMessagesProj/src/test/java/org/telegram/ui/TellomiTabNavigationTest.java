package org.telegram.ui;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiTabNavigationTest {

    @Test
    public void navigationCopyMatchesTheBar() {
        TellomiTabNavigation.Copy chinese = TellomiTabNavigation.Copy.resolve("zh-Hans");
        assertEquals("发现", chinese.feed);
        assertEquals("隐藏发现", chinese.hideFeed);
        assertEquals("隐藏联系人", chinese.hideContacts);
        assertEquals("导航栏", chinese.navigationTitle);
        assertEquals("聊天和设置始终显示，不能关闭。", chinese.navigationFooter);
        assertEquals("还没有内容", chinese.feedEmptyTitle);
        assertEquals("帖子会显示在这里。", chinese.feedEmptyText);
        assertEquals("这条帖子暂时无法打开。", chinese.feedPostUnavailable);

        TellomiTabNavigation.Copy english = TellomiTabNavigation.Copy.resolve("en");
        assertEquals("Discover", english.feed);
        assertEquals("Hide Discover", english.hideFeed);
        assertEquals("Hide Contacts", english.hideContacts);
        assertEquals("Navigation Bar", english.navigationTitle);
        assertEquals("Chats and Settings always stay in the bar.", english.navigationFooter);
        assertEquals("Nothing here yet", english.feedEmptyTitle);
        assertEquals("Posts will show up here.", english.feedEmptyText);
        assertEquals("This post can't be opened yet.", english.feedPostUnavailable);

        assertEquals("发现", TellomiTabNavigation.Copy.resolve("ZH").feed);
        assertEquals("还没有内容", TellomiTabNavigation.Copy.resolve("zh").feedEmptyTitle);
        assertEquals("发现", TellomiTabNavigation.Copy.resolve("zh_hans").feed);
        assertEquals("导航栏", TellomiTabNavigation.Copy.resolve("zh_CN").navigationTitle);
        assertEquals("Discover", TellomiTabNavigation.Copy.resolve("en-US").feed);
        assertEquals("This post can't be opened yet.", TellomiTabNavigation.Copy.resolve("de").feedPostUnavailable);
    }

    @Test
    public void navigationRowsLeaveChatsAndSettingsOn() {
        assertEquals(Arrays.asList(
            TellomiTabNavigation.Row.FEED,
            TellomiTabNavigation.Row.CHATS,
            TellomiTabNavigation.Row.CALLS,
            TellomiTabNavigation.Row.CONTACTS,
            TellomiTabNavigation.Row.SETTINGS
        ), TellomiTabNavigation.ROWS);
        assertTrue(TellomiTabNavigation.Row.FEED.toggleable);
        assertTrue(TellomiTabNavigation.Row.CALLS.toggleable);
        assertTrue(TellomiTabNavigation.Row.CONTACTS.toggleable);
        assertFalse(TellomiTabNavigation.Row.CHATS.toggleable);
        assertFalse(TellomiTabNavigation.Row.SETTINGS.toggleable);

        TellomiTabs.Visibility start = new TellomiTabs.Visibility(true, false, true);
        TellomiTabs.Visibility chatsOff = TellomiTabNavigation.apply(start, TellomiTabNavigation.Row.CHATS, false);
        assertTrue(chatsOff.feed);
        assertFalse(chatsOff.calls);
        assertTrue(chatsOff.contacts);
        TellomiTabs.Visibility settingsOff = TellomiTabNavigation.apply(start, TellomiTabNavigation.Row.SETTINGS, false);
        assertTrue(settingsOff.feed);
        assertTrue(settingsOff.contacts);

        TellomiTabs.Visibility feedOff = TellomiTabNavigation.apply(start, TellomiTabNavigation.Row.FEED, false);
        assertFalse(feedOff.feed);
        assertTrue(feedOff.contacts);
        TellomiTabs.Visibility contactsOn = TellomiTabNavigation.apply(feedOff, TellomiTabNavigation.Row.CONTACTS, true);
        assertFalse(contactsOn.feed);
        assertTrue(contactsOn.contacts);
        TellomiTabs.Visibility callsOn = TellomiTabNavigation.apply(start, TellomiTabNavigation.Row.CALLS, true);
        assertTrue(callsOn.calls);
        assertTrue(callsOn.feed);
    }

    @Test
    public void aPostLinkKeepsDiscoverSelected() {
        assertEquals(TellomiTabs.Tab.FEED, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.CHATS, true));
        assertEquals(TellomiTabs.Tab.FEED, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.SETTINGS, true));
        assertEquals(TellomiTabs.Tab.FEED, TellomiTabNavigation.tabToKeep(null, true));
        assertEquals(TellomiTabs.Tab.CHATS, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.CHATS, false));
        assertEquals(TellomiTabs.Tab.CONTACTS, TellomiTabNavigation.tabToKeep(TellomiTabs.Tab.CONTACTS, false));
        assertEquals(TellomiTabs.Tab.CHATS, TellomiTabNavigation.tabToKeep(null, false));
    }

    @Test
    public void longPressHidesDiscoverCallsAndContactsOnly() {
        TellomiTabs.Visibility all = new TellomiTabs.Visibility(true, true, true);
        assertTrue(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.FEED));
        assertTrue(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.CALLS));
        assertTrue(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.CONTACTS));
        assertFalse(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.CHATS));
        assertFalse(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.SETTINGS));
        assertFalse(TellomiTabNavigation.canLongPressHide(TellomiTabs.Tab.PROFILE));

        TellomiTabs.Visibility hiddenFeed = TellomiTabNavigation.hide(all, TellomiTabs.Tab.FEED);
        assertFalse(hiddenFeed.feed);
        assertTrue(hiddenFeed.calls);
        assertTrue(hiddenFeed.contacts);
        assertTrue(TellomiTabs.visible(hiddenFeed).contains(TellomiTabs.Tab.CHATS));
        assertTrue(TellomiTabs.visible(hiddenFeed).contains(TellomiTabs.Tab.SETTINGS));
        assertFalse(TellomiTabs.visible(hiddenFeed).contains(TellomiTabs.Tab.FEED));

        TellomiTabs.Visibility hiddenContacts = TellomiTabNavigation.hide(all, TellomiTabs.Tab.CONTACTS);
        assertFalse(hiddenContacts.contacts);
        assertTrue(TellomiTabs.visible(hiddenContacts).contains(TellomiTabs.Tab.FEED));
        assertFalse(TellomiTabs.visible(hiddenContacts).contains(TellomiTabs.Tab.CONTACTS));

        TellomiTabs.Visibility untouched = TellomiTabNavigation.hide(all, TellomiTabs.Tab.CHATS);
        assertTrue(untouched.feed);
        assertTrue(untouched.calls);
        assertTrue(untouched.contacts);
        assertEquals(all.feed, TellomiTabNavigation.hide(all, TellomiTabs.Tab.SETTINGS).feed);
        assertEquals(all.contacts, TellomiTabNavigation.hide(all, TellomiTabs.Tab.PROFILE).contacts);
    }

    @Test
    public void hidingContactsKeepsTheNewMessagePicker() {
        TellomiTabs.Visibility hidden = TellomiTabNavigation.hide(
            new TellomiTabs.Visibility(true, false, true),
            TellomiTabs.Tab.CONTACTS
        );
        assertFalse(hidden.contacts);
        assertFalse(TellomiTabs.visible(hidden).contains(TellomiTabs.Tab.CONTACTS));
        assertTrue(TellomiTabs.visible(hidden).contains(TellomiTabs.Tab.CHATS));
        assertEquals(TellomiTabNavigation.NewMessageContacts.PICKER, TellomiTabNavigation.newMessageContacts(false));
        assertEquals(TellomiTabNavigation.NewMessageContacts.PICKER, TellomiTabNavigation.newMessageContacts(true));
    }

    @Test
    public void contactsLookupDoesNotCreateAPage() {
        assertEquals(TellomiTabNavigation.ContactsRead.NONE, TellomiTabNavigation.contactsRead(true, true, false, false));
        assertEquals(TellomiTabNavigation.ContactsRead.PUSHED, TellomiTabNavigation.contactsRead(true, true, false, true));
        assertEquals(TellomiTabNavigation.ContactsRead.TAB, TellomiTabNavigation.contactsRead(true, true, true, false));
        assertEquals(TellomiTabNavigation.ContactsRead.TAB, TellomiTabNavigation.contactsRead(true, true, true, true));
        assertEquals(TellomiTabNavigation.ContactsRead.OWNED, TellomiTabNavigation.contactsRead(false, true, false, false));
        assertEquals(TellomiTabNavigation.ContactsRead.OWNED, TellomiTabNavigation.contactsRead(false, true, true, true));
        assertEquals(TellomiTabNavigation.ContactsRead.OWNED, TellomiTabNavigation.contactsRead(true, false, false, true));
    }

    @Test
    public void aPostLinkIsNotHandledWhenTheBarIsOff() {
        assertEquals("example", TellomiTabNavigation.feedPostId("https://tell.cc/m#example"));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m#example", false));
        assertEquals("example", TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m#example", true));

        assertEquals("example", TellomiTabNavigation.feedPostIdIfHandled("https://TELL.CC/m#example", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://TELL.CC/m#example", false));

        assertEquals("example id", TellomiTabNavigation.feedPostIdIfHandled("https://www.tell.cc/m/#example%20id", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://www.tell.cc/m/#example%20id", false));

        assertEquals("example", TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m?x=1#example", true));
        assertEquals("a+b", TellomiTabNavigation.feedPostId("https://tell.cc/m#a+b"));

        assertNull(TellomiTabNavigation.feedPostId("https://tell.cc/m/some-slug"));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m/some-slug", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m/some-slug", false));

        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://t.me/m#example", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m#", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m#%20", true));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("https://tell.cc/m/extra#example", true));
        assertEquals("example", TellomiTabNavigation.feedPostId("tell.cc", "/m", "example"));
        assertNull(TellomiTabNavigation.feedPostIdIfHandled("tell.cc", "/m", "example", false));
        assertNull(TellomiTabNavigation.feedPostId("tell.cc", "/m/some-slug", "example"));
        assertNull(TellomiTabNavigation.feedPostId("t.me", "/m", "example"));
    }
}
