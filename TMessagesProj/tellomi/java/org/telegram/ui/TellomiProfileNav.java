package org.telegram.ui;

import java.util.Locale;

/** Navigation label for the profile tab. Not the profile page title. */
public final class TellomiProfileNav {

    private TellomiProfileNav() {
    }

    /** Chinese shows 我. Other languages return null and keep the stock tab string. */
    public static String label(String languageCode) {
        if (!isChinese(languageCode)) {
            return null;
        }
        return "我";
    }

    private static boolean isChinese(String languageCode) {
        if (languageCode == null) {
            return false;
        }
        String code = languageCode.trim().toLowerCase(Locale.US).replace('_', '-');
        return "zh".equals(code) || code.startsWith("zh-");
    }
}
