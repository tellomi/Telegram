package org.telegram.ui.tellomi.feed;

import org.telegram.ui.TellomiPack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * City codes for the nearby feed. A code is {@code <iso>:<english-slug>},
 * such as {@code cn:chengdu}. Hong Kong, Macau, and Taiwan use the {@code cn:}
 * prefix. Only a locality name is accepted; a street, a province, or a
 * coordinate never becomes a code.
 */
public final class TellomiDiscoverCity {

    public static final class City {
        public final String code;
        public final String name;

        public City(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    private static final Pattern CODE = Pattern.compile("^[a-z]{2}:[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern COORDINATE = Pattern.compile("^-?\\d{1,3}(\\.\\d+)?$");
    private static final String[] SUFFIXES = {"特别行政区", "特別行政區", "市"};

    private static final class Record {
        final String code;
        final String zh;
        final String en;
        final Set<String> keys;

        Record(String code, String zh, String en, Set<String> keys) {
            this.code = code;
            this.zh = zh;
            this.en = en;
            this.keys = keys;
        }
    }

    private static final List<Record> CATALOG = buildCatalog();

    private TellomiDiscoverCity() {
    }

    /** Format check. {@code hk:}, {@code tw:}, and {@code mo:} are rejected. */
    public static String code(String raw) {
        if (raw == null) {
            return null;
        }
        String value = trim(raw).toLowerCase(Locale.US);
        if (!CODE.matcher(value).matches()) {
            return null;
        }
        String country = value.substring(0, 2);
        if ("hk".equals(country) || "tw".equals(country) || "mo".equals(country)) {
            return null;
        }
        return value;
    }

    /** A code this client will put on a post or a nearby request. */
    public static String knownCode(String raw) {
        String value = code(raw);
        if (value == null) {
            return null;
        }
        for (int i = 0; i < CATALOG.size(); i++) {
            if (CATALOG.get(i).code.equals(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * {@code Address.getLocality()} plus the ISO country. Hong Kong, Macau,
     * and Taiwan map onto {@code cn:}.
     */
    public static String codeFromLocality(String locality, String isoCountryCode) {
        String key = localityKey(locality);
        if (key == null) {
            return null;
        }
        String country = canonicalCountry(isoCountryCode);
        Record match = null;
        for (int i = 0; i < CATALOG.size(); i++) {
            Record record = CATALOG.get(i);
            if (!record.keys.contains(key)) {
                continue;
            }
            if (country != null && !record.code.startsWith(country + ":")) {
                continue;
            }
            if (match != null) {
                return null;
            }
            match = record;
        }
        return match == null ? null : match.code;
    }

    /**
     * Street and state stay in the signature so a placemark cannot be forwarded
     * whole. Only the locality can become a city code.
     */
    public static String cityOnly(String street, String city, String state) {
        return codeFromLocality(city, null);
    }

    public static String displayKey(String code) {
        String value = code(code);
        if (value == null) {
            return null;
        }
        return "Tellomi.Discover.City." + value.replace(':', '_').replace('-', '_');
    }

    static List<String[]> displayRows() {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < CATALOG.size(); i++) {
            Record record = CATALOG.get(i);
            rows.add(new String[] { record.code, record.zh, record.en });
        }
        return rows;
    }

    public static String name(String code, String languageCode) {
        return name(code, languageCode, null);
    }

    public static String name(String code, String languageCode, TellomiPack.Source source) {
        String value = code(code);
        if (value == null) {
            return null;
        }
        String packed = TellomiPack.override(source, displayKey(value));
        if (packed != null) {
            return packed;
        }
        for (int i = 0; i < CATALOG.size(); i++) {
            Record record = CATALOG.get(i);
            if (record.code.equals(value)) {
                return chinese(languageCode) ? record.zh : record.en;
            }
        }
        int colon = value.indexOf(':');
        String slug = colon >= 0 ? value.substring(colon + 1) : value;
        String[] parts = slug.split("-");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                out.append(parts[i].substring(1));
            }
        }
        return out.toString();
    }

    public static List<City> cities(String languageCode) {
        return cities(languageCode, null);
    }

    public static List<City> cities(String languageCode, TellomiPack.Source source) {
        boolean zh = chinese(languageCode);
        List<City> cities = new ArrayList<>();
        for (int i = 0; i < CATALOG.size(); i++) {
            Record record = CATALOG.get(i);
            cities.add(new City(record.code, name(record.code, languageCode, source)));
        }
        final Locale locale = zh ? Locale.CHINA : Locale.ENGLISH;
        Collections.sort(cities, new Comparator<City>() {
            @Override
            public int compare(City left, City right) {
                return left.name.compareToIgnoreCase(right.name) == 0
                    ? left.name.compareTo(right.name)
                    : left.name.toLowerCase(locale).compareTo(right.name.toLowerCase(locale));
            }
        });
        return cities;
    }

    static boolean chinese(String languageCode) {
        if (languageCode == null) {
            return false;
        }
        String value = languageCode.toLowerCase(Locale.US);
        return "zh".equals(value) || value.startsWith("zh-");
    }

    private static String canonicalCountry(String isoCountryCode) {
        if (isoCountryCode == null) {
            return null;
        }
        String value = trim(isoCountryCode).toLowerCase(Locale.US);
        if (value.length() != 2) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 'a' || c > 'z') {
                return null;
            }
        }
        if ("hk".equals(value) || "tw".equals(value) || "mo".equals(value)) {
            return "cn";
        }
        return value;
    }

    private static String localityKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = trim(raw);
        if (value.isEmpty() || COORDINATE.matcher(value).matches()) {
            return null;
        }
        value = value.replace("臺", "台");
        for (int i = 0; i < SUFFIXES.length; i++) {
            String suffix = SUFFIXES[i];
            if (value.endsWith(suffix) && value.length() > suffix.length()) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '-' || c == '·') {
                continue;
            }
            key.append(Character.toLowerCase(c));
        }
        if (key.length() == 0 || key.length() > 40) {
            return null;
        }
        return key.toString();
    }

    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static List<Record> buildCatalog() {
        String[][] rows = {
            {"cn:beijing", "北京", "Beijing"},
            {"cn:shanghai", "上海", "Shanghai"},
            {"cn:tianjin", "天津", "Tianjin"},
            {"cn:chongqing", "重庆", "Chongqing"},
            {"cn:chengdu", "成都", "Chengdu"},
            {"cn:hangzhou", "杭州", "Hangzhou"},
            {"cn:ningbo", "宁波", "Ningbo"},
            {"cn:nanjing", "南京", "Nanjing"},
            {"cn:suzhou", "苏州", "Suzhou"},
            {"cn:wuxi", "无锡", "Wuxi"},
            {"cn:wuhan", "武汉", "Wuhan"},
            {"cn:xian", "西安", "Xi'an", "Xian"},
            {"cn:guangzhou", "广州", "Guangzhou"},
            {"cn:shenzhen", "深圳", "Shenzhen"},
            {"cn:zhuhai", "珠海", "Zhuhai"},
            {"cn:dongguan", "东莞", "Dongguan"},
            {"cn:foshan", "佛山", "Foshan"},
            {"cn:changsha", "长沙", "Changsha"},
            {"cn:zhengzhou", "郑州", "Zhengzhou"},
            {"cn:qingdao", "青岛", "Qingdao"},
            {"cn:jinan", "济南", "Jinan"},
            {"cn:hefei", "合肥", "Hefei"},
            {"cn:fuzhou", "福州", "Fuzhou"},
            {"cn:xiamen", "厦门", "Xiamen"},
            {"cn:kunming", "昆明", "Kunming"},
            {"cn:guiyang", "贵阳", "Guiyang"},
            {"cn:nanning", "南宁", "Nanning"},
            {"cn:haikou", "海口", "Haikou"},
            {"cn:sanya", "三亚", "Sanya"},
            {"cn:shenyang", "沈阳", "Shenyang"},
            {"cn:dalian", "大连", "Dalian"},
            {"cn:harbin", "哈尔滨", "Harbin"},
            {"cn:changchun", "长春", "Changchun"},
            {"cn:shijiazhuang", "石家庄", "Shijiazhuang"},
            {"cn:taiyuan", "太原", "Taiyuan"},
            {"cn:nanchang", "南昌", "Nanchang"},
            {"cn:lanzhou", "兰州", "Lanzhou"},
            {"cn:yinchuan", "银川", "Yinchuan"},
            {"cn:xining", "西宁", "Xining"},
            {"cn:urumqi", "乌鲁木齐", "Urumqi"},
            {"cn:hohhot", "呼和浩特", "Hohhot"},
            {"cn:lhasa", "拉萨", "Lhasa"},
            {"cn:hong-kong", "香港", "Hong Kong"},
            {"cn:macau", "澳门", "Macau", "Macao", "澳門"},
            {"cn:taipei", "台北", "Taipei"},
            {"cn:kaohsiung", "高雄", "Kaohsiung"},
            {"cn:taichung", "台中", "Taichung"},
            {"cn:tainan", "台南", "Tainan"},
            {"us:new-york", "纽约", "New York"},
            {"us:los-angeles", "洛杉矶", "Los Angeles"},
            {"us:san-francisco", "旧金山", "San Francisco"},
            {"gb:london", "伦敦", "London"},
            {"jp:tokyo", "东京", "Tokyo"},
            {"jp:osaka", "大阪", "Osaka"},
            {"kr:seoul", "首尔", "Seoul"},
            {"sg:singapore", "新加坡", "Singapore"},
            {"fr:paris", "巴黎", "Paris"},
            {"de:berlin", "柏林", "Berlin"},
            {"au:sydney", "悉尼", "Sydney"},
            {"ca:toronto", "多伦多", "Toronto"},
            {"th:bangkok", "曼谷", "Bangkok"}
        };
        List<Record> catalog = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            catalog.add(record(rows[i]));
        }
        return catalog;
    }

    private static Record record(String[] row) {
        Set<String> keys = new HashSet<>();
        String code = row[0];
        int colon = code.indexOf(':');
        String slug = colon >= 0 ? code.substring(colon + 1) : code;
        addKey(keys, row[1]);
        addKey(keys, row[2]);
        addKey(keys, slug);
        for (int i = 3; i < row.length; i++) {
            addKey(keys, row[i]);
        }
        return new Record(code, row[1], row[2], keys);
    }

    private static void addKey(Set<String> keys, String value) {
        String key = localityKey(value);
        if (key != null) {
            keys.add(key);
        }
    }
}
