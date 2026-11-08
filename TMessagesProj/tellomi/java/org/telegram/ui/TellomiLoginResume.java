package org.telegram.ui;

import android.os.Bundle;

/**
 * In-memory login form kept across a language redraw.
 * {@code reloadInterface} rebuilds the fragment before the language callback
 * runs, so the bundle has to be visible to that first rebuild. It is not
 * written to {@code logininfo2}. A process death drops it.
 */
public final class TellomiLoginResume {

    private static Bundle pending;
    private static TellomiLoginSnapshot snapshot;

    private TellomiLoginResume() {
    }

    public static void hold(Bundle bundle) {
        pending = bundle;
    }

    public static void holdSnapshot(TellomiLoginSnapshot value) {
        snapshot = value == null ? null : value.afterLanguageRedraw();
    }

    public static TellomiLoginSnapshot peekSnapshot() {
        return snapshot;
    }

    public static Bundle peek() {
        return pending;
    }

    public static void clear() {
        pending = null;
        snapshot = null;
    }

    /** Disk copies must not keep a typed password or 2FA code. */
    public static void stripSecrets(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        bundle.remove("passview_code");
        bundle.remove("password");
    }
}
