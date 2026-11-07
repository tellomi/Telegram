package org.telegram.messenger;

/**
 * Calling codes the login screen may offer.
 * Add a code here to show that country again. Everything else stays hidden.
 */
public final class TellomiCallingCodes {

    /** Digits only, no plus. China is the only code until more are added. */
    public static final String[] ALLOWED = {"86"};

    private TellomiCallingCodes() {
    }

    public static boolean allowed(String code) {
        String digits = digits(code);
        if (digits.isEmpty()) {
            return false;
        }
        for (int i = 0; i < ALLOWED.length; i++) {
            if (ALLOWED[i].equals(digits)) {
                return true;
            }
        }
        return false;
    }

    /** True for an empty box and for a prefix of an allowed code, such as "8" before "86". */
    public static boolean prefixOfAllowed(String code) {
        String digits = digits(code);
        if (digits.isEmpty()) {
            return true;
        }
        for (int i = 0; i < ALLOWED.length; i++) {
            if (ALLOWED[i].startsWith(digits)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A pasted digit string starts with a foreign calling code.
     * A single digit such as "1" counts only when the paste included a plus,
     * so a mainland number 138… is not treated as +1.
     */
    public static boolean foreignPrefix(String digits, boolean explicitInternational, boolean knownCode) {
        if (!knownCode || allowed(digits)) {
            return false;
        }
        if (explicitInternational) {
            return true;
        }
        return digits != null && digits.length() >= 2;
    }

    public static String rejectedText(boolean chinese) {
        if (chinese) {
            return "目前仅支持中国大陆手机号（+86）";
        }
        return "Only mainland China numbers (+86) are supported for now.";
    }

    private static String digits(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
