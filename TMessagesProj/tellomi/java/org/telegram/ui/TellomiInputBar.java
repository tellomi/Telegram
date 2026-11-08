package org.telegram.ui;

/**
 * Idle composer, left to right, matching the current iOS field:
 * attachment outside on the left, emoji inside on the right, voice outside
 * on the right. Text replaces the voice slot with send. The attachment stays.
 */
public final class TellomiInputBar {

    public static final String ATTACH = "attach";
    public static final String EMOJI = "emoji";
    public static final String VOICE = "voice";
    public static final String SEND = "send";

    private TellomiInputBar() {
    }

    public static String[] slots(boolean hasText) {
        if (hasText) {
            return new String[] {ATTACH, EMOJI, SEND};
        }
        return new String[] {ATTACH, EMOJI, VOICE};
    }
}
