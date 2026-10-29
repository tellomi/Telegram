/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi: rewrite the user-visible product name without editing every
 * translated string. Protocol identifiers and class names are unchanged.
 * See docs/TELLOMI.md.
 */

package org.telegram.messenger;

public final class TellomiBrand {

    private static final String[] BRAND_TOKENS = new String[]{
            "Telegram",
            "تيليجرام",
            "텔레그램",
            "टेलीग्राम",
            "电报"
    };

    private TellomiBrand() {
    }

    public static String rewrite(String value) {
        if (!BuildVars.TELLOMI_REBRAND_STRINGS || value == null || value.isEmpty()) {
            return value;
        }
        String name = BuildVars.APP_NAME;
        if (name == null || name.isEmpty()) {
            return value;
        }
        for (int a = 0; a < BRAND_TOKENS.length; a++) {
            String token = BRAND_TOKENS[a];
            if (token.equals(name) || value.indexOf(token) < 0) {
                continue;
            }
            value = value.replace(token, name);
        }
        String host = BuildVars.LINK_HOST;
        if (host != null && !host.isEmpty()) {
            value = replaceHost(value, "telegram.me", host);
            value = replaceHost(value, "telegram.dog", host);
            value = replaceHost(value, "t.me", host);
        }
        return value;
    }

    private static String replaceHost(String value, String from, String to) {
        if (from.equals(to) || value.indexOf(from) < 0) {
            return value;
        }
        return value.replace(from, to);
    }
}
