/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Email OTP login/sign-up lives here so LoginActivity
 * stays a short hook. See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.os.Bundle;

/**
 * Email address used as {@code auth.sendCode} / {@code auth.signUp} {@code phone_number}.
 * Password login is not part of this path.
 */
public final class TellomiEmailLogin {

    /** Bundle flag: this auth started from the email field, not a phone number. */
    public static final String PARAM = "tellomiEmailLogin";
    /** The address the user typed. Sent as {@code phone_number}. */
    public static final String PARAM_EMAIL = "tellomiEmail";

    /** Used only when {@code auth.sentCodeTypeEmailCode} omits a positive length. */
    public static final int FALLBACK_CODE_LENGTH = 5;

    private TellomiEmailLogin() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** One {@code @}, a dotted domain, no spaces. The device SIM is not consulted. */
    public static boolean isValid(String email) {
        if (email == null) {
            return false;
        }
        int n = email.length();
        if (n < 3 || n > 254) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at >= n - 1) {
            return false;
        }
        int dot = email.lastIndexOf('.');
        if (dot < at + 2 || dot >= n - 1) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            char c = email.charAt(i);
            if (c <= ' ' || c == '<' || c == '>' || c == '(' || c == ')' || c == '[' || c == ']' || c == '\\' || c == ',' || c == ';') {
                return false;
            }
        }
        return true;
    }

    public static boolean isEmailLogin(Bundle params) {
        return params != null && params.getBoolean(PARAM);
    }

    public static void stamp(Bundle params, String email) {
        params.putBoolean(PARAM, true);
        params.putString(PARAM_EMAIL, email);
    }

    public static void copy(Bundle from, Bundle to) {
        if (to == null || !isEmailLogin(from)) {
            return;
        }
        to.putBoolean(PARAM, true);
        String email = from.getString(PARAM_EMAIL);
        if (email != null) {
            to.putString(PARAM_EMAIL, email);
        }
    }

    /** Server mask when present, otherwise the address the user just typed. Never null. */
    public static String patternOrEmail(String serverPattern, String typedEmail) {
        if (serverPattern != null && serverPattern.length() > 0) {
            return serverPattern;
        }
        return typedEmail == null ? "" : typedEmail;
    }

    public static int codeLength(int serverLength) {
        return serverLength > 0 ? serverLength : FALLBACK_CODE_LENGTH;
    }
}
