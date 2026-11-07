package org.telegram.messenger;

import java.util.Locale;

/**
 * The small control on the right of one incoming English message.
 * It does not change the bubble width. Whole-chat translation and the
 * long-press sheet stay. The control stays hidden until the self-hosted
 * translator is enabled, and it is not drawn for other languages.
 */
public final class TellomiMessageTranslate {

    private TellomiMessageTranslate() {
    }

    public static boolean english(String code) {
        if (code == null) {
            return false;
        }
        String value = code.trim().toLowerCase(Locale.US).replace('_', '-');
        return "en".equals(value) || value.startsWith("en-");
    }

    /**
     * @param albumEdge single messages, or the bottom-right piece of a group
     * @param holding   this message is already showing its Chinese text
     */
    public static boolean showSide(boolean translatorEnabled, boolean outgoing, boolean english,
                                   boolean restricted, boolean hasWords, boolean albumEdge, boolean holding) {
        if (!translatorEnabled || outgoing || restricted || !hasWords || !albumEdge) {
            return false;
        }
        return english || holding;
    }
}
