package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiDiscoverLinkTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";

    @Test
    public void firstUrlSkipsEmailAndTrailingPunctuation() {
        assertEquals("https://example.com/a", TellomiDiscoverLink.firstUrl("see https://example.com/a."));
        assertEquals("https://example.com/a", TellomiDiscoverLink.firstUrl("看 https://example.com/a。"));
        assertEquals("https://example.com/a", TellomiDiscoverLink.firstUrl("(https://example.com/a)"));
        assertEquals("https://en.wikipedia.org/wiki/URL_(disambiguation)", TellomiDiscoverLink.firstUrl("https://en.wikipedia.org/wiki/URL_(disambiguation)."));
        assertEquals("https://example.com/a", TellomiDiscoverLink.firstUrl("https://example.com/a and https://other.example/b"));
        assertEquals("HTTPS://Example.COM/Path", TellomiDiscoverLink.firstUrl("HTTPS://Example.COM/Path"));
        assertNull(TellomiDiscoverLink.firstUrl("mail a@b.com"));
        assertNull(TellomiDiscoverLink.firstUrl("www.example.com"));
        assertNull(TellomiDiscoverLink.firstUrl("nothttps://example.com"));
        assertNull(TellomiDiscoverLink.firstUrl("see @https://example.com"));
        assertNull(TellomiDiscoverLink.firstUrl("https://user:pass@example.com/a"));
    }

    @Test
    public void fetchableRefusesPrivateTargets() {
        assertTrue(TellomiDiscoverLink.fetchable("https://example.com/a"));
        assertFalse(TellomiDiscoverLink.fetchable("http://127.0.0.1/secret"));
        assertFalse(TellomiDiscoverLink.fetchable("http://10.1.2.3/"));
        assertFalse(TellomiDiscoverLink.fetchable("http://192.168.0.8/a"));
        assertFalse(TellomiDiscoverLink.fetchable("http://169.254.1.1/"));
        assertFalse(TellomiDiscoverLink.fetchable("http://172.16.0.1/"));
        assertFalse(TellomiDiscoverLink.fetchable("http://100.64.0.1/"));
        assertFalse(TellomiDiscoverLink.fetchable("http://localhost/a"));
        assertFalse(TellomiDiscoverLink.fetchable("http://printer.local/a"));
        assertFalse(TellomiDiscoverLink.fetchable("https://[::1]/"));
        assertFalse(TellomiDiscoverLink.fetchable("javascript:alert(1)"));
        assertTrue(TellomiDiscoverLink.blockedLiteral("::1"));
        assertFalse(TellomiDiscoverLink.blockedLiteral("8.8.8.8"));
    }

    @Test
    public void htmlUsesOpenGraphThenFallbacks() {
        String html = "<html><head>"
            + "<meta content=\"Hello &amp; co\" property=\"og:title\">"
            + "<meta name=\"description\" content=\"plain\">"
            + "<meta property=\"og:description\" content=\"From the page\">"
            + "<meta property=\"og:image\" content=\"/cover.jpg\">"
            + "<title>Ignored</title>"
            + "</head></html>";
        TellomiDiscoverLink.Preview preview = TellomiDiscoverLink.parseHtml(html, "https://example.com/post");
        assertEquals("https://example.com/post", preview.url);
        assertEquals("Hello & co", preview.title);
        assertEquals("From the page", preview.description);
        assertEquals("https://example.com/cover.jpg", preview.image);
        assertEquals("example.com", preview.siteLine());
        assertNull(preview.siteName);

        String bare = "<title>Only title</title><meta name=\"description\" content=\"About\">";
        TellomiDiscoverLink.Preview fallback = TellomiDiscoverLink.parseHtml(bare, "https://example.com/post");
        assertEquals("Only title", fallback.title);
        assertEquals("About", fallback.description);
        assertNull(fallback.image);

        String hostile = "<meta property=\"og:image\" content=\"javascript:alert(1)\">"
            + "<meta property=\"og:image\" content=\"https://127.0.0.1/a.jpg\">"
            + "<meta property=\"og:image\" content=\"https://cdn.example/ok.jpg\">"
            + "<meta property=\"og:title\" content=\"Hi\">";
        TellomiDiscoverLink.Preview dropped = TellomiDiscoverLink.parseHtml(hostile, "https://example.com/post");
        assertEquals("Hi", dropped.title);
        assertEquals("https://cdn.example/ok.jpg", dropped.image);

        String named = "<meta property=\"og:site_name\" content=\"Example News\">"
            + "<meta property=\"og:title\" content=\"Hello\">";
        TellomiDiscoverLink.Preview site = TellomiDiscoverLink.parseHtml(named, "https://example.com/post");
        assertEquals("Example News", site.siteName);

        String protocol = "<meta property=\"og:image\" content=\"//cdn.example/a.jpg\">";
        assertEquals("https://cdn.example/a.jpg", TellomiDiscoverLink.parseHtml(protocol, "https://example.com/post").image);
        assertNull(TellomiDiscoverLink.parseHtml(html, "http://127.0.0.1/"));
    }

    @Test
    public void publishFieldsStayMinimal() {
        TellomiDiscoverLink.Preview preview = new TellomiDiscoverLink.Preview(
            "https://example.com/a", "Hello", "From the page", "https://cdn.example/pic.jpg");
        Map<String, String> fields = TellomiDiscoverLink.publishFields(preview);
        assertEquals(Arrays.asList("url", "title", "description", "image"), keys(fields));
        assertEquals("Hello", fields.get("title"));
        assertNull(TellomiDiscoverLink.publishFields(new TellomiDiscoverLink.Preview("http://10.0.0.1/x", "No", null, null)));
        Map<String, String> noImage = TellomiDiscoverLink.publishFields(new TellomiDiscoverLink.Preview(
            "https://example.com/a", "Hello", null, "https://127.0.0.1/a.jpg"));
        assertEquals(Arrays.asList("url", "title"), keys(noImage));
        Map<String, String> urlOnly = TellomiDiscoverLink.publishFields(TellomiDiscoverLink.urlOnly("https://example.com/a"));
        assertEquals(Arrays.asList("url"), keys(urlOnly));
    }

    @Test
    public void publishBodyAddsLinkPreviewWithoutChangingTheOtherKeys() {
        byte[] plain = TellomiDiscover.publishBody("see https://example.com/a", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null);
        TellomiJson without = TellomiJson.parse(plain);
        assertEquals(Arrays.asList("kind", "text", "visibility", "client_nonce"), keys(without.objectValue));
        assertFalse(without.objectValue.containsKey("link_preview"));

        TellomiDiscoverLink.Preview preview = new TellomiDiscoverLink.Preview(
            "https://example.com/a", "Hello", "From the page", "https://cdn.example/pic.jpg");
        byte[] posted = TellomiDiscover.publishBody("see https://example.com/a", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, preview);
        String json = new String(posted, StandardCharsets.UTF_8);
        assertEquals("{\"kind\":\"post\",\"text\":\"see https://example.com/a\",\"visibility\":\"public\",\"client_nonce\":\""
            + NONCE + "\",\"link_preview\":{\"url\":\"https://example.com/a\",\"title\":\"Hello\",\"description\":\"From the page\",\"image\":\"https://cdn.example/pic.jpg\"}}", json);

        byte[] refused = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null,
            new TellomiDiscoverLink.Preview("http://192.168.0.1/", "Lan", null, null));
        assertFalse(new String(refused, StandardCharsets.UTF_8).contains("link_preview"));
    }

    @Test
    public void cardsReadStoredPreviewAndIgnoreAMissingOne() {
        String json = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"card\":{\"text\":\"see https://example.com/a\","
            + "\"link_preview\":{\"url\":\"https://example.com/a\",\"title\":\"Hello\",\"description\":\"From the page\","
            + "\"image\":\"https://cdn.example/pic.jpg\",\"site_name\":\"Example\"}}}]}";
        TellomiDiscover.Card card = TellomiDiscoverParse.cards(json.getBytes(StandardCharsets.UTF_8)).get(0);
        assertEquals("Hello", card.linkPreview.title);
        assertEquals("https://cdn.example/pic.jpg", card.linkPreview.image);
        assertEquals("example.com", card.linkPreview.siteLine());
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(card);
        assertEquals("Hello", post.linkPreview.title);

        String beside = "{\"cards\":[{\"id\":\"post-2\",\"state\":\"ok\",\"link_preview\":{\"url\":\"https://example.com/b\",\"title\":\"Side\"},\"card\":{\"text\":\"x\"}}]}";
        assertEquals("Side", TellomiDiscoverParse.cards(beside.getBytes(StandardCharsets.UTF_8)).get(0).linkPreview.title);

        String dropped = "{\"cards\":[{\"id\":\"post-3\",\"state\":\"ok\",\"card\":{\"text\":\"x\",\"link_preview\":{\"url\":\"https://example.com/c\",\"title\":\"Keep\",\"image\":\"http://10.0.0.5/a.jpg\"}}}]}";
        TellomiDiscover.Card kept = TellomiDiscoverParse.cards(dropped.getBytes(StandardCharsets.UTF_8)).get(0);
        assertEquals("Keep", kept.linkPreview.title);
        assertNull(kept.linkPreview.image);

        String absent = "{\"cards\":[{\"id\":\"post-4\",\"state\":\"ok\",\"card\":{\"text\":\"x\",\"source_url\":\"\"}}]}";
        assertNull(TellomiDiscoverParse.cards(absent.getBytes(StandardCharsets.UTF_8)).get(0).linkPreview);

        String junk = "{\"cards\":[{\"id\":\"post-5\",\"state\":\"ok\",\"card\":{\"text\":\"x\",\"link_preview\":\"nope\"}}]}";
        assertNull(TellomiDiscoverParse.cards(junk.getBytes(StandardCharsets.UTF_8)).get(0).linkPreview);

        String hidden = "{\"cards\":[{\"id\":\"post-6\",\"state\":\"ok\",\"card\":{\"text\":\"x\",\"link_preview\":{\"url\":\"http://127.0.0.1/a\",\"title\":\"No\"}}}]}";
        assertNull(TellomiDiscoverParse.cards(hidden.getBytes(StandardCharsets.UTF_8)).get(0).linkPreview);
    }

    @Test
    public void charsetComesFromTheHeaderOrTheMetaTag() {
        assertEquals("GBK", TellomiDiscoverLink.charsetName(new byte[0], "text/html; charset=gbk"));
        byte[] meta = "<meta charset=\"gb18030\">".getBytes(StandardCharsets.US_ASCII);
        assertEquals("GB18030", TellomiDiscoverLink.charsetName(meta, "text/html"));
        assertEquals("GB18030", TellomiDiscoverLink.charsetName(meta, null));
        assertEquals("UTF-8", TellomiDiscoverLink.charsetName(new byte[] {'x'}, "text/plain"));
    }

    private static java.util.List<String> keys(Map<String, ?> map) {
        java.util.List<String> names = new java.util.ArrayList<>();
        Iterator<? extends Map.Entry<String, ?>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            names.add(String.valueOf(iterator.next().getKey()));
        }
        return names;
    }
}
