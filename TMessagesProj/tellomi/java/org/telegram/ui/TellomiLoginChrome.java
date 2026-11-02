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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
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
            return theme != null && theme.isDark();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Page and field label. Light stays #111111; dark uses white so typed text stays readable. */
    public static int text() {
        return night() ? 0xFFFFFFFF : TEXT;
    }

    public static int fieldFill() {
        return night() ? 0xFF2C2C2E : FIELD;
    }

    public static int fieldHint() {
        return night() ? 0xFF8E8E93 : 0xFFC7C7CC;
    }

    /** Light CTA is #111111. On a dark page that color disappears into the chrome, so the button flips to white. */
    public static int ctaFill(boolean ready) {
        if (ready) {
            return night() ? 0xFFFFFFFF : CTA;
        }
        return night() ? 0xFF3A3A3C : CTA_DISABLED;
    }

    public static int ctaLabel(boolean ready) {
        if (ready) {
            return night() ? 0xFF111111 : Color.WHITE;
        }
        return night() ? 0xFF8E8E93 : CTA_DISABLED_TEXT;
    }

    public static int focusStroke() {
        return night() ? 0xFFFFFFFF : 0xFF111111;
    }

    public static int lineColor() {
        return night() ? 0xFF3A3A3C : LINE;
    }

    public static int checkedFill() {
        return night() ? 0xFFFFFFFF : CHECKED;
    }

    public static int checkedMark() {
        return night() ? 0xFF111111 : Color.WHITE;
    }

    public static int pageFill() {
        return night() ? 0xFF1C1C1E : Color.WHITE;
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
     * Apple, Google, password login, and any other method that is not wired up.
     * Same card as the terms dialog. Copy matches the iOS text alert:
     * title, one sentence, and a single OK button.
     */
    public static void showUnavailable(Context context) {
        if (context == null) {
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(pageFill());
        background.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.TellomiNotOpen));
        title.setTextColor(text());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setText(LocaleController.getString(R.string.TellomiNotOpenBody));
        body.setTextColor(MUTED);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(AndroidUtilities.dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = AndroidUtilities.dp(14);
        bodyLp.bottomMargin = AndroidUtilities.dp(18);
        card.addView(body, bodyLp);

        ActionButton ok = actionButton(context, LocaleController.getString(R.string.TellomiNotOpenOk), v -> dialog.dismiss());
        ok.setReady(true);
        card.addView(ok, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));

        FrameLayout wrap = new FrameLayout(context);
        int side = AndroidUtilities.dp(32);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = side;
        cardLp.rightMargin = side;
        wrap.addView(card, cardLp);
        dialog.setContentView(wrap);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.45f);
        }
        dialog.show();
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
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
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
        cancel.setTextColor(MUTED);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinimumHeight(AndroidUtilities.dp(48));
        cancel.setOnClickListener(v -> dialog.dismiss());
        card.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));

        FrameLayout wrap = new FrameLayout(context);
        int side = AndroidUtilities.dp(32);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = side;
        cardLp.rightMargin = side;
        wrap.addView(card, cardLp);
        dialog.setContentView(wrap);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setDimAmount(0.45f);
        }
        dialog.show();
    }

    private static View optionRow(Context context, String label, Runnable click) {
        TextView row = new TextView(context);
        row.setText(label);
        row.setTextColor(LINK);
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
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
        pill.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
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

    public static TextView swapLink(Context context, String label, View.OnClickListener click) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextColor(LINK);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        view.setCompoundDrawablePadding(AndroidUtilities.dp(6));
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.tellomi_ic_swap, 0, 0, 0);
        view.setPadding(0, AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        view.setOnClickListener(click);
        return view;
    }

    public static View orDivider(Context context, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View left = line(context);
        View right = line(context);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(MUTED);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
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
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(22), AndroidUtilities.dp(22));
        iconLp.leftMargin = AndroidUtilities.dp(16);
        row.addView(icon, iconLp);
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(text());
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setGravity(Gravity.CENTER);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(new Space(context), new LinearLayout.LayoutParams(AndroidUtilities.dp(38), 1));
        row.setOnClickListener(click);
        return row;
    }

    public static ImageView circleButton(Context context, int iconRes, View.OnClickListener click) {
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = AndroidUtilities.dp(12);
        icon.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(pageFill());
        background.setStroke(Math.max(1, AndroidUtilities.dp(1)), lineColor());
        icon.setBackground(background);
        icon.setOnClickListener(click);
        return icon;
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
                }
            }
        }

        public static void restyleCircle(ImageView icon) {
            if (icon == null) {
                return;
            }
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(pageFill());
            background.setStroke(Math.max(1, AndroidUtilities.dp(1)), lineColor());
            icon.setBackground(background);
        }

        public static final class ActionButton extends FrameLayout {
        private final TextView labelView;
        private final ProgressBar progressView;
        private final GradientDrawable background;
        private boolean ready;

        ActionButton(Context context, String label) {
            super(context);
            background = new GradientDrawable();
            background.setColor(ctaFill(false));
            background.setCornerRadius(AndroidUtilities.dp(10));
            setBackground(background);
            setMinimumHeight(AndroidUtilities.dp(48));

            labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(ctaLabel(false));
            labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            labelView.setTypeface(AndroidUtilities.bold());
            labelView.setGravity(Gravity.CENTER);
            addView(labelView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));

            progressView = new ProgressBar(context);
            progressView.setIndeterminate(true);
            progressView.setVisibility(GONE);
            addView(progressView, new FrameLayout.LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24), Gravity.CENTER));
        }

        public void setLabel(String label) {
            labelView.setText(label);
        }

        /** Black (or white in dark mode) when the form can be submitted. Still clickable while empty. */
        public void setReady(boolean ready) {
            this.ready = ready;
            background.setColor(ctaFill(ready));
            labelView.setTextColor(ctaLabel(ready));
        }

        public void applyTheme() {
            setReady(ready);
        }

        public void setBusy(boolean busy) {
            labelView.setVisibility(busy ? INVISIBLE : VISIBLE);
            progressView.setVisibility(busy ? VISIBLE : GONE);
            // Stay enabled. A second tap cancels auth.sendCode instead of
            // starting another one. Disabling the button left no cancel.
            setEnabled(true);
        }

        public boolean isBusy() {
            return progressView.getVisibility() == VISIBLE;
        }
    }
}
