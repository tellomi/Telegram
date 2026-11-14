package org.telegram.ui;

import java.util.Locale;

/**
 * Copy for a failed {@code auth.sendCode}. Unknown errors used to fall
 * through {@code AlertsCreator.processError}, which has no branch for
 * that request and returns immediately on code 406, so the phone screen
 * came back with no dialog. A limit (including an SMS daily cap) uses the
 * flood string. Any other failure stays on the phone screen with the
 * error text.
 */
public final class TellomiLoginErrors {

    private TellomiLoginErrors() {
    }

    public static boolean isSendLimit(String errorText) {
        if (errorText == null || errorText.length() == 0) {
            return false;
        }
        String text = errorText.toUpperCase(Locale.US);
        if (text.contains("FLOOD") || text.contains("LIMIT")) {
            return true;
        }
        return text.contains("SMS_CODE_CREATE_FAILED");
    }

    /**
     * Dialog body. {@code code == -1000} is a dropped connection and stays
     * quiet, matching the previous handler. Everything else is a dialog.
     */
    public static String sendCodeMessage(int code, String errorText, String floodMessage, String genericTitle) {
        if (code == -1000 || errorText == null) {
            return null;
        }
        String flood = floodMessage == null ? "" : floodMessage;
        String generic = genericTitle == null ? "" : genericTitle;
        if (isSendLimit(errorText)) {
            return flood;
        }
        if (generic.length() == 0) {
            return errorText;
        }
        return generic + "\n" + errorText;
    }
}
