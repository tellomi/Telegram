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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.R;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.SlideView;

public class TellomiAuthPage extends SlideView {

    public static final int PHONE_PASSWORD = 1;
    public static final int EMAIL_CODE = 2;
    public static final int EMAIL_PASSWORD = 3;

    public interface Host {
        void pickCountry(Runnable onUpdated);

        String countryLabel();

        String phoneNumber();

        void setPhoneNumber(String phone);

        String email();

        void setEmail(String email);

        void submitEmail(String email, View shakeTarget);

        void submitPassword();

        void openMode(int mode, boolean back);

        void unavailable();

        void cancelAuth();

        void setCustomKeyboard(boolean visible);
    }

    private final int mode;
    private final Host host;
    private TextView countryView;
    private EditTextBoldCursor phoneField;
    private EditTextBoldCursor emailField;
    private OutlineTextContainerView emailOutline;
    private EditTextBoldCursor passwordField;
    private ImageView passwordEye;
    private boolean passwordVisible;
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

        if (mode == PHONE_PASSWORD) {
            countryView = new TextView(context);
            countryView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            countryView.setTextColor(TellomiLoginChrome.text());
            countryView.setGravity(Gravity.CENTER_VERTICAL);
            countryView.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
            OutlineTextContainerView countryOutline = new OutlineTextContainerView(context);
            TellomiLoginChrome.grayField(countryOutline);
            countryOutline.addView(countryView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));
            countryOutline.setOnClickListener(v -> host.pickCountry(this::refreshCountry));
            addView(countryOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 24, 0, 0));

            phoneField = field(context, LocaleController.getString(R.string.TellomiPhoneHint), InputType.TYPE_CLASS_PHONE);
            OutlineTextContainerView phoneOutline = outline(context, phoneField);
            addView(phoneOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 10, 0, 0));
            phoneField.addTextChangedListener(after(s -> {
                host.setPhoneNumber(s.toString());
                refreshReady();
            }));
            phoneField.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    host.setCustomKeyboard(true);
                }
            });
            phoneField.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    passwordField.requestFocus();
                    return true;
                }
                return false;
            });
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

        if (mode == PHONE_PASSWORD || mode == EMAIL_PASSWORD) {
            passwordField = field(context, LocaleController.getString(R.string.TellomiPasswordHint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordField.setTypeface(Typeface.DEFAULT);
            passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            OutlineTextContainerView passwordOutline = new OutlineTextContainerView(context);
            TellomiLoginChrome.grayField(passwordOutline);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            passwordEye = new ImageView(context);
            passwordEye.setImageResource(R.drawable.tellomi_ic_eye);
            passwordEye.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int eyePad = AndroidUtilities.dp(10);
            passwordEye.setPadding(eyePad, eyePad, eyePad, eyePad);
            passwordEye.setColorFilter(new PorterDuffColorFilter(TellomiLoginChrome.text(), PorterDuff.Mode.SRC_IN));
            passwordEye.setOnClickListener(v -> togglePassword());
            row.addView(passwordField, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            row.addView(passwordEye, LayoutHelper.createLinear(40, 40, 0, 0, 4, 0));
            passwordOutline.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            passwordField.setOnFocusChangeListener((v, hasFocus) -> {
                passwordOutline.animateSelection(hasFocus ? 1f : 0f);
                if (hasFocus) {
                    if (mode == PHONE_PASSWORD) {
                        host.setCustomKeyboard(false);
                    }
                    AndroidUtilities.showKeyboard(passwordField);
                }
            });
            passwordField.addTextChangedListener(after(s -> refreshReady()));
            passwordField.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit();
                    return true;
                }
                return false;
            });
            addView(passwordOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, 10, 0, 0));
        }

        int switchMode = mode == EMAIL_CODE ? EMAIL_PASSWORD : mode == EMAIL_PASSWORD ? EMAIL_CODE : 0;
        boolean switchBack = mode != EMAIL_CODE;
        String switchLabel = LocaleController.getString(mode == PHONE_PASSWORD || mode == EMAIL_PASSWORD ? R.string.TellomiCodeLogin : R.string.TellomiPasswordLogin);
        TextView switchView = TellomiLoginChrome.swapLink(context, switchLabel, v -> host.openMode(switchMode, switchBack));
        addView(switchView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 2, 0, 0));

        String buttonLabel = LocaleController.getString(mode == EMAIL_CODE ? R.string.TellomiVerifyLogin : R.string.TellomiOtpLogin);
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

        LinearLayout circles = new LinearLayout(context);
        circles.setOrientation(HORIZONTAL);
        circles.setGravity(Gravity.CENTER);
        int gap = AndroidUtilities.dp(18);
        int google = com.google.android.gms.base.R.drawable.googleg_standard_color_18;
        circles.addView(TellomiLoginChrome.circleButton(context, google, v -> host.unavailable()), circleLp(0));
        circles.addView(TellomiLoginChrome.circleButton(context, R.drawable.tellomi_ic_apple, v -> host.unavailable()), circleLp(gap));
        if (mode == PHONE_PASSWORD) {
            circles.addView(TellomiLoginChrome.circleButton(context, R.drawable.tellomi_ic_mail, v -> host.openMode(EMAIL_CODE, false)), circleLp(gap));
        }
        circles.addView(TellomiLoginChrome.circleButton(context, R.drawable.tellomi_ic_more, v -> host.unavailable()), circleLp(gap));
        addView(circles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 28, 0, 12));
    }

    private String titleText() {
        int res;
        if (mode == PHONE_PASSWORD) {
            res = R.string.TellomiPhonePasswordTitle;
        } else if (mode == EMAIL_PASSWORD) {
            res = R.string.TellomiEmailPasswordTitle;
        } else {
            res = R.string.TellomiEmailTitle;
        }
        return LocaleController.getString(res);
    }

    private void submit() {
        if (!agreement.isAgreed()) {
            TellomiSignUpAgreement.show(getContext(), false, () -> {
                agreement.setAgreed(true);
                submit();
            });
            return;
        }
        if (mode == EMAIL_CODE) {
            host.submitEmail(emailField.getText() == null ? "" : emailField.getText().toString(), emailOutline);
        } else {
            if (mode == EMAIL_PASSWORD && emailField != null) {
                host.setEmail(emailField.getText() == null ? "" : emailField.getText().toString());
            }
            host.submitPassword();
        }
    }

    private void refreshCountry() {
        if (countryView != null) {
            countryView.setText(host.countryLabel());
        }
    }

    private void refreshReady() {
        if (button == null) {
            return;
        }
        boolean ready;
        if (mode == EMAIL_CODE) {
            String email = emailField == null || emailField.getText() == null ? "" : emailField.getText().toString();
            ready = TellomiEmailLogin.isValid(TellomiEmailLogin.normalize(email));
        } else if (mode == EMAIL_PASSWORD) {
            String email = emailField == null || emailField.getText() == null ? "" : emailField.getText().toString();
            ready = TellomiEmailLogin.isValid(TellomiEmailLogin.normalize(email)) && passwordField != null && passwordField.length() > 0;
        } else {
            String digits = phoneField == null || phoneField.getText() == null ? "" : PhoneFormat.stripExceptNumbers(phoneField.getText().toString());
            ready = digits.length() >= 11 && passwordField != null && passwordField.length() > 0;
        }
        button.setReady(ready);
    }

    private void togglePassword() {
        passwordVisible = !passwordVisible;
        int start = passwordField.getSelectionStart();
        if (passwordVisible) {
            passwordField.setTransformationMethod(null);
            passwordEye.setImageResource(R.drawable.tellomi_ic_eye_off);
        } else {
            passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
            passwordEye.setImageResource(R.drawable.tellomi_ic_eye);
        }
        passwordField.setTypeface(Typeface.DEFAULT);
        if (start >= 0) {
            passwordField.setSelection(Math.min(start, passwordField.length()));
        }
    }

    @Override
    public void onShow() {
        if (mode == PHONE_PASSWORD) {
            refreshCountry();
            if (phoneField != null) {
                String phone = host.phoneNumber();
                if (phone != null && !phone.equals(phoneField.getText() == null ? "" : phoneField.getText().toString())) {
                    phoneField.setText(phone);
                }
            }
        }
        if (emailField != null) {
            String email = host.email();
            if (email == null) {
                email = "";
            }
            if (!email.equals(emailField.getText() == null ? "" : emailField.getText().toString())) {
                emailField.setText(email);
            }
            if (mode == EMAIL_CODE) {
                emailField.requestFocus();
                AndroidUtilities.showKeyboard(emailField);
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
        return mode == PHONE_PASSWORD;
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

    private static LinearLayout.LayoutParams circleLp(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48));
        lp.leftMargin = left;
        return lp;
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
