package org.telegram.ui.theme;

/**
 * Decisions for the Telegram theme and wallpaper screens.
 * Built-in themes, accent rows, and color presets stay available when
 * {@code account.getThemes}, {@code account.getChatThemes}, or
 * {@code account.getWallPapers} fails or returns a theme with no wallpaper
 * document. The server catalog is still shown when it arrives.
 */
public final class TelegramThemeFlow {

    /** {@code wallPaperSettings} flag 0: {@code background_color} is present, including black. */
    public static final int BACKGROUND_FLAG = 1;
    /** {@code wallPaperSettings} flag 4: {@code second_background_color} is present. */
    public static final int SECOND_BACKGROUND_FLAG = 16;
    public static final int THIRD_BACKGROUND_FLAG = 32;
    public static final int FOURTH_BACKGROUND_FLAG = 64;

    public static final int CHAT_THEME_DEFAULT = 1;
    public static final int CHAT_THEME_EMOJI = 1 << 1;
    public static final int CHAT_THEME_GIFTS = 1 << 2;

    private TelegramThemeFlow() {
    }

    /** Asset themes (Blue, Dark Blue, Arctic Blue, Day, Night) go in the built-in row. */
    public static boolean includeBundledTheme(String name, boolean hasFile) {
        return name != null && !name.isEmpty() && !hasFile;
    }

    /** The accent swatches stay visible whenever the selected theme has them. */
    public static boolean showAccentRow(boolean themeHasAccents) {
        return themeHasAccents;
    }

    public static String themeKey(String key, boolean night) {
        if (key == null || key.isEmpty()) {
            return night ? "Dark Blue" : "Blue";
        }
        return key;
    }

    public static String wallpaperSlug(String slug) {
        if (slug == null || slug.isEmpty()) {
            return "d";
        }
        return slug;
    }

    public static final class Selection {
        public final String dayTheme;
        public final String nightTheme;
        public final int accentId;
        public final String wallpaperSlug;
        public final int wallpaperColor;
        public final boolean night;

        public Selection(String dayTheme, String nightTheme, int accentId, String wallpaperSlug, int wallpaperColor, boolean night) {
            this.dayTheme = dayTheme;
            this.nightTheme = nightTheme;
            this.accentId = accentId;
            this.wallpaperSlug = wallpaperSlug;
            this.wallpaperColor = wallpaperColor;
            this.night = night;
        }
    }

    public static Selection initialSelection() {
        return new Selection("Blue", "Dark Blue", 99, "d", 0, false);
    }

    public static Selection applyTheme(Selection current, String key, int accentId, boolean night) {
        Selection base = current == null ? initialSelection() : current;
        String stored = themeKey(key, night);
        if (night) {
            return new Selection(base.dayTheme, stored, accentId, base.wallpaperSlug, base.wallpaperColor, true);
        }
        return new Selection(stored, base.nightTheme, accentId, base.wallpaperSlug, base.wallpaperColor, false);
    }

    public static Selection applyWallpaper(Selection current, String slug, int color, boolean failed) {
        Selection base = current == null ? initialSelection() : current;
        if (failed) {
            return base;
        }
        return new Selection(base.dayTheme, base.nightTheme, base.accentId, wallpaperSlug(slug), color, base.night);
    }

    public static String activeTheme(Selection selection) {
        Selection base = selection == null ? initialSelection() : selection;
        return base.night ? base.nightTheme : base.dayTheme;
    }

    /**
     * A transport error or a response that is not {@code account.wallPapers}
     * leaves the list already on screen, including the built-in color presets.
     */
    public static boolean keepCurrentWallpaperList(boolean transportError, boolean parsedList) {
        return transportError || !parsedList;
    }

    /**
     * {@code wallPaperNoFile} is listed even when the only color is black.
     * A negative intensity is a dark-only pattern and stays hidden in day mode.
     */
    public static boolean showColorWallpaper(boolean noFile, int flags, int background, int second, boolean themeDark, int intensity) {
        if (!themeDark && intensity < 0) {
            return false;
        }
        return noFile || backgroundExplicit(flags, background) || secondExplicit(flags, second);
    }

    public static boolean backgroundExplicit(int flags, int background) {
        return (flags & BACKGROUND_FLAG) != 0 || background != 0;
    }

    public static boolean secondExplicit(int flags, int second) {
        return (flags & SECOND_BACKGROUND_FLAG) != 0 || second != 0;
    }

    /** An explicit zero is black, not "no color". */
    public static int opaqueColor(boolean explicit, int color) {
        if (!explicit) {
            return 0;
        }
        return color | 0xff000000;
    }

    public static final class PreviewFill {
        public final int color1;
        public final int color2;
        public final int color3;
        public final int color4;
        public final boolean fromWallpaper;

        public PreviewFill(int color1, int color2, int color3, int color4, boolean fromWallpaper) {
            this.color1 = color1;
            this.color2 = color2;
            this.color3 = color3;
            this.color4 = color4;
            this.fromWallpaper = fromWallpaper;
        }
    }

    /**
     * Chat themes from this server have accent colors and no wallpaper document.
     * The preview still paints: the wallpaper colors when a document-less
     * wallpaper is present, otherwise the accent.
     */
    public static PreviewFill chatThemePreview(boolean hasWallpaper, int background, int second, int third, int fourth, int flags, int accent) {
        if (hasWallpaper && (backgroundExplicit(flags, background) || secondExplicit(flags, second) || third != 0 || fourth != 0)) {
            return new PreviewFill(
                    opaqueColor(backgroundExplicit(flags, background), background),
                    opaqueColor(secondExplicit(flags, second), second),
                    opaqueColor((flags & THIRD_BACKGROUND_FLAG) != 0 || third != 0, third),
                    opaqueColor((flags & FOURTH_BACKGROUND_FLAG) != 0 || fourth != 0, fourth),
                    true
            );
        }
        int fill = opaqueColor(true, accent);
        return new PreviewFill(fill, fill, fill, fill, false);
    }

    /** A failed {@code account.getChatThemes} does not drop themes already stored. */
    public static boolean keepCachedChatThemes(boolean requestFailed) {
        return requestFailed;
    }

    /** Gift-theme RPC failure must not block the emoji chat-theme list. */
    public static boolean loadEmojiThemesAfterGiftFailure() {
        return true;
    }

    public static boolean showChatThemeList(boolean requestFailed) {
        return true;
    }

    public static int chatThemeListFlags() {
        return CHAT_THEME_DEFAULT | CHAT_THEME_EMOJI | CHAT_THEME_GIFTS;
    }

    /** Failed responses keep the cache. A successful payload replaces it. The default row is extra. */
    public static int visibleChatThemes(int cached, int remote, boolean failed, boolean includeDefault) {
        int themes = failed ? Math.max(cached, 0) : Math.max(remote, 0);
        return themes + (includeDefault ? 1 : 0);
    }
}
