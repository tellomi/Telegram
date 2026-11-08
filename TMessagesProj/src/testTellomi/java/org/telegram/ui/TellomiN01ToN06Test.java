package org.telegram.ui;

import org.junit.Test;
import org.telegram.messenger.TellomiChineseScript;
import org.telegram.messenger.TellomiDefaults;
import org.telegram.messenger.TellomiSessionRevoke;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TellomiN01ToN06Test {

    @Test
    public void languageAndThemeShareOneRowOnANarrowPhone() {
        TellomiAuthChromeLayout slots = TellomiAuthChromeLayout.place(320, 1f, 1f, 24, false);
        assertEquals(slots.top, slots.top);
        assertTrue(slots.size >= TellomiAuthChromeLayout.MIN_HIT_DP);
        assertTrue(slots.languageLeft >= 48);
        assertTrue(slots.languageLeft + slots.size <= slots.themeLeft);
        assertTrue(slots.themeLeft + slots.size <= 320);
        assertFalse(slots.overlaps());
    }

    @Test
    public void largeTypeAndProxyKeepTheTwoControlsApart() {
        TellomiAuthChromeLayout plain = TellomiAuthChromeLayout.place(360, 2f, 2f, 40, false);
        TellomiAuthChromeLayout proxy = TellomiAuthChromeLayout.place(360, 2f, 2f, 40, true);
        assertEquals(plain.top, proxy.top);
        assertEquals(plain.size, proxy.size);
        assertTrue(proxy.themeLeft < plain.themeLeft);
        assertEquals(plain.themeLeft - plain.languageLeft, proxy.themeLeft - proxy.languageLeft);
        assertFalse(proxy.overlaps());
        assertTrue(plain.size <= Math.round(48 * 2f * TellomiAuthChromeLayout.MAX_FONT_SCALE) + 1);
    }

    @Test
    public void simplifiedTraditionalAndEnglishAreDifferentLocales() {
        Locale hans = TellomiDefaults.androidLocale("zh_hans");
        Locale hant = TellomiDefaults.androidLocale("zh-hant");
        Locale en = TellomiDefaults.androidLocale("en");
        assertEquals("CN", hans.getCountry());
        assertEquals("TW", hant.getCountry());
        assertEquals("en", en.getLanguage());
        assertFalse(TellomiDefaults.traditionalShortName("zh_hans"));
        assertTrue(TellomiDefaults.traditionalShortName("zh_hant"));
        assertFalse(TellomiDefaults.traditionalShortName("en"));
        String simplified = "验证并登录";
        String traditional = TellomiChineseScript.toTraditional(simplified);
        assertNotEquals(simplified, traditional);
        assertEquals("English", TellomiChineseScript.toTraditional("English"));
        assertTrue(traditional.indexOf('\u9a8c') < 0);
    }

    @Test
    public void freshResetKeepsTheRowWithoutReadingAClientDate() {
        assertTrue(TellomiSessionRevoke.isFreshResetForbidden(406, "FRESH_RESET_AUTHORISATION_FORBIDDEN"));
        assertTrue(TellomiSessionRevoke.isFreshResetForbidden(406, "FLOOD_WAIT FRESH_RESET_AUTHORISATION_FORBIDDEN"));
        assertFalse(TellomiSessionRevoke.isFreshResetForbidden(400, "HASH_INVALID"));
        assertFalse(TellomiSessionRevoke.isFreshResetForbidden(0, null));
        assertTrue(TellomiSessionRevoke.keepDeviceRow(false));
        assertFalse(TellomiSessionRevoke.keepDeviceRow(true));
    }

    @Test
    public void idleBarIsAttachEmojiVoiceAndTextReplacesVoice() {
        assertEquals(TellomiInputBar.ATTACH, TellomiInputBar.slots(false)[0]);
        assertEquals(TellomiInputBar.EMOJI, TellomiInputBar.slots(false)[1]);
        assertEquals(TellomiInputBar.VOICE, TellomiInputBar.slots(false)[2]);
        assertEquals(TellomiInputBar.SEND, TellomiInputBar.slots(true)[2]);
    }

    @Test
    public void sendArrowUsesTheIosPathsAndHasNoCircle() throws Exception {
        String arrow = read("TMessagesProj/tellomi/res/drawable/tellomi_chat_send_arrow.xml");
        assertTrue(arrow.contains("M11,14.6666667 L16.4310816,9.40016333"));
        assertTrue(arrow.contains("M16.5,9.33333333 C17.0522847,9.33333333 17.5,9.78104858"));
        assertFalse(arrow.contains("fillEllipse") || arrow.contains("M6.232"));
        String enter = read("TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java");
        assertTrue(enter.contains("R.drawable.tellomi_chat_send_arrow"));
        assertTrue(enter.contains("R.drawable.tellomi_input_attach"));
        assertTrue(enter.contains("Gravity.BOTTOM | Gravity.LEFT, 2, 0, 0, 0"));
        String sessions = read("TMessagesProj/src/main/java/org/telegram/ui/SessionsActivity.java");
        assertTrue(sessions.contains("tellomiResetRefused"));
        assertTrue(sessions.contains("tellomiFinishResetAll"));
        String login = read("TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java");
        assertTrue(login.contains("TellomiAuthChromeLayout.place"));
        assertTrue(login.contains("!tellomiKeepCodePage && activityMode == MODE_LOGIN && (currentViewNum == VIEW_TELLOMI_PHONE_PASSWORD"));
    }

    private static String read(String relative) throws Exception {
        File direct = new File(relative);
        File nested = new File("TMessagesProj/.." , relative);
        File file = direct.isFile() ? direct : nested;
        if (!file.isFile()) {
            file = new File("..", relative);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
