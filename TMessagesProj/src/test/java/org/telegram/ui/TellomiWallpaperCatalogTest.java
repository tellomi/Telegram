package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiWallpaperCatalogTest {

    @Test
    public void accentTableMatchesThePlan() {
        assertEquals(12, TellomiWallpaperCatalog.ACCENTS.length);
        assertEquals(0xFF3390EC, TellomiWallpaperCatalog.ACCENTS[0]);
        assertEquals(0xFF3390EC, TellomiWallpaperCatalog.ACCENT_DEFAULT);
        assertEquals(0xFF2AABEE, TellomiWallpaperCatalog.ACCENTS[1]);
        assertEquals(0xFF00A8A8, TellomiWallpaperCatalog.ACCENTS[2]);
        assertEquals(0xFF31B545, TellomiWallpaperCatalog.ACCENTS[3]);
        assertEquals(0xFF25D366, TellomiWallpaperCatalog.ACCENTS[4]);
        assertEquals(0xFFF5A623, TellomiWallpaperCatalog.ACCENTS[5]);
        assertEquals(0xFFFF8A3D, TellomiWallpaperCatalog.ACCENTS[6]);
        assertEquals(0xFFE5484D, TellomiWallpaperCatalog.ACCENTS[7]);
        assertEquals(0xFFEC4899, TellomiWallpaperCatalog.ACCENTS[8]);
        assertEquals(0xFF8B5CF6, TellomiWallpaperCatalog.ACCENTS[9]);
        assertEquals(0xFF5B5BD6, TellomiWallpaperCatalog.ACCENTS[10]);
        assertEquals(0xFF5F6B7A, TellomiWallpaperCatalog.ACCENTS[11]);
        HashSet<Integer> unique = new HashSet<>();
        for (int color : TellomiWallpaperCatalog.ACCENTS) {
            assertTrue(unique.add(color));
        }
    }

    @Test
    public void accentIdsKeepTheHomeSlot() {
        int[] blue = TellomiWallpaperCatalog.accentIds("Blue");
        int[] night = TellomiWallpaperCatalog.accentIds("Dark Blue");
        int[] day = TellomiWallpaperCatalog.accentIds("Day");
        assertEquals(12, blue.length);
        assertEquals(99, blue[0]);
        assertEquals(0, night[0]);
        assertEquals(9, day[0]);
        assertUnique(blue);
        assertUnique(night);
        assertUnique(day);
    }

    @Test
    public void defaultWallpaperIsTheDoodle() {
        TellomiWallpaperCatalog.Wallpaper light = TellomiWallpaperCatalog.defaultWallpaper(false);
        TellomiWallpaperCatalog.Wallpaper dark = TellomiWallpaperCatalog.defaultWallpaper(true);
        assertEquals("tm_doodle_light", light.id);
        assertEquals("doodle", light.pattern);
        assertEquals(1, light.colors.length);
        assertEquals(0xFFEFE7DD, light.colors[0]);
        assertEquals(-12, light.intensityPercent);
        assertEquals("tm_doodle_dark", dark.id);
        assertEquals("doodle", dark.pattern);
        assertEquals(0xFF0B141A, dark.colors[0]);
        assertEquals(0xFF16232B, dark.colors[1]);
        assertEquals(8, dark.intensityPercent);
        assertEquals("wallpapers/doodle.png", light.assetPath());
    }

    @Test
    public void presetListKeepsOneSolid() {
        assertEquals(10, TellomiWallpaperCatalog.wallpapers().size());
        assertEquals(11, TellomiWallpaperCatalog.all().size());
        int[][] light = TellomiWallpaperCatalog.solidPresets(false);
        int[][] dark = TellomiWallpaperCatalog.solidPresets(true);
        assertEquals(1, light.length);
        assertEquals(1, light[0].length);
        assertEquals(0xFFF0F2F5, light[0][0]);
        assertEquals(1, dark.length);
        assertEquals(0xFF101418, dark[0][0]);
        TellomiWallpaperCatalog.Wallpaper solid = TellomiWallpaperCatalog.solid();
        assertEquals("tm_solid", solid.id);
        assertNull(solid.pattern);
        assertEquals(0xFFF0F2F5, solid.colorsFor(false)[0]);
        assertEquals(0xFF101418, solid.colorsFor(true)[0]);
    }

    @Test
    public void wallpaperTableMatchesThePlan() {
        List<TellomiWallpaperCatalog.Wallpaper> list = TellomiWallpaperCatalog.wallpapers();
        assertEquals("tm_mint", list.get(2).id);
        assertEquals(-15, list.get(2).intensityPercent);
        assertEquals(0xFFDBEED9, list.get(2).colors[0]);
        assertEquals("tm_peach", list.get(4).id);
        assertEquals("dots", list.get(4).pattern);
        assertEquals("tm_lavender", list.get(5).id);
        assertEquals("waves", list.get(5).pattern);
        assertEquals("tm_sunset", list.get(6).id);
        assertNull(list.get(6).pattern);
        assertEquals(0xFFA6C1EE, list.get(6).colors[3]);
        assertEquals("tm_forest_dark", list.get(7).id);
        assertEquals(10, list.get(7).intensityPercent);
        assertEquals(3, list.get(7).colors.length);
        assertEquals("tm_ocean_dark", list.get(8).id);
        assertEquals(0xFF141E30, list.get(8).colors[0]);
        assertEquals(0xFF243B55, list.get(8).colors[1]);
        assertEquals("tm_aurora_dark", list.get(9).id);
        assertNull(list.get(9).pattern);
        assertTrue(list.get(9).dark);
    }

    @Test
    public void bundledDefaultDoesNotReplaceAChosenWallpaper() {
        assertTrue(TellomiWallpaperCatalog.isStockWallpaperChoice(null, 0, 0, 0, 0, null));
        assertTrue(TellomiWallpaperCatalog.isStockWallpaperChoice("d", 0, 0, 0, 0, ""));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("c", 0xFF000000, 0, 0, 0, ""));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("d", 0xFF000000, 0, 0, 0, ""));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("c", 0xFFF0F2F5, 0, 0, 0, null));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("", 0, 0, 0, 0, "wallpaper.jpg"));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("t", 0, 0, 0, 0, null));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("tm_solid", 0xFF101418, 0, 0, 0, null));
        assertFalse(TellomiWallpaperCatalog.isStockWallpaperChoice("tm_doodle_dark", 0, 0, 0, 0, null));
    }

    @Test
    public void bubblesAndSolidShadow() {
        assertEquals(0xFFFFFFFF, TellomiWallpaperCatalog.inBubble(false));
        assertEquals(0xFF202C33, TellomiWallpaperCatalog.inBubble(true));
        assertEquals(0xFFD2E6FA, TellomiWallpaperCatalog.outBubble(0xFF3390EC, false));
        assertEquals(0xFF1B456E, TellomiWallpaperCatalog.outBubble(0xFF3390EC, true));
        assertEquals(0.5f, TellomiWallpaperCatalog.SOLID_BUBBLE_SHADOW_DP, 0f);
        assertEquals(0x59000000, TellomiWallpaperCatalog.SERVICE_CHIP);
    }

    @Test
    public void licenseNamesTheSources() {
        String notice = TellomiWallpaperCatalog.licenseNotice();
        assertTrue(notice.contains("Tabler Icons"));
        assertTrue(notice.contains("MIT License"));
        assertTrue(notice.contains("Lucide"));
        assertTrue(notice.contains("ISC License"));
        assertTrue(notice.contains("Hero Patterns"));
        assertTrue(notice.contains("CC BY 4.0"));
        assertTrue(notice.contains("Steve Schoger"));
        assertTrue(notice.contains("Pattern Monster"));
    }

    @Test
    public void sharedCatalogFileMatchesTheColors() throws Exception {
        File file = new File("tellomi/assets/wallpapers/WallpaperCatalog.json");
        assertTrue(file.isFile());
        String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        for (int color : TellomiWallpaperCatalog.ACCENTS) {
            assertTrue(json.contains(hex(color)));
        }
        for (TellomiWallpaperCatalog.Wallpaper wallpaper : TellomiWallpaperCatalog.all()) {
            assertTrue(json.contains("\"" + wallpaper.id + "\""));
            if (!wallpaper.adaptive) {
                for (int color : wallpaper.colors) {
                    assertTrue(wallpaper.id + " " + hex(color), json.contains(hex(color)));
                }
            }
        }
        assertTrue(json.contains("#F0F2F5"));
        assertTrue(json.contains("#101418"));
        assertTrue(json.contains("#FFFFFF"));
        assertTrue(json.contains("#202C33"));
        assertFalse(json.contains("#F2F2F2"));
    }

    private static void assertUnique(int[] ids) {
        HashSet<Integer> unique = new HashSet<>();
        for (int id : ids) {
            assertTrue(unique.add(id));
        }
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0x00ffffff);
    }
}
