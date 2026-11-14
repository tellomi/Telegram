package org.telegram.ui;

/**
 * A contact just added with contacts.addContact belongs on the new-group
 * member list. Search can already see the user. The list only reads
 * {@code ContactsController.contacts}, and a response that omits
 * {@code user.contact} never entered that list.
 */
public final class TellomiGroupContacts {

    private TellomiGroupContacts() {
    }

    public static boolean includeAddedUser(long requestedId, long responseId, boolean responseContactFlag, boolean alreadyListed) {
        if (alreadyListed || responseId == 0L) {
            return false;
        }
        if (responseId == requestedId) {
            return true;
        }
        return responseContactFlag;
    }
}
