package org.telegram.messenger;

/**
 * account.resetAuthorization and auth.resetAuthorizations both return
 * 406 FRESH_RESET_AUTHORISATION_FORBIDDEN when this login is under 24 hours
 * old. The client shows that error. It does not decide from a local date.
 */
public final class TellomiSessionRevoke {

    public static final String FRESH_RESET_AUTHORISATION_FORBIDDEN = "FRESH_RESET_AUTHORISATION_FORBIDDEN";

    private TellomiSessionRevoke() {
    }

    public static boolean isFreshResetForbidden(int code, String text) {
        return text != null && text.contains(FRESH_RESET_AUTHORISATION_FORBIDDEN);
    }

    /** A refused call keeps the device row. Success is the only path that drops it. */
    public static boolean keepDeviceRow(boolean success) {
        return !success;
    }
}
