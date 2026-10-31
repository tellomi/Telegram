package org.telegram.ui;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.TellomiDefaults;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

/**
 * Tellomi: agreement line on the phone home and the sign-up screen.
 * A {@link SpannableStringBuilder} is {@link android.text.Editable}, and
 * {@code TextView.setText} then installs an editor that consumes the touch
 * before {@link ClickableSpan}s run. The line is applied as a
 * {@link SpannableString} so the circle and the legal links receive taps.
 * There is no in-app terms popup.
 * See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */
public class TellomiSignUpAgreement {
    public static final String TERMS_URL = "https://tellomi.cn/legal/terms/";
    public static final String PRIVACY_URL = "https://tellomi.cn/legal/privacy/";
    /** Carrier service agreement. Terms and privacy stay on the paths above. */
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

    /** Sign-up name screen. */
    public void install(TextView textView) {
        install(textView, KIND_SIGN_UP);
    }

    /** Phone home passes true. Email home passes false. */
    public void install(TextView textView, boolean phoneCarrierNote) {
        install(textView, phoneCarrierNote ? KIND_PHONE : KIND_EMAIL);
    }

    private void install(TextView textView, int kind) {
        view = textView;
        this.kind = kind;
        render();
    }

    /** Returns true when sign-up may proceed; otherwise shakes the checkbox. */
    public boolean checkAgreed() {
        if (!agreed && view != null) {
            AndroidUtilities.shakeViewSpring(view, 3.5f);
        }
        return agreed;
    }

    private void render() {
        if (view == null) {
            return;
        }
        boolean chinese = TellomiDefaults.uiIsChinese();
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendToggle(sb, agreed ? "● " : "○ ", agreed);
        if (chinese) {
            appendToggle(sb, kind == KIND_SIGN_UP ? "我已阅读并同意 " : "已阅读并同意 ", false);
            appendLink(sb, kind == KIND_SIGN_UP ? "《用户服务协议》" : "用户协议", TERMS_URL);
            appendToggle(sb, " 和 ", false);
            appendLink(sb, kind == KIND_SIGN_UP ? "《隐私政策》" : "隐私政策", PRIVACY_URL);
            if (kind == KIND_PHONE) {
                appendToggle(sb, " 以及 ", false);
                appendLink(sb, "运营商服务协议", LEGAL_URL);
                appendToggle(sb, "。运营商将对你提供的手机号进行验证", false);
            }
        } else if (kind == KIND_PHONE) {
            appendToggle(sb, "I have read and agree to the ", false);
            appendLink(sb, "Terms", TERMS_URL);
            appendToggle(sb, " and ", false);
            appendLink(sb, "Privacy Policy", PRIVACY_URL);
            appendToggle(sb, ". The carrier will verify this phone number.", false);
        } else {
            appendToggle(sb, "I have read and agree to the ", false);
            appendLink(sb, "Terms", TERMS_URL);
            appendToggle(sb, " and ", false);
            appendLink(sb, "Privacy Policy", PRIVACY_URL);
        }
        // SpannableString is not Editable, so LinkMovementMethod receives the click.
        view.setTextIsSelectable(false);
        view.setLinksClickable(true);
        view.setText(new SpannableString(sb), TextView.BufferType.SPANNABLE);
        view.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        view.setContentDescription(sb.toString());
    }

    private void appendToggle(SpannableStringBuilder sb, String s, boolean accent) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                agreed = !agreed;
                render();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Theme.getColor(accent ? Theme.key_windowBackgroundWhiteLinkText : Theme.key_windowBackgroundWhiteGrayText6));
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void appendLink(SpannableStringBuilder sb, String s, String url) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Browser.openUrlInSystemBrowser(widget.getContext(), url);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(0xFF3478F6);
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
