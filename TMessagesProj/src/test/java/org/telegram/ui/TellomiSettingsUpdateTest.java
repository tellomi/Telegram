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
import static org.junit.Assert.assertTrue;

public class TellomiSettingsUpdateTest {

    @Test
    public void regularUsersSeeTheRowTheDebugMenuHides() {
        assertTrue(TellomiSettingsUpdate.rowVisible());
        assertFalse(TellomiSettingsUpdate.debugMenuCheckVisible(false, false, false));
        assertTrue(TellomiSettingsUpdate.debugMenuCheckVisible(true, false, false));
        assertTrue(TellomiSettingsUpdate.debugMenuCheckVisible(false, true, false));
        assertTrue(TellomiSettingsUpdate.debugMenuCheckVisible(false, false, true));
    }

    @Test
    public void settingsTapAttachesProgress() {
        assertTrue(TellomiSettingsUpdate.attachProgressOnManualCheck());
        assertEquals(41, TellomiSettingsUpdate.ROW_ID);
    }

    @Test
    public void copyIsChineseOrEnglish() {
        TellomiSettingsUpdate.Copy zh = TellomiSettingsUpdate.Copy.resolve("zh-Hans");
        assertEquals("检查更新", zh.check);
        assertEquals("已是最新版本", zh.latest);
        assertEquals("检查更新", TellomiSettingsUpdate.Copy.resolve("zh").check);
        assertEquals("已是最新版本", TellomiSettingsUpdate.Copy.resolve("zh_CN").latest);
        assertEquals("检查更新", TellomiSettingsUpdate.Copy.resolve("ZH-Hant").check);

        TellomiSettingsUpdate.Copy en = TellomiSettingsUpdate.Copy.resolve("en");
        assertEquals("Check for updates", en.check);
        assertEquals("Already the latest version", en.latest);
        assertEquals("Check for updates", TellomiSettingsUpdate.Copy.resolve(null).check);
        assertEquals("Already the latest version", TellomiSettingsUpdate.Copy.resolve("de").latest);
    }

    @Test
    public void flavorStringsMatchTheCopy() throws Exception {
        Map<String, String> en = strings(find("TMessagesProj/tellomi/res/values/strings.xml"));
        Map<String, String> zh = strings(find("TMessagesProj/tellomi/res/values-zh/strings.xml"));
        TellomiSettingsUpdate.Copy english = TellomiSettingsUpdate.Copy.resolve("en");
        TellomiSettingsUpdate.Copy chinese = TellomiSettingsUpdate.Copy.resolve("zh");
        assertEquals(english.check, en.get("TellomiCheckForUpdates"));
        assertEquals(english.latest, en.get("YourVersionIsLatest"));
        assertEquals(chinese.check, zh.get("TellomiCheckForUpdates"));
        assertEquals(chinese.latest, zh.get("YourVersionIsLatest"));
    }

    @Test
    public void settingsCallsTheExistingCheck() throws Exception {
        String settings = read("TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java");
        assertTrue(settings.contains("TellomiSettingsUpdate.rowVisible()"));
        assertTrue(settings.contains("TellomiSettingsUpdate.ROW_ID"));
        assertTrue(settings.contains("checkAppUpdate(true, progress)"));
        assertTrue(settings.contains("TellomiSettingsUpdate.attachProgressOnManualCheck()"));
        assertTrue(settings.contains("TellomiSettingsUpdate.debugMenuCheckVisible("));
        String launch = read("TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java");
        assertTrue(launch.contains("new TLRPC.TL_help_getAppUpdate()"));
        assertTrue(launch.contains("LocaleController.getString(R.string.YourVersionIsLatest)"));
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(find(relative).toPath()), StandardCharsets.UTF_8);
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
