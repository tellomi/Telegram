package org.telegram.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Decisions for the navigation settings page, long-press hide, and
 * {@code https://tell.cc/m#<id>} (M2 / D-38, D-57). Same rules as iOS #60.
 * A post link is not consumed when {@link TellomiTabs#enabled()} is false.
 */
public final class TellomiTabNavigation {

    public static final class Copy {
        public final String feed;
        public final String hideFeed;
        public final String hideContacts;
        public final String navigationTitle;
        public final String navigationFooter;
        public final String hideTabLabels;
        public final String feedEmptyTitle;
        public final String feedEmptyText;
        public final String feedPostUnavailable;

        Copy(String feed, String hideFeed, String hideContacts, String navigationTitle, String navigationFooter,
             String hideTabLabels, String feedEmptyTitle, String feedEmptyText, String feedPostUnavailable) {
            this.feed = feed;
            this.hideFeed = hideFeed;
            this.hideContacts = hideContacts;
            this.navigationTitle = navigationTitle;
            this.navigationFooter = navigationFooter;
            this.hideTabLabels = hideTabLabels;
            this.feedEmptyTitle = feedEmptyTitle;
            this.feedEmptyText = feedEmptyText;
            this.feedPostUnavailable = feedPostUnavailable;
        }

        public static Copy resolve(String languageCode) {
            return resolve(languageCode, null);
        }

        public static Copy resolve(String languageCode, TellomiPack.Source source) {
            return new Copy(
                TellomiPack.text("Tellomi.Discover.Feed", languageCode, source),
                TellomiPack.text("Tellomi.Discover.HideFeed", languageCode, source),
                TellomiPack.text("Tellomi.Discover.HideContacts", languageCode, source),
                TellomiPack.text("Tellomi.Discover.NavigationTitle", languageCode, source),
                TellomiPack.text("Tellomi.Discover.NavigationFooter", languageCode, source),
                TellomiPack.text("Tellomi.Discover.HideTabLabels", languageCode, source),
                TellomiPack.text("Tellomi.Discover.FeedEmptyTitle", languageCode, source),
                TellomiPack.text("Tellomi.Discover.FeedEmptyText", languageCode, source),
                TellomiPack.text("Tellomi.Discover.FeedPostUnavailable", languageCode, source)
            );
        }
    }

    /** Settings → Navigation Bar, after Language. Chats stays on. Settings is not a tab. */
    public enum Row {
        FEED(1, true),
        CHATS(2, false),
        CALLS(3, true),
        CONTACTS(4, true),
        SETTINGS(5, false);

        public final int id;
        public final boolean toggleable;

        Row(int id, boolean toggleable) {
            this.id = id;
            this.toggleable = toggleable;
        }
    }

    public static final List<Row> ROWS = Collections.unmodifiableList(Arrays.asList(
        Row.FEED, Row.CHATS, Row.CALLS, Row.CONTACTS
    ));

    /**
     * What a contacts-tab lookup may return. None of these create a page.
     * New Message does not use this lookup.
     */
    public enum ContactsRead {
        /** The bar is off, or it is not built yet. Keep the controller already held. */
        OWNED,
        /** The contacts tab is in the bar. */
        TAB,
        /** The tab is hidden and a contacts page is already on the stack. */
        PUSHED,
        /** The tab is hidden and nothing is pushed. Do not create a page. */
        NONE
    }

    /**
     * New Message presents its own contact picker
     * ({@code DialogsActivity.openWriteContacts}, {@code open_new_dialog}).
     * That list stays when the contacts tab is hidden.
     */
    public enum NewMessageContacts {
        PICKER
    }

    private TellomiTabNavigation() {
    }

    public static Row row(int id) {
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            if (row.id == id) {
                return row;
            }
        }
        return null;
    }

    public static TellomiTabs.Visibility apply(TellomiTabs.Visibility current, Row row, boolean value) {
        TellomiTabs.Visibility base = current == null ? TellomiTabs.Visibility.fresh() : current;
        if (row == null || !row.toggleable) {
            return base;
        }
        switch (row) {
            case FEED:
                return new TellomiTabs.Visibility(value, base.calls, base.contacts);
            case CALLS:
                return new TellomiTabs.Visibility(base.feed, value, base.contacts);
            case CONTACTS:
                return new TellomiTabs.Visibility(base.feed, base.calls, value);
            default:
                return base;
        }
    }

    /**
     * Tab to stay on after the bar is rebuilt. A post link lands on Discover
     * even when the pager still thinks that index is Chats (D-57).
     */
    public static TellomiTabs.Tab tabToKeep(TellomiTabs.Tab current, boolean openFeed) {
        if (openFeed) {
            return TellomiTabs.Tab.FEED;
        }
        return current == null ? TellomiTabs.Tab.CHATS : current;
    }

    /**
     * Bottom-bar label for the profile tab. Chinese shows 我.
     * Any other language returns null so the stock tab string stays.
     * This is not the profile page title.
     */
    public static String profileNavLabel(String languageCode) {
        return TellomiProfileNav.label(languageCode);
    }

    /** Long-press hide. Chats, the removed Settings tab, and Profile are unchanged. */
    public static TellomiTabs.Visibility hide(TellomiTabs.Visibility current, TellomiTabs.Tab tab) {
        if (tab == TellomiTabs.Tab.FEED) {
            return apply(current, Row.FEED, false);
        }
        if (tab == TellomiTabs.Tab.CALLS) {
            return apply(current, Row.CALLS, false);
        }
        if (tab == TellomiTabs.Tab.CONTACTS) {
            return apply(current, Row.CONTACTS, false);
        }
        return current == null ? TellomiTabs.Visibility.fresh() : current;
    }

    public static boolean canLongPressHide(TellomiTabs.Tab tab) {
        return tab == TellomiTabs.Tab.FEED || tab == TellomiTabs.Tab.CALLS || tab == TellomiTabs.Tab.CONTACTS;
    }

    public static ContactsRead contactsRead(boolean navigationEnabled, boolean tabBarReady, boolean tabVisible, boolean pushedPageExists) {
        if (!navigationEnabled || !tabBarReady) {
            return ContactsRead.OWNED;
        }
        if (tabVisible) {
            return ContactsRead.TAB;
        }
        if (pushedPageExists) {
            return ContactsRead.PUSHED;
        }
        return ContactsRead.NONE;
    }

    public static NewMessageContacts newMessageContacts(boolean contactsTabVisible) {
        return NewMessageContacts.PICKER;
    }

    /** {@code https://tell.cc/m#<id>}. {@code /m/<slug>} stays a message link. */
    public static String feedPostId(String url) {
        if (url == null) {
            return null;
        }
        int scheme = schemeSeparator(url);
        if (scheme < 0) {
            return null;
        }
        int start = scheme + 3;
        int cut = start;
        while (cut < url.length()) {
            char c = url.charAt(cut);
            if (c == '/' || c == '?' || c == '#') {
                break;
            }
            cut++;
        }
        if (cut == start) {
            return null;
        }
        int hash = url.indexOf('#', cut);
        if (hash < 0) {
            return null;
        }
        return feedPostId(hostOf(url.substring(start, cut)), pathOf(url.substring(cut, hash)), url.substring(hash + 1));
    }

    public static String feedPostId(String host, String path, String encodedFragment) {
        if (!postHost(host)) {
            return null;
        }
        if (!"/m".equals(path) && !"/m/".equals(path)) {
            return null;
        }
        if (encodedFragment == null) {
            return null;
        }
        String fragment = percentDecode(encodedFragment).trim();
        return fragment.isEmpty() ? null : fragment;
    }

    public static String feedPostIdIfHandled(String url, boolean navigationEnabled) {
        if (!navigationEnabled) {
            return null;
        }
        return feedPostId(url);
    }

    public static String feedPostIdIfHandled(String host, String path, String encodedFragment, boolean navigationEnabled) {
        if (!navigationEnabled) {
            return null;
        }
        return feedPostId(host, path, encodedFragment);
    }

    static boolean chinese(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        String value = code.toLowerCase(Locale.ROOT).replace('_', '-');
        return value.equals("zh") || value.startsWith("zh-");
    }

    private static boolean postHost(String host) {
        if (host == null) {
            return false;
        }
        String value = host.toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.equals("tell.cc") || value.equals("www.tell.cc");
    }

    private static int schemeSeparator(String url) {
        int limit = Math.min(url.length(), 16);
        for (int i = 0; i < limit - 2; i++) {
            if (url.charAt(i) == ':' && url.charAt(i + 1) == '/' && url.charAt(i + 2) == '/') {
                return i == 0 ? -1 : i;
            }
        }
        return -1;
    }

    private static String hostOf(String authority) {
        if (authority == null || authority.isEmpty()) {
            return null;
        }
        int at = authority.lastIndexOf('@');
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        if (hostPort.startsWith("[")) {
            int end = hostPort.indexOf(']');
            return end < 0 ? null : hostPort.substring(1, end);
        }
        int colon = hostPort.indexOf(':');
        return colon >= 0 ? hostPort.substring(0, colon) : hostPort;
    }

    private static String pathOf(String pathAndQuery) {
        if (pathAndQuery == null || pathAndQuery.isEmpty()) {
            return "";
        }
        int query = pathAndQuery.indexOf('?');
        return query >= 0 ? pathAndQuery.substring(0, query) : pathAndQuery;
    }

    static String percentDecode(String raw) {
        if (raw == null || raw.indexOf('%') < 0) {
            return raw == null ? "" : raw;
        }
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            return raw;
        }
    }
}
