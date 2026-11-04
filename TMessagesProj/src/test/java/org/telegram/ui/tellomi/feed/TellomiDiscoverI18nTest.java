package org.telegram.ui.tellomi.feed;

import org.junit.Test;
import org.telegram.ui.TellomiPack;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiDiscoverI18nTest {

    @Test
    public void japanesePackWinsOverBundledEnglish() {
        TellomiPack.Source japanese = new TellomiPack.Source() {
            @Override
            public String get(String key) {
                if ("Tellomi.Discover.Recommend".equals(key)) {
                    return "おすすめ";
                }
                if ("Tellomi.Discover.Feed".equals(key)) {
                    return "発見";
                }
                if ("Tellomi.Discover.City.cn_chengdu".equals(key)) {
                    return "成都ジャ";
                }
                return null;
            }
        };
        TellomiDiscover.Copy copy = TellomiDiscover.Copy.resolve("ja", japanese);
        assertEquals("おすすめ", copy.recommend);
        assertNotEquals("For You", copy.recommend);
        assertNotEquals("推荐", copy.recommend);
        assertEquals("Following", copy.following);
        assertEquals("発見", org.telegram.ui.TellomiTabNavigation.Copy.resolve("ja", japanese).feed);
        assertEquals("成都ジャ", TellomiDiscoverCity.name("cn:chengdu", "ja", japanese));
        assertEquals("成都", TellomiDiscoverCity.name("cn:chengdu", "zh"));
        assertEquals("Chengdu", TellomiDiscoverCity.name("cn:chengdu", "ja"));
    }

    @Test
    public void stringResourcesMatchTheLangpackKeys() throws Exception {
        Map<String, String> english = load("tellomi/res/values/strings.xml");
        Map<String, String> chinese = load("tellomi/res/values-zh/strings.xml");
        Map<String, String> hans = load("tellomi/res/values-b+zh+Hans/strings.xml");
        Map<String, String> cn = load("tellomi/res/values-zh-rCN/strings.xml");
        for (TellomiPack.Entry entry : TellomiPack.entries()) {
            String name = TellomiPack.resourceName(entry.key);
            assertEquals(entry.key, entry.en, english.get(name));
            assertEquals(entry.key, entry.zh, chinese.get(name));
            assertEquals(entry.zh, hans.get(name));
            assertEquals(entry.zh, cn.get(name));
        }
        for (String[] row : TellomiDiscoverCity.displayRows()) {
            String key = TellomiDiscoverCity.displayKey(row[0]);
            String name = TellomiPack.resourceName(key);
            assertEquals(key, row[2], english.get(name));
            assertEquals(key, row[1], chinese.get(name));
            assertEquals(row[1], hans.get(name));
            assertEquals(row[1], cn.get(name));
        }
        assertEquals("Tellomi.Discover.City.cn_hong_kong", TellomiDiscoverCity.displayKey("cn:hong-kong"));
        assertEquals("Tellomi.Discover.City.us_new_york", TellomiDiscoverCity.displayKey("us:new-york"));
        assertEquals("Tellomi.Discover.City.us_los_angeles", TellomiDiscoverCity.displayKey("us:los-angeles"));
        assertEquals("Tellomi.Discover.City.us_san_francisco", TellomiDiscoverCity.displayKey("us:san-francisco"));
        assertEquals("Tellomi_Discover_City_cn_hong_kong", TellomiPack.resourceName("Tellomi.Discover.City.cn_hong_kong"));
        assertEquals("Tellomi_Discover_City_us_new_york", TellomiPack.resourceName("Tellomi.Discover.City.us_new_york"));
        assertEquals("Tellomi_Discover_City_us_los_angeles", TellomiPack.resourceName("Tellomi.Discover.City.us_los_angeles"));
        assertEquals("Tellomi_Discover_City_us_san_francisco", TellomiPack.resourceName("Tellomi.Discover.City.us_san_francisco"));
        assertNull(TellomiPack.find("Tellomi.Discover.Tab.Feed"));
        assertNull(TellomiPack.find("Tellomi.Discover.LegacyEmptyTitle"));
        assertNull(TellomiPack.find("Tellomi.Signup.AvatarFailed"));
        assertEquals("Discover", TellomiPack.bundled("Tellomi.Discover.Feed", "en"));
        assertEquals("发现", TellomiPack.bundled("Tellomi.Discover.Feed", "zh-Hant"));
    }

    private static Map<String, String> load(String relative) throws Exception {
        File file = new File(relative);
        if (!file.isFile()) {
            file = new File("TMessagesProj", relative);
        }
        assertTrue(file.getPath(), file.isFile());
        String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        Matcher matcher = Pattern.compile("<string name=\"([^\"]+)\">([^<]*)</string>").matcher(xml);
        while (matcher.find()) {
            values.put(matcher.group(1), unescape(matcher.group(2)));
        }
        return values;
    }

    private static String unescape(String value) {
        return value.replace("\\'", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
