package org.telegram.ui;

/**
 * Offline profile open. The page draws the cached user at once.
 * users.getFullUser and the posts list never hold the fragment shut.
 */
public final class ProfileOffline {

    private ProfileOffline() {
    }

    /** The open slide does not wait for a network round-trip. */
    public static boolean delayOpenUntilNetwork() {
        return false;
    }

    /** Read user_settings when the in-memory UserFull is missing. */
    public static boolean loadCachedFullUser(boolean fullUserAlreadyInMemory) {
        return !fullUserAlreadyInMemory;
    }

    /** Read the users table when the in-memory user is missing. */
    public static boolean readStoredUserWhenMemoryEmpty(boolean memoryUserMissing) {
        return memoryUserMissing;
    }

    /**
     * Message-media columns live in sharedMediaData.
     * The posts tab (16) is not one of them; using it as an index crashes the profile.
     * Poll (15) is stored at column 8.
     */
    public static boolean usesSharedMediaArray(int tab) {
        if (tab == 16) {
            return false;
        }
        if (tab == 15) {
            return true;
        }
        return tab >= 0 && tab <= 5;
    }

    /** Keep a real cached name. Fill only a blank card from a feed author. */
    public static String firstNameIfBlank(String currentFirst, String currentLast, String authorName) {
        boolean blank = (currentFirst == null || currentFirst.length() == 0) && (currentLast == null || currentLast.length() == 0);
        if (blank && authorName != null && authorName.length() > 0) {
            return authorName;
        }
        return currentFirst;
    }

    public static String usernameIfBlank(String current, String authorUsername) {
        if ((current == null || current.length() == 0) && authorUsername != null && authorUsername.length() > 0) {
            return authorUsername;
        }
        return current;
    }

    public static long accessHashIfMissing(long current, long authorHash) {
        if (current == 0L && authorHash != 0L) {
            return authorHash;
        }
        return current;
    }
}
