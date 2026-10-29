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

    /** Remote locale for {@code zh-hans} or {@code zh-hant}. The strings come from langpack.getLangPack. */
    public static LocaleController.LocaleInfo remoteChineseLocale(String langCode) {
        boolean traditional = LANG_ZH_HANT.equals(langCode);
        LocaleController.LocaleInfo info = new LocaleController.LocaleInfo();
        info.name = traditional ? "繁體中文" : "简体中文";
        info.nameEnglish = traditional ? "Chinese (Traditional)" : "Chinese (Simplified)";
        info.shortName = (langCode == null ? LANG_ZH_HANS : langCode).replace('-', '_');
        info.pluralLangCode = "zh";
        info.baseLangCode = "";
        info.pathToFile = "remote";
        info.serverIndex = Integer.MAX_VALUE;
        return info;
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
