/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi: bubble accessibility dates must not repeat the Chinese preposition.
 */

package org.telegram.messenger;

import java.util.Locale;

/**
 * Spoken “sent at” / “received at” line on a message bubble.
 *
 * <p>{@code ChatMessageCell} used to pass {@code TodayAt + " " + time} into
 * {@code AccDescrSentDate}. English is {@code Sent %s} plus {@code at}, which
 * reads “Sent at HH:mm”. Simplified Chinese {@code AccDescrSentDate} is already
 * {@code 已发送于 %s} and {@code TodayAt} is {@code 于}, so the same composition
 * reads “已发送于 于 HH:mm”. Traditional Chinese does the same with {@code 於}.
 * {@code AccDescrReceivedDate} does not contain that preposition, and the chat
 * list passes {@code formatDateAudio} (whose today form is {@code TodayAtFormatted},
 * {@code %1$s} in zh-Hans) into the same sent-date key. Dropping {@code 于} from
 * the string would fix the bubble and remove it from the chat list.
 */
public final class TellomiAccDescr {

    private TellomiAccDescr() {
    }

    public static String sentOrReceivedAt(String format, String todayAt, String time) {
        String pattern = format == null ? "%s" : format;
        String at = todayAt == null ? "" : todayAt;
        String clock = time == null ? "" : time;
        String arg = clock;
        if (!at.isEmpty() && patternEndsWithPreposition(pattern, at)) {
            String trimmed = pattern.replace('\u00a0', ' ').trim();
            // “已发送于%s” has no space before the placeholder. Insert one
            // so the spoken line is “已发送于 HH:mm”, not “已发送于于 HH:mm”.
            if (trimmed.endsWith(at + "%s") || trimmed.endsWith(at + "%1$s")) {
                arg = " " + clock;
            }
        } else if (!at.isEmpty()) {
            arg = at + " " + clock;
        }
        String spoken;
        try {
            spoken = String.format(Locale.US, pattern, arg);
        } catch (RuntimeException e) {
            spoken = pattern;
        }
        return collapseDoubledCjkPreposition(spoken, at);
    }

    static boolean patternEndsWithPreposition(String pattern, String at) {
        if (pattern == null || at == null || at.isEmpty()) {
            return false;
        }
        String trimmed = pattern.replace('\u00a0', ' ').trim();
        String head;
        if (trimmed.endsWith("%s")) {
            head = trimmed.substring(0, trimmed.length() - 2);
        } else if (trimmed.endsWith("%1$s")) {
            head = trimmed.substring(0, trimmed.length() - 4);
        } else {
            return false;
        }
        int end = head.length();
        while (end > 0 && Character.isWhitespace(head.charAt(end - 1))) {
            end--;
        }
        head = head.substring(0, end);
        if (!head.endsWith(at)) {
            return false;
        }
        int boundary = head.length() - at.length();
        if (boundary == 0) {
            return true;
        }
        char prev = head.charAt(boundary - 1);
        if (Character.isWhitespace(prev)) {
            return true;
        }
        return isCjk(at.charAt(0)) && isCjk(prev);
    }

    /** “于 于 ” and “于于 ” become “于 ”. Latin words such as “at” are left alone. */
    static String collapseDoubledCjkPreposition(String spoken, String at) {
        if (spoken == null || at == null || at.isEmpty() || !isCjk(at.charAt(0))) {
            return spoken;
        }
        String once = at + " ";
        String out = spoken.replace(at + " " + at + " ", once);
        return out.replace(at + at + " ", once);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
