package org.telegram.messenger;

import android.content.Context;
import android.util.SparseIntArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.telegram.localization.LocalizationUtils;
import org.telegram.utils.localization.Localization;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Downloaded langpack values win once {@link BuildVars#USE_CLOUD_STRINGS} is on.
 * No device: this installs the pack the same way a finished langpack file does,
 * then reads it through getString and formatPluralStringComma.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
public class TellomiCloudStringsTest {

    private boolean previousCloud;

    @Before
    public void setUp() {
        previousCloud = BuildVars.USE_CLOUD_STRINGS;
        BuildVars.USE_CLOUD_STRINGS = true;
        LocaleController.replaceInstanceForTest(new LocaleController(false));
    }

    @After
    public void tearDown() {
        BuildVars.USE_CLOUD_STRINGS = previousCloud;
        LocaleController.replaceInstanceForTest(null);
    }

    @Test
    public void buildEnablesCloudStrings() {
        assertTrue(BuildConfig.TELLOMI_USE_CLOUD_STRINGS);
    }

    @Test
    public void loadedPackReplacesUiCopyAndKeepsTellomi() {
        HashMap<String, String> simplified = pack(
                "Settings", "设置",
                "FeedTab", "动态",
                "InviteText", "Invite friends to Telegram",
                "Members_other", "%1$d Telegram members");
        LocaleController.getInstance().useCloudPackForTest(simplified, "zh");

        assertEquals("设置", LocaleController.getString("Settings"));
        assertEquals("动态", LocaleController.getString("FeedTab"));
        assertEquals("Invite friends to Tellomi", LocaleController.getString("InviteText"));
        assertEquals("1 Tellomi members", LocaleController.formatPluralStringComma("Members", 1));
        assertEquals("1,234 Tellomi members", LocaleController.formatPluralStringComma("Members", 1234));
        assertFalse(LocaleController.getString("InviteText").contains("Telegram"));

        HashMap<String, String> traditional = pack(
                "Settings", "設定",
                "FeedTab", "動態");
        LocaleController.getInstance().useCloudPackForTest(traditional, "zh");
        assertEquals("設定", LocaleController.getString("Settings"));
        assertEquals("動態", LocaleController.getString("FeedTab"));
        assertEquals("LOC_ERR:InviteText", LocaleController.getString("InviteText"));

        HashMap<String, String> japanese = pack(
                "Settings", "設定",
                "FeedTab", "モーメント",
                "Members_other", "%1$d 人");
        LocaleController.getInstance().useCloudPackForTest(japanese, "ja");
        assertEquals("モーメント", LocaleController.getString("FeedTab"));
        assertEquals("2 人", LocaleController.formatPluralStringComma("Members", 2));

        HashMap<String, String> korean = pack(
                "Settings", "설정",
                "FeedTab", "모멘트",
                "Members_other", "멤버 %1$d명");
        LocaleController.getInstance().useCloudPackForTest(korean, "ko");
        assertEquals("설정", LocaleController.getString("Settings"));
        assertEquals("멤버 4명", LocaleController.formatPluralStringComma("Members", 4));

        HashMap<String, String> german = pack(
                "Settings", "Einstellungen",
                "FeedTab", "Momente",
                "Members_one", "%1$d Mitglied",
                "Members_other", "%1$d Mitglieder");
        LocaleController.getInstance().useCloudPackForTest(german, "de");
        assertEquals("Einstellungen", LocaleController.getString("Settings"));
        assertEquals("1 Mitglied", LocaleController.formatPluralStringComma("Members", 1));
        assertEquals("3 Mitglieder", LocaleController.formatPluralStringComma("Members", 3));

        HashMap<String, String> russian = pack(
                "Settings", "Настройки",
                "FeedTab", "Лента",
                "Members_one", "%1$d участник",
                "Members_few", "%1$d участника",
                "Members_many", "%1$d участников");
        LocaleController.getInstance().useCloudPackForTest(russian, "ru");
        assertEquals("Настройки", LocaleController.getString("Settings"));
        assertEquals("Лента", LocaleController.getString("FeedTab"));
        assertEquals("1 участник", LocaleController.formatPluralStringComma("Members", 1));
        assertEquals("2 участника", LocaleController.formatPluralStringComma("Members", 2));
        assertEquals("5 участников", LocaleController.formatPluralStringComma("Members", 5));
        assertEquals("11 участников", LocaleController.formatPluralStringComma("Members", 11));

        HashMap<String, String> english = pack(
                "Settings", "Settings",
                "FeedTab", "Moments",
                "Members_one", "%1$d member",
                "Members_other", "%1$d members");
        LocaleController.getInstance().useCloudPackForTest(english, "en");
        assertEquals("Settings", LocaleController.getString("Settings"));
        assertEquals("Moments", LocaleController.getString("FeedTab"));
        assertEquals("1 member", LocaleController.formatPluralStringComma("Members", 1));
        assertEquals("2 members", LocaleController.formatPluralStringComma("Members", 2));
    }

    /**
     * zh-Hans reports plural code {@code zh}. {@code applyLanguage} then uses
     * {@code Locale("zh")}, whose language tag is {@code zh}. The built-in
     * overlay for that tag is {@code values-zh}, not {@code values-zh-rCN}.
     * Plural {@code zh-hant} tags as {@code zh} as well. {@code zh_tw} still
     * uses {@code values-zh-rTW}. The downloaded pack still owns
     * {@code MainTabsProfile}.
     */
    @Test
    public void zhPluralCodeKeepsAvatarCaptionWhenThePackSaysProfile() throws Exception {
        Context previous = ApplicationLoader.applicationContext;
        SparseIntArray previousBindings = resourceBindings();
        try {
            ApplicationLoader.applicationContext = RuntimeEnvironment.getApplication();
            bindResource("TellomiTabMe", R.string.TellomiTabMe);
            bindResource("MainTabsProfile", R.string.MainTabsProfile);

            HashMap<String, String> simplified = pack("MainTabsProfile", "个人资料");
            LocaleController controller = LocaleController.getInstance();
            showBuiltin(controller, "zh", simplified, "src/main/res/values-zh/strings.xml");
            assertEquals("zh", controller.getCurrentLocale().toLanguageTag());
            assertEquals("localization_zh.bin", LocalizationUtils.getLocalizationAsset(controller.getCurrentLocale()));
            assertEquals("我", LocaleController.getString(R.string.TellomiTabMe));
            assertEquals("个人资料", LocaleController.getString(R.string.MainTabsProfile));
            assertFalse(simplified.containsKey("TellomiTabMe"));

            HashMap<String, String> traditional = pack("MainTabsProfile", "個人資料");
            showBuiltin(controller, "zh-hant", traditional, "src/main/res/values-zh/strings.xml");
            assertEquals("zh", controller.getCurrentLocale().toLanguageTag());
            assertEquals("localization_zh.bin", LocalizationUtils.getLocalizationAsset(controller.getCurrentLocale()));
            assertEquals("我", LocaleController.getString(R.string.TellomiTabMe));
            assertEquals("個人資料", LocaleController.getString(R.string.MainTabsProfile));

            showBuiltin(controller, "zh_tw", pack("MainTabsProfile", "個人資料"), "src/main/res/values-zh-rTW/strings.xml");
            assertEquals("zh-TW", controller.getCurrentLocale().toLanguageTag());
            assertEquals("localization_zh_tw.bin", LocalizationUtils.getLocalizationAsset(controller.getCurrentLocale()));
            assertEquals("我", LocaleController.getString(R.string.TellomiTabMe));

            showBuiltin(controller, "en", pack(), "src/main/res/values/strings.xml");
            assertEquals(LocalizationUtils.DEFAULT_LOCALIZATION, LocalizationUtils.getLocalizationAsset(controller.getCurrentLocale()));
            assertEquals("Me", LocaleController.getString(R.string.TellomiTabMe));

            showBuiltin(controller, "zh", pack("TellomiTabMe", "个人资料"), "src/main/res/values-zh/strings.xml");
            assertEquals("个人资料", LocaleController.getString(R.string.TellomiTabMe));
        } finally {
            ApplicationLoader.applicationContext = previous;
            setResourceBindings(previousBindings);
        }
    }

    @Test
    public void pluralFallsBackToOtherInsideTheCloudPack() {
        HashMap<String, String> russian = pack(
                "Members_other", "%1$d участников");
        LocaleController.getInstance().useCloudPackForTest(russian, "ru");
        assertEquals("2 участников", LocaleController.formatPluralStringComma("Members", 2));
    }

    @Test
    public void cloudPackIsIgnoredWhenTheFlagIsOff() {
        HashMap<String, String> simplified = pack(
                "Settings", "设置",
                "Members_other", "%1$d 位成员");
        LocaleController.getInstance().useCloudPackForTest(simplified, "zh");
        BuildVars.USE_CLOUD_STRINGS = false;

        String settings = LocaleController.getString("Settings");
        assertFalse(settings.contains("设置"));
        assertEquals("LOC_ERR:Settings", settings);
    }

    private static void showBuiltin(LocaleController controller, String pluralLangCode, HashMap<String, String> cloud, String builtinXml) throws Exception {
        controller.useCloudPackForTest(cloud, pluralLangCode);
        controller.useBuiltInStringsForTest(readStrings(builtinXml));
    }

    private static HashMap<String, String> readStrings(String relative) throws Exception {
        String xml = new String(Files.readAllBytes(repoRoot().resolve("TMessagesProj").resolve(relative)), StandardCharsets.UTF_8);
        HashMap<String, String> values = new HashMap<>();
        Matcher matcher = Pattern.compile("<string name=\"([^\"]+)\">(.*?)</string>", Pattern.DOTALL).matcher(xml);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2));
        }
        return values;
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

    private static void bindResource(String name, int resId) throws Exception {
        SparseIntArray bindings = resourceBindings();
        if (bindings == null) {
            bindings = new SparseIntArray();
            setResourceBindings(bindings);
        }
        bindings.put(resId, name.hashCode());
    }

    private static SparseIntArray resourceBindings() throws Exception {
        Field field = Localization.class.getDeclaredField("rawResBindings");
        field.setAccessible(true);
        return (SparseIntArray) field.get(null);
    }

    private static void setResourceBindings(SparseIntArray bindings) throws Exception {
        Field field = Localization.class.getDeclaredField("rawResBindings");
        field.setAccessible(true);
        field.set(null, bindings);
    }

    private static HashMap<String, String> pack(String... pairs) {
        HashMap<String, String> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }
}
