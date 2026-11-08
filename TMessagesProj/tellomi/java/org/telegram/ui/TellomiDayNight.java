package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;

/**
 * Day and night toggle shared by the welcome page and the login pages.
 * The icon is the same sun animation. The click target is sized by
 * {@link TellomiAuthChromeLayout}.
 */
public final class TellomiDayNight {

    private TellomiDayNight() {
    }

    public static RLottieImageView create(Context context) {
        RLottieImageView icon = new RLottieImageView(context);
        RLottieDrawable drawable = new RLottieDrawable(R.raw.sun, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        drawable.setPlayInDirectionOfCustomEndFrame(true);
        drawable.beginApplyLayerColors();
        drawable.commitApplyLayerColors();
        boolean dark = Theme.getCurrentTheme() != null && Theme.getCurrentTheme().isDark();
        drawable.setCustomEndFrame(dark ? drawable.getFramesCount() - 1 : 0);
        drawable.setCurrentFrame(dark ? drawable.getFramesCount() - 1 : 0, false);
        icon.setAnimation(drawable);
        icon.setContentDescription(description(dark));
        icon.setOnClickListener(TellomiDayNight::toggle);
        return icon;
    }

    public static void toggle(View view) {
        if (!(view instanceof RLottieImageView) || DialogsActivity.switchingTheme) {
            return;
        }
        DialogsActivity.switchingTheme = true;
        RLottieImageView icon = (RLottieImageView) view;
        RLottieDrawable drawable = icon.getAnimatedDrawable();
        boolean toDark = !Theme.isCurrentThemeDark();
        Theme.ThemeInfo themeInfo = Theme.getTheme(toDark ? "Night" : "Blue");
        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
        Theme.saveAutoNightThemeConfig();
        Theme.cancelAutoNightThemeCallbacks();
        if (drawable != null) {
            drawable.setCustomEndFrame(toDark ? drawable.getFramesCount() - 1 : 0);
            icon.playAnimation();
        }
        int[] pos = new int[2];
        icon.getLocationInWindow(pos);
        pos[0] += icon.getMeasuredWidth() / 2;
        pos[1] += icon.getMeasuredHeight() / 2;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, icon);
        icon.setContentDescription(description(toDark));
    }

    public static String description(boolean dark) {
        return LocaleController.getString(dark ? R.string.AccDescrSwitchToDayTheme : R.string.AccDescrSwitchToNightTheme);
    }
}
