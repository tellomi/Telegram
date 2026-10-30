/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Copy for auth.sendCode errors that upstream does not
 * map to an alert. See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.messenger;

/**
 * User-facing text for Tellomi login RPC errors.
 * The sentence is fixed Chinese, same approach as {@code TellomiSignUpAgreement}:
 * it is not in the cloud langpack, so a string resource would miss on zh-hans.
 */
public final class TellomiLoginErrors {

    /** MTProto error from auth.sendCode when the server could not create an SMS code. */
    public static final String SMS_CODE_CREATE_FAILED = "SMS_CODE_CREATE_FAILED";

    /** Alert body for {@link #SMS_CODE_CREATE_FAILED}. */
    public static final String SMS_CODE_CREATE_FAILED_ALERT = "验证码发送失败，请稍后重试";

    private TellomiLoginErrors() {
    }

    /**
     * True when {@code errorText} is {@code SMS_CODE_CREATE_FAILED}.
     * Matches the {@code contains} checks next to it in the login send-code callback.
     */
    public static boolean isSmsCodeCreateFailed(String errorText) {
        return errorText != null && errorText.contains(SMS_CODE_CREATE_FAILED);
    }
}
