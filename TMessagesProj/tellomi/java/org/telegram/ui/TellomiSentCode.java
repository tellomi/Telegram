/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Which sentence the login code screen shows for an auth.sentCode type.
 * Auth-type numbers match LoginActivity.AUTH_TYPE_*. See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

public final class TellomiSentCode {

    public static final int KIND_APP = 1;
    public static final int KIND_SMS = 2;
    public static final int KIND_CALL = 3;
    public static final int KIND_EMAIL = 4;

    /** LoginActivity.AUTH_TYPE_MESSAGE */
    private static final int AUTH_TYPE_MESSAGE = 1;
    /** LoginActivity.AUTH_TYPE_FLASH_CALL */
    private static final int AUTH_TYPE_FLASH_CALL = 3;
    /** LoginActivity.AUTH_TYPE_CALL */
    private static final int AUTH_TYPE_CALL = 4;
    /** LoginActivity.AUTH_TYPE_MISSED_CALL */
    private static final int AUTH_TYPE_MISSED_CALL = 11;

    private TellomiSentCode() {
    }

    /**
     * @param authType LoginActivity auth type, ignored when {@code emailLogin} is true
     * @param emailLogin auth.sentCodeTypeEmailCode started from the email field
     */
    /** "简体中文 ▾". An empty name falls back to English. */
    public static String languageLabel(String name) {
        if (name == null || name.length() == 0) {
            name = "English";
        }
        return name + " \u25BE";
    }

    public static int kind(int authType, boolean emailLogin) {
        if (emailLogin) {
            return KIND_EMAIL;
        }
        if (authType == AUTH_TYPE_MESSAGE) {
            return KIND_APP;
        }
        if (authType == AUTH_TYPE_CALL || authType == AUTH_TYPE_FLASH_CALL || authType == AUTH_TYPE_MISSED_CALL) {
            return KIND_CALL;
        }
        return KIND_SMS;
    }
}
