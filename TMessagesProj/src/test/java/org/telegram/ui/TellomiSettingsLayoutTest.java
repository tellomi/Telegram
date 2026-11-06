package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiSettingsLayoutTest {

    @Test
    public void homeIsThreeGroupsAndDropsTipsAndWatch() {
        TellomiSettingsModel.Copy en = TellomiSettingsModel.Copy.resolve("en");
        List<TellomiSettingsModel.Entry> home = TellomiSettingsModel.arrangeEntries(sample(true), en);
        List<Integer> rows = rowIds(home);
        assertEquals(Arrays.asList(
            TellomiSettingsModel.ROW_ACCOUNT,
            TellomiSettingsModel.ROW_GENERAL,
            TellomiSettingsModel.ROW_SUPPORT
        ), rows.subList(0, 3));
        assertEquals("Account", textOf(home, TellomiSettingsModel.ROW_ACCOUNT));
        assertEquals("General", textOf(home, TellomiSettingsModel.ROW_GENERAL));
        assertEquals("Support", textOf(home, TellomiSettingsModel.ROW_SUPPORT));
        assertFalse(rows.contains(TellomiSettingsModel.TIPS_ID));
        assertFalse(rows.contains(TellomiSettingsModel.WATCH_ID));
        assertFalse(rows.contains(1));
        assertFalse(rows.contains(18));
        assertTrue(rows.contains(24));
        assertTrue(rows.contains(20));
        assertEquals(Integer.valueOf(41), rows.get(rows.size() - 1));
        assertEquals("page.account", TellomiSettingsModel.destination(TellomiSettingsModel.ROW_ACCOUNT));
        assertEquals("page.general", TellomiSettingsModel.destination(TellomiSettingsModel.ROW_GENERAL));
        assertEquals("page.support", TellomiSettingsModel.destination(TellomiSettingsModel.ROW_SUPPORT));
    }

    @Test
    public void secondaryPagesKeepOrderAndRenameHelp() {
        TellomiSettingsModel.Copy zh = TellomiSettingsModel.Copy.resolve("zh-CN");
        List<TellomiSettingsModel.Entry> source = sample(true);
        assertEquals(Arrays.asList(1, 3, 8), rowIds(TellomiSettingsModel.pageEntries(TellomiSettingsModel.ROW_ACCOUNT, source, zh)));
        assertEquals(Arrays.asList(5, 2, 6, 7, 9, 10, 40, 25, 11, 12, 13, 15, 16), rowIds(TellomiSettingsModel.pageEntries(TellomiSettingsModel.ROW_GENERAL, source, zh)));
        List<TellomiSettingsModel.Entry> support = TellomiSettingsModel.pageEntries(TellomiSettingsModel.ROW_SUPPORT, source, zh);
        assertEquals(Arrays.asList(18, 17, 19, 27), rowIds(support));
        assertFalse(rowIds(support).contains(41));
        assertEquals("帮助中心", textOf(support, 18));
        assertEquals("账号", TellomiSettingsModel.pageTitle(TellomiSettingsModel.ROW_ACCOUNT, zh));
        assertEquals("通用", TellomiSettingsModel.pageTitle(TellomiSettingsModel.ROW_GENERAL, zh));
        assertEquals("支持", TellomiSettingsModel.pageTitle(TellomiSettingsModel.ROW_SUPPORT, zh));
        for (int id : TellomiSettingsModel.ACCOUNT_IDS) {
            assertFalse(TellomiSettingsModel.destination(id).isEmpty());
        }
        for (int id : TellomiSettingsModel.GENERAL_IDS) {
            assertFalse(TellomiSettingsModel.destination(id).isEmpty());
        }
        for (int id : TellomiSettingsModel.SUPPORT_IDS) {
            assertFalse(TellomiSettingsModel.destination(id).isEmpty());
        }
        assertEquals("", TellomiSettingsModel.destination(TellomiSettingsModel.TIPS_ID));
        assertEquals("", TellomiSettingsModel.destination(TellomiSettingsModel.WATCH_ID));
        assertEquals("help", TellomiSettingsModel.destination(TellomiSettingsModel.FAQ_ID));
    }

    @Test
    public void releaseHomeKeepsTheUpdateRowAboveTheVersion() {
        List<TellomiSettingsModel.Entry> source = new ArrayList<>();
        source.add(custom(0));
        source.add(row(1, "Account"));
        source.add(row(2, "Chat"));
        source.add(row(3, "Privacy"));
        source.add(row(5, "Notifications"));
        source.add(row(6, "Data"));
        source.add(row(7, "Folders"));
        source.add(row(8, "Devices"));
        source.add(row(9, "Power"));
        source.add(row(10, "Language"));
        source.add(row(40, "Navigation"));
        source.add(shadow());
        source.add(header("Help"));
        source.add(row(17, "Ask"));
        source.add(row(18, "FAQ"));
        source.add(row(23, "Tips"));
        source.add(row(19, "Policy"));
        source.add(row(27, "Licenses"));
        source.add(shadow());
        source.add(row(41, "Updates"));
        source.add(custom(1));

        List<TellomiSettingsModel.Entry> home = TellomiSettingsModel.arrangeEntries(source, TellomiSettingsModel.Copy.resolve("en"));
        assertEquals(Arrays.asList(
            TellomiSettingsModel.ROW_ACCOUNT,
            TellomiSettingsModel.ROW_GENERAL,
            TellomiSettingsModel.ROW_SUPPORT,
            41
        ), rowIds(home));
        int update = indexOf(home, 41);
        assertTrue(update > 0);
        assertEquals(TellomiSettingsModel.KIND_SHADOW, home.get(update - 1).kind);
        assertEquals(TellomiSettingsModel.KIND_CUSTOM, home.get(home.size() - 1).kind);
        assertEquals(Arrays.asList(18, 17, 19, 27), rowIds(TellomiSettingsModel.pageEntries(TellomiSettingsModel.ROW_SUPPORT, source, TellomiSettingsModel.Copy.resolve("zh"))));
        assertEquals("updates", TellomiSettingsModel.destination(41));
    }

    @Test
    public void gatedRowsStayOffThePageWhenTheyWereNotBuilt() {
        List<TellomiSettingsModel.Entry> source = sample(false);
        List<Integer> general = rowIds(TellomiSettingsModel.pageEntries(TellomiSettingsModel.ROW_GENERAL, source, TellomiSettingsModel.Copy.resolve("en")));
        assertFalse(general.contains(11));
        assertFalse(general.contains(12));
        assertFalse(general.contains(25));
        assertTrue(general.contains(5));
        assertTrue(general.contains(40));
    }

    @Test
    public void appleWatchTitleIsDroppedEvenWithAnotherId() {
        List<TellomiSettingsModel.Entry> source = new ArrayList<>();
        source.add(row(1, "Account"));
        source.add(row(99, "Apple Watch"));
        List<TellomiSettingsModel.Entry> home = TellomiSettingsModel.arrangeEntries(source, TellomiSettingsModel.Copy.resolve("de"));
        assertFalse(rowIds(home).contains(99));
        assertFalse(rowIds(home).contains(1));
        assertEquals("Account", textOf(home, TellomiSettingsModel.ROW_ACCOUNT));
    }

    @Test
    public void cardShowsPhoneAndEmailTogether() {
        TellomiSettingsModel.Copy zh = TellomiSettingsModel.Copy.resolve("zh-Hans");
        TellomiSettingsModel.Card both = TellomiSettingsModel.card("Ada", "+86 138 0000 0000", "ada@tellomi.cn", "ada", zh);
        assertEquals("Ada", both.title);
        assertEquals("手机号 +86 138 0000 0000", both.phoneLine);
        assertEquals("邮箱 ada@tellomi.cn", both.emailLine);
        assertEquals("用户名 @ada", both.usernameLine);
        assertEquals("手机号 +86 138 0000 0000\n邮箱 ada@tellomi.cn\n用户名 @ada", both.subtitle);

        TellomiSettingsModel.Card emailOnly = TellomiSettingsModel.card("Ada", null, "ada@tellomi.cn", " @ada ", TellomiSettingsModel.Copy.resolve("en"));
        assertNull(emailOnly.phoneLine);
        assertEquals("Email ada@tellomi.cn", emailOnly.emailLine);
        assertEquals("Username @ada", emailOnly.usernameLine);
        assertEquals("Email ada@tellomi.cn\nUsername @ada", emailOnly.subtitle);

        TellomiSettingsModel.Card empty = TellomiSettingsModel.card(null, "  ", "", null, TellomiSettingsModel.Copy.resolve("en"));
        assertEquals("", empty.title);
        assertEquals("", empty.subtitle);
    }

    @Test
    public void helpUrlUsesAppConfigOrTheHelpCenter() {
        assertEquals("https://tellomi.cn/help", TellomiSettingsModel.DEFAULT_HELP_URL);
        assertFalse(TellomiSettingsModel.DEFAULT_HELP_URL.endsWith("/"));
        assertEquals("tellomi_help_url", TellomiSettingsModel.HELP_CONFIG_KEY);
        assertEquals("https://tellomi.cn/help", TellomiSettingsModel.normalizeHelpUrl(null));
        assertEquals("https://tellomi.cn/help", TellomiSettingsModel.normalizeHelpUrl("  "));
        assertEquals("https://tellomi.cn/help", TellomiSettingsModel.normalizeHelpUrl("tellomi.cn/help"));
        assertEquals("https://tellomi.cn/help", TellomiSettingsModel.normalizeHelpUrl("javascript:alert(1)"));
        assertEquals("https://help.example/docs", TellomiSettingsModel.normalizeHelpUrl("  https://help.example/docs  "));
        assertEquals("http://10.0.0.8/help", TellomiSettingsModel.normalizeHelpUrl("http://10.0.0.8/help"));
    }

    @Test
    public void sectionClickOpensAChildAndNotAGroupOrTips() {
        List<Integer> opened = new ArrayList<>();
        TellomiSettingsModel.RowOpener opener = opened::add;
        TellomiSettingsModel.dispatch(opener, 3);
        TellomiSettingsModel.dispatch(opener, TellomiSettingsModel.ROW_ACCOUNT);
        TellomiSettingsModel.dispatch(opener, TellomiSettingsModel.TIPS_ID);
        TellomiSettingsModel.dispatch(opener, TellomiSettingsModel.WATCH_ID);
        TellomiSettingsModel.dispatch(null, 3);
        assertEquals(Collections.singletonList(3), opened);
    }

    @Test
    public void packKeysAreTellomiSettingsAndMatchTheBundles() throws Exception {
        int settings = 0;
        Map<String, String> english = load("TMessagesProj/tellomi/res/values/strings.xml");
        Map<String, String> chinese = load("TMessagesProj/tellomi/res/values-zh/strings.xml");
        Map<String, String> hans = load("TMessagesProj/tellomi/res/values-b+zh+Hans/strings.xml");
        Map<String, String> cn = load("TMessagesProj/tellomi/res/values-zh-rCN/strings.xml");
        for (TellomiPack.Entry entry : TellomiPack.entries()) {
            if (!entry.key.startsWith("Tellomi.Settings.")) {
                continue;
            }
            settings++;
            assertTrue(entry.key, TellomiSettingsModel.validKey(entry.key));
            assertFalse(entry.key.contains("-"));
            String name = TellomiPack.resourceName(entry.key);
            assertEquals(entry.key, entry.en, english.get(name));
            assertEquals(entry.key, entry.zh, chinese.get(name));
            assertEquals(entry.zh, hans.get(name));
            assertEquals(entry.zh, cn.get(name));
        }
        assertEquals(7, settings);
        TellomiPack.Source japanese = key -> "Tellomi.Settings.Help".equals(key) ? "ヘルプ" : null;
        assertEquals("ヘルプ", TellomiSettingsModel.Copy.resolve("ja", japanese).help);
        assertEquals("Account", TellomiSettingsModel.Copy.resolve("ja", japanese).account);
        assertEquals("帮助中心", TellomiSettingsModel.Copy.resolve("zh-Hant").help);
        assertFalse(TellomiSettingsModel.validKey("Tellomi.Settings.Help-Center"));
        assertFalse(TellomiSettingsModel.validKey("Settings.Help"));
    }

    @Test
    public void settingsHooksStaySmallAndLeaveLoginAlone() throws Exception {
        String settings = read("TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java");
        String config = read("TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java");
        String profile = read("TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java");
        String links = read("TMessagesProj/src/main/java/org/telegram/ui/LinkManager.java");
        String login = read("TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java");
        String layout = read("TMessagesProj/tellomi/java/org/telegram/ui/TellomiSettingsLayout.java");
        String buildVars = read("TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java");
        String help = read("TMessagesProj/tellomi/java/org/telegram/messenger/TellomiHelp.java");
        assertTrue(settings.contains("TellomiSettingsLayout.arrange(items)"));
        assertTrue(settings.contains("TellomiSettingsLayout.applyCard(currentAccount, user, titleView, subtitleView, topView)"));
        assertTrue(settings.contains("TellomiSettingsLayout.section(item.id, this::openTellomiSettingsRow)"));
        assertTrue(settings.contains("TellomiSettingsLayout.helpUrl(currentAccount)"));
        assertTrue(config.contains("ofString(\"tellomi_help_url\", TellomiHelp.URL)"));
        assertTrue(buildVars.contains("HELP_URL = TellomiHelp.URL"));
        assertTrue(help.contains("https://tellomi.cn/help"));
        assertFalse(help.contains("https://tellomi.cn/help/"));
        assertTrue(profile.contains("TellomiSettingsLayout.helpUrl(currentAccount)"));
        assertTrue(profile.contains("req2.url = TellomiSettingsLayout.helpUrl(currentAccount)"));
        assertFalse(profile.contains("TelegramFaqUrl"));
        assertTrue(links.contains("TellomiSettingsLayout.helpUrl(currentAccount)"));
        assertFalse(login.contains("TellomiSettingsLayout"));
        assertFalse(login.contains("TellomiSettingsModel"));
        assertTrue(layout.contains("finishFragment()"));
        assertTrue(layout.contains("dispatch(opener::open, item.id)"));
        assertTrue(layout.contains("R.drawable.msg_settings"));
        assertTrue(layout.contains("R.drawable.settings_account"));
        assertTrue(layout.contains("R.drawable.settings_faq"));
        assertFalse(layout.contains("settings_chat"));
        assertTrue(layout.contains("addOnAttachStateChangeListener"));
        assertTrue(layout.contains("applySideMargins"));
        assertTrue(count("TellomiSettingsLayout", settings) <= 6);
    }

    private static List<TellomiSettingsModel.Entry> sample(boolean gated) {
        List<TellomiSettingsModel.Entry> items = new ArrayList<>();
        items.add(custom(0));
        items.add(other(0));
        items.add(shadow());
        items.add(header("Accounts"));
        items.add(other(1));
        items.add(shadow());
        items.add(row(25, "Wallet"));
        items.add(row(1, "Account"));
        items.add(row(2, "Chat"));
        items.add(row(3, "Privacy"));
        items.add(row(5, "Notifications"));
        items.add(row(6, "Data"));
        items.add(row(7, "Folders"));
        items.add(row(8, "Devices"));
        items.add(row(9, "Power"));
        items.add(row(10, "Language"));
        items.add(row(40, "Navigation"));
        items.add(shadow());
        if (gated) {
            items.add(row(11, "Premium"));
            items.add(row(12, "Stars"));
            items.add(row(13, "Ton"));
            items.add(row(15, "Business"));
            items.add(row(16, "Gifts"));
            items.add(shadow());
        }
        items.add(header("Help"));
        items.add(row(17, "Ask"));
        items.add(row(18, "FAQ"));
        items.add(row(23, "Tips"));
        items.add(row(28, "Apple Watch"));
        items.add(row(19, "Policy"));
        items.add(row(27, "Licenses"));
        items.add(shadow());
        items.add(header("Experimental"));
        items.add(row(24, "Round"));
        items.add(row(26, "Audio"));
        items.add(shadow());
        items.add(header("Debug"));
        items.add(row(20, "Logs"));
        items.add(row(21, "Last"));
        items.add(row(22, "Clear"));
        items.add(shadow());
        items.add(row(41, "Updates"));
        items.add(custom(1));
        if (!gated) {
            items.removeIf(entry -> entry.id == 25);
        }
        return items;
    }

    private static List<Integer> rowIds(List<TellomiSettingsModel.Entry> items) {
        List<Integer> ids = new ArrayList<>();
        for (TellomiSettingsModel.Entry entry : items) {
            if (entry.kind == TellomiSettingsModel.KIND_ROW) {
                ids.add(entry.id);
            }
        }
        return ids;
    }

    private static int indexOf(List<TellomiSettingsModel.Entry> items, int id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id && items.get(i).kind == TellomiSettingsModel.KIND_ROW) {
                return i;
            }
        }
        return -1;
    }

    private static String textOf(List<TellomiSettingsModel.Entry> items, int id) {
        for (TellomiSettingsModel.Entry entry : items) {
            if (entry.id == id) {
                return entry.text;
            }
        }
        return null;
    }

    private static TellomiSettingsModel.Entry row(int id, String text) {
        return new TellomiSettingsModel.Entry(id, TellomiSettingsModel.KIND_ROW, text, id);
    }

    private static TellomiSettingsModel.Entry header(String text) {
        return new TellomiSettingsModel.Entry(0, TellomiSettingsModel.KIND_HEADER, text, -1);
    }

    private static TellomiSettingsModel.Entry shadow() {
        return new TellomiSettingsModel.Entry(0, TellomiSettingsModel.KIND_SHADOW, null, -1);
    }

    private static TellomiSettingsModel.Entry custom(int id) {
        return new TellomiSettingsModel.Entry(id, TellomiSettingsModel.KIND_CUSTOM, null, -1);
    }

    private static TellomiSettingsModel.Entry other(int id) {
        return new TellomiSettingsModel.Entry(id, TellomiSettingsModel.KIND_OTHER, null, -1);
    }

    private static int count(String needle, String haystack) {
        int n = 0;
        int from = 0;
        while (from >= 0) {
            from = haystack.indexOf(needle, from);
            if (from < 0) {
                break;
            }
            n++;
            from += needle.length();
        }
        return n;
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(find(relative).toPath()), StandardCharsets.UTF_8);
    }

    private static Map<String, String> load(String relative) throws Exception {
        String xml = read(relative);
        Map<String, String> values = new HashMap<>();
        Matcher matcher = Pattern.compile("<string name=\"([^\"]+)\">([^<]*)</string>").matcher(xml);
        while (matcher.find()) {
            values.put(matcher.group(1), matcher.group(2).replace("\\'", "'"));
        }
        return values;
    }

    private static File find(String relative) {
        File file = new File(relative);
        if (!file.isFile()) {
            file = new File("TMessagesProj", relative);
        }
        assertTrue(file.getPath(), file.isFile());
        return file;
    }
}
