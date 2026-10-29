package org.telegram.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

/**
 * Tellomi: the sign-up screen's "我已阅读并同意《用户协议》和《隐私政策》" checkbox.
 * It replaces the stock terms text in {@link LoginActivity.LoginActivityRegisterView};
 * sign-up does not proceed until it is checked. There is no in-app terms popup.
 * See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */
public class TellomiSignUpAgreement {
    public static final String TERMS_URL = "https://tellomi.cn/legal/terms/";
    public static final String PRIVACY_URL = "https://tellomi.cn/legal/privacy/";

    private TextView view;
    private boolean agreed;

    public boolean isAgreed() {
        return agreed;
    }

    /** Shows the agreement on {@code textView}: a link opens the document, the rest toggles the box. */
    public void install(TextView textView) {
        view = textView;
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
        SpannableStringBuilder sb = new SpannableStringBuilder();
        appendToggle(sb, agreed ? "☑ " : "☐ ", agreed);
        appendToggle(sb, "我已阅读并同意", false);
        appendLink(sb, "《用户协议》", TERMS_URL);
        appendToggle(sb, "和", false);
        appendLink(sb, "《隐私政策》", PRIVACY_URL);
        view.setText(sb);
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
                Browser.openUrl(widget.getContext(), url);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                ds.setUnderlineText(false);
            }
        }, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
