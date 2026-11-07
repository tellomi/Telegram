package org.telegram.ui;

/**
 * One open chat can be held quietly. Secret view also withholds the
 * foreground online heartbeat. A locked chat holds reads until the pin
 * is accepted, and does not withhold online.
 */
public final class TellomiQuietOpen {

    public static final String BANNER = "秘密查看";

    private static Hold hold;

    private TellomiQuietOpen() {
    }

    public static synchronized void enter(int account, long dialogId, boolean stealth) {
        if (dialogId == 0) {
            return;
        }
        hold = new Hold(account, dialogId, stealth);
    }

    public static synchronized void release(int account, long dialogId) {
        if (hold != null && hold.account == account && hold.dialogId == dialogId) {
            hold = null;
        }
    }

    public static synchronized boolean blockRead(int account, long dialogId) {
        return hold != null && hold.account == account && hold.dialogId == dialogId;
    }

    public static synchronized boolean blockTyping(int account, long dialogId) {
        return blockRead(account, dialogId);
    }

    /** Only a secret view withholds account.updateStatus(online). */
    public static synchronized boolean blockOnline(int account) {
        return hold != null && hold.stealth && hold.account == account;
    }

    public static synchronized boolean stealth(int account, long dialogId) {
        return hold != null && hold.stealth && hold.account == account && hold.dialogId == dialogId;
    }

    private static final class Hold {
        final int account;
        final long dialogId;
        final boolean stealth;

        Hold(int account, long dialogId, boolean stealth) {
            this.account = account;
            this.dialogId = dialogId;
            this.stealth = stealth;
        }
    }
}
