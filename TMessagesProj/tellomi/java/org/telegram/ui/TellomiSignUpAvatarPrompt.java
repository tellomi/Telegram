package org.telegram.ui;

import android.app.Activity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.Bulletin;

/**
 * Shows 「头像上传失败」 plus the server error code, with a retry.
 * It does not log the account out.
 */
public final class TellomiSignUpAvatarPrompt {

    private TellomiSignUpAvatarPrompt() {
    }

    public static void show(String errorText, int errorCode, Runnable retry) {
        final TellomiSignUpAvatar.Outcome outcome = TellomiSignUpAvatar.afterUpload(errorCode, errorText);
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                String language = "en";
                try {
                    LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
                    if (info != null && info.shortName != null) {
                        language = info.shortName;
                    }
                } catch (Throwable ignored) {
                }
                String title = TellomiPack.text(outcome.titleKey, language, TellomiPack.device());
                String retryLabel = TellomiPack.text(TellomiSignUpAvatar.RETRY_KEY, language, TellomiPack.device());
                String message = TellomiSignUpAvatar.message(title, outcome.errorCode);
                Activity activity = null;
                try {
                    activity = LaunchActivity.instance;
                } catch (Throwable ignored) {
                }
                if (!(activity instanceof Activity) || activity.isFinishing()) {
                    try {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, message);
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(message);
                builder.setMessage(message);
                if (retry != null) {
                    builder.setPositiveButton(retryLabel, (dialog, which) -> retry.run());
                }
                String ok = "OK";
                try {
                    ok = LocaleController.getString(R.string.OK);
                } catch (Throwable ignored) {
                }
                builder.setNegativeButton(ok, null);
                try {
                    builder.show();
                } catch (Throwable ignored) {
                    try {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR, message);
                    } catch (Throwable ignored2) {
                    }
                }
            }
        });
    }
}
