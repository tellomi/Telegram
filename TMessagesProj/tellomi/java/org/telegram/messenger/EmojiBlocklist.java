/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Hides emoji from the picker, search, recent row, and
 * reaction picker. Sprite data in EmojiData.data is left intact so a message
 * that already contains a blocked emoji still draws. See
 * TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.messenger;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class EmojiBlocklist {

    /** Regional-indicator Taiwan flag, U+1F1F9 U+1F1FC. */
    public static final String TAIWAN_FLAG = "\uD83C\uDDF9\uD83C\uDDFC";

    private static final String[] BUILTIN_RAW = {
            TAIWAN_FLAG
    };

    private static final Set<String> BUILTIN = builtin();
    private static volatile Set<String> blocked = BUILTIN;

    private static String[][] pickerSources;
    private static boolean pickerInstalled;

    private EmojiBlocklist() {
    }

    /**
     * True when {@code emoji} is a blocked emoji string. Ids that start with
     * {@code animated_} are not blocked here. Variation selectors U+FE0F and
     * U+FE0E are ignored, so the Taiwan flag matches with or without one.
     */
    public static boolean isBlocked(CharSequence emoji) {
        if (emoji == null || emoji.length() == 0) {
            return false;
        }
        String value = emoji instanceof String ? (String) emoji : emoji.toString();
        if (value.startsWith("animated_")) {
            return false;
        }
        return blocked.contains(canonical(value));
    }

    /**
     * Union {@code serverEntries} onto the built-in default and, when the
     * picker sections are already installed, rewrite them.
     * <p>
     * Each entry is either an emoji string or a codepoint sequence:
     * {@code U+1F1F9 U+1F1FC}, {@code 1F1F9-1F1FC}, {@code 1F1F9_1F1FC}.
     * An absent or empty server list leaves the built-in default in place,
     * so the Taiwan flag stays hidden until a later list adds more emoji.
     * The server list cannot remove a built-in entry.
     *
     * @return true when the effective set changed
     */
    public static boolean mergeServerList(Collection<String> serverEntries) {
        Set<String> next = new HashSet<>(BUILTIN);
        if (serverEntries != null) {
            for (String raw : serverEntries) {
                String canonical = canonical(raw);
                if (!canonical.isEmpty()) {
                    next.add(canonical);
                }
            }
        }
        if (next.equals(blocked)) {
            return false;
        }
        blocked = next;
        reapplyPicker();
        return true;
    }

    /**
     * Called from {@link EmojiData}'s static init after {@code dataColored}
     * null sections have been pointed at {@code data}. Copies those sections
     * and drops blocked emoji. {@code EmojiData.data} is not modified, so
     * sprite indexes built from it stay aligned.
     */
    public static void installPickerSections(String[][] colored) {
        if (colored == null) {
            return;
        }
        String[][] copies = new String[colored.length][];
        for (int i = 0; i < colored.length; i++) {
            copies[i] = colored[i] == null ? null : colored[i].clone();
        }
        pickerSources = copies;
        pickerInstalled = true;
        applyPicker(colored);
    }

    private static void reapplyPicker() {
        if (!pickerInstalled) {
            return;
        }
        applyPicker(EmojiData.dataColored);
    }

    private static void applyPicker(String[][] colored) {
        String[][] sources = pickerSources;
        if (sources == null || colored == null) {
            return;
        }
        Set<String> blockedNow = blocked;
        for (int i = 0; i < sources.length && i < colored.length; i++) {
            colored[i] = withoutBlocked(sources[i], blockedNow);
        }
    }

    private static String[] withoutBlocked(String[] section, Set<String> blockedNow) {
        if (section == null || section.length == 0 || blockedNow.isEmpty()) {
            return section;
        }
        int drop = 0;
        for (int i = 0; i < section.length; i++) {
            if (section[i] != null && blockedNow.contains(canonical(section[i]))) {
                drop++;
            }
        }
        if (drop == 0) {
            return section;
        }
        String[] out = new String[section.length - drop];
        int write = 0;
        for (int i = 0; i < section.length; i++) {
            if (section[i] == null || !blockedNow.contains(canonical(section[i]))) {
                out[write++] = section[i];
            }
        }
        return out;
    }

    private static Set<String> builtin() {
        HashSet<String> set = new HashSet<>();
        for (int i = 0; i < BUILTIN_RAW.length; i++) {
            String canonical = canonical(BUILTIN_RAW[i]);
            if (!canonical.isEmpty()) {
                set.add(canonical);
            }
        }
        return Collections.unmodifiableSet(set);
    }

    static String canonical(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        String asCodepoints = tryCodepoints(value);
        if (asCodepoints != null) {
            return asCodepoints;
        }
        if (looksLikeCodepointAttempt(value)) {
            return "";
        }
        return stripSelectors(value);
    }

    private static boolean looksLikeCodepointAttempt(String value) {
        if (value.length() >= 2 && (value.charAt(0) == 'U' || value.charAt(0) == 'u') && value.charAt(1) == '+') {
            return true;
        }
        boolean hex = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (isHex(c)) {
                hex = true;
            } else if (!isSep(c)) {
                return false;
            }
        }
        return hex;
    }

    /**
     * @return the emoji, or null when {@code raw} is not a codepoint sequence
     */
    private static String tryCodepoints(String raw) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = raw.length();
        int tokens = 0;
        while (i < n) {
            while (i < n && isSep(raw.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            if ((raw.charAt(i) == 'U' || raw.charAt(i) == 'u') && i + 1 < n && raw.charAt(i + 1) == '+') {
                i += 2;
            }
            int start = i;
            while (i < n && isHex(raw.charAt(i))) {
                i++;
            }
            int len = i - start;
            if (len < 4 || len > 6) {
                return null;
            }
            if (i < n && !isSep(raw.charAt(i))) {
                return null;
            }
            int codepoint;
            try {
                codepoint = Integer.parseInt(raw.substring(start, i), 16);
            } catch (NumberFormatException e) {
                return null;
            }
            if (codepoint < 0 || codepoint > 0x10FFFF || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
                return null;
            }
            out.appendCodePoint(codepoint);
            tokens++;
        }
        if (tokens == 0) {
            return null;
        }
        return stripSelectors(out.toString());
    }

    private static String stripSelectors(String value) {
        if (value.indexOf('\uFE0F') < 0 && value.indexOf('\uFE0E') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\uFE0F' && c != '\uFE0E') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isSep(char c) {
        return c <= ' ' || c == ',' || c == ';' || c == '_' || c == '-' || c == '+';
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
