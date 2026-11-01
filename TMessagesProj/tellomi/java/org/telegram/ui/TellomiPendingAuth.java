package org.telegram.ui;

/**
 * One in-flight login code request.
 * A later submit or a back/close replaces the ticket, so a late
 * auth.sendCode response cannot paint a previous number.
 */
public final class TellomiPendingAuth {

    private int generation;
    private String digits;

    /** @return the ticket the matching response must present */
    public int begin(String digits) {
        this.digits = digits == null ? "" : digits;
        return ++generation;
    }

    /** Drops the in-flight request. Older tickets no longer match. */
    public void clear() {
        digits = null;
        generation++;
    }

    public boolean accepts(int ticket, String digits) {
        return ticket == generation && this.digits != null && this.digits.equals(digits == null ? "" : digits);
    }

    public String digits() {
        return digits;
    }

    public static String digitsOnly(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
