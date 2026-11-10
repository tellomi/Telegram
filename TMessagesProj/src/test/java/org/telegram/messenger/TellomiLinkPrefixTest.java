package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Generated short links use tell.cc. A server me_url_prefix is kept only when
 * it is already a Tellomi host. t.me, telegram.me, and telegram.dog still parse.
 */
public class TellomiLinkPrefixTest {

    @Test
    public void normalizeLinkPrefixRewritesTelegramHosts() {
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix(null));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix(""));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("   "));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("t.me"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("T.ME"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("https://t.me/"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("http://t.me"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("telegram.me/"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("https://telegram.dog"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("https://tell.cc/"));
        assertEquals("tell.cc", TellomiLinks.normalizeLinkPrefix("tell.cc"));
    }

    @Test
    public void normalizeLinkPrefixKeepsATellomiHostFromTheServer() {
        assertEquals("links.tellomi.cn", TellomiLinks.normalizeLinkPrefix("https://links.tellomi.cn/"));
        assertEquals("links.tellomi.cn", TellomiLinks.normalizeLinkPrefix("http://links.tellomi.cn"));
        assertEquals("share.tellomi.cn", TellomiLinks.normalizeLinkPrefix("share.tellomi.cn/"));
    }

    @Test
    public void builtLinksUseTellCcOrTheServerHost() {
        assertEquals("https://tell.cc/durov", TellomiLinks.https("t.me", "durov"));
        assertEquals("https://tell.cc/durov/42", TellomiLinks.https("https://t.me/", "durov/42"));
        assertEquals("https://tell.cc/durov/42?t=1", TellomiLinks.https(null, "durov/42?t=1"));
        assertEquals("https://tell.cc/addstickers/pack", TellomiLinks.https("telegram.me", "addstickers/pack"));
        assertEquals("https://tell.cc/addemoji/pack", TellomiLinks.https("telegram.dog", "/addemoji/pack"));
        assertEquals("https://tell.cc/+15551212", TellomiLinks.https("t.me", "+15551212"));
        assertEquals("https://tell.cc/name/s/7", TellomiLinks.https("https://t.me/", "name/s/7"));
        assertEquals("https://tell.cc/boost/channel", TellomiLinks.https("t.me", "boost/channel"));
        assertEquals("https://tell.cc/boost/?c=5", TellomiLinks.https(null, "boost/?c=5"));
        assertEquals("https://links.tellomi.cn/durov", TellomiLinks.https("https://links.tellomi.cn/", "durov"));
        assertEquals("https://tell.cc/", TellomiLinks.https("t.me", ""));
        assertFalse(TellomiLinks.https("https://t.me/", "name").contains("t.me"));
        assertFalse(TellomiLinks.https("telegram.dog", "name").contains("telegram"));
    }

    @Test
    public void inviteTitlesAcceptLegacyAndTellomiHosts() {
        assertEquals("tell.cc/+AbCd", TellomiLinks.invitePlusTitle("t.me", "tell.cc", "https://t.me/+AbCd"));
        assertEquals("tell.cc/+AbCd", TellomiLinks.invitePlusTitle("https://t.me/", "tell.cc", "https://tell.cc/+AbCd"));
        assertEquals("tell.cc/+AbCd", TellomiLinks.invitePlusTitle(null, "tell.cc", "http://telegram.me/+AbCd"));
        assertEquals("links.tellomi.cn/+AbCd", TellomiLinks.invitePlusTitle("links.tellomi.cn", "tell.cc", "https://t.me/+AbCd"));
        assertEquals("hash", TellomiLinks.inviteJoinchatTitle("tell.cc", "https://t.me/joinchat/hash"));
        assertEquals("hash", TellomiLinks.inviteJoinchatTitle("tell.cc", "https://tell.cc/joinchat/hash"));
        assertNull(TellomiLinks.invitePlusTitle("tell.cc", "tell.cc", "https://example.com/+AbCd"));
        assertTrue(TellomiLinks.isPublicChatInvite("https://t.me/+PublicChat", "tell.cc"));
        assertTrue(TellomiLinks.isPublicChatInvite("https://tell.cc/+PublicChat", "tell.cc"));
        assertFalse(TellomiLinks.isPublicChatInvite("https://t.me/+Other", "tell.cc"));
    }

    @Test
    public void legacyHostsStillParse() {
        assertTrue(TellomiLinks.isLegacyLinkHost("t.me"));
        assertTrue(TellomiLinks.isLegacyLinkHost("telegram.me"));
        assertTrue(TellomiLinks.isLegacyLinkHost("telegram.dog"));
        assertTrue(TellomiLinks.isLinkHost("tell.cc", "tell.cc"));
        assertTrue(TellomiLinks.isLinkHost("t.me", "tell.cc"));
        assertFalse(TellomiLinks.isLinkHost("example.com", "tell.cc"));
        assertEquals("durov", TellomiLinks.pathAfterKnownHost("https://t.me/durov", "tell.cc"));
        assertEquals("durov", TellomiLinks.pathAfterKnownHost("https://tell.cc/durov", "tell.cc"));
        assertEquals("durov", TellomiLinks.pathAfterKnownHost("t.me/durov", "tell.cc"));
        assertEquals("durov/2", TellomiLinks.pathAfterKnownHost("http://telegram.dog/durov/2", "tell.cc"));
        assertTrue(TellomiLinks.isMentionLink("https://t.me/durov", "@durov", "tell.cc"));
        assertTrue(TellomiLinks.isMentionLink("https://tell.cc/durov", "@durov", "tell.cc"));
        assertFalse(TellomiLinks.isMentionLink("https://example.com/durov", "@durov", "tell.cc"));
        assertTrue(TellomiLinks.containsInstantView("https://t.me/iv?url=1", "tell.cc"));
        assertTrue(TellomiLinks.containsInstantView("https://tell.cc/iv?url=1", "tell.cc"));
        assertTrue(TellomiLinks.isBusinessMessageLink("https://t.me/m/slug", "slug", "tell.cc"));
        assertTrue(TellomiLinks.isBusinessMessageLink("https://tell.cc/m/slug", "slug", "tell.cc"));
    }

    @Test
    public void clientWiresTheHostThroughBuildVarsAndTheManifest() throws Exception {
        String vars = read("src/main/java/org/telegram/messenger/BuildVars.java");
        String messages = read("src/main/java/org/telegram/messenger/MessagesController.java");
        String brand = read("src/main/java/org/telegram/messenger/TellomiBrand.java");
        String gradle = read("build.gradle");
        String props = read("../gradle.properties");
        String manifest = read("src/main/AndroidManifest.xml");

        assertTrue(vars.contains("LINK_HOST = BuildConfig.TELLOMI_LINK_HOST"));
        assertTrue(vars.contains("LINK_BASE = \"https://\" + LINK_HOST + \"/\""));
        assertTrue(vars.contains("normalizeLinkPrefix"));
        assertTrue(vars.contains("shareLink"));
        assertTrue(messages.contains("BuildVars.normalizeLinkPrefix(mainPreferences.getString(\"linkPrefix\", BuildVars.LINK_HOST))"));
        assertTrue(messages.contains("linkPrefix = BuildVars.normalizeLinkPrefix(config.me_url_prefix)"));
        assertTrue(brand.contains("replaceHost(value, \"t.me\", host)"));
        assertTrue(gradle.contains("TELLOMI_LINK_HOST"));
        assertTrue(props.contains("TELLOMI_LINK_HOST=tell.cc"));
        assertTrue(manifest.contains("android:scheme=\"https\" android:host=\"tell.cc\""));
        assertTrue(manifest.contains("android:scheme=\"http\" android:host=\"tell.cc\""));
        assertFalse(manifest.contains("android:host=\"tell.cc\" android:pathPrefix=\"/m\""));
        assertTrue(manifest.contains("android:host=\"t.me\""));
        assertTrue(manifest.contains("android:host=\"telegram.me\""));
        assertTrue(manifest.contains("android:host=\"telegram.dog\""));
        assertTrue(manifest.contains("android:scheme=\"tellomi\""));
    }

    private static String read(String relative) throws Exception {
        Path[] candidates = new Path[]{
                Paths.get("TMessagesProj").resolve(relative),
                Paths.get(relative),
                Paths.get("..").resolve(relative)
        };
        for (int i = 0; i < candidates.length; i++) {
            if (Files.isRegularFile(candidates[i])) {
                return new String(Files.readAllBytes(candidates[i]), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
