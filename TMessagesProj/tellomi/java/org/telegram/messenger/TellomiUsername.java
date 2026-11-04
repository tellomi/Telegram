package org.telegram.messenger;

/**
 * Public username length for a user, channel, group, or bot.
 * Teamgram accepts 4 characters. Upstream Telegram rejected a name shorter
 * than 4 locally and also treated a 4-character {@code USERNAME_INVALID}
 * as too short, so the effective minimum was 5.
 */
public final class TellomiUsername {

    public static final int MIN_LENGTH = 4;

    private TellomiUsername() {
    }

    /** True when {@code username} is missing or shorter than {@link #MIN_LENGTH}. */
    public static boolean isTooShort(String username) {
        return username == null || username.length() < MIN_LENGTH;
    }
}
