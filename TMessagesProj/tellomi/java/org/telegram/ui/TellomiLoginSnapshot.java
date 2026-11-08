package org.telegram.ui;

/**
 * Login step kept for a language redraw. A process death uses {@link #forProcessDeath()}
 * and drops the password. A redraw does not ask the server for another code.
 */
public final class TellomiLoginSnapshot {

    public static final int PHONE = 0;
    public static final int CODE_SMS = 2;
    public static final int PASSWORD = 6;
    public static final int REGISTER = 5;
    public static final int SCREEN_PHONE = 0;
    public static final int SCREEN_EMAIL = 2;

    public int page = PHONE;
    public int screen = SCREEN_PHONE;
    public String phone = "";
    public String email = "";
    public String typedCode = "";
    public boolean agreed;
    public int remainingMs;
    /** {@link android.os.SystemClock#elapsedRealtime()} instant when resend becomes allowed. */
    public long resendDeadlineElapsed;
    public int openedAtSec;
    public String password = "";
    public boolean resendRequested;

    public TellomiLoginSnapshot copy() {
        TellomiLoginSnapshot next = new TellomiLoginSnapshot();
        next.page = page;
        next.screen = screen;
        next.phone = phone;
        next.email = email;
        next.typedCode = typedCode;
        next.agreed = agreed;
        next.remainingMs = remainingMs;
        next.resendDeadlineElapsed = resendDeadlineElapsed;
        next.openedAtSec = openedAtSec;
        next.password = password;
        next.resendRequested = resendRequested;
        return next;
    }

    /** Same step after a language redraw. Does not send another code. */
    public TellomiLoginSnapshot afterLanguageRedraw() {
        TellomiLoginSnapshot next = copy();
        next.resendRequested = false;
        return next;
    }

    /** What may be written for a killed process. Password and the code step are dropped. */
    public TellomiLoginSnapshot forProcessDeath() {
        TellomiLoginSnapshot next = copy();
        next.password = "";
        next.resendRequested = false;
        if (isCodePage(page)) {
            next.page = PHONE;
            next.typedCode = "";
            next.remainingMs = 0;
            next.resendDeadlineElapsed = 0;
            next.openedAtSec = 0;
        }
        return next;
    }

    /**
     * Milliseconds left until {@code deadlineElapsed}. Both clocks are
     * {@code SystemClock.elapsedRealtime()}. A language redraw keeps the same
     * deadline, so the wait does not restart at 60 seconds and does not grow.
     */
    public static int remainingMs(long deadlineElapsed, long nowElapsed) {
        if (deadlineElapsed <= 0 || nowElapsed >= deadlineElapsed) {
            return 0;
        }
        long left = deadlineElapsed - nowElapsed;
        return left > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) left;
    }

    public static boolean isCodePage(int page) {
        return page == CODE_SMS || page == 1 || page == 3 || page == 4 || page == 11
                || page == 13 || page == 14 || page == 15 || page == 16 || page == 17;
    }
}
