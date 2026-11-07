package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Built-in {@code night.attheme} pairs for the screens checked on 2026-11-06.
 * This reads the theme file. It does not render a window.
 */
public class TellomiNightThemeTest {

    @Test
    public void builtInNightKeepsPrimaryTextReadable() throws Exception {
        Map<String, Integer> theme = load(find("TMessagesProj/src/main/assets/night.attheme"));
        assertFalse(theme.containsKey("telegram_color_dialogsLogo"));
        assertEquals(0xFFFFFFFF, (int) theme.get("windowBackgroundWhiteBlackText"));
        assertEquals(0xFFFFFFFF, (int) theme.get("chat_outBubble"));

        assertAtLeast(theme, "chats_name", "dialogBackground", 4.5);
        assertAtLeast(theme, "chat_messageTextIn", "chat_inBubble", 4.5);
        assertAtLeast(theme, "chat_messageTextOut", "chat_outBubble", 4.5);
        assertAtLeast(theme, "chat_messagePanelText", "chat_messagePanelBackground", 4.5);
        assertAtLeast(theme, "dialogSearchText", "dialogBackground", 4.5);
        assertAtLeast(theme, "dialogTextBlack", "dialogBackground", 4.5);
        assertAtLeast(theme, "actionBarDefaultTitle", "actionBarDefault", 4.5);
        assertAtLeast(theme, "actionBarDefaultIcon", "actionBarDefault", 4.5);
        assertAtLeast(theme, "windowBackgroundWhiteBlackText", "windowBackgroundWhite", 4.5);
        assertAtLeast(theme, "player_actionBarTitle", "actionBarDefault", 4.5);

        assertAtLeast(theme, "dialogTextGray3", "dialogBackground", 3.0);
        assertTrue(ratio(theme.get("dialogTextGray3"), theme.get("dialogBackground")) < 4.5);
    }

    private static void assertAtLeast(Map<String, Integer> theme, String foreground, String background, double min) {
        assertTrue(theme.containsKey(foreground));
        assertTrue(theme.containsKey(background));
        double value = ratio(theme.get(foreground), theme.get(background));
        assertTrue(foreground + " on " + background + " is " + value, value >= min);
    }

    private static double ratio(int foreground, int background) {
        double lighter = Math.max(luminance(foreground), luminance(background));
        double darker = Math.min(luminance(foreground), luminance(background));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel((color >> 16) & 0xFF)
            + 0.7152 * channel((color >> 8) & 0xFF)
            + 0.0722 * channel(color & 0xFF);
    }

    private static double channel(int component) {
        double s = component / 255.0;
        if (s <= 0.04045) {
            return s / 12.92;
        }
        return Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static Map<String, Integer> load(File file) throws Exception {
        Map<String, Integer> out = new HashMap<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(line.substring(0, eq), parseColor(line.substring(eq + 1).trim()));
        }
        return out;
    }

    private static int parseColor(String raw) {
        if (raw.startsWith("#")) {
            long value = Long.parseLong(raw.substring(1), 16);
            if (raw.length() <= 7) {
                value |= 0xFF000000L;
            }
            return (int) value;
        }
        return Integer.parseInt(raw);
    }

    private static File find(String relative) {
        File direct = new File(relative);
        if (direct.isFile()) {
            return direct;
        }
        File nested = new File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        String stripped = relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative;
        File fromModule = new File("..", stripped);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative);
    }
}
