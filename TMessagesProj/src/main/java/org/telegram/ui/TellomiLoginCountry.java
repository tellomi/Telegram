package org.telegram.ui;

/**
 * Fresh phone login opens on China (+86) with an empty number.
 * The country list still changes the code. Changing an existing
 * number keeps the account phone and does not use this default.
 */
public final class TellomiLoginCountry {

    public static final String ISO = "CN";
    public static final String CALLING_CODE = "86";

    private TellomiLoginCountry() {
    }

    /**
     * Unauthenticated phone screen, including adding an account.
     * Change-number keeps the number already on the account.
     */
    public static boolean useChinaDefault(boolean loginMode) {
        return loginMode;
    }

    /**
     * Calling code for a fresh login. The SIM ISO and the nearest
     * datacenter country are not used.
     */
    public static String defaultCallingCode(String simIso, String nearestCountryIso) {
        return CALLING_CODE;
    }

    /** A fresh login does not copy the SIM line into the field. */
    public static boolean skipLineNumberPrefill(boolean loginMode) {
        return useChinaDefault(loginMode);
    }

    /**
     * National digits to insert. A fresh login inserts none, so a SIM
     * line such as {@code 15551234567} is not shown as {@code 555 123 4567}.
     */
    public static String nationalNumberPrefill(boolean loginMode, String simLine) {
        if (skipLineNumberPrefill(loginMode) || simLine == null || simLine.isEmpty()) {
            return "";
        }
        StringBuilder digits = new StringBuilder(simLine.length());
        for (int i = 0; i < simLine.length(); i++) {
            char c = simLine.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
