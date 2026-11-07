package org.telegram.messenger;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * DownloadManager plus the system package installer.
 * The download keeps running if the page closes or the process restarts.
 * Installing always opens the system installer. This class does not install silently.
 */
public final class TellomiApkDownload {

    private static final String PREFS = "tellomi_apk_update";
    private static final String KEY = "session";
    private static final String HASH = "sha256";
    public static final int INSTALL_REQUEST = 510;

    private TellomiApkDownload() {
    }

    public static void rememberHash(Context context, String sha256) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putString(HASH, sha256 == null ? "" : sha256).apply();
    }

    public static String publishedHash(Context context) {
        if (context == null) {
            return null;
        }
        String value = prefs(context).getString(HASH, null);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    public static TellomiApkUpdate.Session load(Context context) {
        if (context == null) {
            return TellomiApkUpdate.idle();
        }
        return TellomiApkUpdate.decode(prefs(context).getString(KEY, null));
    }

    public static void save(Context context, TellomiApkUpdate.Session session) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putString(KEY, TellomiApkUpdate.encode(session)).apply();
    }

    public static TellomiApkUpdate.Session begin(Context context, String url, int versionCode) {
        TellomiApkUpdate.Session current = refresh(context);
        TellomiApkUpdate.Session next = TellomiApkUpdate.startDownload(current, url, versionCode);
        if (!TellomiApkUpdate.shouldEnqueue(next)) {
            save(context, next);
            return next;
        }
        DownloadManager manager = manager(context);
        if (manager == null) {
            next = TellomiApkUpdate.downloadFinished(next, false, null);
            save(context, next);
            return next;
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(next.url));
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(false);
        request.setTitle("Tellomi");
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName(next.versionCode));
        long id;
        try {
            id = manager.enqueue(request);
        } catch (Exception e) {
            FileLog.e(e);
            next = TellomiApkUpdate.downloadFinished(next, false, null);
            save(context, next);
            return next;
        }
        next = TellomiApkUpdate.attachDownloadId(next, id);
        save(context, next);
        return next;
    }

    /** Reads DownloadManager so a restart or a second tap sees the same job. */
    public static TellomiApkUpdate.Session refresh(Context context) {
        TellomiApkUpdate.Session session = TellomiApkUpdate.afterRestart(load(context));
        if (session.state != TellomiApkUpdate.DOWNLOADING || session.downloadId < 0) {
            save(context, session);
            return session;
        }
        DownloadManager manager = manager(context);
        if (manager == null) {
            return session;
        }
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(session.downloadId));
            if (cursor == null || !cursor.moveToFirst()) {
                session = TellomiApkUpdate.downloadFinished(session, false, null);
                save(context, session);
                return session;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                String path = localFile(context, session.versionCode).getAbsolutePath();
                session = TellomiApkUpdate.downloadFinished(session, new File(path).isFile(), path);
            } else if (status == DownloadManager.STATUS_FAILED) {
                session = TellomiApkUpdate.downloadFinished(session, false, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        save(context, session);
        return session;
    }

    public static int progress(Context context) {
        TellomiApkUpdate.Session session = load(context);
        if (session.state != TellomiApkUpdate.DOWNLOADING || session.downloadId < 0) {
            return session.state == TellomiApkUpdate.READY ? 100 : 0;
        }
        DownloadManager manager = manager(context);
        if (manager == null) {
            return 0;
        }
        Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(session.downloadId));
            if (cursor == null || !cursor.moveToFirst()) {
                return 0;
            }
            long loaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return TellomiApkUpdate.percent(loaded, total);
        } catch (Exception e) {
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static int checkFile(Context context, TellomiApkUpdate.Session session, String publishedSha256) {
        if (context == null || session == null || session.localPath == null) {
            return TellomiApkUpdate.BAD_FILE;
        }
        File file = new File(session.localPath);
        if (!file.isFile()) {
            return TellomiApkUpdate.BAD_FILE;
        }
        byte[] head = new byte[4];
        byte[] tail = new byte[(int) Math.min(file.length(), 22 + 65535)];
        try (InputStream input = new FileInputStream(file)) {
            if (input.read(head) != 4) {
                return TellomiApkUpdate.BAD_FILE;
            }
        } catch (Exception e) {
            return TellomiApkUpdate.BAD_FILE;
        }
        try (InputStream input = new FileInputStream(file)) {
            long skip = file.length() - tail.length;
            while (skip > 0) {
                long skipped = input.skip(skip);
                if (skipped <= 0) {
                    break;
                }
                skip -= skipped;
            }
            int read = input.read(tail);
            if (read != tail.length) {
                return TellomiApkUpdate.BAD_FILE;
            }
        } catch (Exception e) {
            return TellomiApkUpdate.BAD_FILE;
        }
        String fileHash = null;
        if (publishedSha256 != null) {
            try (InputStream input = new FileInputStream(file)) {
                fileHash = TellomiApkUpdate.sha256Hex(input);
            } catch (Exception e) {
                return TellomiApkUpdate.BAD_FILE;
            }
        }
        PackageManager manager = context.getPackageManager();
        String expectedPackage = context.getPackageName();
        int installedCode = 0;
        byte[] installedCert = null;
        String actualPackage = null;
        int apkCode = 0;
        byte[] apkCert = null;
        try {
            PackageInfo installed = signatures(manager, expectedPackage, 0);
            if (installed != null) {
                installedCode = versionCode(installed);
                installedCert = firstCert(installed);
            }
            PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), signatureFlags());
            if (archive != null) {
                if (archive.applicationInfo != null) {
                    archive.applicationInfo.sourceDir = file.getAbsolutePath();
                    archive.applicationInfo.publicSourceDir = file.getAbsolutePath();
                }
                actualPackage = archive.packageName;
                apkCode = versionCode(archive);
                apkCert = firstCert(archive);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return TellomiApkUpdate.BAD_FILE;
        }
        return TellomiApkUpdate.verify(
                expectedPackage,
                actualPackage,
                installedCode,
                apkCode,
                installedCert,
                apkCert,
                TellomiApkUpdate.zipMarkersIntact(head, tail),
                publishedSha256,
                fileHash
        );
    }

    public static boolean openInstaller(Activity activity, TellomiApkUpdate.Session session) {
        if (activity == null || session == null || session.localPath == null) {
            return false;
        }
        if (!TellomiApkInstall.checkPermission(activity)) {
            save(activity, TellomiApkUpdate.permissionNeeded(session));
            return false;
        }
        File file = new File(session.localPath);
        if (!file.isFile()) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            } else {
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivityForResult(intent, INSTALL_REQUEST);
            save(activity, TellomiApkUpdate.installerOpened(session));
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    /** After the unknown-sources screen, or when a download finished in the background. */
    public static void onHostResume(Activity activity) {
        if (activity == null) {
            return;
        }
        TellomiApkUpdate.Session session = refresh(activity);
        if (session.state == TellomiApkUpdate.NEED_PERMISSION) {
            boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls();
            session = TellomiApkUpdate.permissionReturned(session, granted);
            save(activity, session);
        }
        if (TellomiApkUpdate.shouldOpenInstaller(session, canInstall(activity))) {
            openInstaller(activity, session);
        }
    }

    public static String messageKey(int error) {
        if (error == TellomiApkUpdate.BAD_PACKAGE) {
            return "TellomiUpdateBadPackage";
        }
        if (error == TellomiApkUpdate.BAD_SIGNATURE) {
            return "TellomiUpdateBadSignature";
        }
        if (error == TellomiApkUpdate.VERSION_NOT_NEWER) {
            return "TellomiUpdateNotNewer";
        }
        if (error == TellomiApkUpdate.HOST_REJECTED) {
            return "TellomiUpdateBadSource";
        }
        return "TellomiUpdateBadFile";
    }

    private static boolean canInstall(Activity activity) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls();
    }

    private static File localFile(Context context, int versionCode) {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        return new File(dir, fileName(versionCode));
    }

    private static String fileName(int versionCode) {
        return "Tellomi-" + versionCode + ".apk";
    }

    private static DownloadManager manager(Context context) {
        if (context == null) {
            return null;
        }
        Object service = context.getSystemService(Context.DOWNLOAD_SERVICE);
        return service instanceof DownloadManager ? (DownloadManager) service : null;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int signatureFlags() {
        if (Build.VERSION.SDK_INT >= 28) {
            return PackageManager.GET_SIGNING_CERTIFICATES;
        }
        return PackageManager.GET_SIGNATURES;
    }

    private static PackageInfo signatures(PackageManager manager, String packageName, int extra) throws Exception {
        if (Build.VERSION.SDK_INT >= 28) {
            return manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES | extra);
        }
        return manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES | extra);
    }

    private static int versionCode(PackageInfo info) {
        if (info == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            return (int) info.getLongVersionCode();
        }
        return info.versionCode;
    }

    private static byte[] firstCert(PackageInfo info) {
        if (info == null) {
            return null;
        }
        Signature[] signatures = null;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        }
        if ((signatures == null || signatures.length == 0) && info.signatures != null) {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0 || signatures[0] == null) {
            return null;
        }
        return signatures[0].toByteArray();
    }
}
