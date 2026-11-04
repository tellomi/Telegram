package org.telegram.messenger;

import org.junit.Test;
import org.telegram.ui.TellomiLanguageSheet;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiCloudStringsTest {

    @Test
    public void downloadedPackIsReadForAnyLanguage() {
        assertTrue(TellomiDefaults.readDownloadedPack(true, false));
        assertTrue(TellomiDefaults.readDownloadedPack(false, true));
    }

    @Test
    public void bundledRowDoesNotPretendToBeADownload() {
        assertFalse(TellomiDefaults.readDownloadedPack(false, false));
    }

    @Test
    public void builtInRowsExceptEnglishAndChineseRequestAServerPack() {
        for (String code : TellomiLanguageSheet.bundledCodes()) {
            LocaleController.LocaleInfo info = builtin(code);
            boolean server = !"en".equals(code) && !"zh_hans".equals(code) && !"zh_hant".equals(code);
            assertEquals(code, server, TellomiDefaults.needsServerLangpack(info));
            assertEquals(code, server, TellomiDefaults.attachServerLangpack(info));
            if (server) {
                assertTrue(code, info.isRemote());
                assertTrue(code, TellomiDefaults.readDownloadedPack(info.isRemote(), info.isUnofficial()));
                assertFalse(code, TellomiDefaults.attachServerLangpack(info));
            } else {
                assertFalse(code, info.isRemote());
                assertNull(code, info.pathToFile);
            }
        }
    }

    @Test
    public void englishAndChineseStayOnTheLocalBin() {
        LocaleController.LocaleInfo english = builtin("en");
        assertFalse(TellomiDefaults.attachServerLangpack(english));
        assertNull(english.pathToFile);

        LocaleController.LocaleInfo hans = TellomiDefaults.builtinChinese(false);
        LocaleController.LocaleInfo hant = TellomiDefaults.builtinChinese(true);
        assertTrue(TellomiDefaults.bundledChinese(hans));
        assertTrue(TellomiDefaults.bundledChinese(hant));
        assertFalse(TellomiDefaults.needsServerLangpack(hans));
        assertFalse(TellomiDefaults.needsServerLangpack(hant));
        assertFalse(TellomiDefaults.attachServerLangpack(hans));
        assertFalse(TellomiDefaults.attachServerLangpack(hant));
        assertTrue(TellomiDefaults.bundledChinese(hans));
        assertTrue(TellomiDefaults.bundledChinese(hant));
        assertTrue(TellomiDefaults.useCloudStrings(hans));
        assertTrue(TellomiDefaults.useCloudStrings(hant));
        assertFalse(TellomiDefaults.useCloudStrings(english));
    }

    @Test
    public void remoteUnofficialAndEmptyRowsAreLeftAlone() {
        assertFalse(TellomiDefaults.needsServerLangpack(null));
        assertFalse(TellomiDefaults.attachServerLangpack(null));

        LocaleController.LocaleInfo remote = builtin("it");
        remote.pathToFile = "remote";
        assertFalse(TellomiDefaults.needsServerLangpack(remote));
        assertFalse(TellomiDefaults.attachServerLangpack(remote));
        assertEquals("remote", remote.pathToFile);

        LocaleController.LocaleInfo unofficial = builtin("ja");
        unofficial.pathToFile = "unofficial";
        assertFalse(TellomiDefaults.attachServerLangpack(unofficial));
        assertEquals("unofficial", unofficial.pathToFile);

        LocaleController.LocaleInfo plain = builtin("de");
        plain.builtIn = false;
        assertFalse(TellomiDefaults.needsServerLangpack(plain));
    }

    @Test
    public void applyLanguageHooksTheServerPackAndLoginRedrawsAfterIt() throws Exception {
        String locale = read("src/main/java/org/telegram/messenger/LocaleController.java");
        int apply = locale.indexOf("public int applyLanguage(final LocaleInfo localeInfo");
        int applyEnd = locale.indexOf("public LocaleInfo getCurrentLocaleInfo()", apply);
        assertTrue(apply >= 0 && applyEnd > apply);
        String method = locale.substring(apply, applyEnd);
        assertTrue(method.contains("TellomiDefaults.attachServerLangpack(localeInfo)"));
        assertTrue(method.contains("remoteLanguages.add(tracked)"));
        int pack = locale.indexOf("FileLog.d(\"applyRemoteLanguage getLangPack\");");
        int packEnd = locale.indexOf("return 0;", pack);
        String download = locale.substring(pack, packEnd);
        assertTrue(download.contains("onPartlyDone.run()"));

        String login = read("src/main/java/org/telegram/ui/LoginActivity.java");
        int tellomi = login.indexOf("void tellomiApplyInterfaceLanguage");
        int tellomiEnd = login.indexOf("private void tellomiLogOutNewSession", tellomi);
        assertTrue(tellomi >= 0 && tellomiEnd > tellomi);
        String body = login.substring(tellomi, tellomiEnd);
        assertTrue(body.contains("applyLanguage(info, true, false, false, true, currentAccount, () -> AndroidUtilities.runOnUIThread"));
        assertTrue(body.contains("rebuildAllFragmentViews(true, true)"));
        assertTrue(body.indexOf("applyLanguage") < body.indexOf("rebuildAllFragmentViews"));
        assertTrue(body.contains("TellomiLanguageAlert.rememberUserChoice(info)"));
    }

    private static LocaleController.LocaleInfo builtin(String code) {
        LocaleController.LocaleInfo info = new LocaleController.LocaleInfo();
        info.shortName = code;
        info.pluralLangCode = code;
        info.builtIn = true;
        info.pathToFile = null;
        return info;
    }

    private static String read(String relative) throws Exception {
        File file = find(relative);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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
        String stripped = relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative;
        File fromModule = new File(stripped);
        if (fromModule.isFile()) {
            return fromModule;
        }
        File fromModuleParent = new File("..", stripped);
        if (fromModuleParent.isFile()) {
            return fromModuleParent;
        }
        throw new AssertionError("missing " + relative + " from " + new File(".").getAbsolutePath());
    }
}
