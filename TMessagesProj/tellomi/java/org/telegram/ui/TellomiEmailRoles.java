package org.telegram.ui;

/**
 * Three different emails. A sign-in address is not a recovery email and not
 * the attached login-code email.
 *
 * <ul>
 *   <li>Sign-in: the address sent as {@code auth.sendCode} / {@code auth.signUp}
 *   {@code phone_number}. The session stays {@code user.id}.</li>
 *   <li>Login-code: {@code account.getPassword.login_email_pattern}, changed
 *   only with {@code account.sendVerifyEmailCode} purpose login-change.
 *   That does not replace {@code user.id} or the sign-in address.</li>
 *   <li>Recovery: two-step verification. Never inferred from the sign-in address.</li>
 * </ul>
 */
public final class TellomiEmailRoles {

    private TellomiEmailRoles() {
    }

    public static boolean isSignInAddress(String email) {
        return TellomiEmailLogin.isValid(TellomiEmailLogin.normalize(email));
    }

    /**
     * Address to store after a successful email sign-in. Typed wins, then the
     * address kept on the login page, then an email the server put in {@code user.phone}.
     * Empty when none of those is an email. Never a recovery flag.
     */
    public static String rememberCandidate(String typed, String shared, String userPhone) {
        String typedEmail = TellomiEmailLogin.normalize(typed);
        if (TellomiEmailLogin.isValid(typedEmail)) {
            return typedEmail;
        }
        String sharedEmail = TellomiEmailLogin.normalize(shared);
        if (TellomiEmailLogin.isValid(sharedEmail)) {
            return sharedEmail;
        }
        String phoneEmail = TellomiEmailLogin.normalize(userPhone);
        if (TellomiEmailLogin.isValid(phoneEmail)) {
            return phoneEmail;
        }
        return "";
    }

    /** A pure-email registration does not set the two-step recovery email. */
    public static boolean countsAsRecovery(String signInEmail) {
        return false;
    }
}
