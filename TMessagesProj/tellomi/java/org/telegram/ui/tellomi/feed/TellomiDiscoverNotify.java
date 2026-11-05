package org.telegram.ui.tellomi.feed;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.TellomiPack;

/**
 * Discover interaction pushes (D-18). The switch is {@code account.updateNotifySettings}
 * on the official account @faxian (user id 777005). No row means on. {@code mute_until}
 * after now means off. Muting every private chat does not mute this peer.
 */
public final class TellomiDiscoverNotify {

    public static final long OFFICIAL_USER_ID = 777005L;
    public static final String USERNAME = "faxian";
    public static final String PEER_PREF = "tellomi_discover_notify_peer";

    private static boolean busy;

    private TellomiDiscoverNotify() {
    }

    /**
     * {@code notifyType} is {@code notify2_}: missing (-1) or explicit unmute (0) is on,
     * forever (2) is off, and timed (3) is off only while {@code muteUntil} is still ahead.
     */
    public static boolean pushesEnabled(int notifyType, int muteUntil, int now) {
        if (notifyType == 2) {
            return false;
        }
        if (notifyType == 3) {
            return muteUntil <= now;
        }
        return true;
    }

    public static boolean enabled(int account) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(account);
        if (preferences == null) {
            return true;
        }
        long peer = preferences.getLong(PEER_PREF, OFFICIAL_USER_ID);
        int type = preferences.getInt("notify2_" + peer, -1);
        int until = preferences.getInt("notifyuntil_" + peer, 0);
        int now;
        try {
            now = org.telegram.tgnet.ConnectionsManager.getInstance(account).getCurrentTime();
        } catch (Throwable ignored) {
            now = (int) (System.currentTimeMillis() / 1000L);
        }
        return pushesEnabled(type, until, now);
    }

    public static String title(String language) {
        return TellomiPack.text("Tellomi.Discover.NotifySwitch", language, TellomiPack.device());
    }

    public static String info(String language) {
        return TellomiPack.text("Tellomi.Discover.NotifySwitchInfo", language, TellomiPack.device());
    }

    public static void toggle(int account, Runnable done) {
        if (busy) {
            return;
        }
        busy = true;
        boolean next = !enabled(account);
        ensurePeer(account, peer -> {
            if (peer != null && peer > 0) {
                apply(account, peer, next);
            }
            busy = false;
            if (done != null) {
                AndroidUtilities.runOnUIThread(done);
            }
        });
    }

    static void apply(int account, long peer, boolean on) {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(account);
        if (preferences != null) {
            preferences.edit().putLong(PEER_PREF, peer).apply();
        }
        int setting = on ? NotificationsController.SETTING_MUTE_UNMUTE : NotificationsController.SETTING_MUTE_FOREVER;
        NotificationsController.getInstance(account).setDialogNotificationsSettings(peer, 0, setting);
    }

    private static void ensurePeer(int account, org.telegram.messenger.Utilities.Callback<Long> done) {
        long stored = OFFICIAL_USER_ID;
        SharedPreferences preferences = MessagesController.getNotificationsSettings(account);
        if (preferences != null) {
            stored = preferences.getLong(PEER_PREF, OFFICIAL_USER_ID);
        }
        TLRPC.User cached = MessagesController.getInstance(account).getUser(stored);
        if (cached != null && cached.access_hash != 0) {
            done.run(cached.id);
            return;
        }
        TLRPC.User official = MessagesController.getInstance(account).getUser(OFFICIAL_USER_ID);
        if (official != null && official.access_hash != 0) {
            done.run(official.id);
            return;
        }
        MessagesController.getInstance(account).getUserNameResolver().resolve(USERNAME, peer -> {
            if (peer != null && peer > 0) {
                done.run(peer);
            } else {
                done.run(null);
            }
        });
    }
}
