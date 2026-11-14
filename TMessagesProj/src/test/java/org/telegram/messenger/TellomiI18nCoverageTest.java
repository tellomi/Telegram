package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every Tellomi string key has a value for each langpack.getLanguages code.
 * Placeholders match the English source. Existing strings.xml entries stay.
 */
public class TellomiI18nCoverageTest {

    private static final Pattern STRING = Pattern.compile(
            "<string name=\"([^\"]+)\">([\\s\\S]*?)</string>");
    private static final Pattern SPEC = Pattern.compile("%(?:\\d+\\$)?([sdif])");

    /** langpack code, resource directory that applyLanguage reads. */
    private static final String[][] LANG_DIRS = {
            {"zh-hans", "values-zh"},
            {"zh-hant", "values-zh-rTW"},
            {"en", "values"},
            {"ar", "values-ar"},
            {"be", "values-be"},
            {"ca", "values-ca"},
            {"cs", "values-cs"},
            {"de", "values-de"},
            {"el", "values-el"},
            {"es", "values-es"},
            {"fa", "values-fa"},
            {"fi", "values-fi"},
            {"fr", "values-fr"},
            {"he", "values-iw"},
            {"hi", "values-hi"},
            {"id", "values-in"},
            {"it", "values-it"},
            {"ja", "values-ja"},
            {"ko", "values-ko"},
            {"ms", "values-ms"},
            {"nb", "values-nb"},
            {"nl", "values-nl"},
            {"pl", "values-pl"},
            {"pt-br", "values-pt"},
            {"ro", "values-ro"},
            {"ru", "values-ru"},
            {"sv", "values-sv"},
            {"th", "values-th"},
            {"tr", "values-tr"},
            {"uk", "values-uk"},
            {"uz", "values-uz"},
            {"vi", "values-vi"},
    };

    @Test
    public void traditionalChineseDoesNotShareTheSimplifiedBin() {
        assertEquals("zh", TellomiStringLocales.localeFor("zh", "zh_hans").getLanguage());
        assertEquals("", TellomiStringLocales.localeFor("zh", "zh_hans").getCountry());
        assertEquals("zh", TellomiStringLocales.localeFor("zh", "zh-hans").getLanguage());
        assertEquals("", TellomiStringLocales.localeFor("zh", "zh-hans").getCountry());

        Locale traditional = TellomiStringLocales.localeFor("zh", "zh_hant");
        assertEquals("zh", traditional.getLanguage());
        assertEquals("TW", traditional.getCountry());
        assertEquals("TW", TellomiStringLocales.localeFor("zh", "zh-hant").getCountry());
        assertEquals("TW", TellomiStringLocales.localeFor("zh_tw", "zh_tw").getCountry());

        Locale portuguese = TellomiStringLocales.localeFor("pt", "pt_br");
        assertEquals("pt", portuguese.getLanguage());
        assertEquals("", portuguese.getCountry());
        assertEquals("BR", TellomiStringLocales.localeFor("pt_br", "pt_br").getCountry());
        assertEquals("en", TellomiStringLocales.localeFor("en", "en").getLanguage());

        String controller = read(repoRoot().resolve(
                "TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java"));
        assertTrue(controller.contains("TellomiStringLocales.localeFor"));
    }

    @Test
    public void everyLangpackLanguageHasEveryTellomiKey() throws Exception {
        assertEquals(32, LANG_DIRS.length);
        Path res = repoRoot().resolve("TMessagesProj/src/main/res");
        Map<String, String> english = readFolder(res.resolve("values"));
        List<String> keys = tellomiKeys(english);
        assertFalse(keys.isEmpty());

        for (String[] row : LANG_DIRS) {
            Map<String, String> values = readFolder(res.resolve(row[1]));
            for (String key : keys) {
                String value = values.get(key);
                assertTrue(row[0] + " missing " + key, value != null && value.length() > 0);
                assertEquals(row[0] + " " + key, specs(english.get(key)), specs(value));
            }
        }

        Map<String, String> simplified = readFolder(res.resolve("values-zh-rCN"));
        Map<String, String> builtInPortuguese = readFolder(res.resolve("values-pt-rBR"));
        for (String key : keys) {
            assertTrue("zh-rCN missing " + key, simplified.containsKey(key));
            assertTrue("pt-rBR missing " + key, builtInPortuguese.containsKey(key));
        }

        String zh = read(res.resolve("values-zh/strings.xml"));
        String traditional = read(res.resolve("values-zh-rTW/strings.xml"));
        assertTrue(zh.contains("<string name=\"TellomiTabFeed\">发现</string>"));
        assertTrue(zh.contains("<string name=\"TellomiNavBar\">导航栏</string>"));
        assertTrue(traditional.contains("<string name=\"TellomiTabFeed\">发现</string>"));
        assertTrue(traditional.contains("<string name=\"TellomiNavBar\">导航栏</string>"));
        assertTrue(readFolder(res.resolve("values-zh-rTW")).get("Moments_Title").contains("動態"));
        assertEquals("动态", readFolder(res.resolve("values-zh")).get("Moments_Title"));
    }

    @Test
    public void auditCsvMatchesTheKeysThatWereMissing() throws Exception {
        Path csv = repoRoot().resolve("tellomi/i18n/audit-android.csv");
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        assertEquals("key,file,languages missing before,fixed", lines.get(0));
        Map<String, String> english = readFolder(repoRoot().resolve("TMessagesProj/src/main/res/values"));
        Set<String> expected = new HashSet<>(tellomiKeys(english));
        Set<String> seen = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (String[] row : LANG_DIRS) {
            codes.add(row[0]);
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).length() == 0) {
                continue;
            }
            String[] cells = splitCsv(lines.get(i));
            assertEquals(lines.get(i), 4, cells.length);
            String key = cells[0];
            assertTrue("unexpected csv key " + key, expected.contains(key));
            assertTrue(seen.add(key));
            assertTrue(cells[1].startsWith("TMessagesProj/src/main/res/values"));
            boolean missing = cells[2].length() > 0;
            assertEquals(key, missing ? "yes" : "no", cells[3]);
            if (missing) {
                for (String code : cells[2].split(";")) {
                    assertTrue(key + " " + code, codes.contains(code));
                }
            }
        }
        assertEquals(expected, seen);
    }

    private static List<String> tellomiKeys(Map<String, String> english) {
        List<String> keys = new ArrayList<>();
        for (String key : english.keySet()) {
            if (key.startsWith("Moments_") || key.startsWith("Tellomi")
                    || key.equals("SettingsTools")
                    || key.equals("MainTabsMenuSettings")
                    || key.equals("MainTabsHideLabels")
                    || key.equals("MainTabsButtons")
                    || key.equals("NewContactPhoneNotOnTelegram")
                    || key.equals("NewContactPhoneOnTelegram")
                    || key.equals("NewContactPhoneInContacts")
                    || key.equals("ForwardHideSenderNamePremium")
                    || key.equals("ThemePreviewBetaChat")) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Map<String, String> readFolder(Path folder) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> listing = Files.list(folder)) {
            List<Path> files = new ArrayList<>();
            listing.filter(path -> path.getFileName().toString().endsWith(".xml")
                    && path.getFileName().toString().contains("string")).forEach(files::add);
            for (Path file : files) {
                Matcher matcher = STRING.matcher(read(file));
                while (matcher.find()) {
                    String previous = values.put(matcher.group(1), matcher.group(2));
                    assertTrue(folder + " duplicates " + matcher.group(1), previous == null);
                }
            }
        }
        return values;
    }

    private static List<String> specs(String value) {
        List<String> found = new ArrayList<>();
        Matcher matcher = SPEC.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String[] splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(path.toString(), e);
        }
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("TMessagesProj"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Paths.get("").toAbsolutePath());
    }
}
