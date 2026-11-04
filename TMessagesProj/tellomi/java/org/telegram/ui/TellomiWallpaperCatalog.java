package org.telegram.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in chat wallpapers and the shared accent palette.
 * Color values match the Tellomi wallpaper plan. No Android types, so unit tests can read them.
 */
public final class TellomiWallpaperCatalog {

    public static final int ACCENT_DEFAULT = 0xFF3390EC;
    public static final int[] ACCENTS = {
            0xFF3390EC,
            0xFF2AABEE,
            0xFF00A8A8,
            0xFF31B545,
            0xFF25D366,
            0xFFF5A623,
            0xFFFF8A3D,
            0xFFE5484D,
            0xFFEC4899,
            0xFF8B5CF6,
            0xFF5B5BD6,
            0xFF5F6B7A
    };

    public static final int SOLID_LIGHT = 0xFFF0F2F5;
    public static final int SOLID_DARK = 0xFF101418;

    public static final int IN_BUBBLE_LIGHT = 0xFFFFFFFF;
    public static final int IN_BUBBLE_DARK = 0xFF202C33;

    /** Flat date and service chip used on a solid wallpaper. */
    public static final int SERVICE_CHIP = 0x59000000;
    public static final int SERVICE_CHIP_PRESSED = 0x73000000;

    /** Hairline bubble shadow, in dp, drawn only on a solid wallpaper. */
    public static final float SOLID_BUBBLE_SHADOW_DP = 0.5f;
    public static final int SOLID_BUBBLE_SHADOW_COLOR = 0x59000000;

    public static final String DEFAULT_LIGHT_ID = "tm_doodle_light";
    public static final String DEFAULT_DARK_ID = "tm_doodle_dark";
    public static final String SOLID_ID = "tm_solid";

    private static final List<Wallpaper> WALLPAPERS = build();

    private TellomiWallpaperCatalog() {
    }

    public static final class Wallpaper {
        public final String id;
        public final String name;
        /** doodle, dots, waves, or null when the wallpaper is only color. */
        public final String pattern;
        public final int[] colors;
        public final boolean dark;
        /** Light and dark each have their own solid color. */
        public final boolean adaptive;
        /** Signed percent. Negative is dark ink on a light ground. */
        public final int intensityPercent;

        Wallpaper(String id, String name, String pattern, int[] colors, boolean dark, boolean adaptive, int intensityPercent) {
            this.id = id;
            this.name = name;
            this.pattern = pattern;
            this.colors = colors;
            this.dark = dark;
            this.adaptive = adaptive;
            this.intensityPercent = intensityPercent;
        }

        public int[] colorsFor(boolean themeDark) {
            if (adaptive) {
                return new int[]{themeDark ? SOLID_DARK : SOLID_LIGHT};
            }
            return colors;
        }

        public float intensity() {
            return intensityPercent / 100f;
        }

        public String assetPath() {
            return pattern == null ? null : "wallpapers/" + pattern + ".png";
        }
    }

    /** The ten patterned or gradient wallpapers, in catalog order. */
    public static List<Wallpaper> wallpapers() {
        ArrayList<Wallpaper> list = new ArrayList<>(WALLPAPERS.size() - 1);
        for (int i = 0; i < WALLPAPERS.size(); i++) {
            Wallpaper wallpaper = WALLPAPERS.get(i);
            if (!wallpaper.adaptive) {
                list.add(wallpaper);
            }
        }
        return list;
    }

    /** Ten wallpapers plus the one adaptive solid. */
    public static List<Wallpaper> all() {
        return Collections.unmodifiableList(WALLPAPERS);
    }

    public static Wallpaper solid() {
        return byId(SOLID_ID);
    }

    public static Wallpaper defaultWallpaper(boolean dark) {
        return byId(dark ? DEFAULT_DARK_ID : DEFAULT_LIGHT_ID);
    }

    public static Wallpaper byId(String id) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < WALLPAPERS.size(); i++) {
            Wallpaper wallpaper = WALLPAPERS.get(i);
            if (id.equals(wallpaper.id)) {
                return wallpaper;
            }
        }
        return null;
    }

    public static boolean isBuiltin(String slug) {
        return byId(slug) != null;
    }

    /**
     * The bundled doodle is only the wallpaper a person never chose.
     * Slug {@code d} with no color and no file is that stock default.
     * A color (including pure black), a gallery file, a theme wallpaper,
     * or any other slug is a choice and must be left in place.
     */
    public static boolean isStockWallpaperChoice(String slug, int color, int gradient1, int gradient2, int gradient3, String fileName) {
        if (slug != null && !"d".equals(slug)) {
            return false;
        }
        if (color != 0 || gradient1 != 0 || gradient2 != 0 || gradient3 != 0) {
            return false;
        }
        return fileName == null || fileName.isEmpty();
    }

    public static boolean isDefaultId(String id, boolean dark) {
        return dark ? DEFAULT_DARK_ID.equals(id) : DEFAULT_LIGHT_ID.equals(id);
    }

    public static boolean isBuiltinTheme(String name) {
        return "Blue".equals(name) || "Dark Blue".equals(name) || "Arctic Blue".equals(name) || "Day".equals(name) || "Night".equals(name);
    }

    /** One solid preset. Every other upstream solid and gradient is omitted. */
    public static int[][] solidPresets(boolean dark) {
        return new int[][]{new int[]{dark ? SOLID_DARK : SOLID_LIGHT}};
    }

    public static int inBubble(boolean dark) {
        return dark ? IN_BUBBLE_DARK : IN_BUBBLE_LIGHT;
    }

    /**
     * Light outgoing bubbles are the accent mixed toward white.
     * Dark outgoing bubbles are the accent mixed toward the night wallpaper.
     */
    public static int outBubble(int accent, boolean dark) {
        int rgb = accent & 0x00ffffff;
        if (dark) {
            return 0xff000000 | mix(rgb, 0x0B141A, 40);
        }
        return 0xff000000 | mix(rgb, 0xFFFFFF, 22);
    }

    public static int homeAccentId(String themeName) {
        if ("Blue".equals(themeName)) {
            return 99;
        }
        if ("Day".equals(themeName)) {
            return 9;
        }
        return 0;
    }

    /** Parallel to {@link #ACCENTS}. Index 0 is the default blue and uses the theme's home id. */
    public static int[] accentIds(String themeName) {
        int home = homeAccentId(themeName);
        int[] ids = new int[ACCENTS.length];
        int next = ACCENTS.length - 1;
        for (int i = 0; i < ACCENTS.length; i++) {
            if (i == 0) {
                ids[i] = home;
                continue;
            }
            if (next == home) {
                next--;
            }
            ids[i] = next--;
        }
        return ids;
    }

    public static String licenseNotice() {
        return "聊天壁纸图案来自下列开源项目，已打包在应用内，不使用 WhatsApp 或 Telegram 的图案。\n\n"
                + "Tabler Icons\n"
                + "MIT License\n"
                + "Copyright (c) 2020-2026 Paweł Kuna\n"
                + "https://github.com/tabler/tabler-icons\n\n"
                + "Lucide\n"
                + "ISC License\n"
                + "Copyright (c) Lucide Contributors\n"
                + "https://github.com/lucide-icons/lucide\n\n"
                + "Hero Patterns\n"
                + "CC BY 4.0\n"
                + "Copyright Steve Schoger\n"
                + "https://heropatterns.com\n\n"
                + "Pattern Monster\n"
                + "MIT License\n"
                + "Copyright (c) 2020-2023 pattern.monster\n"
                + "https://github.com/catchspider2002/svelte-svg-patterns\n";
    }

    private static int mix(int accent, int toward, int accentPercent) {
        int keep = 100 - accentPercent;
        int r = (((accent >> 16) & 0xff) * accentPercent + ((toward >> 16) & 0xff) * keep) / 100;
        int g = (((accent >> 8) & 0xff) * accentPercent + ((toward >> 8) & 0xff) * keep) / 100;
        int b = ((accent & 0xff) * accentPercent + (toward & 0xff) * keep) / 100;
        return (r << 16) | (g << 8) | b;
    }

    private static List<Wallpaper> build() {
        ArrayList<Wallpaper> list = new ArrayList<>();
        list.add(new Wallpaper(DEFAULT_LIGHT_ID, "米色涂鸦", "doodle", new int[]{0xFFEFE7DD}, false, false, -12));
        list.add(new Wallpaper(DEFAULT_DARK_ID, "夜间涂鸦", "doodle", new int[]{0xFF0B141A, 0xFF16232B}, true, false, 8));
        list.add(new Wallpaper("tm_mint", "薄荷涂鸦", "doodle", new int[]{0xFFDBEED9, 0xFFB8DCC8, 0xFFE6F2D8, 0xFFC2E3D1}, false, false, -15));
        list.add(new Wallpaper("tm_sky", "天空涂鸦", "doodle", new int[]{0xFFD6E8F7, 0xFFB9D3EE, 0xFFE3DDF5, 0xFFC8E0F4}, false, false, -15));
        list.add(new Wallpaper("tm_peach", "蜜桃", "dots", new int[]{0xFFFCE3D6, 0xFFF8CFC4, 0xFFFDEBD0, 0xFFF6D6DE}, false, false, -15));
        list.add(new Wallpaper("tm_lavender", "薰衣草", "waves", new int[]{0xFFE6DDF7, 0xFFD3C8F0, 0xFFF1E3F5, 0xFFDCD6F6}, false, false, -15));
        list.add(new Wallpaper("tm_sunset", "日落渐变", null, new int[]{0xFFFAD0C4, 0xFFFFD1FF, 0xFFFBC2EB, 0xFFA6C1EE}, false, false, 0));
        list.add(new Wallpaper("tm_forest_dark", "森林夜", "doodle", new int[]{0xFF0F2027, 0xFF203A43, 0xFF2C5364}, true, false, 10));
        list.add(new Wallpaper("tm_ocean_dark", "深海", "waves", new int[]{0xFF141E30, 0xFF243B55}, true, false, 10));
        list.add(new Wallpaper("tm_aurora_dark", "极光", null, new int[]{0xFF1A2A6C, 0xFF3B2667, 0xFF0F3443, 0xFF2D1B4E}, true, false, 0));
        list.add(new Wallpaper(SOLID_ID, "纯色", null, new int[]{SOLID_LIGHT, SOLID_DARK}, false, true, 0));
        return list;
    }
}
