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

    /** Daily cap or a timed flood on auth.sendCode / auth.resendCode. */
    public static boolean isCodeFlood(String errorText) {
        if (errorText == null || errorText.isEmpty()) {
            return false;
        }
        return errorText.contains("PHONE_CODE_FLOOD")
                || errorText.contains("PHONE_NUMBER_FLOOD")
                || errorText.contains("FLOOD_WAIT");
    }

    /** PHONE_CODE_FLOOD / PHONE_NUMBER_FLOOD: stop offering another send today. */
    public static boolean isDailyCodeFlood(String errorText) {
        return errorText != null && (errorText.contains("PHONE_CODE_FLOOD") || errorText.contains("PHONE_NUMBER_FLOOD"));
    }

    /** Seconds from {@code FLOOD_WAIT_X}. Null when the text has no count. */
    public static Integer floodWaitSeconds(String errorText) {
        if (errorText == null) {
            return null;
        }
        int mark = errorText.indexOf("FLOOD_WAIT_");
        if (mark < 0) {
            return null;
        }
        int start = mark + "FLOOD_WAIT_".length();
        int end = start;
        while (end < errorText.length() && errorText.charAt(end) >= '0' && errorText.charAt(end) <= '9') {
            end++;
        }
        if (end == start) {
            return null;
        }
        try {
            return Integer.parseInt(errorText.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Alert body. Daily cap is one sentence. A timed flood names the wait.
     * Null when {@code errorText} is not a code flood.
     */
    public static String codeFloodText(String errorText, boolean chinese) {
        if (!isCodeFlood(errorText)) {
            return null;
        }
        if (isDailyCodeFlood(errorText)) {
            return chinese
                    ? "今天的验证码已达上限，每个手机号或邮箱最多 10 条，请明天再试"
                    : "You have reached today's limit of 10 codes for this phone number or email. Try again tomorrow.";
        }
        Integer seconds = floodWaitSeconds(errorText);
        if (seconds == null || seconds <= 0) {
            return chinese ? "验证码发送过于频繁，请稍后再试" : "Too many codes were requested. Please try again later.";
        }
        // FLOOD_WAIT is the short resend lock, usually 60 seconds. It is not the daily cap.
        if (seconds < 120) {
            return chinese
                    ? "发送太频繁，请 " + seconds + " 秒后再试"
                    : "Please wait " + seconds + " seconds before requesting another code.";
        }
        if (seconds >= 3600) {
            int hours = Math.max(1, (seconds + 3599) / 3600);
            return chinese
                    ? "验证码发送过于频繁，请约 " + hours + " 小时后再试"
                    : "Too many codes were requested. Please try again in about " + hours + " hours.";
        }
        if (seconds >= 60) {
            int minutes = Math.max(1, (seconds + 59) / 60);
            return chinese
                    ? "验证码发送过于频繁，请 " + minutes + " 分钟后再试"
                    : "Too many codes were requested. Please try again in " + minutes + " minutes.";
        }
        return chinese
                ? "验证码发送过于频繁，请 " + seconds + " 秒后再试"
                : "Too many codes were requested. Please try again in " + seconds + " seconds.";
    }
}
