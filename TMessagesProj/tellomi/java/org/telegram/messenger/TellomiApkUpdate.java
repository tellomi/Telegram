package org.telegram.messenger;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * In-app website-APK update: download state, and the checks that run
 * before the system installer. A sha256 published next to the file is
 * not enough. The installed app's package name and signing certificate
 * have to match, and the version code has to move forward.
 */
public final class TellomiApkUpdate {

    public static final int IDLE = 0;
    public static final int DOWNLOADING = 1;
    public static final int READY = 2;
    public static final int NEED_PERMISSION = 3;
    public static final int FAILED = 4;

    public static final int OK = 0;
    public static final int BAD_PACKAGE = 1;
    public static final int BAD_SIGNATURE = 2;
    public static final int VERSION_NOT_NEWER = 3;
    public static final int BAD_FILE = 4;
    public static final int HOST_REJECTED = 5;

    private TellomiApkUpdate() {
    }

    public static final class Session {
        public final int state;
        public final String url;
        public final int versionCode;
        public final long downloadId;
        public final String localPath;
        public final int error;
        public final boolean awaitingPermission;
        public final boolean installerOpened;

        public Session(int state, String url, int versionCode, long downloadId, String localPath, int error, boolean awaitingPermission, boolean installerOpened) {
            this.state = state;
            this.url = url;
            this.versionCode = versionCode;
            this.downloadId = downloadId;
            this.localPath = localPath;
            this.error = error;
            this.awaitingPermission = awaitingPermission;
            this.installerOpened = installerOpened;
        }
    }

    public static Session idle() {
        return new Session(IDLE, null, 0, -1L, null, OK, false, false);
    }

    /** A second tap on the same download does not start another one. */
    public static Session startDownload(Session current, String url, int versionCode) {
        String accepted = TellomiReleaseUpdate.acceptableApkUrl(url);
        if (accepted == null || versionCode <= 0) {
            return failed(current, HOST_REJECTED);
        }
        if (current != null && current.state == DOWNLOADING && accepted.equals(current.url) && current.downloadId >= 0) {
            return current;
        }
        return new Session(DOWNLOADING, accepted, versionCode, -1L, null, OK, false, false);
    }

    public static boolean shouldEnqueue(Session session) {
        return session != null && session.state == DOWNLOADING && session.downloadId < 0;
    }

    public static Session attachDownloadId(Session session, long downloadId) {
        if (session == null || session.state != DOWNLOADING || downloadId < 0) {
            return session;
        }
        return new Session(DOWNLOADING, session.url, session.versionCode, downloadId, null, OK, false, false);
    }

    public static Session downloadFinished(Session session, boolean success, String path) {
        if (session == null) {
            return idle();
        }
        if (!success || path == null || path.isEmpty()) {
            return failed(session, BAD_FILE);
        }
        return new Session(READY, session.url, session.versionCode, session.downloadId, path, OK, false, false);
    }

    public static Session permissionNeeded(Session session) {
        if (session == null) {
            return idle();
        }
        return new Session(NEED_PERMISSION, session.url, session.versionCode, session.downloadId, session.localPath, OK, true, false);
    }

    /** Coming back from the system settings screen continues the same file. */
    public static Session permissionReturned(Session session, boolean granted) {
        if (session == null) {
            return idle();
        }
        if (!granted) {
            return permissionNeeded(session);
        }
        if (session.localPath == null || session.localPath.isEmpty()) {
            return session;
        }
        return new Session(READY, session.url, session.versionCode, session.downloadId, session.localPath, OK, false, false);
    }

    public static boolean shouldOpenInstaller(Session session, boolean permissionGranted) {
        return session != null
                && session.state == READY
                && session.localPath != null
                && permissionGranted
                && !session.installerOpened;
    }

    public static Session installerOpened(Session session) {
        if (session == null) {
            return idle();
        }
        return new Session(session.state, session.url, session.versionCode, session.downloadId, session.localPath, session.error, false, true);
    }

    /** A process restart keeps a DownloadManager id and a file that is already on disk. */
    public static Session afterRestart(Session saved) {
        if (saved == null) {
            return idle();
        }
        if (saved.state == DOWNLOADING && saved.downloadId >= 0) {
            return saved;
        }
        if ((saved.state == READY || saved.state == NEED_PERMISSION) && saved.localPath != null) {
            return saved;
        }
        if (saved.state == FAILED) {
            return saved;
        }
        return idle();
    }

    public static int percent(long loaded, long total) {
        if (total <= 0 || loaded < 0) {
            return 0;
        }
        if (loaded >= total) {
            return 100;
        }
        return (int) (loaded * 100L / total);
    }

    public static int verify(String expectedPackage, String actualPackage, int installedVersionCode, int apkVersionCode, byte[] installedCert, byte[] apkCert, boolean zipIntact, String publishedSha256, String fileSha256) {
        if (!zipIntact) {
            return BAD_FILE;
        }
        if (expectedPackage == null || actualPackage == null || !expectedPackage.equals(actualPackage)) {
            return BAD_PACKAGE;
        }
        if (installedVersionCode <= 0 || apkVersionCode <= installedVersionCode) {
            return VERSION_NOT_NEWER;
        }
        if (installedCert == null || apkCert == null || installedCert.length == 0 || !Arrays.equals(installedCert, apkCert)) {
            return BAD_SIGNATURE;
        }
        if (publishedSha256 != null) {
            if (fileSha256 == null || !publishedSha256.equalsIgnoreCase(fileSha256)) {
                return BAD_FILE;
            }
        }
        return OK;
    }

    public static String sha256Hex(InputStream input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean zipMarkersIntact(byte[] head, byte[] tail) {
        if (head == null || head.length < 4 || tail == null || tail.length < 22) {
            return false;
        }
        if (head[0] != 'P' || head[1] != 'K' || head[2] != 3 || head[3] != 4) {
            return false;
        }
        for (int i = tail.length - 22; i >= 0; i--) {
            if (tail[i] == 'P' && tail[i + 1] == 'K' && tail[i + 2] == 5 && tail[i + 3] == 6) {
                return true;
            }
        }
        return false;
    }

    public static String encode(Session session) {
        Session value = session == null ? idle() : session;
        return value.state + "\n"
                + safe(value.url) + "\n"
                + value.versionCode + "\n"
                + value.downloadId + "\n"
                + safe(value.localPath) + "\n"
                + value.error + "\n"
                + (value.awaitingPermission ? 1 : 0) + "\n"
                + (value.installerOpened ? 1 : 0);
    }

    public static Session decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return idle();
        }
        String[] parts = raw.split("\n", -1);
        if (parts.length != 8) {
            return idle();
        }
        try {
            return new Session(
                    Integer.parseInt(parts[0]),
                    emptyToNull(parts[1]),
                    Integer.parseInt(parts[2]),
                    Long.parseLong(parts[3]),
                    emptyToNull(parts[4]),
                    Integer.parseInt(parts[5]),
                    "1".equals(parts[6]),
                    "1".equals(parts[7])
            );
        } catch (Exception e) {
            return idle();
        }
    }

    private static Session failed(Session current, int error) {
        String url = current == null ? null : current.url;
        int version = current == null ? 0 : current.versionCode;
        return new Session(FAILED, url, version, -1L, null, error, false, false);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ');
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
