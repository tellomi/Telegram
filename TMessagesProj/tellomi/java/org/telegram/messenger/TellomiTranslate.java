package org.telegram.messenger;

import java.util.Locale;

/**
 * Chat translation is English to Simplified Chinese on Tellomi's own service.
 * {@code to_lang} may only be {@code zh}, {@code zh-cn}, or {@code zh-hans}.
 * Google, on-device ML Kit, and other fallbacks are not used.
 */
public final class TellomiTranslate {

    public static final String TO_LANG = "zh-hans";

    private TellomiTranslate() {
    }

    /** Null when {@code raw} is not one of the three accepted codes. */
    public static String acceptedToLang(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.US).replace('_', '-');
        if ("zh".equals(value) || "zh-cn".equals(value) || "zh-hans".equals(value)) {
            return TO_LANG;
        }
        return null;
    }

    /** Wire value. Anything else becomes Simplified Chinese so the RPC is not TO_LANG_INVALID. */
    public static String rpcToLang(String raw) {
        String accepted = acceptedToLang(raw);
        return accepted != null ? accepted : TO_LANG;
    }

    /** App config string that may open manual or automatic translation. */
    public static boolean configEnabled(String value) {
        return "enabled".equals(value);
    }
}
