package org.telegram.ui;

/**
 * Clearing a private chat offers “also clear history for this user”.
 * The days dialog used the delete-for-both string, so the clear-history
 * wording never appeared.
 */
public final class TellomiClearHistory {

    private TellomiClearHistory() {
    }

    public static final String ALSO_CLEAR = "ClearHistoryOptionAlso";
    public static final String ALSO_DELETE = "DeleteMessagesOptionAlso";

    public static String alsoForUserKey(boolean clearingWholeHistory, boolean userChat) {
        if (clearingWholeHistory && userChat) {
            return ALSO_CLEAR;
        }
        return ALSO_DELETE;
    }
}
