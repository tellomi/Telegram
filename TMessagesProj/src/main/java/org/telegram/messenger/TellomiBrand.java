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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        value = rewritePolicyLinks(value);
        String host = BuildVars.LINK_HOST;
        if (host != null && !host.isEmpty()) {
            value = replaceHost(value, "getdesktop.telegram.org", "tellomi.cn/download");
            value = replaceHost(value, "desktop.telegram.org", "tellomi.cn/download");
            value = replaceHost(value, "sms@telegram.org", "support@tellomi.app");
            value = replaceHost(value, "recover@telegram.org", "support@tellomi.app");
            value = replaceHost(value, "www.telegram.org", "tellomi.cn");
            value = replaceHostBoundary(value, "telegram.org", "tellomi.cn");
            value = replaceHost(value, "telegram.me", host);
            value = replaceHost(value, "telegram.dog", host);
            value = replaceHost(value, "t.me", host);
        }
        return value;
    }

    private static final Pattern FAQ_URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?telegram\\.org/faq[^\\s\"'<>)\\]]*");
    private static final Pattern PRIVACY_URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?telegram\\.org/privacy[^\\s\"'<>)\\]]*");
    private static final Pattern TOS_URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?telegram\\.org/tos[^\\s\"'<>)\\]]*");
    private static final Pattern BLOG_URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?telegram\\.org/blog[^\\s\"'<>)\\]]*");
    private static final Pattern DEACTIVATE_URL = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?telegram\\.org/deactivate[^\\s\"'<>)\\]]*");
    private static final Pattern TRANSLATIONS_URL = Pattern.compile("(?i)(?:https?://)?translations\\.telegram\\.org[^\\s\"'<>)\\]]*");
    private static final Pattern CORE_URL = Pattern.compile("(?i)(?:https?://)?core\\.telegram\\.org[^\\s\"'<>)\\]]*");
    private static final Pattern ADS_URL = Pattern.compile("(?i)(?:https?://)?(?:ads|promote)\\.telegram\\.org[^\\s\"'<>)\\]]*");
    private static final Pattern WEB_URL = Pattern.compile("(?i)(?:https?://)?web\\.telegram\\.org[^\\s\"'<>)\\]]*");
    private static final Pattern OTHER_SITE_URL = Pattern.compile("(?i)https?://(?:www\\.)?telegram\\.org(?:/[^\\s\"'<>)\\]]*)?");

    /** Help, privacy, and terms go to tellomi.cn. Other telegram.org links are rewritten afterwards. */
    private static String rewritePolicyLinks(String value) {
        value = replaceUrl(value, FAQ_URL, "faq", BuildVars.HELP_URL);
        value = replaceUrl(value, PRIVACY_URL, "privacy", BuildVars.PRIVACY_URL);
        value = replaceUrl(value, TOS_URL, "tos", BuildVars.TOS_URL);
        value = replaceUrl(value, BLOG_URL, "blog", "https://tellomi.cn/changelog/");
        value = replaceUrl(value, DEACTIVATE_URL, "deactivate", "https://tellomi.cn/account/delete/");
        value = replaceUrl(value, TRANSLATIONS_URL, "translations.telegram.org", BuildVars.HELP_URL);
        value = replaceUrl(value, CORE_URL, "core.telegram.org", BuildVars.HELP_URL);
        value = replaceUrl(value, ADS_URL, "telegram.org", "https://tellomi.cn/legal/community/");
        value = replaceUrl(value, WEB_URL, "web.telegram.org", "https://tellomi.cn/help/start/link-desktop/");
        value = replaceUrl(value, OTHER_SITE_URL, "telegram.org", "https://tellomi.cn/");
        return value;
    }

    private static String replaceUrl(String value, Pattern pattern, String needle, String replacement) {
        if (value.indexOf(needle) < 0 && value.toLowerCase().indexOf(needle) < 0) {
            return value;
        }
        Matcher matcher = pattern.matcher(value);
        StringBuffer out = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        if (!found) {
            return value;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String replaceHost(String value, String from, String to) {
        if (from.equals(to) || value.indexOf(from) < 0) {
            return value;
        }
        return value.replace(from, to);
    }

    /** Replace {@code from} only when it is its own host, not a suffix of core.telegram.org and not an email. */
    private static String replaceHostBoundary(String value, String from, String to) {
        if (from.equals(to) || value.indexOf(from) < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        boolean changed = false;
        while (i < value.length()) {
            int j = value.indexOf(from, i);
            if (j < 0) {
                break;
            }
            boolean boundary = true;
            if (j > 0) {
                char prev = value.charAt(j - 1);
                if (prev == '@' || prev == '.' || Character.isLetterOrDigit(prev)) {
                    boundary = false;
                }
            }
            int end = j + from.length();
            if (end < value.length() && Character.isLetterOrDigit(value.charAt(end))) {
                boundary = false;
            }
            sb.append(value, i, j);
            if (boundary) {
                sb.append(to);
                changed = true;
            } else {
                sb.append(value, j, end);
            }
            i = end;
        }
        if (!changed) {
            return value;
        }
        sb.append(value, i, value.length());
        return sb.toString();
    }
}
