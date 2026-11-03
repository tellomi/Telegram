/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Settings-style icon plates stay black, white, or gray.
 * See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

/**
 * Maps a Telegram icon-plate color onto a dark neutral so the white glyph
 * keeps contrast. A color that is already neutral, or fully transparent, is
 * returned unchanged.
 */
public final class TellomiIconColors {

    private TellomiIconColors() {
    }

    public static int plate(int color) {
        int alpha = color >>> 24;
        if (alpha == 0) {
            return color;
        }
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        if (max - min < 18) {
            return color;
        }
        float luminance = (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f;
        int value = 0x14 + Math.round(luminance * 0x36);
        if (value < 0x14) {
            value = 0x14;
        } else if (value > 0x4A) {
            value = 0x4A;
        }
        return (alpha << 24) | (value << 16) | (value << 8) | value;
    }
}
