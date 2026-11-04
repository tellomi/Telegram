package org.telegram.ui;

/**
 * Sign-up finishes when {@code auth.signUp} succeeds. A later
 * {@code photos.uploadProfilePhoto} failure does not undo the account.
 * The text shown with 「头像上传失败」 is the server error code.
 */
public final class TellomiSignUpAvatar {

    public static final String TITLE_KEY = "Tellomi.Signup.AvatarFailed";
    public static final String RETRY_KEY = "Tellomi.Signup.AvatarRetry";

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

    /** The account stays created. {@code errorCode} is the server text, or the numeric code when the text is empty. */
    public static Outcome afterUpload(int errorCode, String errorText) {
        return new Outcome(true, false, true, TITLE_KEY, realCode(errorText, errorCode));
    }

    public static String realCode(String errorText, int errorCode) {
        if (errorText != null) {
            String trimmed = errorText.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        if (errorCode != 0) {
            return Integer.toString(errorCode);
        }
        return null;
    }

    public static String message(String title, String errorCode) {
        if (title == null || title.isEmpty()) {
            return errorCode == null ? "" : errorCode;
        }
        if (errorCode == null || errorCode.isEmpty()) {
            return title;
        }
        return title + "\n" + errorCode;
    }
}
