package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.TellomiSoftUpdateDialog;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    protected boolean isInAppUpdatesEnabled() {
        return true;
    }

    @Override
    public boolean checkApkInstallPermissions(final Context context) {
        return TellomiApkInstall.checkPermission(context);
    }

    @Override
    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        return TellomiApkInstall.open(activity, UserConfig.selectedAccount, document);
    }

    @Override
    public boolean showUpdateAppPopup(Context context, TLRPC.TL_help_appUpdate update, int account) {
        if (context == null || update == null) {
            return false;
        }
        try {
            new TellomiSoftUpdateDialog(context, update, account).show();
        } catch (Exception e) {
            FileLog.e(e);
            if (!update.can_not_skip && update.version != null && SharedConfig.pendingAppUpdate != null && update.version.equals(SharedConfig.pendingAppUpdate.version)) {
                SharedConfig.pendingAppUpdate = null;
                SharedConfig.saveConfig();
            }
            return false;
        }
        return true;
    }
}
