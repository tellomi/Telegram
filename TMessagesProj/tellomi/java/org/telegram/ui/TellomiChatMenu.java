package org.telegram.ui;

import android.os.Bundle;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;

/** Long-press and profile entries. They do not replace the existing menu rows. */
public final class TellomiChatMenu {

    public static final int PROFILE_STEALTH = 8801;
    public static final int PROFILE_LOCK = 8802;

    private TellomiChatMenu() {
    }

    public static void openStealth(BaseFragment fragment, long dialogId) {
        if (fragment == null || dialogId == 0) {
            return;
        }
        Bundle args = new Bundle();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isChatDialog(dialogId)) {
            args.putLong("chat_id", -dialogId);
        } else {
            args.putLong("user_id", dialogId);
        }
        args.putBoolean("tellomi_stealth", true);
        fragment.presentFragment(new ChatActivity(args));
    }

    public static void addProfileItems(ActionBarMenuItem menu, BaseFragment fragment, int account, long dialogId) {
        if (menu == null || dialogId == 0) {
            return;
        }
        menu.addSubItem(PROFILE_STEALTH, R.drawable.msg_secret, LocaleController.getString(R.string.TellomiStealthView));
        menu.addSubItem(PROFILE_LOCK, R.drawable.msg_permissions, LocaleController.getString(R.string.TellomiChatPassword));
    }
}
