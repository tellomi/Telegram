package org.telegram.ui;

/**
 * Email identity versus a phone account's attached login email versus recovery.
 *
 * <p>Server contract (teamgram-server PR 227, head {@code b4a57578}): an email-only
 * account stores the address in {@code users.email} and leaves {@code users.phone}
 * NULL. {@code account.getPassword.login_email_pattern} is a read-only mask of
 * that address ({@code u***@tellomi.app}), returned even when there is no cloud
 * password. {@code has_recovery} and {@code email_unconfirmed_pattern} stay on
 * the cloud-password record. {@code emailVerifyPurposeLoginChange} may confirm
 * the same identity and returns {@code EMAIL_NOT_ALLOWED} for any other address,
 * including another account's {@code users.email}. That call does not change
 * {@code user.id}. A phone account has no identity mask and may still attach a
 * free login email.
 *
 * <p>The on-device cache and {@code user.phone} are not the authority. The mask
 * is not a full address and must not be sent as {@code auth.sendCode.phone_number}.
 */
public final class TellomiEmailRoles {

    public static final int SHOW_IDENTITY = 1;
    public static final int ATTACH_LOGIN_EMAIL = 2;
    /** Password settings have not arrived, and this is not a known phone account. */
    public static final int WAIT = 3;

    private TellomiEmailRoles() {
    }

    public static boolean isSignInAddress(String email) {
        return TellomiEmailLogin.isValid(TellomiEmailLogin.normalize(email));
    }

    /** {@code u***@domain} as returned in {@code login_email_pattern}. Not sendable. */
    public static boolean isIdentityMask(String pattern) {
        if (pattern == null) {
            return false;
        }
        String value = pattern.trim().toLowerCase();
        int at = value.indexOf('@');
        if (at != 4 || value.lastIndexOf('@') != at || value.indexOf('*') != 1) {
            return false;
        }
        if (!"***".equals(value.substring(1, 4))) {
            return false;
        }
        String domain = value.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        return dot > 0 && dot < domain.length() - 1 && domain.indexOf('*') < 0;
    }

    public static boolean sendable(String email) {
        return isSignInAddress(email) && !isIdentityMask(email);
    }

    /** A full address matches the server mask. The mask itself does not match. */
    public static boolean matchesMask(String full, String pattern) {
        if (!isIdentityMask(pattern) || !sendable(full)) {
            return false;
        }
        String email = TellomiEmailLogin.normalize(full).toLowerCase();
        String mask = pattern.trim().toLowerCase();
        int at = email.indexOf('@');
        return email.charAt(0) == mask.charAt(0) && email.substring(at).equals(mask.substring(4));
    }

    /**
     * What the account row may show. A cache hit is shown only when it matches
     * the mask. Otherwise the mask is shown as-is. Empty when the server has
     * not reported an identity.
     */
    public static String display(String pattern, String cachedFull) {
        if (!isIdentityMask(pattern)) {
            return "";
        }
        if (matchesMask(cachedFull, pattern)) {
            return TellomiEmailLogin.normalize(cachedFull);
        }
        return pattern.trim();
    }

    /**
     * Address to keep after sign-in. A mask is never stored. {@code user.phone}
     * counts only when the server actually put an email there.
     */
    public static String rememberCandidate(String typed, String shared, String userPhone) {
        if (sendable(typed)) {
            return TellomiEmailLogin.normalize(typed);
        }
        if (sendable(shared)) {
            return TellomiEmailLogin.normalize(shared);
        }
        if (sendable(userPhone)) {
            return TellomiEmailLogin.normalize(userPhone);
        }
        return "";
    }

    /** Bound even when the cloud password and the recovery email are absent. */
    public static boolean identityBound(String pattern, boolean hasPassword, boolean hasRecovery) {
        return isIdentityMask(pattern);
    }

    /** A pure-email registration does not set the two-step recovery email. */
    public static boolean countsAsRecovery(String signInEmail) {
        return false;
    }

    /**
     * {@code help.getPromoData} on head {@code b4a57578} does not offer these.
     * A copy saved before that filter must not open the attach form.
     */
    public static boolean isSetupLoginEmailSuggestion(String name) {
        return "SETUP_LOGIN_EMAIL".equals(name) || "SETUP_LOGIN_EMAIL_NOSKIP".equals(name);
    }

    public static boolean looksLikePhone(String phone) {
        if (phone == null || phone.indexOf('@') >= 0) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < phone.length(); i++) {
            if (Character.isDigit(phone.charAt(i))) {
                digits++;
            }
        }
        return digits >= 6;
    }

    /**
     * Account-page action. An identity mask never opens the phone-account
     * attach form. Until {@code account.getPassword} returns, a missing phone
     * waits instead of asking the user to bind.
     */
    public static int accountRow(boolean passwordLoaded, String pattern, String userPhone) {
        if (isIdentityMask(pattern)) {
            return SHOW_IDENTITY;
        }
        if (passwordLoaded || looksLikePhone(userPhone)) {
            return ATTACH_LOGIN_EMAIL;
        }
        return WAIT;
    }
}
