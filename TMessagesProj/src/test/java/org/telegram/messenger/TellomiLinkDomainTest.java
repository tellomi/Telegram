package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Generated short links use tell.cc. Website links use pages that exist on
 * tellomi.cn. t.me, telegram.me, and telegram.dog still count as internal.
 */
public class TellomiLinkDomainTest {

    private static final String HOST = "tell.cc";

    @Test
    public void shareLinksKeepTheTelegramPathOnTellCc() {
        assertEquals("https://tell.cc/durov", TellomiLinks.https("t.me", "durov"));
        assertEquals("https://tell.cc/+invite", TellomiLinks.https("telegram.me", "+invite"));
        assertEquals("https://tell.cc/joinchat/hash", TellomiLinks.https("telegram.dog", "joinchat/hash"));
        assertEquals("https://tell.cc/addstickers/pack", TellomiLinks.https(null, "addstickers/pack"));
        assertEquals("https://tell.cc/addemoji/pack", TellomiLinks.https("https://t.me/", "/addemoji/pack"));
        assertEquals("https://tell.cc/c/123456/7", TellomiLinks.https("t.me", "c/123456/7"));
        assertEquals("https://tell.cc/share/url?url=https%3A%2F%2Fexample.com",
                TellomiLinks.https("t.me", "share/url?url=https%3A%2F%2Fexample.com"));
        assertEquals("https://tell.cc/proxy?server=10.0.0.1&port=443&secret=ee",
                TellomiLinks.https("telegram.me", "proxy?server=10.0.0.1&port=443&secret=ee"));
        assertEquals("https://tell.cc/socks?server=10.0.0.1&port=1080",
                TellomiLinks.https("telegram.dog", "socks?server=10.0.0.1&port=1080"));
        assertEquals("https://tell.cc/boost/channel", TellomiLinks.https(null, "boost/channel"));
        assertEquals("https://tell.cc/addlist/folder", TellomiLinks.https("t.me", "addlist/folder"));
        assertEquals("https://tell.cc/name/s/7", TellomiLinks.https("https://t.me/", "name/s/7"));
        assertEquals("https://tell.cc/bg/slug", TellomiLinks.https("t.me", "bg/slug"));
        assertEquals("https://tell.cc/nft/gift", TellomiLinks.https("t.me", "nft/gift"));
        assertFalse(TellomiLinks.https("https://t.me/durov", "durov").contains("t.me"));
        assertFalse(TellomiLinks.https("telegram.dog", "name").contains("telegram"));
    }

    @Test
    public void legacyShortLinksStillResolveAsInternalHosts() {
        assertTrue(TellomiLinks.isLinkHost(HOST, HOST));
        assertTrue(TellomiLinks.isLinkHost("t.me", HOST));
        assertTrue(TellomiLinks.isLinkHost("telegram.me", HOST));
        assertTrue(TellomiLinks.isLinkHost("telegram.dog", HOST));
        assertFalse(TellomiLinks.isLinkHost("telegram.org", HOST));
        assertFalse(TellomiLinks.isLinkHost("tellomi.cn", HOST));
        assertEquals("durov", TellomiLinks.pathAfterKnownHost("https://tell.cc/durov", HOST));
        assertEquals("durov", TellomiLinks.pathAfterKnownHost("https://t.me/durov", HOST));
        assertEquals("+invite", TellomiLinks.pathAfterKnownHost("https://telegram.me/+invite", HOST));
        assertEquals("joinchat/hash", TellomiLinks.pathAfterKnownHost("http://telegram.dog/joinchat/hash", HOST));
        assertEquals("proxy?server=1", TellomiLinks.pathAfterKnownHost("https://t.me/proxy?server=1", HOST));
        assertEquals("socks?server=1", TellomiLinks.pathAfterKnownHost("https://tell.cc/socks?server=1", HOST));
        assertTrue(TellomiLinks.isBusinessMessageLink("https://tell.cc/m/slug", "slug", HOST));
        assertTrue(TellomiLinks.isBusinessMessageLink("https://t.me/m/slug", "slug", HOST));
    }

    @Test
    public void displayedStringsRewriteWebsiteLinksToTellomiCnAndShortLinksToTellCc() {
        assertEquals("https://tell.cc/durov", TellomiBrand.rewrite("https://t.me/durov"));
        assertEquals("https://tell.cc/joinchat/hash", TellomiBrand.rewrite("https://telegram.me/joinchat/hash"));
        assertEquals("https://tell.cc/addstickers/pack", TellomiBrand.rewrite("https://telegram.dog/addstickers/pack"));
        assertEquals("https://tellomi.cn/help", TellomiBrand.rewrite("https://telegram.org/faq#secret-chats"));
        assertEquals("https://tellomi.cn/legal/privacy/", TellomiBrand.rewrite("https://telegram.org/privacy#5-6-no-ads-based-on-user-data"));
        assertEquals("https://tellomi.cn/legal/terms/", TellomiBrand.rewrite("https://telegram.org/tos/mini-apps"));
        assertEquals("https://tellomi.cn/changelog/", TellomiBrand.rewrite("https://telegram.org/blog/telegram-stars"));
        assertEquals("https://tellomi.cn/account/delete/", TellomiBrand.rewrite("https://telegram.org/deactivate?phone=1"));
        assertEquals("https://tellomi.cn/", TellomiBrand.rewrite("https://telegram.org"));
        assertEquals("https://tellomi.cn/download/", TellomiBrand.rewrite("https://getdesktop.telegram.org/"));
        assertEquals("https://tellomi.cn/download/", TellomiBrand.rewrite("https://desktop.telegram.org/"));
        assertEquals("https://tellomi.cn/help/start/link-desktop/", TellomiBrand.rewrite("https://web.telegram.org/"));
        assertEquals("https://tellomi.cn/legal/community/", TellomiBrand.rewrite("https://promote.telegram.org/guidelines"));
        assertEquals("https://tellomi.cn/legal/community/", TellomiBrand.rewrite("https://ads.telegram.org"));
        assertEquals("https://tellomi.cn/help", TellomiBrand.rewrite("https://core.telegram.org/bots/webapps#launching-the-main-mini-app"));
        assertEquals("https://tellomi.cn/help", TellomiBrand.rewrite("https://translations.telegram.org/en/emoji"));
        assertEquals("Learn more at tellomi.cn", TellomiBrand.rewrite("Learn more at telegram.org"));
        assertEquals("support@tellomi.app", TellomiBrand.rewrite("sms@telegram.org"));
        assertEquals("support@tellomi.app", TellomiBrand.rewrite("recover@telegram.org"));
        assertTrue(TellomiBrand.rewrite("tg://resolve?domain=durov").startsWith("tg:"));
    }

    @Test
    public void recognitionAndManifestKeepLegacyHostsAndTellCc() throws Exception {
        String browser = read("src/main/java/org/telegram/messenger/browser/Browser.java");
        String launch = read("src/main/java/org/telegram/ui/LaunchActivity.java");
        String messages = read("src/main/java/org/telegram/messenger/MessagesController.java");
        String manifest = read("src/main/AndroidManifest.xml");

        assertTrue(browser.contains("BuildVars.isLinkHost(host)"));
        assertTrue(browser.contains("\"telegram.dog\".equals(host)"));
        assertTrue(browser.contains("t\\\\.me|telegram\\\\.me|telegram\\\\.dog"));
        assertTrue(launch.contains("Pattern.quote(BuildVars.LINK_HOST) + \"|t\\\\.me"));
        assertTrue(launch.contains("tg://telegram.org"));
        assertTrue(messages.contains("BuildVars.normalizeLinkPrefix(mainPreferences.getString(\"linkPrefix\", BuildVars.LINK_HOST))"));
        assertTrue(messages.contains("linkPrefix = BuildVars.normalizeLinkPrefix(config.me_url_prefix)"));
        assertTrue(manifest.contains("android:scheme=\"https\" android:host=\"tell.cc\""));
        assertTrue(manifest.contains("android:scheme=\"http\" android:host=\"tell.cc\""));
        assertTrue(manifest.contains("android:autoVerify=\"true\""));
        assertTrue(manifest.contains("android:host=\"t.me\""));
        assertTrue(manifest.contains("android:host=\"telegram.me\""));
        assertTrue(manifest.contains("android:host=\"telegram.dog\""));
    }

    @Test
    public void userFacingSourcesDoNotKeepTelegramHttpLinks() throws Exception {
        Pattern url = Pattern.compile("https?://(?:[\\w.-]+\\.)?(?:t\\.me|telegram\\.me|telegram\\.dog|telegram\\.org)(?![A-Za-z])");
        Path root = repoRoot().resolve("TMessagesProj/src/main");
        StringBuilder leftover = new StringBuilder();
        Files.walk(root).filter(path -> {
            String full = path.toString().replace('\\', '/');
            if (full.contains("/jni/")) {
                return false;
            }
            String name = path.getFileName().toString();
            return Files.isRegularFile(path) && (name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".kt"));
        }).forEach(path -> {
            String rel = root.relativize(path).toString().replace('\\', '/');
            try {
                scan(rel, new String(Files.readAllBytes(path), StandardCharsets.UTF_8), url, leftover);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(leftover.toString(), "", leftover.toString());
    }

    private static void scan(String rel, String text, Pattern url, StringBuilder leftover) {
        boolean inBlock = false;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String code = line;
            if (inBlock) {
                int end = code.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                inBlock = false;
                code = code.substring(end + 2);
            }
            int block = code.indexOf("/*");
            if (block >= 0) {
                int end = code.indexOf("*/", block + 2);
                if (end < 0) {
                    inBlock = true;
                    code = code.substring(0, block);
                } else {
                    code = code.substring(0, block) + code.substring(end + 2);
                }
            }
            int slash = code.indexOf("//");
            if (slash >= 0) {
                code = code.substring(0, slash);
            }
            Matcher matcher = url.matcher(code);
            while (matcher.find()) {
                String found = matcher.group();
                if (isKeptUrl(rel, found)) {
                    continue;
                }
                leftover.append(rel).append(':').append(i + 1).append(' ').append(found).append('\n');
            }
        }
    }

    /** Embed origins and the wearable login request stay. They are not links the user shares. */
    private static boolean isKeptUrl(String rel, String found) {
        if (found.startsWith("https://messenger.telegram.org") || found.startsWith("http://messenger.telegram.org")) {
            return rel.equals("java/org/telegram/ui/Components/EmbedBottomSheet.java")
                    || rel.equals("java/org/telegram/ui/Components/PhotoViewerWebView.java");
        }
        if ("https://telegram.org/embed".equals(found)) {
            return rel.equals("java/org/telegram/ui/ArticleViewer.java");
        }
        if (found.startsWith("https://web.telegram.org") || found.startsWith("http://web.telegram.org")) {
            return rel.equals("java/org/telegram/ui/WearAuthSheet.java");
        }
        return false;
    }

    private static String read(String relative) throws Exception {
        Path[] candidates = new Path[]{
                Paths.get("TMessagesProj").resolve(relative),
                Paths.get(relative)
        };
        for (int i = 0; i < candidates.length; i++) {
            if (Files.isRegularFile(candidates[i])) {
                return new String(Files.readAllBytes(candidates[i]), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("missing " + relative);
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
}
