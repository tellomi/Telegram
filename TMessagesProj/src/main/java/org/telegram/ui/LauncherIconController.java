package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        // TELLOMI: Aqua/Vintage/Turbo/Nox/Premium keep their upstream component names
        // so an already-enabled icon keeps working. Art and titles are the Tellomi set.
        // Order is daily, then structure, then the two specials. None are premium-gated.
        DEFAULT("DefaultIcon", R.color.tellomi_launcher_background, R.drawable.tellomi_launcher_foreground, R.string.AppIconDefault),
        WATER("AquaIcon", R.drawable.tellomi_bg_water, R.drawable.tellomi_launcher_foreground, R.string.AppIconWater),
        SUNSET("VintageIcon", R.drawable.tellomi_bg_sunset, R.drawable.tellomi_launcher_foreground, R.string.AppIconSunset),
        CLASSIC("ClassicIcon", R.drawable.tellomi_bg_classic, R.drawable.tellomi_fg_mark_dark, R.string.AppIconClassic),
        CLASSIC_BLACK("ClassicBlackIcon", R.drawable.tellomi_bg_classic_black, R.drawable.tellomi_launcher_foreground, R.string.AppIconClassicBlack),
        FILLED("TurboIcon", R.drawable.tellomi_bg_filled, R.drawable.tellomi_fg_filled, R.string.AppIconFilled),
        FILLED_BLACK("FilledBlackIcon", R.drawable.tellomi_bg_filled_black, R.drawable.tellomi_fg_filled_black, R.string.AppIconFilledBlack),
        STARRY("NoxIcon", R.drawable.tellomi_bg_starry, R.drawable.tellomi_launcher_foreground, R.string.AppIconStarry),
        PREMIUM("PremiumIcon", R.drawable.tellomi_bg_premium, R.drawable.tellomi_fg_premium, R.string.AppIconPremium);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
