package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TellomiDefaults;
import org.telegram.messenger.browser.Browser;

/**
 * Tellomi agreement line and the owner modal.
 * The line is a {@link SpannableString}. A {@link SpannableStringBuilder} is
 * {@link android.text.Editable}, and {@code TextView.setText} then installs an
 * editor that consumes the touch before the circle and the links run.
 * Submitting while the box is clear opens {@link #show}.
 * See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */
public class TellomiSignUpAgreement {
    public static final String TERMS_URL = "https://tellomi.cn/legal/terms/";
    public static final String PRIVACY_URL = "https://tellomi.cn/legal/privacy/";
    /** Carrier agreement. Same host the product locked for legal pages. */
    public static final String LEGAL_URL = "https://tellomi.cn/legal";

    private static final int KIND_SIGN_UP = 0;
    private static final int KIND_PHONE = 1;
    private static final int KIND_EMAIL = 2;

    private TextView view;
    private boolean agreed;
    private int kind = KIND_SIGN_UP;

    public boolean isAgreed() {
        return agreed;
    }

    public void setAgreed(boolean value) {
        agreed = value;
        render();
    }

    /** Sign-up name screen. */
    public void install(TextView textView) {
        install(textView, KIND_SIGN_UP);
    }

    /** Phone home passes true. Email and password screens pass false. */
    public void install(TextView textView, boolean phoneCarrierNote) {
        install(textView, phoneCarrierNote ? KIND_PHONE : KIND_EMAIL);
    }

    private void install(TextView textView, int kind) {
        view = textView;
        this.kind = kind;
        if (view != null) {
            view.setHighlightColor(Color.TRANSPARENT);
        }
        render();
    }

    /** Returns true when sign-up may proceed; otherwise shakes the checkbox. */
    public boolean checkAgreed() {
        if (!agreed && view != null) {
            AndroidUtilities.shakeViewSpring(view, 3.5f);
        }
        return agreed;
    }

    /**
     * Agreement modal. {@code carrier} includes the carrier agreement (phone home only).
     * Agree runs {@code onAgree}. Disagree only dismisses.
     */
    public static void show(Context context, boolean carrier, Runnable onAgree) {
        if (context == null) {
            return;
        }
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(background);

        TextView title = new TextView(context);
        title.setText(LocaleController.getString(R.string.TellomiAgreeTitle));
        title.setTextColor(TellomiLoginChrome.TEXT);
        title.setTextSize(17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setText(bodyText(context, carrier));
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setHighlightColor(Color.TRANSPARENT);
        body.setTextSize(14);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(AndroidUtilities.dp(3), 1f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = AndroidUtilities.dp(14);
        bodyLp.bottomMargin = AndroidUtilities.dp(18);
        card.addView(body, bodyLp);

        TellomiLoginChrome.ActionButton agree = TellomiLoginChrome.actionButton(context, LocaleController.getString(R.string.TellomiAgreeAndLogin), v -> {
            dialog.dismiss();
            if (onAgree != null) {
                onAgree.run();
            }
        });
        agree.setReady(true);
        card.addView(agree, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));

        TextView decline = new TextView(context);
        decline.setText(LocaleController.getString(R.string.TellomiDisagree));
        decline.setTextColor(TellomiLoginChrome.MUTED);
        decline.setTextSize(15);
        decline.setGravity(Gravity.CENTER);
        decline.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(10));
        decline.setOnClickListener(v -> dialog.dismiss());
        card.addView(decline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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

    private static CharSequence bodyText(Context context, boolean carrier) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        boolean chinese = TellomiDefaults.uiIsChinese();
        if (chinese) {
            appendPlain(sb, "已阅读并同意 ");
            appendDoc(sb, context, "用户协议", TERMS_URL);
            appendPlain(sb, " 和 ");
            appendDoc(sb, context, "隐私政策", PRIVACY_URL);
            if (carrier) {
                appendPlain(sb, " 以及 ");
                appendDoc(sb, context, LocaleController.getString(R.string.TellomiCarrierAgreement), LEGAL_URL);
                appendPlain(sb, "，运营商将对你提供的手机号进行验证");
            }
        } else {
            appendPlain(sb, "I have read and agree to the ");
            appendDoc(sb, context, "Terms", TERMS_URL);
            appendPlain(sb, " and ");
            appendDoc(sb, context, "Privacy Policy", PRIVACY_URL);
            if (carrier) {
                appendPlain(sb, " and the ");
                appendDoc(sb, context, LocaleController.getString(R.string.TellomiCarrierAgreement), LEGAL_URL);
                appendPlain(sb, ". The carrier will verify this phone number.");
            }
        }
        return new SpannableString(sb);
    }

    private void render() {
        if (view == null) {
            return;
        }
        boolean chinese = TellomiDefaults.uiIsChinese();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendBox(sb);
        if (chinese) {
            appendToggle(sb, kind == KIND_SIGN_UP ? "我已阅读并同意 " : "已阅读并同意 ");
            appendLink(sb, kind == KIND_SIGN_UP ? "《用户服务协议》" : "用户协议", TERMS_URL);
            appendToggle(sb, " 和 ");
            appendLink(sb, kind == KIND_SIGN_UP ? "《隐私政策》" : "隐私政策", PRIVACY_URL);
            if (kind == KIND_PHONE) {
                appendToggle(sb, " 以及 ");
                appendLink(sb, LocaleController.getString(R.string.TellomiCarrierAgreement), LEGAL_URL);
                appendToggle(sb, "，运营商将对你提供的手机号进行验证");
            }
        } else if (kind == KIND_PHONE) {
            appendToggle(sb, "I have read and agree to the ");
            appendLink(sb, "Terms", TERMS_URL);
            appendToggle(sb, " and ");
            appendLink(sb, "Privacy Policy", PRIVACY_URL);
            appendToggle(sb, " and the ");
            appendLink(sb, LocaleController.getString(R.string.TellomiCarrierAgreement), LEGAL_URL);
            appendToggle(sb, ". The carrier will verify this phone number.");
        } else {
            appendToggle(sb, "I have read and agree to the ");
            appendLink(sb, "Terms", TERMS_URL);
            appendToggle(sb, " and ");
            appendLink(sb, "Privacy Policy", PRIVACY_URL);
        }
        view.setTextIsSelectable(false);
        view.setLinksClickable(true);
        view.setHighlightColor(Color.TRANSPARENT);
        view.setText(new SpannableString(sb), TextView.BufferType.SPANNABLE);
        view.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        view.setContentDescription(sb.toString());
    }

    private void appendBox(SpannableStringBuilder sb) {
        int start = sb.length();
        sb.append("  ");
        final boolean checked = agreed;
        sb.setSpan(new ReplacementSpan() {
            @Override
            public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
                return AndroidUtilities.dp(20);
            }

            @Override
            public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
                float cx = x + AndroidUtilities.dp(8);
                float cy = (top + bottom) / 2f;
                float r = AndroidUtilities.dp(7);
                if (checked) {
                    box.setStyle(Paint.Style.FILL);
                    box.setColor(TellomiLoginChrome.CHECKED);
                    canvas.drawCircle(cx, cy, r, box);
                    box.setColor(Color.WHITE);
                    box.setStyle(Paint.Style.STROKE);
                    box.setStrokeWidth(AndroidUtilities.dp(1.4f));
                    box.setStrokeCap(Paint.Cap.ROUND);
                    box.setStrokeJoin(Paint.Join.ROUND);
                    float s = AndroidUtilities.dp(3.2f);
                    canvas.drawLine(cx - s, cy, cx - s * 0.2f, cy + s * 0.75f, box);
                    canvas.drawLine(cx - s * 0.2f, cy + s * 0.75f, cx + s * 1.15f, cy - s * 0.7f, box);
                } else {
                    box.setStyle(Paint.Style.STROKE);
                    box.setStrokeWidth(AndroidUtilities.dp(1.2f));
                    box.setColor(0xFFC8C8CC);
                    canvas.drawCircle(cx, cy, r, box);
                }
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(toggleSpan(), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendToggle(SpannableStringBuilder sb, String s) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(toggleSpan(), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private ClickableSpan toggleSpan() {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                agreed = !agreed;
                render();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(TellomiLoginChrome.MUTED);
                ds.setUnderlineText(false);
            }
        };
    }

    private void appendLink(SpannableStringBuilder sb, String s, String url) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(docSpan(url), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendPlain(SpannableStringBuilder sb, String s) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(new android.text.style.ForegroundColorSpan(TellomiLoginChrome.MUTED), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void appendDoc(SpannableStringBuilder sb, Context context, String s, String url) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Browser.openUrlInSystemBrowser(context, url);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(TellomiLoginChrome.LINK);
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static ClickableSpan docSpan(String url) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Browser.openUrlInSystemBrowser(widget.getContext(), url);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(TellomiLoginChrome.LINK);
                ds.setUnderlineText(false);
            }
        };
    }
}
