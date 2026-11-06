package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.InputStream;
import java.util.HashMap;

/** Loads a catalog wallpaper from the bundled pattern bitmaps. */
public final class TellomiWallpaperDraw {

    private static final HashMap<String, Bitmap> PATTERNS = new HashMap<>();

    private TellomiWallpaperDraw() {
    }

    public static Drawable createDefault(boolean dark) {
        return create(TellomiWallpaperCatalog.defaultWallpaper(dark), dark);
    }

    public static Drawable create(TellomiWallpaperCatalog.Wallpaper wallpaper, boolean dark) {
        if (wallpaper == null) {
            return new ColorDrawable(dark ? TellomiWallpaperCatalog.SOLID_DARK : TellomiWallpaperCatalog.SOLID_LIGHT);
        }
        int[] colors = wallpaper.colorsFor(dark);
        if (wallpaper.pattern == null && colors.length < 2) {
            return new ColorDrawable(colors[0]);
        }
        Bitmap pattern = wallpaper.pattern == null ? null : loadPattern(wallpaper.pattern);
        if (pattern == null && colors.length < 2) {
            return new ColorDrawable(colors[0]);
        }
        return motion(colors, pattern == null ? 0 : wallpaper.intensityPercent, pattern);
    }

    private static MotionBackgroundDrawable motion(int[] colors, int intensityPercent, Bitmap pattern) {
        int c0 = colors[0];
        int c1 = colors.length > 1 ? colors[1] : c0;
        int c2 = colors.length > 2 ? colors[2] : c1;
        int c3 = colors.length > 3 ? colors[3] : c2;
        MotionBackgroundDrawable drawable = new MotionBackgroundDrawable(c0, c1, c2, c3, false);
        if (pattern != null && intensityPercent != 0) {
            drawable.setPatternBitmap(intensityPercent, pattern, true);
            drawable.setPatternColorFilter(drawable.getPatternColor());
        }
        return drawable;
    }

    private static Bitmap loadPattern(String name) {
        Bitmap cached = PATTERNS.get(name);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        try (InputStream input = ApplicationLoader.applicationContext.getAssets().open("wallpapers/" + name + ".png")) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap != null) {
                PATTERNS.put(name, bitmap);
            }
            return bitmap;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
