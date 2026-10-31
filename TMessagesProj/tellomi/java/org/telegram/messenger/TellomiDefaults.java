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
     * True when auth chrome and bundled flavor strings should be Simplified Chinese.
     * A language the user saved in settings wins. Until then, a Chinese phone wins
     * even if the activity configuration is still English.
     */
    public static boolean uiIsChinese() {
        try {
            String saved = MessagesController.getGlobalMainSettings().getString("language", null);
            if (saved != null && saved.length() > 0) {
                return saved.toLowerCase(Locale.US).startsWith("zh");
            }
        } catch (Throwable ignored) {
        }
        if (systemChineseLangCode(deviceLocale()) != null) {
            return true;
        }
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            return locale != null && "zh".equalsIgnoreCase(locale.getLanguage());
        } catch (Throwable ignored) {
            return false;
        }
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
            Configuration config = new Configuration(ApplicationLoader.applicationContext.getResources().getConfiguration());
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

    /** True when this locale's strings should be read from the downloaded android langpack. */
    public static boolean useCloudStrings(LocaleController.LocaleInfo info) {
        if (info == null || info.shortName == null) {
            return false;
        }
        String code = info.shortName.replace('_', '-').toLowerCase(Locale.US);
        return LANG_ZH_HANS.equals(code) || LANG_ZH_HANT.equals(code);
    }
}
