package org.telegram.ui.Components;

/** Pure helpers for picking a legible send-button glyph color. */
public final class SendButtonColors {
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;

    private SendButtonColors() {
    }

    /** Relative luminance approximation (Rec. 709 weights), 0..1. */
    public static double luminance(int color) {
        final int r = (color >> 16) & 0xFF;
        final int g = (color >> 8) & 0xFF;
        final int b = color & 0xFF;
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    /** White glyph on dark fills, black glyph on light fills. */
    public static int contrastGlyph(int fill) {
        return luminance(fill) > 0.55 ? BLACK : WHITE;
    }
}
