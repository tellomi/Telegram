package org.telegram.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Offline profile open. The page draws the cached user at once.
 * users.getFullUser and the posts list never hold the fragment shut.
 */
public final class ProfileOffline {

    /**
     * UI-thread bound for one users-table read.
     * A wedged storage queue must not sit on the main thread long enough to ANR.
     */
    public static final long STORED_USER_WAIT_MS = 1500L;

    private ProfileOffline() {
    }

    /** A full in-memory user is enough. A missing user or a min stub still needs the users table. */
    public static boolean needsStoredUser(boolean memoryPresent, boolean memoryMin) {
        return !memoryPresent || memoryMin;
    }

    public enum AuthorSeed {
        USE_MEMORY,
        USE_STORED,
        INSERT_MIN_STUB,
        LEAVE_ABSENT
    }

    /**
     * Memory, then the users-table row, then a stub.
     * A stub is refused for the logged-in user and when the table read did not finish,
     * so a timeout cannot hide a row that is still on disk.
     */
    public static AuthorSeed authorSeed(boolean memoryPresent, boolean memoryMin, boolean storedPresent, boolean storedReadFinished, long authorId, long currentUserId) {
        if (authorId == 0) {
            return AuthorSeed.LEAVE_ABSENT;
        }
        if (memoryPresent && !memoryMin) {
            return AuthorSeed.USE_MEMORY;
        }
        if (storedPresent) {
            return AuthorSeed.USE_STORED;
        }
        if (!storedReadFinished) {
            return memoryPresent ? AuthorSeed.USE_MEMORY : AuthorSeed.LEAVE_ABSENT;
        }
        if (memoryPresent) {
            return AuthorSeed.USE_MEMORY;
        }
        if (currentUserId != 0 && authorId == currentUserId) {
            return AuthorSeed.LEAVE_ABSENT;
        }
        return AuthorSeed.INSERT_MIN_STUB;
    }

    /** A min user is merged into an existing user and does not replace one. */
    public static boolean authorStubIsMin() {
        return true;
    }

    /**
     * putUser(..., fromCache=true) inserts only when the id is absent,
     * replaces a min user, and does not write the current-user config.
     */
    public static boolean authorStubFromCache() {
        return true;
    }

    public static boolean awaitStoredUser(CountDownLatch latch) {
        return awaitStoredUser(latch, STORED_USER_WAIT_MS);
    }

    public static boolean awaitStoredUser(CountDownLatch latch, long timeoutMs) {
        if (latch == null) {
            return false;
        }
        try {
            return latch.await(Math.max(0L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
