package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AlertsCreator;

import java.io.File;

/** Installs an update APK from {@code help.appUpdate#document}. */
public final class TellomiApkInstall {

    private TellomiApkInstall() {
    }

    public static boolean checkPermission(Context context) {
        if (context == null || ApplicationLoader.applicationContext == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    public static boolean open(Activity activity, int account, TLRPC.Document document) {
        if (activity == null || document == null) {
            return false;
        }
        try {
            File file = FileLoader.getInstance(account).getPathToAttach(document, true);
            if (file == null || !file.exists()) {
                return false;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= 24) {
                intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file), "application/vnd.android.package-archive");
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            }
            try {
                activity.startActivityForResult(intent, 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
