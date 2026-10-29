package org.telegram.ui.Stories;

import org.telegram.messenger.BuildVars;

import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

public class ChannelBoostUtilities {
    public static String createLink(int currentAccount, long dialogId) {
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        String username = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            return BuildVars.LINK_BASE + "boost/" + ChatObject.getPublicUsername(chat);
        } else {
            return BuildVars.LINK_BASE + "boost/?c=" + -dialogId;
        }
    }
}
