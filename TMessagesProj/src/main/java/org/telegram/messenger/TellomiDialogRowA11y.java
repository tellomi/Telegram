package org.telegram.messenger;

/**
 * Chat-list rows are drawn, not {@code TextView}s. UiAutomator reads
 * {@code AccessibilityNodeInfo.getText()} and the content description.
 * The spoken line has to contain the name, the last message, the time,
 * and the unread count or the row is missing from the tree.
 */
public final class TellomiDialogRowA11y {

    private TellomiDialogRowA11y() {
    }

    public static String rowText(String name, String lastMessage, String time, int unread) {
        StringBuilder sb = new StringBuilder();
        append(sb, name);
        append(sb, lastMessage);
        append(sb, time);
        if (unread > 0) {
            append(sb, Integer.toString(unread));
        }
        return sb.toString();
    }

    public static boolean containsCount(CharSequence text, int unread) {
        if (unread <= 0 || text == null) {
            return unread <= 0;
        }
        return text.toString().contains(Integer.toString(unread));
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null) {
            return;
        }
        String trimmed = part.trim();
        if (trimmed.length() == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(trimmed);
    }
}
