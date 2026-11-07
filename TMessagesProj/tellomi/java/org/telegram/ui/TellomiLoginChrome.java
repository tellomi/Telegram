/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Login, email, OTP, password, and sign-up share one
 * action button and the owner-2026-10-31 layout. Brand color is black on
 * white: a ready CTA is #111111 with white text, an empty CTA is #E5E5E5
 * with gray text. Links stay #3478F6. See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

import androidx.core.graphics.ColorUtils;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.OutlineTextContainerView;

public final class TellomiLoginChrome {

    /** Ready primary CTA. White label. */
    public static final int CTA = 0xFF111111;
    /** Empty primary CTA. Gray label. */
    public static final int CTA_DISABLED = 0xFFE5E5E5;
    public static final int CTA_DISABLED_TEXT = 0xFFA3A3A3;
    public static final int FIELD = 0xFFF3F4F6;
    public static final int LINK = 0xFF3478F6;
    public static final int ERROR = 0xFFFF3B30;
    public static final int TEXT = 0xFF111111;
    /** Checked agreement box. */
    public static final int CHECKED = 0xFF111111;
    public static final int MUTED = 0xFF8E8E93;
    public static final int LINE = 0xFFE6E6E6;

    private TellomiLoginChrome() {
    }

    public static boolean night() {
        try {
            Theme.ThemeInfo theme = Theme.getActiveTheme();
            if (theme != null && theme.isDark()) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
            double lum = (0.2126 * Color.red(bg) + 0.7152 * Color.green(bg) + 0.0722 * Color.blue(bg)) / 255.0;
            return lum < 0.5;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Page and field label. Follows the active theme, not a separate night guess. */
    public static int text() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int fieldFill() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteInputField);
    }

    public static int fieldHint() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteHintText);
    }

    public static int muted() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6);
    }

    public static int link() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
    }

    public static int errorColor() {
        return Theme.getColor(Theme.key_text_RedBold);
    }

    /** Empty agreement circle. The old #C8C8CC stroke disappeared on a light page. */
    public static int uncheckedStroke() {
        return Theme.getColor(Theme.key_checkboxSquareUnchecked);
    }

    /**
     * Typed text, hint, cursor, and selection for one field. Cursor and selection
     * follow the same ink as the digits so a dark field does not keep a black cursor.
     */
    public static void applyInput(EditTextBoldCursor field) {
        if (field == null) {
            return;
        }
        field.setTextColor(text());
        field.setHintTextColor(fieldHint());
        field.setCursorColor(focusStroke());
        field.setHighlightColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated) & 0x55FFFFFF);
    }

    /** Upstream login button: blue when it can submit, gray when it cannot. */
    public static int ctaFill(boolean ready) {
        if (ready) {
            return Theme.getColor(Theme.key_windowBackgroundWhiteBlueButton);
        }
        return Theme.getColor(Theme.key_windowBackgroundWhiteInputField);
    }

    public static int ctaLabel(boolean ready) {
        if (ready) {
            return Color.WHITE;
        }
        return fieldHint();
    }

    public static int ctaPressed(boolean ready) {
        return ColorUtils.blendARGB(ctaFill(ready), text(), 0.16f);
    }

    public static int focusStroke() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated);
    }

    public static int lineColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteInputField);
    }

    public static int checkedFill() {
        return Theme.getColor(Theme.key_checkboxSquareBackground);
    }

    public static int checkedMark() {
        return Theme.getColor(Theme.key_checkboxSquareCheck);
    }

    public static int pageFill() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    /** Gray rounded field. The caller clears the floating label when it should not show. */
    public static void grayField(OutlineTextContainerView outline) {
        if (outline == null) {
            return;
        }
        outline.setTellomiFilled(fieldFill());
        outline.setText("");
    }

    public static ActionButton actionButton(Context context, String label, View.OnClickListener click) {
        ActionButton button = new ActionButton(context, label);
        button.setOnClickListener(click);
        return button;
    }

    /**
     * Login copy follows the system font, capped so a 16sp label does not become
     * a 32sp label at the largest setting. Pass the design size in sp.
     */
    public static void textSize(TextView view, float baseSp) {
        if (view == null) {
            return;
        }
        float scale = 1f;
        if (view.getResources() != null && view.getResources().getConfiguration() != null) {
            scale = view.getResources().getConfiguration().fontScale;
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TellomiLoginFit.cappedSp(baseSp, scale, TellomiLoginFit.MAX_FONT_SCALE));
    }

    /** Outline around a single-line field. Taller when the font no longer fits 50dp. */
    public static int fieldHeight(Context context) {
        float scale = 1f;
        if (context != null && context.getResources() != null && context.getResources().getConfiguration() != null) {
            scale = context.getResources().getConfiguration().fontScale;
        }
        if (scale <= 0f) {
            scale = 1f;
        }
        int text = Math.round(AndroidUtilities.dp(16) * Math.min(scale, TellomiLoginFit.MAX_FONT_SCALE));
        return TellomiLoginFit.fieldHeightPx(AndroidUtilities.dp(50), text, AndroidUtilities.dp(24));
    }

    /**
     * Card dialog that scrolls and stays inside the window. A language list or a
     * large font otherwise runs off a short or landscape screen, including under
     * the navigation bar.
     */
    public static void presentCard(Dialog dialog, View card) {
        if (dialog == null || card == null) {
            return;
        }
        Context context = card.getContext();
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        int side = AndroidUtilities.dp(24);
        int width = TellomiLoginFit.dialogWidthPx(screenW, side, AndroidUtilities.dp(TellomiLoginFit.DIALOG_MAX_DP));
        int reserved = AndroidUtilities.statusBarHeight
                + Math.max(AndroidUtilities.navigationBarHeight, AndroidUtilities.dp(24))
                + AndroidUtilities.dp(32);
        final int maxHeight = TellomiLoginFit.dialogMaxHeightPx(screenH, reserved);
        ScrollView scroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(card, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout wrap = new FrameLayout(context);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        wrap.addView(scroll, cardLp);
        dialog.setContentView(wrap);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setDimAmount(0.45f);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /**
     * Options under「收不到验证码？」. Help opens {@link BuildVars#HELP_URL}.
     * {@code resend} is the SMS / resend row; null hides it (app-code screen
     * already shows that action on the page).
     */
    public static void showCodeOptions(Context context, String resendLabel, Runnable resend) {
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
        background.setColor(pageFill());
        background.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.TellomiOtpMissing));
        title.setTextColor(text());
        textSize(title, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.leftMargin = AndroidUtilities.dp(12);
        titleLp.rightMargin = AndroidUtilities.dp(12);
        titleLp.bottomMargin = AndroidUtilities.dp(8);
        card.addView(title, titleLp);

        if (resend != null && resendLabel != null && resendLabel.length() > 0) {
            card.addView(optionRow(context, resendLabel, () -> {
                dialog.dismiss();
                resend.run();
            }));
        }
        card.addView(optionRow(context, LocaleController.getString(R.string.TellomiHelp), () -> {
            dialog.dismiss();
            Browser.openUrlInSystemBrowser(context, BuildVars.HELP_URL);
        }));

        TextView cancel = new TextView(context);
        cancel.setText(LocaleController.getString(R.string.Cancel));
        cancel.setTextColor(muted());
        textSize(cancel, 16);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinimumHeight(AndroidUtilities.dp(48));
        cancel.setOnClickListener(v -> dialog.dismiss());
        card.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));

        presentCard(dialog, card);
        dialog.show();
    }

    private static View optionRow(Context context, String label, Runnable click) {
        TextView row = new TextView(context);
        row.setText(label);
        row.setTextColor(link());
        textSize(row, 16);
        row.setMaxLines(3);
        row.setGravity(Gravity.CENTER);
        row.setMinimumHeight(AndroidUtilities.dp(48));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    /** Dark pill used when an SMS code has been sent. Sits in the middle of the screen. */
    public static void showSentPill(Context context, String text) {
        if (!(context instanceof Activity) || text == null) {
            return;
        }
        Activity activity = (Activity) context;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            return;
        }
        ViewGroup root = (ViewGroup) content;
        TextView pill = new TextView(context);
        pill.setText(text);
        pill.setTextColor(Color.WHITE);
        textSize(pill, 14);
        pill.setMaxLines(3);
        int padH = AndroidUtilities.dp(16);
        int padV = AndroidUtilities.dp(10);
        pill.setPadding(padH, padV, padH, padV);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xE63A3A3C);
        background.setCornerRadius(AndroidUtilities.dp(18));
        pill.setBackground(background);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pill.setAlpha(0f);
        root.addView(pill, lp);
        pill.animate().alpha(1f).setDuration(150).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            pill.animate().alpha(0f).setDuration(180).withEndAction(() -> root.removeView(pill)).start();
        }, 1600);
    }

    public static View orDivider(Context context, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View left = line(context);
        View right = line(context);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(muted());
        textSize(text, 13);
        text.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        row.addView(left, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(1), 1f));
        row.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(right, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(1), 1f));
        return row;
    }

    /** Full-width social row. The label is centered; the icon sits on the left. */
    public static View socialRow(Context context, int iconRes, String label, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(AndroidUtilities.dp(48));
        GradientDrawable background = new GradientDrawable();
        background.setColor(pageFill());
        background.setCornerRadius(AndroidUtilities.dp(10));
        background.setStroke(Math.max(1, AndroidUtilities.dp(1)), lineColor());
        row.setBackground(background);
        row.setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(text(), android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22));
        iconLp.leftMargin = AndroidUtilities.dp(16);
        row.addView(icon, iconLp);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(text());
        textSize(text, 16);
        text.setGravity(Gravity.CENTER);
        text.setMaxLines(2);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Space(context), new LinearLayout.LayoutParams(AndroidUtilities.dp(38), 1));
        row.setOnClickListener(click);
        return row;
    }

    private static View line(Context context) {
        View view = new View(context);
        view.setBackgroundColor(lineColor());
        return view;
    }

        public static void restyleSocial(View row) {
            if (!(row instanceof LinearLayout)) {
                return;
            }
            GradientDrawable background = new GradientDrawable();
            background.setColor(pageFill());
            background.setCornerRadius(AndroidUtilities.dp(10));
            background.setStroke(Math.max(1, AndroidUtilities.dp(1)), lineColor());
            row.setBackground(background);
            LinearLayout layout = (LinearLayout) row;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(text());
                } else if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(new android.graphics.PorterDuffColorFilter(text(), android.graphics.PorterDuff.Mode.SRC_IN));
                }
            }
        }

        public static final class ActionButton extends FrameLayout {
        private final TextView labelView;
        private final TextView dotsView;
        private final ProgressBar progressView;
        private final GradientDrawable background;
        private boolean ready;

        ActionButton(Context context, String label) {
            super(context);
            background = new GradientDrawable();
            background.setColor(ctaFill(false));
            background.setCornerRadius(AndroidUtilities.dp(10));
            setBackground(background);
            setForeground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            setMinimumHeight(AndroidUtilities.dp(48));

            labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(ctaLabel(false));
            textSize(labelView, 16);
            labelView.setTypeface(AndroidUtilities.bold());
            labelView.setGravity(Gravity.CENTER);
            labelView.setMaxLines(2);
            int padH = AndroidUtilities.dp(16);
            int padV = AndroidUtilities.dp(12);
            labelView.setPadding(padH, padV, padH, padV);
            addView(labelView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            dotsView = new TextView(context);
            dotsView.setText("•••");
            dotsView.setTextColor(ctaLabel(true));
            textSize(dotsView, 22);
            dotsView.setTypeface(AndroidUtilities.bold());
            dotsView.setLetterSpacing(0.35f);
            dotsView.setGravity(Gravity.CENTER);
            dotsView.setVisibility(GONE);
            addView(dotsView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            progressView = new ProgressBar(context);
            progressView.setIndeterminate(true);
            progressView.setVisibility(GONE);
            tintProgress();
            addView(progressView, new FrameLayout.LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24), Gravity.CENTER));
        }

        public void setLabel(String label) {
            labelView.setText(label);
        }

        /** Black (or white in dark mode) when the form can be submitted. Still clickable while empty. */
        public boolean isReady() {
            return ready;
        }

        public void setReady(boolean ready) {
            this.ready = ready;
            if (dotsView.getVisibility() == VISIBLE) {
                background.setColor(ctaFill(true));
                return;
            }
            background.setColor(ctaFill(ready));
            labelView.setTextColor(ctaLabel(ready));
        }

        public void applyTheme() {
            setReady(ready);
            tintProgress();
        }

        private void tintProgress() {
            if (progressView.getIndeterminateDrawable() != null) {
                progressView.getIndeterminateDrawable().setColorFilter(new android.graphics.PorterDuffColorFilter(ctaLabel(true), android.graphics.PorterDuff.Mode.SRC_IN));
            }
        }

        public void setBusy(boolean busy) {
            labelView.setVisibility(busy ? INVISIBLE : VISIBLE);
            dotsView.setVisibility(busy ? VISIBLE : GONE);
            dotsView.setTextColor(ctaLabel(true));
            progressView.setVisibility(GONE);
            background.setColor(ctaFill(busy || ready));
            if (!busy) {
                labelView.setTextColor(ctaLabel(ready));
            }
            // Stay enabled. A second tap cancels auth.sendCode instead of
            // starting another one. Disabling the button left no cancel.
            setEnabled(true);
        }

        public boolean isBusy() {
            return dotsView.getVisibility() == VISIBLE;
        }
    }
}
