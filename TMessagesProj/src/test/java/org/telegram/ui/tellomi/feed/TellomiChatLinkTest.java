package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiChatLinkTest {

    @Test
    public void internalHostsStayOnTheServer() {
        assertTrue(TellomiChatLink.internalHost("tell.cc", "tell.cc"));
        assertTrue(TellomiChatLink.internalHost("www.tell.cc", "tell.cc"));
        assertTrue(TellomiChatLink.internalHost("t.me", "tell.cc"));
        assertTrue(TellomiChatLink.internalHost("telegram.me", "tell.cc"));
        assertTrue(TellomiChatLink.internalHost("Telegram.DOG", "tell.cc"));
        assertTrue(TellomiChatLink.internalHost("tell.cc.", "tell.cc"));
        assertFalse(TellomiChatLink.internalHost("example.com", "tell.cc"));
        assertFalse(TellomiChatLink.internalHost("cdn.tell.cc", "tell.cc"));
        assertFalse(TellomiChatLink.internalHost("www.t.me", "tell.cc"));
    }

    @Test
    public void externalPagesAreFetchedOnThePhone() {
        assertTrue(TellomiChatLink.clientFetch("https://example.com/a", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("https://tell.cc/addstickers/erelpack", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("https://t.me/s/somechannel/12", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("https://telegram.me/joinchat/abc", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("http://127.0.0.1/secret", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("http://10.1.2.3/", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("http://192.168.1.9/a", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("http://169.254.1.1/", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("http://localhost/a", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("https://[::1]/", "tell.cc"));
        assertFalse(TellomiChatLink.clientFetch("javascript:alert(1)", "tell.cc"));
    }

    @Test
    public void unverifiedAuthorIsNotShown() {
        assertTrue(TellomiChatLink.hideAuthor("unverified"));
        assertTrue(TellomiChatLink.hideAuthor(" Unverified "));
        assertFalse(TellomiChatLink.hideAuthor("Ada"));
        assertFalse(TellomiChatLink.hideAuthor(null));
    }

    @Test
    public void textIsClampedToTheServerLimits() {
        String longTitle = repeat('测', 200);
        assertEquals(TellomiChatLink.TITLE_RUNES, TellomiChatLink.clampRunes(longTitle, TellomiChatLink.TITLE_RUNES).codePointCount(0, TellomiChatLink.clampRunes(longTitle, TellomiChatLink.TITLE_RUNES).length()));
        assertEquals("Hello", TellomiChatLink.clampRunes(" Hello ", 128));
        assertNull(TellomiChatLink.clampRunes("   ", 128));
    }

    @Test
    public void openGraphSiteNameIsKeptForTheCard() {
        String html = "<meta property=\"og:site_name\" content=\"Example News\">"
            + "<meta property=\"og:title\" content=\"Hello\">"
            + "<meta property=\"og:description\" content=\"About the page\">"
            + "<meta property=\"og:image\" content=\"https://cdn.example/a.jpg\">";
        TellomiDiscoverLink.Preview preview = TellomiDiscoverLink.parseHtml(html, "https://example.com/post");
        assertEquals("Example News", preview.siteName);
        assertEquals("Hello", preview.title);
        assertEquals(TellomiChatLink.SITE_RUNES, TellomiChatLink.clampRunes(repeat('站', 80), TellomiChatLink.SITE_RUNES).codePointCount(0, TellomiChatLink.clampRunes(repeat('站', 80), TellomiChatLink.SITE_RUNES).length()));
    }

    @Test
    public void extensionFlagsMatchTheServerBits() {
        assertEquals(0, TellomiChatLink.extensionFlags(null, "", null, false));
        assertEquals(TellomiChatLink.FLAG_TITLE | TellomiChatLink.FLAG_DESCRIPTION | TellomiChatLink.FLAG_SITE | TellomiChatLink.FLAG_PHOTO,
            TellomiChatLink.extensionFlags("Title", "Body", "Example", true));
        assertEquals(1 << 3, TellomiChatLink.FLAG_TITLE);
        assertEquals(1 << 4, TellomiChatLink.FLAG_DESCRIPTION);
        assertEquals(1 << 5, TellomiChatLink.FLAG_SITE);
        assertEquals(1 << 6, TellomiChatLink.FLAG_PHOTO);
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
