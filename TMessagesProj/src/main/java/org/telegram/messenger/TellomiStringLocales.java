/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi: which built-in string bin a langpack row reads.
 */

package org.telegram.messenger;

import java.util.Locale;

/**
 * Langpack plural codes choose the Android resource locale. zh-Hans and
 * zh-Hant both report plural code {@code zh}, so a locale built from the
 * plural alone loads {@code values-zh} for traditional Chinese as well.
 * Traditional rows use {@code values-zh-rTW}.
 */
public final class TellomiStringLocales {

    private TellomiStringLocales() {
    }

    public static Locale localeFor(String pluralOrCode, String shortName) {
        String code = pluralOrCode == null ? "" : pluralOrCode;
        String name = shortName == null ? "" : shortName;
        if (isTraditionalChinese(code, name)) {
            return new Locale("zh", "TW");
        }
        int split = code.indexOf('_');
        if (split > 0 && split + 1 < code.length()) {
            return new Locale(code.substring(0, split), code.substring(split + 1));
        }
        if (code.length() > 0) {
            return new Locale(code);
        }
        return Locale.ENGLISH;
    }

    static boolean isTraditionalChinese(String code, String shortName) {
        String blob = ((code == null ? "" : code) + "\n" + (shortName == null ? "" : shortName)).toLowerCase(Locale.US);
        return blob.contains("hant") || blob.contains("zh_tw") || blob.contains("zh-tw") || blob.contains("zhtw");
    }
}
