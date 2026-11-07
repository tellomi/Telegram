package org.telegram.ui.theme;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * List, preview, apply, persistence, day/night, missing wallpaper data, and a failed catalog request.
 * These call {@link TelegramThemeFlow}, which the theme and wallpaper screens use.
 * They do not open a device window.
 */
public class TelegramThemeFlowTest {

    @Test
    public void builtInThemeListKeepsEveryAssetThemeAndTheAccentRow() {
        String[] bundled = {"Blue", "Dark Blue", "Arctic Blue", "Day", "Night"};
        for (String name : bundled) {
            assertTrue(name, TelegramThemeFlow.includeBundledTheme(name, false));
        }
        assertFalse(TelegramThemeFlow.includeBundledTheme("remote12", true));
        assertFalse(TelegramThemeFlow.includeBundledTheme("", false));
        assertTrue(TelegramThemeFlow.showAccentRow(true));
        assertFalse(TelegramThemeFlow.showAccentRow(false));
    }

    @Test
    public void applyPersistsThemeAccentAndWallpaperAcrossDayAndNight() {
        TelegramThemeFlow.Selection fresh = TelegramThemeFlow.initialSelection();
        assertEquals("Blue", TelegramThemeFlow.activeTheme(fresh));
        assertEquals("d", fresh.wallpaperSlug);
        assertEquals(99, fresh.accentId);

        TelegramThemeFlow.Selection day = TelegramThemeFlow.applyTheme(fresh, "Arctic Blue", 11, false);
        assertEquals("Arctic Blue", day.dayTheme);
        assertEquals("Dark Blue", day.nightTheme);
        assertEquals("Arctic Blue", TelegramThemeFlow.activeTheme(day));
        assertEquals(11, day.accentId);

        TelegramThemeFlow.Selection night = TelegramThemeFlow.applyTheme(day, "Night", 3, true);
        assertEquals("Arctic Blue", night.dayTheme);
        assertEquals("Night", night.nightTheme);
        assertEquals("Night", TelegramThemeFlow.activeTheme(night));
        assertEquals(3, night.accentId);

        TelegramThemeFlow.Selection wallpaper = TelegramThemeFlow.applyWallpaper(night, "c", 0xfff2f2f2, false);
        assertEquals("c", wallpaper.wallpaperSlug);
        assertEquals(0xfff2f2f2, wallpaper.wallpaperColor);
        assertEquals("Night", TelegramThemeFlow.activeTheme(wallpaper));

        TelegramThemeFlow.Selection kept = TelegramThemeFlow.applyWallpaper(wallpaper, "gone", 0, true);
        assertEquals("c", kept.wallpaperSlug);
        assertEquals(0xfff2f2f2, kept.wallpaperColor);
        assertEquals("Arctic Blue", kept.dayTheme);

        assertEquals("Blue", TelegramThemeFlow.themeKey(null, false));
        assertEquals("Dark Blue", TelegramThemeFlow.themeKey("", true));
        assertEquals("Day", TelegramThemeFlow.themeKey("Day", false));
        assertEquals("d", TelegramThemeFlow.wallpaperSlug(null));
        assertEquals("fqv01SQemVIBAAAApND8LDRUhRU", TelegramThemeFlow.wallpaperSlug("fqv01SQemVIBAAAApND8LDRUhRU"));
    }

    @Test
    public void missingWallpaperStillPreviewsAndBlackSolidStaysInTheList() {
        TelegramThemeFlow.PreviewFill accentOnly = TelegramThemeFlow.chatThemePreview(false, 0, 0, 0, 0, 0, 0x3390ec);
        assertFalse(accentOnly.fromWallpaper);
        assertEquals(0xff3390ec, accentOnly.color1);
        assertEquals(0xff3390ec, accentOnly.color2);

        TelegramThemeFlow.PreviewFill blackAccent = TelegramThemeFlow.chatThemePreview(false, 0, 0, 0, 0, 0, 0);
        assertEquals(0xff000000, blackAccent.color1);

        TelegramThemeFlow.PreviewFill solid = TelegramThemeFlow.chatThemePreview(true, 0xf2f2f2, 0, 0, 0, TelegramThemeFlow.BACKGROUND_FLAG, 0);
        assertTrue(solid.fromWallpaper);
        assertEquals(0xfff2f2f2, solid.color1);
        assertEquals(0, solid.color2);

        TelegramThemeFlow.PreviewFill blackPaper = TelegramThemeFlow.chatThemePreview(true, 0, 0, 0, 0, TelegramThemeFlow.BACKGROUND_FLAG, 0x3390ec);
        assertTrue(blackPaper.fromWallpaper);
        assertEquals(0xff000000, blackPaper.color1);

        assertTrue(TelegramThemeFlow.showColorWallpaper(true, TelegramThemeFlow.BACKGROUND_FLAG, 0, 0, false, 0));
        assertTrue(TelegramThemeFlow.showColorWallpaper(true, TelegramThemeFlow.BACKGROUND_FLAG, 0xf2f2f2, 0, true, 0));
        assertTrue(TelegramThemeFlow.showColorWallpaper(false, TelegramThemeFlow.BACKGROUND_FLAG | TelegramThemeFlow.SECOND_BACKGROUND_FLAG, 0x121212, 0x1e1e1e, true, 0));
        assertFalse(TelegramThemeFlow.showColorWallpaper(false, 0, 0, 0, false, 0));
        assertFalse(TelegramThemeFlow.showColorWallpaper(true, TelegramThemeFlow.BACKGROUND_FLAG, 0, 0, false, -34));
        assertEquals(0xff000000, TelegramThemeFlow.opaqueColor(true, 0));
        assertEquals(0, TelegramThemeFlow.opaqueColor(false, 0));
    }

    @Test
    public void networkFailureKeepsTheWallpaperListAndTheChatThemeCache() {
        assertTrue(TelegramThemeFlow.keepCurrentWallpaperList(true, false));
        assertTrue(TelegramThemeFlow.keepCurrentWallpaperList(true, true));
        assertTrue(TelegramThemeFlow.keepCurrentWallpaperList(false, false));
        assertFalse(TelegramThemeFlow.keepCurrentWallpaperList(false, true));

        assertTrue(TelegramThemeFlow.keepCachedChatThemes(true));
        assertFalse(TelegramThemeFlow.keepCachedChatThemes(false));
        assertTrue(TelegramThemeFlow.loadEmojiThemesAfterGiftFailure());
        assertTrue(TelegramThemeFlow.showChatThemeList(true));
        assertTrue(TelegramThemeFlow.showChatThemeList(false));
        assertEquals(TelegramThemeFlow.CHAT_THEME_DEFAULT, TelegramThemeFlow.chatThemeListFlags() & TelegramThemeFlow.CHAT_THEME_DEFAULT);

        assertEquals(1, TelegramThemeFlow.visibleChatThemes(0, 0, true, true));
        assertEquals(3, TelegramThemeFlow.visibleChatThemes(2, 6, true, true));
        assertEquals(7, TelegramThemeFlow.visibleChatThemes(2, 6, false, true));
        assertEquals(6, TelegramThemeFlow.visibleChatThemes(2, 6, false, false));
    }
}
