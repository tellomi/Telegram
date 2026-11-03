package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TellomiLoginFixesTest {

    @Test
    public void laterSubmitReplacesThePreviousNumber() {
        TellomiPendingAuth auth = new TellomiPendingAuth();
        int first = auth.begin("8613883860913");
        int second = auth.begin("8613883889878");
        assertFalse(auth.accepts(first, "8613883860913"));
        assertTrue(auth.accepts(second, "8613883889878"));
        assertFalse(auth.accepts(second, "8613883860913"));
        assertEquals("8613883889878", auth.digits());
        assertEquals("8613883889878", TellomiPendingAuth.digitsOnly("+86 138 8388 9878"));
        assertFalse(TellomiPendingAuth.digitsOnly("+86 138-8386-0913").contains("9878"));
    }

    @Test
    public void backOrCloseDropsThePendingCode() {
        TellomiPendingAuth auth = new TellomiPendingAuth();
        int ticket = auth.begin("8613883889878");
        auth.clear();
        assertFalse(auth.accepts(ticket, "8613883889878"));
        assertEquals(null, auth.digits());
    }

    @Test
    public void sixBoxesFitANarrowPhone() {
        int available = 320;
        int[] sized = TellomiCodeBoxes.fit(available, 6, 52, 36, 10, 4);
        int used = sized[0] * 6 + sized[1] * 5;
        assertTrue("boxes overflow " + used + " > " + available, used <= available);
        assertTrue(sized[0] >= 36);
    }

    @Test
    public void widePhoneKeepsThePreferredBox() {
        int[] sized = TellomiCodeBoxes.fit(400, 4, 52, 36, 10, 4);
        assertEquals(52, sized[0]);
        assertEquals(10, sized[1]);
    }

    @Test
    public void eightBoxesStayInsideANarrowRow() {
        int available = 280;
        int[] sized = TellomiCodeBoxes.fit(available, 8, 52, 36, 8, 4);
        int used = sized[0] * 8 + sized[1] * 7;
        assertTrue(used <= available);
        float digit = TellomiLoginFit.digitPx(sized[0], 20f * TellomiLoginFit.MAX_FONT_SCALE);
        assertTrue(digit <= sized[0] * 0.62f + 0.01f);
        assertTrue(digit >= 1f);
    }

    @Test
    public void wideWindowCentersTheColumn() {
        assertEquals(0, TellomiLoginFit.extraSidePadPx(360, 420));
        assertEquals(0, TellomiLoginFit.extraSidePadPx(420, 420));
        assertEquals(190, TellomiLoginFit.extraSidePadPx(800, 420));
    }

    @Test
    public void dialogStaysInsideAShortLandscapeWindow() {
        int width = TellomiLoginFit.dialogWidthPx(640, 24, 400);
        int height = TellomiLoginFit.dialogMaxHeightPx(360, 24 + 48 + 32);
        assertTrue(width <= 400);
        assertTrue(height < 360);
        assertTrue(height > 0);
        assertEquals(1, TellomiLoginFit.dialogMaxHeightPx(40, 80));
    }

    @Test
    public void fontScaleGrowsTextAndThenStops() {
        assertEquals(16f, TellomiLoginFit.cappedSp(16f, 1f, TellomiLoginFit.MAX_FONT_SCALE), 0.01f);
        assertEquals(16f * 1.3f / 1.3f, TellomiLoginFit.cappedSp(16f, 1.3f, TellomiLoginFit.MAX_FONT_SCALE), 0.01f);
        float largest = TellomiLoginFit.cappedSp(16f, 2f, TellomiLoginFit.MAX_FONT_SCALE);
        assertEquals(16f * TellomiLoginFit.MAX_FONT_SCALE / 2f, largest, 0.01f);
        assertTrue(TellomiLoginFit.fieldHeightPx(50, 40, 24) >= 50);
        assertEquals(64, TellomiLoginFit.fieldHeightPx(50, 40, 24));
    }

    @Test
    public void landscapeSubmitStaysReachableWhenThePageScrolls() {
        int visible = 360 - 230;
        int submitBottom = 24 + 30 + 56 + 30 + 50 + 12 + 48;
        assertTrue(TellomiLoginFit.submitClipped(submitBottom, visible, false));
        assertFalse(TellomiLoginFit.submitClipped(submitBottom, visible, true));
        assertTrue(TellomiLoginFit.expandToViewport(400, TellomiLoginFit.MEASURE_EXACTLY, 800));
        assertFalse(TellomiLoginFit.expandToViewport(900, TellomiLoginFit.MEASURE_EXACTLY, 800));
    }

    @Test
    public void flavorStringsHaveSimplifiedChinese() throws Exception {
        File english = find("TMessagesProj/tellomi/res/values/strings.xml");
        File chinese = find("TMessagesProj/tellomi/res/values-zh/strings.xml");
        Map<String, String> en = strings(english);
        Map<String, String> zh = strings(chinese);
        assertFalse(en.isEmpty());
        for (Map.Entry<String, String> entry : en.entrySet()) {
            String name = entry.getKey();
            assertTrue("missing zh for " + name, zh.containsKey(name));
            if ("TellomiChooseLanguage".equals(name)) {
                assertTrue(entry.getValue().contains("选择语言"));
                assertTrue(entry.getValue().contains("Language"));
                assertEquals(entry.getValue(), zh.get(name));
                continue;
            }
            if (name.startsWith("Tellomi") && !isBrand(entry.getValue())) {
                assertNotEquals("untranslated " + name, entry.getValue(), zh.get(name));
                assertTrue("no Chinese in " + name + ": " + zh.get(name), hasCjk(zh.get(name)));
            }
        }
        assertTrue(zh.get("TellomiNotOpenBody").contains("即将开放"));
        assertEquals("绑定后可使用手机号登录", zh.get("TellomiBindPhoneHint"));
        assertEquals("验证码已发送到你在其他设备上登录的 Tellomi", zh.get("TellomiOtpSentApp"));
        assertEquals("改用短信接收", zh.get("TellomiUseSms"));
        assertEquals("短信已发送至 %1$s", zh.get("TellomiOtpSentSms"));
        assertTrue(zh.get("TellomiOtpSentCall").contains("拨打"));
        assertTrue(zh.get("TellomiEmailOtpSent").contains("邮件已发送"));
    }

    @Test
    public void languageButtonNamesTheCurrentLanguage() {
        assertEquals("简体中文 ▾", TellomiSentCode.languageLabel("简体中文"));
        assertEquals("繁體中文 ▾", TellomiSentCode.languageLabel("繁體中文"));
        assertEquals("English ▾", TellomiSentCode.languageLabel("English"));
        assertEquals("English ▾", TellomiSentCode.languageLabel(""));
    }

    @Test
    public void sentCodeCopyFollowsTheDeliveryChannel() {
        assertEquals(TellomiSentCode.KIND_APP, TellomiSentCode.kind(1, false));
        assertEquals(TellomiSentCode.KIND_SMS, TellomiSentCode.kind(2, false));
        assertEquals(TellomiSentCode.KIND_CALL, TellomiSentCode.kind(3, false));
        assertEquals(TellomiSentCode.KIND_CALL, TellomiSentCode.kind(4, false));
        assertEquals(TellomiSentCode.KIND_EMAIL, TellomiSentCode.kind(2, true));
    }

    @Test
    public void duplicateLoginLogsOutAndSendCodeStopsOnTimeout() throws Exception {
        String login = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(login.contains("new TLRPC.TL_auth_logOut()"));
        assertTrue(login.contains("tellomiLogOutNewSession"));
        assertTrue(login.contains("tellomiWatchSendCode"));
        assertTrue(login.contains("TELLOMI_SEND_CODE_TIMEOUT_MS = 20000"));
        assertTrue(login.contains("TellomiAuthTimeout"));
        assertTrue(login.contains("tellomiShowSendFailure"));
        assertTrue(login.contains("VIEW_TELLOMI_EMAIL_CODE"));
        assertTrue(login.contains("VIEW_TELLOMI_PHONE_PASSWORD"));
        assertTrue(login.contains("VIEW_TELLOMI_EMAIL_PASSWORD"));
        assertTrue(login.contains("tellomiOpenScreen(TELLOMI_EMAIL_CODE"));
        assertFalse(login.contains("currentViewNum = VIEW_TELLOMI_EMAIL_CODE"));
        assertTrue(login.contains("new TellomiLanguageSelectActivity"));
        assertTrue(login.contains("R.drawable.msg2_language"));
        assertTrue(login.contains("introView = intro"));
        assertFalse(login.contains("tellomiLanguageButton.setText"));
        assertTrue(login.contains("showCodeOptions"));
        String chrome = new String(Files.readAllBytes(find("TMessagesProj/tellomi/java/org/telegram/ui/TellomiLoginChrome.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(chrome.contains("BuildVars.HELP_URL"));
        assertEquals(12, TellomiLanguageSheet.bundledCodes().size());
        assertTrue(TellomiLanguageSheet.bundledCodes().contains("zh_hans"));
        assertTrue(TellomiLanguageSheet.bundledCodes().contains("zh_hant"));
        assertTrue(TellomiLanguageSheet.bundledCodes().contains("en"));
        assertTrue(TellomiLanguageSheet.bundledCodes().contains("ru"));
        assertTrue(TellomiLanguageSheet.bundledCodes().contains("pt_br"));
    }

    @Test
    public void langpackRequestsRunBeforeLogin() throws Exception {
        String locale = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(flagFollows(locale, "new TLRPC.TL_langpack_getLanguages()", 8000));
        assertTrue(flagFollows(locale, "new TLRPC.TL_langpack_getLangPack()", 1500));
        assertTrue(flagFollows(locale, "new TLRPC.TL_langpack_getDifference()", 1500));
        String launch = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(flagFollows(launch, "new TLRPC.TL_langpack_getLanguage()", 2500));
        assertTrue(flagFollows(launch, "new TLRPC.TL_langpack_getStrings()", 2500));
    }

    private static boolean flagFollows(String text, String needle, int window) {
        int from = 0;
        boolean found = false;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return found;
            }
            found = true;
            int flag = text.indexOf("RequestFlagWithoutLogin", at);
            if (flag < 0 || flag - at > window) {
                return false;
            }
            from = at + needle.length();
        }
    }

    @Test
    public void chinesePackIncludesFlavorStrings() throws Exception {
        File plugin = find("buildSrc/src/main/kotlin/org/telegram/plugin/TelegramBuildAppPlugin.kt");
        String source = new String(Files.readAllBytes(plugin.toPath()), StandardCharsets.UTF_8);
        assertTrue(source.contains("tellomi/res/values"));
        assertTrue(source.contains("tellomi/res") && source.contains("values-zh/strings.xml"));
    }

    private static boolean isBrand(String value) {
        return "Tellomi".equals(value);
    }

    private static boolean hasCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> strings(File file) throws Exception {
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("<string name=\"([^\"]+)\">([\\s\\S]*?)</string>").matcher(xml);
        Map<String, String> out = new LinkedHashMap<>();
        while (matcher.find()) {
            out.put(matcher.group(1), matcher.group(2).trim());
        }
        return out;
    }

    private static File find(String relative) {
        File direct = new File(relative);
        if (direct.isFile()) {
            return direct;
        }
        File nested = new File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        File fromModule = new File("..", relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative + " from " + new File(".").getAbsolutePath());
    }
}
