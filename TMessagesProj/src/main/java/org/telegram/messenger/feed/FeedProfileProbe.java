package org.telegram.messenger.feed;

/**
 * Profile-post probe rules. Opening a profile again is not the same event as
 * rebuilding the tab strip, and a confirmed empty homepage does not latch the
 * process shut. No Android types.
 */
public final class FeedProfileProbe {

    /** First retry waits this long. Later retries double until {@link #BACKOFF_CAP_MS}. */
    public static final long BACKOFF_FIRST_MS = 2_000L;
    public static final long BACKOFF_CAP_MS = 30_000L;

    /** One peer. Success, including a confirmed empty page, does not block the next open. */
    public static final class Peer {
        public boolean inFlight;
        public int failures;
        public long failedAtMs;
        public boolean retryArmed;
        /** The layout that may hear a visibility change. Null after it leaves. */
        public Object listener;
    }

    private FeedProfileProbe() {
    }

    public static long backoffMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return 0L;
        }
        int shift = Math.min(consecutiveFailures - 1, 4);
        long delay = BACKOFF_FIRST_MS << shift;
        return delay > BACKOFF_CAP_MS ? BACKOFF_CAP_MS : delay;
    }

    /**
     * Open or resume. False while a request for this peer is already running,
     * or while a failure backoff has not elapsed. A finished probe does not
     * stick.
     */
    public static boolean beginOpen(Peer peer, long nowMs) {
        if (peer == null || peer.inFlight) {
            return false;
        }
        if (peer.failures > 0 && nowMs - peer.failedAtMs < backoffMs(peer.failures)) {
            return false;
        }
        peer.inFlight = true;
        peer.retryArmed = false;
        return true;
    }

    /** Rebuilding tabs never starts a fetch. */
    public static boolean beginFromTabs() {
        return false;
    }

    /**
     * The one delayed retry after a failure. A success that landed first
     * (failures already cleared) does not start another fetch.
     */
    public static boolean beginRetry(Peer peer, long nowMs) {
        if (peer == null || peer.failures <= 0) {
            if (peer != null) {
                peer.retryArmed = false;
            }
            return false;
        }
        return beginOpen(peer, nowMs);
    }

    public static void succeed(Peer peer) {
        if (peer == null) {
            return;
        }
        peer.inFlight = false;
        peer.failures = 0;
        peer.failedAtMs = 0L;
        peer.retryArmed = false;
    }

    /**
     * A transport failure. The known bit stays with the caller.
     * @return delay for the single armed retry, or 0 when one is already armed.
     */
    public static long fail(Peer peer, long nowMs) {
        if (peer == null) {
            return 0L;
        }
        peer.inFlight = false;
        peer.failures++;
        peer.failedAtMs = nowMs;
        if (peer.listener == null) {
            peer.retryArmed = false;
            return 0L;
        }
        if (peer.retryArmed) {
            return 0L;
        }
        peer.retryArmed = true;
        return backoffMs(peer.failures);
    }

    public static void bind(Peer peer, Object listener) {
        if (peer == null || listener == null) {
            return;
        }
        peer.listener = listener;
    }

    /** Drop the listener only when it is still the one that registered. */
    public static boolean unbind(Peer peer, Object listener) {
        if (peer == null || listener == null || peer.listener != listener) {
            return false;
        }
        peer.listener = null;
        return true;
    }

    /** A destroyed layout is not attached, and a cleared listener is not registered. */
    public static boolean acceptCallback(boolean attached, boolean registered) {
        return attached && registered;
    }

    public static boolean dispatch(Peer peer, boolean attached) {
        return peer != null && acceptCallback(attached, peer.listener != null);
    }

    /**
     * Deleting every loaded row is not a confirmed empty profile while the
     * next page cursor is still set.
     */
    public static boolean confirmNoPostsAfterDelete(int remaining, boolean hasNextPage) {
        return remaining <= 0 && !hasNextPage;
    }
}
