package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Built-in string values use Tellomi. Resource names, hosts, and tg:// stay.
 */
public class TellomiDefaultStringsTest {

    private static final Pattern STRING = Pattern.compile(
            "<string\\b([^>]*)>(.*?)</string>", Pattern.DOTALL);
    private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern URL = Pattern.compile(
            "(?i)(?:https?://[^\\s'\"<>)\\]]+|tg://[^\\s'\"<>)\\]]+|(?:[a-z0-9-]+\\.)*telegram\\.(?:org|me|dog)(?:/[^\\s'\"<>)\\]]*)?|\\bt\\.me(?:/[^\\s'\"<>)\\]]*)?)");
    private static final String[] BRAND = new String[]{
            "Telegram",
            "TELEGRAM",
            "تيليجرام",
            "텔레그램",
            "टेलीग्राम",
            "电报",
            "電報"
    };
    private static final String[] SHORTCUT_KEYS = new String[]{
            "NewConversationShortcut",
            "ContactShortcutMessage",
            "ContactShortcutVoiceCall",
            "ContactShortcutVideoCall"
    };

    @Test
    public void builtInStringValuesUseTellomi() throws Exception {
        List<Path> files = new ArrayList<>();
        files.add(source("src/main/res/values/strings.xml"));
        files.add(source("src/main/res/values-ar/strings.xml"));
        files.add(source("src/main/res/values-de/strings.xml"));
        files.add(source("src/main/res/values-es/strings.xml"));
        files.add(source("src/main/res/values-it/strings.xml"));
        files.add(source("src/main/res/values-ko/strings.xml"));
        files.add(source("src/main/res/values-nl/strings.xml"));
        files.add(source("src/main/res/values-pt-rBR/strings.xml"));
        files.add(source("src/main/res/values-ru/strings.xml"));
        files.add(source("src/main/res/values-uk/strings.xml"));
        files.add(source("src/main/res/values-zh-rCN/strings.xml"));
        files.add(source("src/main/res/values-zh-rTW/strings.xml"));
        files.add(standalone("src/main/res/values/strings.xml"));

        for (Path file : files) {
            String xml = read(file);
            Matcher matcher = STRING.matcher(xml);
            boolean sawString = false;
            while (matcher.find()) {
                sawString = true;
                String attrs = matcher.group(1);
                String value = matcher.group(2);
                Matcher name = NAME.matcher(attrs);
                assertTrue(file + " string is missing a name", name.find());
                String visible = URL.matcher(value).replaceAll(" ");
                for (String token : BRAND) {
                    assertFalse(file + " " + name.group(1) + " still shows " + token,
                            visible.contains(token));
                }
            }
            assertTrue(file + " has no strings", sawString);
        }
    }

    @Test
    public void resourceNamesAndLinkHostsStay() throws Exception {
        String english = read(source("src/main/res/values/strings.xml"));
        assertTrue(english.contains("name=\"TelegramFAQ\""));
        assertTrue(english.contains("name=\"TelegramVersion\""));
        assertTrue(english.contains("https://tellomi.cn/help"));
        assertTrue(english.contains("https://tellomi.cn/legal/privacy/"));
        assertTrue(english.contains("https://tellomi.cn/legal/terms/"));
        assertTrue(english.contains("https://tellomi.cn/download/"));
        assertFalse(english.contains("https://telegram.org"));
        assertFalse(english.contains("https://t.me"));
        assertFalse(english.contains("telegram.me"));
        assertFalse(english.contains("telegram.dog"));
        assertTrue(english.contains("https://tell.cc/TelegramTips"));
        assertTrue(english.contains("sms@telegram.org"));
        assertTrue(english.contains("*tellomi.cn/download*"));
        assertFalse(english.contains("getdesktop.telegram.org"));
        assertFalse(english.contains("desktop.telegram.org"));
        assertFalse(english.contains("web.telegram.org"));
        assertTrue(english.contains(">Tellomi FAQ<"));
        assertTrue(english.contains(">INVITE TO TELLOMI<"));
        assertTrue(english.contains(">Tellomi<"));
    }

    @Test
    public void chineseShortcutLabelsExist() throws Exception {
        String simplified = read(source("src/main/res/values-zh-rCN/strings.xml"));
        String traditional = read(source("src/main/res/values-zh-rTW/strings.xml"));
        assertTrue(simplified.contains(">新对话<"));
        assertTrue(simplified.contains(">发消息给 %1$s<"));
        assertTrue(simplified.contains(">语音通话 %1$s<"));
        assertTrue(simplified.contains(">视频通话 %1$s<"));
        assertTrue(traditional.contains(">新對話<"));
        assertTrue(traditional.contains(">傳訊息給 %1$s<"));
        assertTrue(traditional.contains(">語音通話 %1$s<"));
        assertTrue(traditional.contains(">視訊通話 %1$s<"));
        for (String key : SHORTCUT_KEYS) {
            assertTrue(simplified.contains("name=\"" + key + "\""));
            assertTrue(traditional.contains("name=\"" + key + "\""));
        }
        assertTrue(simplified.contains("name=\"FeedTab\">动态<"));
    }

    @Test
    public void shortcutsKeepSchemeAndHaveNoBrandWord() throws Exception {
        String shortcuts = read(source("src/main/res/xml/shortcuts.xml"));
        assertTrue(shortcuts.contains("tg://search"));
        assertTrue(shortcuts.contains("tg://call"));
        assertTrue(shortcuts.contains("tg://settings"));
        assertTrue(shortcuts.contains("tg://scanqr"));
        assertTrue(shortcuts.contains("tg://calllog"));
        assertTrue(shortcuts.contains("org.telegram.ui.LaunchActivity"));
        assertTrue(shortcuts.contains("org.telegram.messenger.SHORTCUT_SHARE"));
        assertTrue(shortcuts.contains("actions.intent.GET_THING"));
        assertTrue(shortcuts.contains("actions.intent.CREATE_CALL"));
        assertTrue(shortcuts.contains("actions.intent.GET_ACCOUNT"));
        assertTrue(shortcuts.contains("actions.intent.GET_BARCODE"));
        assertTrue(shortcuts.contains("actions.intent.GET_CALL"));
        assertFalse(shortcuts.contains("Telegram"));
        assertFalse(shortcuts.contains("Tellomi"));
    }

    @Test
    public void hardcodedVisibleCopyUsesTellomi() throws Exception {
        String preview = read(source("src/main/java/org/telegram/ui/ThemePreviewActivity.java"));
        String contact = read(source("src/main/java/org/telegram/ui/NewContactBottomSheet.java"));
        String login = read(source("src/main/java/org/telegram/ui/LoginActivity.java"));
        String bulletin = read(source("src/main/java/org/telegram/ui/Components/MessagePreviewView.java"));

        assertTrue(preview.contains("\"Tellomi Beta Chat\""));
        assertFalse(preview.contains("\"Telegram Beta Chat\""));
        assertTrue(contact.contains("This phone number is not on Tellomi."));
        assertTrue(contact.contains("This phone number is on Tellomi."));
        assertFalse(contact.contains("on Telegram"));
        assertTrue(contact.contains("https://tellomi.cn/download/"));
        assertFalse(contact.contains("telegram.org/dl"));
        assertFalse(contact.contains("telegram.org/download"));
        String invites = read(source("src/main/java/org/telegram/messenger/ContactsController.java"));
        assertTrue(invites.contains("https://tellomi.cn/download/"));
        assertFalse(invites.contains("telegram.org/dl"));
        assertFalse(invites.contains("telegram.org/download"));
        assertTrue(login.contains("But Tellomi says it's banned."));
        assertTrue(login.contains("But Tellomi says it's invalid."));
        assertFalse(login.contains("But Telegram says"));
        assertTrue(login.contains("recover@telegram.org"));
        assertTrue(login.contains("login@stel.com"));
        assertTrue(bulletin.contains("Subscribe to **Tellomi Premium**"));
        assertFalse(bulletin.contains("Subscribe to **Telegram Premium**"));
    }

    @Test
    public void manifestLabelsAndCallPreviewUseTellomi() throws Exception {
        String release = read(source("config/release/AndroidManifest.xml"));
        String debug = read(source("config/debug/AndroidManifest.xml"));
        assertTrue(release.contains("android:label=\"@string/AppName\""));
        assertTrue(debug.contains("android:label=\"@string/AppNameBeta\""));
        assertFalse(release.contains(">Telegram<"));
        String call = read(source("src/main/res/layout/call_notification.xml"));
        String callRtl = read(source("src/main/res/layout/call_notification_rtl.xml"));
        assertTrue(call.contains("tools:text=\"Tellomi Call\""));
        assertTrue(callRtl.contains("tools:text=\"Tellomi Call\""));
        assertFalse(call.contains("Telegram Call"));
    }

    private static Path source(String relative) {
        return repoRoot().resolve("TMessagesProj").resolve(relative);
    }

    private static Path standalone(String relative) {
        return repoRoot().resolve("TMessagesProj_AppStandalone").resolve(relative);
    }

    /** Unit tests run from the module directory or the repo root. */
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
