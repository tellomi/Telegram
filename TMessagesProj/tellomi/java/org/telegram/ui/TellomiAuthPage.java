/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Password and email login as their own SlideView pages. LoginActivity.setPage
 * slides them with the same translation as the phone → code step.
 * See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.SlideView;

public class TellomiAuthPage extends SlideView {

    public static final int PHONE_CODE = 0;
    public static final int PHONE_PASSWORD = 1;
    public static final int EMAIL_CODE = 2;
    public static final int EMAIL_PASSWORD = 3;

    public interface Host {
        String email();

        void setEmail(String email);

        void submitEmail(String email, View shakeTarget);

        void cancelAuth();

        void afterIntro(Runnable action);
    }

    private final int mode;
    private final Host host;
    private EditTextBoldCursor emailField;
    private OutlineTextContainerView emailOutline;
    private TellomiLoginChrome.ActionButton button;
    private final TellomiSignUpAgreement agreement = new TellomiSignUpAgreement();

    public TellomiAuthPage(Context context, int mode, Host host) {
        super(context);
        this.mode = mode;
        this.host = host;
        setOrientation(VERTICAL);
        setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(context);
        title.setText(titleText());
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(TellomiLoginChrome.text());
        title.setGravity(Gravity.LEFT);
        addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 56, 0, 0));

        if (mode == EMAIL_CODE) {
            TextView subtitle = new TextView(context);
            subtitle.setText(LocaleController.getString(R.string.TellomiEmailSubtitle));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitle.setTextColor(TellomiLoginChrome.MUTED);
            subtitle.setGravity(Gravity.LEFT);
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        if (mode == EMAIL_CODE || mode == EMAIL_PASSWORD) {
            emailField = field(context, LocaleController.getString(R.string.TellomiEmailHint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            emailOutline = outline(context, emailField);
            addView(emailOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, mode == EMAIL_CODE ? 20 : 24, 0, 0));
            emailField.addTextChangedListener(after(s -> {
                host.setEmail(s.toString());
                refreshReady();
            }));
            emailField.setOnFocusChangeListener((v, hasFocus) -> emailOutline.animateSelection(hasFocus ? 1f : 0f));
        }

        String buttonLabel = LocaleController.getString(R.string.TellomiVerifyLogin);
        button = TellomiLoginChrome.actionButton(context, buttonLabel, v -> {
            if (button.isBusy()) {
                host.cancelAuth();
                return;
            }
            submit();
        });
        addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        TextView agreementView = new TextView(context);
        agreementView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        agreementView.setGravity(Gravity.CENTER);
        agreementView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        agreement.install(agreementView, false);
        addView(agreementView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 4, 14, 4, 0));

    }

    private String titleText() {
        return LocaleController.getString(R.string.TellomiEmailTitle);
    }

    private void submit() {
        if (!agreement.isAgreed()) {
            TellomiSignUpAgreement.show(getContext(), false, () -> {
                agreement.setAgreed(true);
                submit();
            });
            return;
        }
        if (emailField != null) {
            host.submitEmail(emailField.getText() == null ? "" : emailField.getText().toString(), emailOutline);
        }
    }

    private void refreshReady() {
        if (button == null) {
            return;
        }
        String email = emailField == null || emailField.getText() == null ? "" : emailField.getText().toString();
        button.setReady(TellomiEmailLogin.isValid(TellomiEmailLogin.normalize(email)));
    }

    @Override
    public void onShow() {
        if (emailField != null) {
            String email = host.email();
            if (email == null) {
                email = "";
            }
            if (!email.equals(emailField.getText() == null ? "" : emailField.getText().toString())) {
                emailField.setText(email);
            }
            if (mode == EMAIL_CODE) {
                host.afterIntro(() -> {
                    emailField.requestFocus();
                    AndroidUtilities.showKeyboard(emailField);
                });
            }
        }
        refreshReady();
    }

    @Override
    public boolean needBackButton() {
        return true;
    }

    @Override
    public boolean hasCustomKeyboard() {
        return false;
    }

    @Override
    public void onTellomiProgress(boolean show) {
        if (button != null) {
            button.setBusy(show);
        }
    }

    @Override
    public String getHeaderName() {
        return titleText();
    }

    private static EditTextBoldCursor field(Context context, String hint, int inputType) {
        EditTextBoldCursor field = new EditTextBoldCursor(context);
        field.setCursorSize(AndroidUtilities.dp(20));
        field.setCursorWidth(1.5f);
        field.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setMaxLines(1);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setHint(hint);
        field.setHintTextColor(TellomiLoginChrome.fieldHint());
        field.setTextColor(TellomiLoginChrome.text());
        field.setBackground(null);
        field.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        return field;
    }

    private static OutlineTextContainerView outline(Context context, EditTextBoldCursor field) {
        OutlineTextContainerView outline = new OutlineTextContainerView(context);
        TellomiLoginChrome.grayField(outline);
        outline.attachEditText(field);
        outline.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        return outline;
    }

    private interface After {
        void on(Editable s);
    }

    private static TextWatcher after(After after) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                after.on(s);
            }
        };
    }
}
