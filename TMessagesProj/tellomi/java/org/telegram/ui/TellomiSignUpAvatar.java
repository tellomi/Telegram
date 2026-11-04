package org.telegram.ui;

import java.util.Locale;

/**
 * Sign-up finishes when {@code auth.signUp} succeeds. A later
 * {@code photos.uploadProfilePhoto} failure does not undo the account.
 * The alert is {@code Tellomi.SignUp.AvatarFailed}, for example
 * 「头像上传失败（500 INTERNAL_SERVER_ERROR）」.
 */
public final class TellomiSignUpAvatar {

    public static final String TITLE_KEY = "Tellomi.SignUp.AvatarFailed";
    public static final String RETRY_KEY = "Tellomi.SignUp.AvatarRetry";

    public static final class Outcome {
        public final boolean accountCreated;
        public final boolean uploadAvatar;
        public final boolean canRetry;
        public final String titleKey;
        public final String errorCode;

        Outcome(boolean accountCreated, boolean uploadAvatar, boolean canRetry, String titleKey, String errorCode) {
            this.accountCreated = accountCreated;
            this.uploadAvatar = uploadAvatar;
            this.canRetry = canRetry;
            this.titleKey = titleKey;
            this.errorCode = errorCode;
        }
    }

    private TellomiSignUpAvatar() {
    }

    /** Called only after {@code auth.signUp} has already succeeded. */
    public static Outcome afterSignUp(boolean hasLocalAvatar) {
        return new Outcome(true, hasLocalAvatar, false, null, null);
    }

    public static Outcome afterUploadSuccess() {
        return new Outcome(true, false, false, null, null);
    }

    /**
     * The account stays created. {@code errorCode} is the numeric code and the
     * server text together, for example {@code 500 INTERNAL_SERVER_ERROR}.
     */
    public static Outcome afterUpload(int errorCode, String errorText) {
        return new Outcome(true, false, true, TITLE_KEY, realCode(errorText, errorCode));
    }

    public static String realCode(String errorText, int errorCode) {
        String text = errorText == null ? "" : errorText.trim();
        if (errorCode != 0 && text.isEmpty()) {
            return Integer.toString(errorCode);
        }
        if (text.isEmpty()) {
            return null;
        }
        String prefix = errorCode == 0 ? "" : Integer.toString(errorCode);
        if (prefix.isEmpty() || text.equals(prefix) || text.startsWith(prefix + " ")) {
            return text;
        }
        return prefix + " " + text;
    }

    /** {@code pattern} is the langpack value. {@code %@} is accepted so an iOS pack formats here. */
    public static String message(String pattern, String detail) {
        String format = pattern == null ? "" : pattern.replace("%@", "%s");
        if (detail == null || detail.isEmpty()) {
            return format.replace("（%s）", "").replace(" (%s)", "").replace("(%s)", "").trim();
        }
        try {
            return String.format(Locale.US, format, detail);
        } catch (RuntimeException ignored) {
            return detail;
        }
    }
}
