/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Bundled language list for the pre-login picker. The old card hardcoded
 * three rows (简体中文, 繁體中文, English). That was not a langpack result.
 * The codes below are the packs this tree ships a local bin for. English
 * and Chinese bins are complete. The others are a fallback until
 * LocaleController.applyLanguage downloads langpack.getLangPack.
 * See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDefaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class TellomiLanguageSheet {

    public interface Callback {
        void onLanguage(LocaleController.LocaleInfo info);
    }

    private TellomiLanguageSheet() {
    }

    public static String currentLabel() {
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        return TellomiSentCode.languageLabel(info == null ? null : info.name);
    }

    public static boolean sameLanguage(LocaleController.LocaleInfo a, LocaleController.LocaleInfo b) {
        if (a == null || b == null || a.shortName == null || b.shortName == null) {
            return false;
        }
        return a.shortName.replace('-', '_').equalsIgnoreCase(b.shortName.replace('-', '_'));
    }

    /**
     * Languages shown before langpack.getLanguages returns.
     * values-* folders: ar, de, es, it, ko, nl, pt-rBR, ru, uk, zh.
     * English is the default values/ pack. zh_hans and zh_hant both read localization_zh.bin.
     * The other codes still call langpack.getLangPack on apply. Their bins are a
     * fallback when that request fails. Anything outside this list still needs
     * langpack.getLanguages.
     */
    public static final List<String> BUNDLED_LANG_CODES = Collections.unmodifiableList(Arrays.asList(
            "en", "it", "es", "de", "nl", "ar", "pt_br", "ko", "uk", "ru", "zh_hans", "zh_hant"
    ));

    public static List<String> bundledCodes() {
        return BUNDLED_LANG_CODES;
    }

    /** Built-in rows for every bundled pack. Works with no network. */
    public static List<LocaleController.LocaleInfo> bundledChoices() {
        LocaleController controller = LocaleController.getInstance();
        ArrayList<LocaleController.LocaleInfo> out = new ArrayList<>();
        for (int i = 0; i < BUNDLED_LANG_CODES.size(); i++) {
            String code = BUNDLED_LANG_CODES.get(i);
            LocaleController.LocaleInfo info = controller.getLanguageFromDict(code);
            if (info == null && "zh_hans".equals(code)) {
                info = TellomiDefaults.builtinChinese(false);
            }
            if (info == null && "zh_hant".equals(code)) {
                info = TellomiDefaults.builtinChinese(true);
            }
            if (info != null) {
                out.add(info);
            }
        }
        return out;
    }

    /**
     * Bundled packs, then any extra row {@code langpack.getLanguages} added.
     * That request uses {@code RequestFlagWithoutLogin}, so this works before sign-in.
     */
    public static List<LocaleController.LocaleInfo> loginChoices() {
        List<LocaleController.LocaleInfo> bundled = bundledChoices();
        ArrayList<LocaleController.LocaleInfo> out = new ArrayList<>(bundled);
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < bundled.size(); i++) {
            LocaleController.LocaleInfo info = bundled.get(i);
            if (info != null && info.shortName != null) {
                seen.add(info.shortName.replace('-', '_').toLowerCase());
            }
        }
        ArrayList<LocaleController.LocaleInfo> all = LocaleController.getInstance().languages;
        for (int i = 0; i < all.size(); i++) {
            LocaleController.LocaleInfo info = all.get(i);
            if (info == null || info.shortName == null) {
                continue;
            }
            if (!info.isRemote() && !info.isBuiltIn()) {
                continue;
            }
            String key = info.shortName.replace('-', '_').toLowerCase();
            if (seen.add(key)) {
                out.add(info);
            }
        }
        return out;
    }

    public static LocaleController.LocaleInfo[] choices() {
        List<LocaleController.LocaleInfo> list = loginChoices();
        return list.toArray(new LocaleController.LocaleInfo[0]);
    }

    public static void show(Context context, Callback callback) {
        if (context == null) {
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(18), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(TellomiLoginChrome.pageFill());
        background.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.TellomiChooseLanguage));
        title.setTextColor(TellomiLoginChrome.text());
        TellomiLoginChrome.textSize(title, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = AndroidUtilities.dp(12);
        titleLp.rightMargin = AndroidUtilities.dp(12);
        titleLp.bottomMargin = AndroidUtilities.dp(8);
        card.addView(title, titleLp);

        LocaleController.LocaleInfo current = LocaleController.getInstance().getCurrentLocaleInfo();
        LocaleController.LocaleInfo[] choices = choices();
        for (int i = 0; i < choices.length; i++) {
            LocaleController.LocaleInfo info = choices[i];
            if (info == null) {
                continue;
            }
            card.addView(row(context, info.name, TellomiLanguageSheet.sameLanguage(current, info), () -> {
                dialog.dismiss();
                if (callback != null && !sameLanguage(current, info)) {
                    callback.onLanguage(info);
                }
            }));
        }

        TextView cancel = new TextView(context);
        cancel.setText(LocaleController.getString(R.string.Cancel));
        cancel.setTextColor(TellomiLoginChrome.MUTED);
        TellomiLoginChrome.textSize(cancel, 16);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinimumHeight(AndroidUtilities.dp(48));
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48));
        cancelLp.topMargin = AndroidUtilities.dp(4);
        card.addView(cancel, cancelLp);

        TellomiLoginChrome.presentCard(dialog, card);
        dialog.show();
    }

    private static View row(Context context, String name, boolean checked, Runnable click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(AndroidUtilities.dp(48));
        row.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

        TextView label = new TextView(context);
        label.setText(name);
        label.setTextColor(TellomiLoginChrome.text());
        TellomiLoginChrome.textSize(label, 16);
        label.setMaxLines(2);
        if (checked) {
            label.setTypeface(AndroidUtilities.bold());
        }
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView mark = new TextView(context);
        mark.setText("\u2713");
        mark.setTextColor(TellomiLoginChrome.text());
        TellomiLoginChrome.textSize(mark, 18);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
        row.addView(mark, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> click.run());
        return row;
    }
}
