package org.telegram.messenger;

import java.util.List;
import java.util.Locale;

/**
 * Unauthenticated entry skips the welcome carousel and opens the phone screen.
 * {@code IntroActivity} stays in the tree. Set {@link #SKIP_WELCOME} to false
 * to show the carousel again.
 *
 * <p>A system language that is not built in (Simplified Chinese is the usual
 * case) is taken from the loaded language list. A language written by Settings
 * is not replaced.
 */
public final class TellomiIntro {

    /** Welcome page is hidden, not removed. */
    public static final boolean SKIP_WELCOME = true;

    private TellomiIntro() {
    }

    /**
     * Cold start with no activated account. {@code savedViewNum != 0} already
     * resumed a login step before this skip existed.
     */
    public static boolean openPhoneLogin(int savedViewNum) {
        return SKIP_WELCOME || savedViewNum != 0;
    }

    /**
     * Short name to apply instead of {@code currentShortName}, or null to keep it.
     * {@code languageSaved} is the {@code mainconfig} {@code language} value Settings writes.
     */
    public static String languageToApply(boolean languageSaved, String currentShortName,
            String systemLanguage, String systemCountry, String systemScript,
            List<String> availableShortNames) {
        if (!SKIP_WELCOME || languageSaved) {
            return null;
        }
        String match = matchShortName(systemLanguage, systemCountry, systemScript, availableShortNames);
        if (match == null || norm(match).equals(norm(currentShortName))) {
            return null;
        }
        return match;
    }

    /**
     * Best loaded short name for the device locale, or null when the list has
     * no row for that language. Chinese with no country prefers Simplified
     * ({@code zh_hans} / {@code zh_cn}); Taiwan, Hong Kong, Macau, and Hant
     * prefer Traditional.
     */
    public static String matchShortName(String systemLanguage, String systemCountry, String systemScript,
            List<String> availableShortNames) {
        if (availableShortNames == null || availableShortNames.isEmpty()) {
            return null;
        }
        String language = norm(systemLanguage);
        if (language.isEmpty()) {
            language = "en";
        }
        String country = norm(systemCountry);
        String script = norm(systemScript);
        String alias = LocaleController.getLocaleAlias(language);

        String exact = null;
        String byLanguage = null;
        String chinese = null;
        int chineseScore = 0;
        boolean traditional = isTraditionalChinese(language, country, script);

        for (int i = 0; i < availableShortNames.size(); i++) {
            String raw = availableShortNames.get(i);
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String name = norm(raw);
            if (exact == null && isExact(name, language, country, script)) {
                exact = raw;
            }
            if (byLanguage == null && (name.equals(language) || (alias != null && name.equals(alias)))) {
                byLanguage = raw;
            }
            if ("zh".equals(language)) {
                int score = chineseScore(name, traditional);
                if (score > chineseScore) {
                    chineseScore = score;
                    chinese = raw;
                }
            }
        }
        if (exact != null) {
            return exact;
        }
        if (chinese != null) {
            return chinese;
        }
        return byLanguage;
    }

    private static boolean isExact(String name, String language, String country, String script) {
        if (!country.isEmpty() && name.equals(language + "_" + country)) {
            return true;
        }
        if (!script.isEmpty() && name.equals(language + "_" + script)) {
            return true;
        }
        return !script.isEmpty() && !country.isEmpty() && name.equals(language + "_" + script + "_" + country);
    }

    private static boolean isTraditionalChinese(String language, String country, String script) {
        if (!"zh".equals(language)) {
            return false;
        }
        return "hant".equals(script) || "tw".equals(country) || "hk".equals(country) || "mo".equals(country);
    }

    /** Higher is a better Chinese pack. 0 is not a Chinese row. */
    private static int chineseScore(String name, boolean traditional) {
        if (traditional) {
            if ("zh_hant".equals(name)) {
                return 60;
            }
            if ("zh_tw".equals(name)) {
                return 50;
            }
            if ("zh_hk".equals(name)) {
                return 40;
            }
            if ("zh_mo".equals(name)) {
                return 30;
            }
            if ("zh".equals(name)) {
                return 20;
            }
            if ("zh_hans".equals(name) || "zh_cn".equals(name) || "zh_sg".equals(name)) {
                return 10;
            }
            if (name.startsWith("zh_")) {
                return 5;
            }
            return 0;
        }
        if ("zh_hans".equals(name)) {
            return 60;
        }
        if ("zh_cn".equals(name)) {
            return 50;
        }
        if ("zh_sg".equals(name)) {
            return 40;
        }
        if ("zh".equals(name)) {
            return 30;
        }
        if ("zh_hant".equals(name) || "zh_tw".equals(name) || "zh_hk".equals(name) || "zh_mo".equals(name)) {
            return 10;
        }
        if (name.startsWith("zh_")) {
            return 5;
        }
        return 0;
    }

    private static String norm(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).replace('-', '_');
    }
}
