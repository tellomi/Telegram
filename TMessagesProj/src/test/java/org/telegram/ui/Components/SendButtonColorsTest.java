package org.telegram.ui.Components;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SendButtonColorsTest {
    @Test
    public void tellomiDarkFillGetsWhiteGlyph() {
        // ThemeColors.TELEGRAM_COLOR, used for both key_dialogFloatingButton and key_chat_messagePanelSend.
        assertEquals(SendButtonColors.WHITE, SendButtonColors.contrastGlyph(0xFF111111));
        assertEquals(SendButtonColors.WHITE, SendButtonColors.contrastGlyph(0xFF000000));
        assertEquals(SendButtonColors.WHITE, SendButtonColors.contrastGlyph(0xFF3A8EE6));
    }

    @Test
    public void lightFillGetsBlackGlyph() {
        assertEquals(SendButtonColors.BLACK, SendButtonColors.contrastGlyph(0xFFFFFFFF));
        assertEquals(SendButtonColors.BLACK, SendButtonColors.contrastGlyph(0xFFF0F0F0));
    }

    @Test
    public void luminanceBounds() {
        assertEquals(0.0, SendButtonColors.luminance(0xFF000000), 1e-9);
        assertEquals(1.0, SendButtonColors.luminance(0xFFFFFFFF), 1e-9);
    }
}
