/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * The post-login "choose a language" dialog is upstream. The welcome screen
 * used to record language_showed2 when it offered the system language, which
 * is what keeps the dialog from opening again. Tellomi's picker did not.
 * See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;

import java.util.Locale;

public final class TellomiLanguageAlert {

    private TellomiLanguageAlert() {
    }

    /**
     * The user picked a language before login. Store the suggestion the
     * post-login dialog compares, or the chosen code when the suggestion
     * has not arrived yet.
     */
    public static void rememberUserChoice(LocaleController.LocaleInfo info) {
        String shown = null;
        try {
            MessagesController controller = MessagesController.getInstance(UserConfig.selectedAccount);
            if (controller != null && controller.suggestedLangCode != null && controller.suggestedLangCode.length() > 0) {
                shown = controller.suggestedLangCode;
            }
        } catch (Throwable ignored) {
        }
        if ((shown == null || shown.length() == 0) && info != null) {
            shown = info.getLangCode();
        }
        writeShown(shown);
    }

    /**
     * @return true when {@code LaunchActivity.showLanguageAlert} should not open.
     * A saved language ({@code languageOverride}) or a current language that
     * already matches the suggestion is enough.
     */
    public static boolean suppress(String suggested) {
        boolean chosen = false;
        try {
            chosen = LocaleController.getInstance().hasLanguageOverride();
        } catch (Throwable ignored) {
        }
        if (!chosen && !sameAsCurrent(suggested)) {
            return false;
        }
        writeShown(suggested);
        return true;
    }

    /**
     * {@code zh_hans} matches {@code zh-hans} and {@code zh}. {@code zh-hant}
     * does not match {@code zh-hans}. The shared plural {@code zh} is not used,
     * or traditional would match a simplified suggestion.
     */
    public static boolean sameSuggestion(String shortName, String langCode, String suggested) {
        String want = norm(suggested);
        if (want.length() == 0) {
            return false;
        }
        String[] codes = new String[] {norm(shortName), norm(langCode)};
        for (int i = 0; i < codes.length; i++) {
            String code = codes[i];
            if (code.length() == 0) {
                continue;
            }
            if (code.equals(want) || code.startsWith(want + "-") || want.startsWith(code + "-")) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameAsCurrent(String suggested) {
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info == null) {
                return false;
            }
            return sameSuggestion(info.shortName, info.getLangCode(), suggested);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeShown(String shown) {
        if (shown == null || shown.length() == 0) {
            return;
        }
        try {
            MessagesController.getGlobalMainSettings().edit().putString("language_showed2", shown).apply();
        } catch (Throwable ignored) {
        }
    }

    private static String norm(String code) {
        if (code == null) {
            return "";
        }
        return code.replace('_', '-').toLowerCase(Locale.US);
    }
}
