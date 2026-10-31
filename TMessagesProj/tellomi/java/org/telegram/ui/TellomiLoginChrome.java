/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Login home, email, and OTP share one pink action
 * and gray field. There is no phone-password sign-in on this client:
 * auth.sendCode is the login, and the cloud password screen stays the
 * 2FA step that already follows a code. See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.OutlineTextContainerView;

public final class TellomiLoginChrome {

    public static final int PINK = 0xFFFF4B6A;
    public static final int FIELD = 0xFFF3F4F6;

    private TellomiLoginChrome() {
    }

    /** Gray rounded field. The floating outline label is cleared by the caller when it should not show. */
    public static void grayField(OutlineTextContainerView outline) {
        if (outline == null) {
            return;
        }
        outline.setTellomiFilled(FIELD);
        outline.setText("");
    }

    public static ActionButton actionButton(Context context, String label, View.OnClickListener click) {
        ActionButton button = new ActionButton(context, label);
        button.setOnClickListener(click);
        return button;
    }

    public static final class ActionButton extends FrameLayout {
        private final TextView labelView;
        private final ProgressBar progressView;

        ActionButton(Context context, String label) {
            super(context);
            GradientDrawable background = new GradientDrawable();
            background.setColor(PINK);
            background.setCornerRadius(AndroidUtilities.dp(24));
            setBackground(background);
            setMinimumHeight(AndroidUtilities.dp(48));

            labelView = new TextView(context);
            labelView.setText(label);
            labelView.setTextColor(0xFFFFFFFF);
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

        public void setBusy(boolean busy) {
            labelView.setVisibility(busy ? INVISIBLE : VISIBLE);
            progressView.setVisibility(busy ? VISIBLE : GONE);
            setEnabled(!busy);
        }
    }
}
