/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Login country and the Chinese langpack choice live here
 * so src/main stays a short hook. See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.messenger;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

public final class TellomiDefaults {

    /** Calling code shown on the login screen until the user edits it. */
    public static final String LOGIN_COUNTRY_CODE = "86";
    /** ISO country that owns {@link #LOGIN_COUNTRY_CODE}. */
    public static final String LOGIN_COUNTRY_ISO = "CN";

    /** Android langpack code for Simplified Chinese. */
    public static final String LANG_ZH_HANS = "zh-hans";
    /** Android langpack code for Traditional Chinese. */
    public static final String LANG_ZH_HANT = "zh-hant";

    private TellomiDefaults() {
    }

    /**
     * Langpack to request when the system language is Chinese.
     * Simplified (Hans, CN, SG, or language-only {@code zh}) is {@code zh-hans}.
     * Traditional (Hant, TW, HK, MO) is {@code zh-hant}. Anything else is null.
     */
    public static String systemChineseLangCode(Locale locale) {
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) {
            return null;
        }
        String script = locale.getScript();
        if ("Hant".equalsIgnoreCase(script)) {
            return LANG_ZH_HANT;
        }
        if ("Hans".equalsIgnoreCase(script)) {
            return LANG_ZH_HANS;
        }
        String country = locale.getCountry();
        if ("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country)) {
            return LANG_ZH_HANT;
        }
        return LANG_ZH_HANS;
    }

    /**
     * Built-in list entry. {@code pathToFile} stays null so Settings shows 简体中文 / 繁體中文
     * even when langpack.getLanguages has not returned, and the row is not dropped as unofficial.
     */
    public static LocaleController.LocaleInfo builtinChinese(boolean traditional) {
        LocaleController.LocaleInfo info = new LocaleController.LocaleInfo();
        info.name = traditional ? "繁體中文" : "简体中文";
        info.nameEnglish = traditional ? "Chinese (Traditional)" : "Chinese (Simplified)";
        info.shortName = traditional ? "zh_hant" : "zh_hans";
        info.pluralLangCode = "zh";
        info.baseLangCode = "";
        info.pathToFile = null;
        info.builtIn = true;
        info.serverIndex = 0;
        return info;
    }

    /** Remote locale for {@code zh-hans} or {@code zh-hant}. The strings come from langpack.getLangPack. */
    public static LocaleController.LocaleInfo remoteChineseLocale(String langCode) {
        String code = langCode == null ? LANG_ZH_HANS : langCode.replace('_', '-').toLowerCase(Locale.US);
        boolean traditional = LANG_ZH_HANT.equals(code);
        LocaleController.LocaleInfo info = builtinChinese(traditional);
        info.builtIn = false;
        info.pathToFile = "remote";
        info.serverIndex = Integer.MAX_VALUE;
        return info;
    }

    /** First entry of Settings.System system_locales, or null. This is the phone language, not the locale the app installed. */
    private static Locale systemLocalesSetting() {
        try {
            if (ApplicationLoader.applicationContext == null) {
                return null;
            }
            String raw = Settings.System.getString(ApplicationLoader.applicationContext.getContentResolver(), "system_locales");
            if (raw == null || raw.length() == 0) {
                return null;
            }
            String first = raw.split(",")[0].trim();
            if (first.length() == 0) {
                return null;
            }
            return Locale.forLanguageTag(first.replace('_', '-'));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The device locale, not the locale {@link LocaleController} may have installed
     * on the app. Android reports {@link Locale#getDefault()} as English when the
     * process has already applied the bundled default, even if the phone is Chinese.
     */
    public static Locale deviceLocale() {
        Locale tagged = systemLocalesSetting();
        if (systemChineseLangCode(tagged) != null) {
            return tagged;
        }
        try {
            Configuration config = Resources.getSystem().getConfiguration();
            if (config != null) {
                if (Build.VERSION.SDK_INT >= 24 && config.getLocales() != null && !config.getLocales().isEmpty()) {
                    Locale locale = config.getLocales().get(0);
                    if (systemChineseLangCode(locale) != null) {
                        return locale;
                    }
                    Locale fallback = Locale.getDefault();
                    if (systemChineseLangCode(fallback) != null) {
                        return fallback;
                    }
                    if (locale != null) {
                        return locale;
                    }
                }
                if (config.locale != null && systemChineseLangCode(config.locale) != null) {
                    return config.locale;
                }
                if (config.locale != null) {
                    Locale fallback = Locale.getDefault();
                    if (systemChineseLangCode(fallback) != null) {
                        return fallback;
                    }
                    return config.locale;
                }
            }
        } catch (Throwable ignored) {
        }
        return Locale.getDefault();
    }

    /**
     * True when the active app language is Chinese ({@code zh}, {@code zh-hans}, {@code zh-hant}).
     * The in-memory language wins, then a language saved in settings. The phone locale
     * is used only until a language has been applied, so Italian on a Chinese phone
     * does not keep the Chinese string overlay.
     */
    public static boolean uiIsChinese() {
        try {
            LocaleController.LocaleInfo info = LocaleController.peekCurrentLocaleInfo();
            if (info != null && info.shortName != null && info.shortName.length() > 0) {
                return info.shortName.toLowerCase(Locale.US).startsWith("zh");
            }
        } catch (Throwable ignored) {
        }
        try {
            String saved = MessagesController.getGlobalMainSettings().getString("language", null);
            if (saved != null && saved.length() > 0) {
                return saved.toLowerCase(Locale.US).startsWith("zh");
            }
        } catch (Throwable ignored) {
        }
        return systemChineseLangCode(deviceLocale()) != null;
    }

    /**
     * A flavor string in Simplified Chinese. The English localization bin is not
     * consulted: intro and login copy live there and would stay English on a Chinese phone.
     */
    public static String chineseResource(int resId) {
        String simplified = resourceFor(Locale.SIMPLIFIED_CHINESE, resId);
        String english = resourceFor(Locale.ENGLISH, resId);
        if (simplified != null && simplified.length() > 0 && (english == null || !simplified.equals(english))) {
            return simplified;
        }
        // values-zh-rCN and values-b+zh+Hans are the qualifiers some devices match
        // before the plain values-zh folder. Try each before giving up.
        String hans = resourceFor(Locale.forLanguageTag("zh-Hans"), resId);
        if (hans != null && hans.length() > 0 && (english == null || !hans.equals(english))) {
            return hans;
        }
        String zh = resourceFor(new Locale("zh"), resId);
        if (zh != null && zh.length() > 0 && (english == null || !zh.equals(english))) {
            return zh;
        }
        return simplified == null ? "" : simplified;
    }

    /** The same resource in English, used to tell a real Chinese translation from the default. */
    public static String englishResource(int resId) {
        return resourceFor(Locale.ENGLISH, resId);
    }

    private static String resourceFor(Locale locale, int resId) {
        if (resId == 0 || ApplicationLoader.applicationContext == null || locale == null) {
            return "";
        }
        try {
            // Copying the live configuration keeps the activity locale on some
            // devices, so a zh lookup and an en lookup both come back English.
            Configuration config = new Configuration();
            config.setLocale(locale);
            if (Build.VERSION.SDK_INT >= 24) {
                config.setLocales(new android.os.LocaleList(locale));
            }
            String value = ApplicationLoader.applicationContext.createConfigurationContext(config).getResources().getString(resId);
            return TellomiBrand.rewrite(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * English and both Chinese packs ship a full local bin. They must apply
     * without waiting for {@code langpack.getLangPack}. A catalog reply that
     * sets {@code pathToFile} to {@code remote} used to leave the login page
     * unchanged until that request returned.
     */
    public static boolean completeLocalBin(String shortName) {
        if (shortName == null || shortName.length() == 0) {
            return false;
        }
        String code = shortName.replace('-', '_').toLowerCase(Locale.US);
        return "en".equals(code) || "zh".equals(code) || LANG_ZH_HANS.equals(code.replace('_', '-')) || LANG_ZH_HANT.equals(code.replace('_', '-'));
    }

    /** True when a built-in English or Chinese row should drop a {@code remote} placeholder. */
    public static boolean clearRemotePlaceholder(String shortName, String pathToFile) {
        return completeLocalBin(shortName) && "remote".equals(pathToFile);
    }

    /**
     * Simplified and Traditional Chinese ship in {@code localization_zh.bin}.
     * They must stay built-in. A remote row with no downloaded file used to
     * replace them and leave every string on the English bin.
     */
    public static boolean bundledChinese(LocaleController.LocaleInfo info) {
        if (info == null || !info.builtIn || info.pathToFile != null || info.shortName == null) {
            return false;
        }
        String code = info.shortName.replace('_', '-').toLowerCase(Locale.US);
        return LANG_ZH_HANS.equals(code) || LANG_ZH_HANT.equals(code);
    }

    /**
     * A remote or unofficial row has a langpack file from {@code langpack.getLangPack}.
     * Those strings are used for every language, not only Chinese.
     */
    public static boolean readDownloadedPack(boolean remote, boolean unofficial) {
        return remote || unofficial;
    }

    /**
     * True when applying this built-in row must call {@code langpack.getLangPack}.
     * English ships the full default table. Simplified and Traditional Chinese ship
     * the full {@code localization_zh.bin} and stay built-in. The other built-in
     * bins (it, es, de, nl, ar, pt_br, ko, uk, ru) are a small subset, so leaving
     * them local looks like English.
     */
    public static boolean needsServerLangpack(LocaleController.LocaleInfo info) {
        if (info == null || !info.builtIn || info.shortName == null || info.shortName.length() == 0) {
            return false;
        }
        if (info.pathToFile != null && info.pathToFile.length() > 0) {
            return false;
        }
        String code = info.shortName.replace('-', '_').toLowerCase(Locale.US);
        return !"en".equals(code) && !"zh".equals(code) && !"zh_hans".equals(code) && !"zh_hant".equals(code);
    }

    /**
     * Marks {@code info} remote so {@code LocaleController.applyLanguage} downloads it.
     * @return true when the row changed
     */
    public static boolean attachServerLangpack(LocaleController.LocaleInfo info) {
        if (!needsServerLangpack(info)) {
            return false;
        }
        info.pathToFile = "remote";
        return true;
    }

    /**
     * True when this locale's strings should be read from the downloaded android langpack
     * even though the row is still the built-in Chinese entry. Other languages are read
     * once {@link #attachServerLangpack} or {@code langpack.getLanguages} marks them remote.
     */
    public static boolean useCloudStrings(LocaleController.LocaleInfo info) {
        if (info == null || info.shortName == null) {
            return false;
        }
        String code = info.shortName.replace('_', '-').toLowerCase(Locale.US);
        return LANG_ZH_HANS.equals(code) || LANG_ZH_HANT.equals(code);
    }

    /**
     * Locale installed for a built-in row. Simplified and Traditional must not
     * both become {@code new Locale("zh")} via the shared plural code.
     */
    public static Locale androidLocale(String shortName) {
        if (shortName == null || shortName.length() == 0) {
            return null;
        }
        String code = shortName.replace('_', '-').toLowerCase(Locale.US);
        if ("en".equals(code)) {
            return Locale.ENGLISH;
        }
        if ("zh".equals(code) || "zh-cn".equals(code) || LANG_ZH_HANS.equals(code)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if ("zh-tw".equals(code) || "zh-hk".equals(code) || "zh-mo".equals(code) || LANG_ZH_HANT.equals(code)) {
            return Locale.TRADITIONAL_CHINESE;
        }
        return null;
    }

    public static boolean traditionalShortName(String shortName) {
        Locale locale = androidLocale(shortName);
        return locale != null && "TW".equals(locale.getCountry());
    }

    public static boolean traditionalUi() {
        try {
            LocaleController.LocaleInfo info = LocaleController.peekCurrentLocaleInfo();
            return info != null && traditionalShortName(info.shortName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Flavor string for the active Chinese script. Traditional falls back to Simplified. */
    public static String scriptResource(int resId, boolean traditional) {
        if (!traditional) {
            return chineseResource(resId);
        }
        String english = resourceFor(Locale.ENGLISH, resId);
        String tw = resourceFor(Locale.TRADITIONAL_CHINESE, resId);
        if (differs(tw, english)) {
            return tw;
        }
        String hant = resourceFor(Locale.forLanguageTag("zh-Hant"), resId);
        if (differs(hant, english)) {
            return hant;
        }
        return chineseResource(resId);
    }

    private static boolean differs(String value, String english) {
        return value != null && value.length() > 0 && (english == null || !value.equals(english));
    }
}
