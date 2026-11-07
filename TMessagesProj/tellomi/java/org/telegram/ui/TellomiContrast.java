package org.telegram.ui;

/**
 * Login ink that has to stay readable on the Tellomi page and field fills.
 * These are the login chrome colors, not a replacement for chat theme keys.
 */
public final class TellomiContrast {

    public static final int PAGE_NIGHT = 0xFF000000;
    public static final int PAGE_DAY = 0xFFFFFFFF;
    public static final int FIELD_NIGHT = 0xFF2C2C2E;
    public static final int FIELD_DAY = 0xFFF3F4F6;
    public static final int DIGIT_NIGHT = 0xFFFFFFFF;
    public static final int DIGIT_DAY = 0xFF111111;
    /** Light enough on {@link #FIELD_NIGHT}. The old #8E8E93 sat too close to that fill. */
    public static final int HINT_NIGHT = 0xFFD1D1D6;
    /** Dark enough on {@link #FIELD_DAY}. The old #C7C7CC sat on #F3F4F6. */
    public static final int HINT_DAY = 0xFF5C5C61;
    public static final int SELECTION_NIGHT = 0x663478F6;
    public static final int SELECTION_DAY = 0x553478F6;
    public static final int ERROR = 0xFFFF3B30;

    /** WCAG AA body text. */
    public static final double MIN_TEXT = 4.5;
    /** WCAG AA for a control boundary (the empty checkbox ring, field stroke, divider). */
    public static final double MIN_UI = 3.0;

    private TellomiContrast() {
    }

    public static int digit(boolean night) {
        return night ? DIGIT_NIGHT : DIGIT_DAY;
    }

    public static int hint(boolean night) {
        return night ? HINT_NIGHT : HINT_DAY;
    }

    public static int field(boolean night) {
        return night ? FIELD_NIGHT : FIELD_DAY;
    }

    public static int selection(boolean night) {
        return night ? SELECTION_NIGHT : SELECTION_DAY;
    }

    /** Social icons use the same ink as typed text. The drawable's #111111 does not. */
    public static int iconInk(boolean night) {
        return digit(night);
    }

    public static double ratio(int foreground, int background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    public static boolean readable(int foreground, int background) {
        return ratio(foreground, background) >= MIN_TEXT;
    }

    /**
     * Keep a theme color when it already clears {@code minRatio} on {@code background}.
     * Otherwise walk it toward {@code strong} (the page's text ink) until it does.
     * Login fields paint the hint key on a filled input, where that key is below AA.
     */
    public static int ensure(int preferred, int strong, int background, double minRatio) {
        if (ratio(preferred, background) >= minRatio) {
            return preferred | 0xFF000000;
        }
        if (ratio(strong, background) < minRatio) {
            return strong | 0xFF000000;
        }
        float lo = 0f;
        float hi = 1f;
        int best = strong;
        for (int i = 0; i < 16; i++) {
            float mid = (lo + hi) / 2f;
            int mixed = blend(preferred, strong, mid);
            if (ratio(mixed, background) >= minRatio) {
                best = mixed;
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return best | 0xFF000000;
    }

    public static int blend(int from, int to, float amount) {
        if (amount <= 0f) {
            return from;
        }
        if (amount >= 1f) {
            return to;
        }
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * amount);
        int g = Math.round(fg + (tg - fg) * amount);
        int b = Math.round(fb + (tb - fb) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int component) {
        double s = component / 255.0;
        if (s <= 0.04045) {
            return s / 12.92;
        }
        return Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
