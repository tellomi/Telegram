package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Welcome carousel is skipped. Phone login, system language, and the shippable
 * datacenter stay checkable without a device.
 */
public class TellomiIntroTest {

    private static final List<String> PACKS = Arrays.asList(
            "en", "ru", "de", "pt_br", "zh_hans", "zh_hant", "zh_cn");

    @Test
    public void coldStartOpensPhoneLoginAndKeepsTheWelcomeReturn() {
        assertTrue(TellomiIntro.SKIP_WELCOME);
        assertTrue(TellomiIntro.openPhoneLogin(0));
        assertTrue(TellomiIntro.openPhoneLogin(3));

        String launch = read("src/main/java/org/telegram/ui/LaunchActivity.java");
        String chooser = method(launch, "getClientNotActivatedFragment");
        assertTrue(chooser.contains("TellomiIntro.openPhoneLogin"));
        assertTrue(chooser.contains("return new LoginActivity()"));
        assertTrue(chooser.contains("return new IntroActivity()"));
        assertFalse(chooser.contains("10.0.2.2"));

        String logout = method(launch, "switchToAvailableAccountOrLogout");
        assertTrue(logout.contains("presentFragment(new LoginActivity(), false, true)"));
        assertTrue(logout.contains("presentFragment(new IntroActivity().setOnLogout())"));
        assertTrue(method(launch, "needCloseLastFragment").contains("layout.getFragmentStack().size() <= 1"));
        assertTrue(method(launch, "needCloseLastFragment").contains("finish()"));

        String intro = read("src/main/java/org/telegram/ui/IntroActivity.java");
        assertTrue(intro.contains("class IntroActivity"));
        assertTrue(intro.contains("R.string.StartMessaging"));
        assertTrue(intro.contains("setOnLogout()"));
    }

    @Test
    public void otherLoginPathsStayOnThePhoneScreen() {
        assertTrue(read("src/main/java/org/telegram/ui/MainTabsActivity.java").contains("presentFragment(new LoginActivity(availableAccount))"));
        assertTrue(read("src/main/java/org/telegram/ui/LogoutActivity.java").contains("presentFragment(new LoginActivity(availableAccount))"));
        assertTrue(read("src/main/java/org/telegram/ui/UserInfoActivity.java").contains("presentFragment(new LoginActivity(availableAccount))"));
        assertTrue(read("src/main/java/org/telegram/ui/ProfileActivity.java").contains("presentFragment(new LoginActivity(freeAccount))"));
        assertTrue(read("src/main/java/org/telegram/ui/LaunchActivity.java").contains("switchToAccount(account, true)"));

        String launch = read("src/main/java/org/telegram/ui/LaunchActivity.java");
        assertTrue(launch.contains("ACTION_TYPE_QR_LOGIN"));
        String login = read("src/main/java/org/telegram/ui/LoginActivity.java");
        assertTrue(login.contains("changeEmail("));
        assertTrue(login.contains("TL_help_getNearestDc"));
        assertTrue(read("src/main/assets/countries.txt").contains("86;CN;China"));
        assertTrue(login.contains("key_windowBackgroundWhite"));
        assertFalse(method(login, "isLightStatusBar").contains("return true"));
    }

    @Test
    public void chineseSystemSelectsChineseAndASavedLanguageStays() {
        assertEquals("zh_cn", TellomiIntro.matchShortName("zh", "CN", "", PACKS));
        assertEquals("zh_hans", TellomiIntro.matchShortName("zh", "CN", "Hans", Arrays.asList("en", "zh_hans", "zh_hant")));
        assertEquals("zh_hans", TellomiIntro.matchShortName("zh", "", "", Arrays.asList("en", "zh_hant", "zh_hans")));
        assertEquals("zh_hant", TellomiIntro.matchShortName("zh", "TW", "", PACKS));
        assertEquals("zh_hant", TellomiIntro.matchShortName("zh", "HK", "", Arrays.asList("en", "zh_hans", "zh_hant")));
        assertEquals("zh_tw", TellomiIntro.matchShortName("zh", "TW", "", Arrays.asList("en", "zh_tw", "zh_hans")));
        assertEquals("zh-hans", TellomiIntro.matchShortName("zh-Hans", "CN", "", Arrays.asList("en", "zh-hans")));
        assertEquals("ru", TellomiIntro.matchShortName("ru", "RU", "", PACKS));
        assertEquals("en", TellomiIntro.matchShortName("en", "US", "", PACKS));
        assertEquals("pt_br", TellomiIntro.matchShortName("pt", "BR", "", PACKS));
        assertNull(TellomiIntro.matchShortName("zh", "CN", "", Arrays.asList("en", "ru")));
        assertNull(TellomiIntro.matchShortName("zh", "CN", "", Collections.emptyList()));

        assertEquals("zh_hans", TellomiIntro.languageToApply(false, "en", "zh", "CN", "", Arrays.asList("en", "zh_hans")));
        assertNull(TellomiIntro.languageToApply(false, "zh_hans", "zh", "CN", "", Arrays.asList("en", "zh_hans")));
        assertNull(TellomiIntro.languageToApply(true, "en", "zh", "CN", "", Arrays.asList("en", "zh_hans", "zh_hant")));
        assertNull(TellomiIntro.languageToApply(true, "ru", "zh", "CN", "", PACKS));
        assertNull(TellomiIntro.languageToApply(false, "en", "en", "US", "", PACKS));

        String locale = read("src/main/java/org/telegram/messenger/LocaleController.java");
        String intro = read("src/main/java/org/telegram/messenger/TellomiIntro.java");
        assertTrue(intro.contains("matchShortName"));
        assertTrue(method(intro, "chooseInitial").contains("matchShortName"));
        assertTrue(method(intro, "chooseInitial").contains("return new InitialLanguage(chosen, false)"));
        assertTrue(method(intro, "chooseInitial").contains("return new InitialLanguage(savedKey, true)"));
        assertTrue(locale.contains("TellomiIntro.chooseInitial"));
        assertTrue(locale.contains("localeInfo.shortName = \"zh_hans\""));
        assertTrue(locale.contains("localeInfo.shortName = \"zh_hant\""));
        assertTrue(locale.contains("localeInfo.pluralLangCode = \"zh\""));
        assertTrue(locale.contains("initial.writeLanguage"));
        assertTrue(locale.contains("applyLanguage(currentInfo, override, true, UserConfig.selectedAccount)"));
        assertTrue(locale.contains("if (override)"));
        assertTrue(locale.contains("editor.putString(\"language\", localeInfo.getKey())"));
        assertTrue(locale.contains("applyLoadedLanguage"));
        assertTrue(method(locale, "applyLoadedLanguage").contains("languageToApply"));
        assertTrue(method(locale, "applyLoadedLanguage").contains("override = false"));
    }

    @Test
    public void firstLaunchUsesSimplifiedChineseAndDoesNotWriteLanguage() {
        List<String> names = Arrays.asList("en", "ru", "de", "zh_hans", "zh_hant");

        TellomiIntro.InitialLanguage simplified = TellomiIntro.chooseInitial(
                null, "zh", "CN", "Hans", names, null, null);
        assertEquals("zh_hans", simplified.key);
        assertFalse(simplified.writeLanguage);

        TellomiIntro.InitialLanguage simplifiedCountry = TellomiIntro.chooseInitial(
                null, "zh", "CN", "", names, null, null);
        assertEquals("zh_hans", simplifiedCountry.key);
        assertFalse(simplifiedCountry.writeLanguage);

        TellomiIntro.InitialLanguage traditional = TellomiIntro.chooseInitial(
                null, "zh", "TW", "", names, null, null);
        assertEquals("zh_hant", traditional.key);
        assertFalse(traditional.writeLanguage);

        TellomiIntro.InitialLanguage english = TellomiIntro.chooseInitial(
                null, "en", "US", "", names, "en", null);
        assertEquals("en", english.key);
        assertFalse(english.writeLanguage);

        TellomiIntro.InitialLanguage saved = TellomiIntro.chooseInitial(
                "en", "zh", "CN", "Hans", names, "en", null);
        assertEquals("en", saved.key);
        assertTrue(saved.writeLanguage);

        TellomiIntro.InitialLanguage savedChinese = TellomiIntro.chooseInitial(
                "zh_hans", "en", "US", "", names, "en", null);
        assertEquals("zh_hans", savedChinese.key);
        assertTrue(savedChinese.writeLanguage);

        TellomiIntro.InitialLanguage unknown = TellomiIntro.chooseInitial(
                null, "fr", "FR", "", names, null, null);
        assertEquals("en", unknown.key);
        assertFalse(unknown.writeLanguage);

        String simplifiedXml = read("src/main/res/values-zh/strings.xml");
        String mainlandXml = read("src/main/res/values-zh-rCN/strings.xml");
        for (String xml : new String[]{simplifiedXml, mainlandXml}) {
            assertTrue(xml.contains("name=\"YourNumber\">你的电话号码<"));
            assertTrue(xml.contains("name=\"StartText\">请确认你的国家代码并输入你的电话号码。<"));
            assertTrue(xml.contains("name=\"Country\">国家<"));
            assertTrue(xml.contains("name=\"PhoneNumber\">电话号码<"));
            assertTrue(xml.contains("name=\"LoginAccessibilityCountryCode\">国家代码<"));
            assertTrue(xml.contains("name=\"YourCode\">手机验证<"));
            assertTrue(xml.contains("name=\"SentSmsCodeTitle\">输入验证码<"));
            assertFalse(xml.contains("Your phone number"));
        }
    }

    @Test
    public void remotePackSaveDoesNotFreezeAnAutomaticLanguage() {
        List<String> names = Arrays.asList("en", "de", "zh_hans", "zh_hant");
        MemoryLanguagePrefs prefs = new MemoryLanguagePrefs(names);
        LocaleController controller = new LocaleController(false);

        controller.storeLanguageAfterRemoteSave(prefs, "zh_hans", "zh_hans");
        assertNull(prefs.language);

        assertEquals("en", TellomiIntro.languageToApply(prefs.language != null, "zh_hans", "en", "US", "", names));
        assertEquals("zh_hant", TellomiIntro.languageToApply(prefs.language != null, "zh_hans", "zh", "TW", "", names));

        prefs.language = "de";
        controller.storeLanguageAfterRemoteSave(prefs, "de", "de");
        assertEquals("de", prefs.language);
        assertNull(TellomiIntro.languageToApply(prefs.language != null, "de", "zh", "CN", "", names));

        prefs.language = "zh_hans";
        controller.storeLanguageAfterRemoteSave(prefs, "zh_hans", "zh_hans");
        assertEquals("zh_hans", prefs.language);
        assertNull(TellomiIntro.languageToApply(true, "zh_hans", "zh", "TW", "", names));

        prefs.language = "no-such-lang";
        controller.storeLanguageAfterRemoteSave(prefs, "zh_hans", "zh_hans");
        assertEquals("no-such-lang", prefs.language);
    }

    @Test
    public void releaseDoesNotEmbedTheLocalDatacenter() throws Exception {
        String gradle = read("build.gradle");
        assertTrue(gradle.contains("local: '1:10.0.2.2:10443,2:10.0.2.2:10443'"));
        assertTrue(gradle.contains("tellomiEmbeddedShippableServer"));
        assertTrue(gradle.contains("tellomiRefuseNonProductionShip"));
        assertTrue(gradle.contains("A shippable build must embed"));
        assertFalse(read("src/main/java/org/telegram/messenger/TellomiIntro.java").contains("10.0.2.2"));

        String workflow = readWorkflow();
        assertTrue(workflow.contains("-PTELLOMI_SERVER_PROFILE=dev"));
        assertFalse(workflow.contains("-PTELLOMI_SERVER_PROFILE=local"));
    }

    /** In-memory {@code mainconfig} language row. */
    private static final class MemoryLanguagePrefs implements LocaleController.LanguagePreference {
        String language;
        private final Set<String> known;

        MemoryLanguagePrefs(List<String> known) {
            this.known = new HashSet<>(known);
        }

        @Override
        public String get() {
            return language;
        }

        @Override
        public boolean isKnown(String key) {
            return known.contains(key);
        }

        @Override
        public void put(String key) {
            language = key;
        }
    }

    private static String method(String source, String name) {
        int from = 0;
        while (from < source.length()) {
            int start = source.indexOf(name + "(", from);
            assertTrue(name + " is missing", start >= 0);
            int close = source.indexOf(')', start);
            int brace = source.indexOf('{', close);
            int semi = source.indexOf(';', close);
            if (brace >= 0 && (semi < 0 || brace < semi)) {
                int depth = 0;
                for (int i = brace; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            return source.substring(start, i + 1);
                        }
                    }
                }
                throw new AssertionError(name + " has no closing brace");
            }
            from = start + name.length();
        }
        throw new AssertionError(name + " is missing");
    }

    private static String read(String relative) throws RuntimeException {
        try {
            return new String(Files.readAllBytes(repoRoot().resolve("TMessagesProj").resolve(relative)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(relative, e);
        }
    }

    private static String readWorkflow() throws Exception {
        return new String(Files.readAllBytes(repoRoot().resolve(".github/workflows/android-apk.yml")), StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("TMessagesProj"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Paths.get("").toAbsolutePath());
    }
}
