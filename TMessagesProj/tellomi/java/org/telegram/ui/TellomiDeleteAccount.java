/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Immediate account deletion, matching the iOS
 * "Delete Account Now" step that ends in account.deleteAccount.
 * See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.content.DialogInterface;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;

/**
 * Settings → Privacy → Delete my account → Delete account now.
 * Explains what is removed, confirms, and sends account.deleteAccount.
 * A cloud password is passed as InputCheckPasswordSRP on the official constructor.
 */
public final class TellomiDeleteAccount {

    private static final String REASON = "user_initiated";

    private TellomiDeleteAccount() {
    }

    public static void start(BaseFragment fragment, TL_account.Password knownPassword) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.TellomiDeleteAccountNow));
        builder.setMessage(LocaleController.getString(R.string.TellomiDeleteAccountConsequences));
        builder.setPositiveButton(LocaleController.getString(R.string.Continue), (dialog, which) -> confirm(fragment, knownPassword));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(builder.create());
    }

    private static void confirm(BaseFragment fragment, TL_account.Password knownPassword) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.TellomiDeleteAccountConfirmTitle));
        builder.setMessage(LocaleController.getString(R.string.TellomiDeleteAccountConfirmText));
        builder.setPositiveButton(LocaleController.getString(R.string.TellomiDeleteAccountConfirmButton), (dialog, which) -> afterConfirm(fragment, knownPassword));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(shown -> {
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });
        fragment.showDialog(dialog);
    }

    private static void afterConfirm(BaseFragment fragment, TL_account.Password knownPassword) {
        if (knownPassword != null) {
            if (knownPassword.has_password) {
                askPassword(fragment, knownPassword);
            } else {
                send(fragment, null, null, new Attempt());
            }
            return;
        }
        if (fragment.getParentActivity() == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        TL_account.getPassword req = new TL_account.getPassword();
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progress.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (fragment.getParentActivity() == null) {
                return;
            }
            if (error == null && response instanceof TL_account.Password) {
                TL_account.Password loaded = (TL_account.Password) response;
                TwoStepVerificationActivity.initPasswordNewAlgo(loaded);
                if (loaded.has_password) {
                    askPassword(fragment, loaded);
                } else {
                    send(fragment, null, null, new Attempt());
                }
            } else {
                show(fragment, messageFor(error));
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void askPassword(BaseFragment fragment, TL_account.Password password) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        if (!(password.current_algo instanceof TLRPC.TL_passwordKdfAlgoSHA256SHA256PBKDF2HMACSHA512iter100000SHA256ModPow)) {
            AlertsCreator.showUpdateAppAlert(fragment.getParentActivity(), LocaleController.getString(R.string.UpdateAppAlert), true);
            return;
        }
        TwoStepVerificationActivity screen = new TwoStepVerificationActivity(fragment.getCurrentAccount());
        Attempt attempt = new Attempt();
        screen.setPassword(password);
        screen.setDelegate(TwoStepVerificationActivity.DELEGATE_DELETE_ACCOUNT, proof -> send(fragment, screen, proof, attempt));
        fragment.presentFragment(screen);
    }

    private static void send(BaseFragment fragment, TwoStepVerificationActivity passwordFragment, TLRPC.InputCheckPasswordSRP password, Attempt attempt) {
        if (attempt.inFlight) {
            if (passwordFragment != null) {
                passwordFragment.needHideProgress();
            }
            return;
        }
        if (passwordFragment != null && password == null) {
            passwordFragment.rejectEnteredPassword();
            show(passwordFragment, LocaleController.getString(R.string.TellomiDeleteAccountPasswordInvalid));
            return;
        }
        attempt.inFlight = true;
        final int account = fragment.getCurrentAccount();
        final AlertDialog progress;
        if (passwordFragment == null && fragment.getParentActivity() != null) {
            progress = new AlertDialog(fragment.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progress.setCanCancel(false);
            progress.show();
        } else {
            progress = null;
        }
        TL_account.deleteAccount req = new TL_account.deleteAccount();
        req.reason = REASON;
        req.password = password;
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            attempt.inFlight = false;
            if (progress != null) {
                try {
                    progress.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (error == null && response instanceof TLRPC.TL_boolTrue) {
                if (passwordFragment != null) {
                    passwordFragment.needHideProgress();
                }
                AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account).performLogout(1));
                return;
            }
            if (error != null && "SRP_ID_INVALID".equals(error.text) && passwordFragment != null && !attempt.retried) {
                attempt.retried = true;
                passwordFragment.retryAfterStaleChallenge();
                return;
            }
            BaseFragment host = passwordFragment != null ? passwordFragment : fragment;
            if (passwordFragment != null && error != null && "PASSWORD_HASH_INVALID".equals(error.text)) {
                passwordFragment.rejectEnteredPassword();
            } else if (passwordFragment != null) {
                passwordFragment.needHideProgress();
            }
            show(host, messageFor(error));
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static void show(BaseFragment host, String message) {
        if (host == null || host.getParentActivity() == null || message == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(host.getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.TellomiDeleteAccountNow));
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        host.showDialog(builder.create());
    }

    private static String messageFor(TLRPC.TL_error error) {
        if (error == null || error.text == null) {
            return LocaleController.getString(R.string.TellomiDeleteAccountFailed);
        }
        if ("PASSWORD_HASH_INVALID".equals(error.text)) {
            return LocaleController.getString(R.string.TellomiDeleteAccountPasswordInvalid);
        }
        if (error.text.startsWith("2FA_CONFIRM_WAIT")) {
            return LocaleController.formatString(R.string.TellomiDeleteAccountConfirmWait, waitLabel(error.text));
        }
        if (error.text.startsWith("FLOOD_WAIT")) {
            return LocaleController.formatString(R.string.TellomiDeleteAccountFloodWait, waitLabel(error.text));
        }
        return LocaleController.getString(R.string.TellomiDeleteAccountFailed) + "\n" + error.text;
    }

    private static String waitLabel(String text) {
        int seconds = Utilities.parseInt(text);
        if (seconds < 0) {
            seconds = 0;
        }
        if (seconds >= 86400) {
            return LocaleController.formatPluralString("Days", Math.max(1, seconds / 86400));
        }
        if (seconds >= 3600) {
            return LocaleController.formatPluralString("Hours", Math.max(1, seconds / 3600));
        }
        if (seconds >= 60) {
            return LocaleController.formatPluralString("Minutes", Math.max(1, seconds / 60));
        }
        return LocaleController.formatPluralString("Seconds", Math.max(1, seconds));
    }

    private static final class Attempt {
        boolean inFlight;
        boolean retried;
    }
}
