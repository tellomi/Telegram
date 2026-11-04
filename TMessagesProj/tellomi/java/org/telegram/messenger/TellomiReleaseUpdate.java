package org.telegram.messenger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Release check for the shipped App module.
 *
 * <p>The publish job writes {@code android/latest.json} to R2 {@code tellomi-updates}.
 * The official site copies that file to {@code https://www.tellomi.cn/dl/android/latest.json}.
 * The backup copy is {@code https://dl.tellomi.cn/android/latest.json}. Those two feeds
 * are the release. A newer {@code versionCode} is an update even when {@code versionName}
 * did not change.</p>
 *
 * <p>Two other published files are not this check. {@code android/test/latest.json} is the
 * test channel. {@code versions.json} is the download-page aggregate and can lag the
 * release feed. {@code help.getAppUpdate} is not updated by the installer publish, and
 * its {@code version} string does not carry {@code versionCode}.</p>
 */
public final class TellomiReleaseUpdate {

    public static final String PRIMARY_FEED = "https://www.tellomi.cn/dl/android/latest.json";
    public static final String FALLBACK_FEED = "https://dl.tellomi.cn/android/latest.json";
    public static final String[] FEED_URLS = {PRIMARY_FEED, FALLBACK_FEED};
    public static final String[] FEED_HOSTS = {"www.tellomi.cn", "dl.tellomi.cn", "updates.tellomi.app"};

    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int REDIRECT_LIMIT = 3;

    private TellomiReleaseUpdate() {
    }

    public static final class Manifest {
        public final String versionName;
        public final int versionCode;
        public final String url;
        public final String fallbackUrl;
        public final String notes;
        public final boolean fromFallback;

        Manifest(String versionName, int versionCode, String url, String fallbackUrl, String notes, boolean fromFallback) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.url = url;
            this.fallbackUrl = fallbackUrl;
            this.notes = notes;
            this.fromFallback = fromFallback;
        }
    }

    /** Reads both release feeds and keeps the higher {@code versionCode}. */
    public static Manifest fetch() {
        return fetch(FEED_URLS, FEED_HOSTS);
    }

    public static Manifest fetch(String[] urls, String[] hosts) {
        if (urls == null || urls.length == 0) {
            return null;
        }
        final String[] bodies = new String[urls.length];
        final boolean[] finished = new boolean[urls.length];
        Thread[] threads = new Thread[urls.length];
        for (int i = 0; i < urls.length; i++) {
            final int index = i;
            final String url = urls[i];
            threads[i] = new Thread(() -> {
                String body = httpGet(url, hosts, 0);
                synchronized (bodies) {
                    bodies[index] = body;
                    finished[index] = true;
                }
            }, "tellomi-release-feed-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (Thread thread : threads) {
            try {
                thread.join(CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS + 2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Manifest best = null;
        synchronized (bodies) {
            for (int i = 0; i < urls.length; i++) {
                if (!finished[i]) {
                    continue;
                }
                best = prefer(best, parse(bodies[i], i > 0));
            }
        }
        return best;
    }

    /**
     * Picks the release with the higher {@code versionCode}. Equal codes keep the
     * primary feed so the download stays on the official host when that host answered.
     */
    public static Manifest choose(String primaryBody, String fallbackBody) {
        return prefer(parse(primaryBody, false), parse(fallbackBody, true));
    }

    public static Manifest prefer(Manifest current, Manifest candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        if (candidate.versionCode > current.versionCode) {
            return candidate;
        }
        if (candidate.versionCode < current.versionCode) {
            return current;
        }
        if (current.fromFallback && !candidate.fromFallback) {
            return candidate;
        }
        return current;
    }

    public static Manifest parse(String json, boolean fromFallback) {
        java.util.Map<String, String> fields = topLevelFields(json);
        if (fields == null) {
            return null;
        }
        String platform = fields.get("platform");
        if (platform != null && !"android".equalsIgnoreCase(platform)) {
            return null;
        }
        String channel = fields.get("channel");
        if (channel != null && !"release".equalsIgnoreCase(channel)) {
            return null;
        }
        String versionName = fields.get("versionName");
        if (versionName == null || versionName.trim().isEmpty()) {
            return null;
        }
        int versionCode;
        try {
            String raw = fields.get("versionCode");
            if (raw == null || raw.indexOf('.') >= 0) {
                return null;
            }
            versionCode = Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return null;
        }
        if (versionCode <= 0) {
            return null;
        }
        return new Manifest(
                versionName.trim(),
                versionCode,
                emptyToNull(fields.get("url")),
                emptyToNull(fields.get("fallbackUrl")),
                emptyToNull(fields.get("notes")),
                fromFallback);
    }

    /**
     * A published {@code versionCode} higher than the installed one is an update.
     * Marketing {@code versionName} can stay the same across those builds.
     * Name comparison is only the fallback when this install has no version code.
     */
    public static boolean isNewer(int installedVersionCode, String installedVersionName, Manifest remote) {
        if (remote == null || remote.versionCode <= 0) {
            return false;
        }
        if (installedVersionCode > 0) {
            return remote.versionCode > installedVersionCode;
        }
        return versionNameIsNewer(remote.versionName, installedVersionName);
    }

    public static boolean versionNameIsNewer(String remote, String installed) {
        if (remote == null || installed == null) {
            return false;
        }
        String[] remoteParts = remote.trim().split("\\.");
        String[] installedParts = installed.trim().split("\\.");
        int count = Math.max(remoteParts.length, installedParts.length);
        for (int i = 0; i < count; i++) {
            int remotePart = i < remoteParts.length ? versionPart(remoteParts[i]) : 0;
            int installedPart = i < installedParts.length ? versionPart(installedParts[i]) : 0;
            if (remotePart != installedPart) {
                return remotePart > installedPart;
            }
        }
        return false;
    }

    /**
     * The host that served the manifest is the host that should serve the APK.
     * A fallback feed still advertises the official URL first; that host may be down.
     */
    public static String downloadUrl(Manifest manifest) {
        if (manifest == null) {
            return null;
        }
        String preferred = manifest.fromFallback ? manifest.fallbackUrl : manifest.url;
        String other = manifest.fromFallback ? manifest.url : manifest.fallbackUrl;
        String chosen = acceptableApkUrl(preferred);
        if (chosen == null) {
            chosen = acceptableApkUrl(other);
        }
        return chosen;
    }

    /**
     * A saved offer still applies when its remote version code is above the
     * install it was recorded against. Version-name equality is not "already installed".
     */
    public static boolean keepPendingForVersionCode(int installedVersionCode, int pendingRemoteVersionCode, int pendingSavedAgainstCode) {
        if (pendingRemoteVersionCode <= 0 || installedVersionCode <= 0) {
            return false;
        }
        if (pendingSavedAgainstCode != installedVersionCode) {
            return false;
        }
        return pendingRemoteVersionCode > installedVersionCode;
    }

    static String httpGet(String url, String[] hosts, int redirects) {
        HttpURLConnection connection = null;
        try {
            if (url == null || redirects > REDIRECT_LIMIT) {
                return null;
            }
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!schemeAllowed(scheme, host) || !hostAllowed(host, hosts)) {
                return null;
            }
            connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("User-Agent", "TellomiAndroid");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null || location.trim().isEmpty()) {
                    return null;
                }
                URI next = uri.resolve(location.trim());
                return httpGet(next.toString(), hosts, redirects + 1);
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            int length = connection.getContentLength();
            if (length > MAX_BODY_BYTES) {
                return null;
            }
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean schemeAllowed(String scheme, String host) {
        if (scheme == null || host == null) {
            return false;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return true;
        }
        return "http".equalsIgnoreCase(scheme) && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
    }

    private static boolean hostAllowed(String host, String[] hosts) {
        if (host == null || hosts == null) {
            return false;
        }
        for (String allowed : hosts) {
            if (allowed != null && allowed.equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    static String acceptableApkUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !hostAllowed(uri.getHost(), FEED_HOSTS)) {
                return null;
            }
            if (path == null || !path.toLowerCase(Locale.US).endsWith(".apk")) {
                return null;
            }
            return trimmed;
        } catch (Exception e) {
            return null;
        }
    }

    private static int versionPart(String part) {
        if (part == null || part.isEmpty()) {
            return 0;
        }
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static java.util.Map<String, String> topLevelFields(String json) {
        if (json == null) {
            return null;
        }
        Cursor cursor = new Cursor(skipWs(json, 0));
        if (cursor.i >= json.length() || json.charAt(cursor.i) != '{') {
            return null;
        }
        cursor.i++;
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        while (cursor.i < json.length()) {
            cursor.i = skipWs(json, cursor.i);
            if (cursor.i < json.length() && json.charAt(cursor.i) == '}') {
                return fields;
            }
            if (cursor.i >= json.length() || json.charAt(cursor.i) != '"') {
                return null;
            }
            String key = readString(json, cursor);
            if (key == null) {
                return null;
            }
            cursor.i = skipWs(json, cursor.i);
            if (cursor.i >= json.length() || json.charAt(cursor.i) != ':') {
                return null;
            }
            cursor.i++;
            cursor.i = skipWs(json, cursor.i);
            if (cursor.i >= json.length()) {
                return null;
            }
            char value = json.charAt(cursor.i);
            if (value == '"') {
                String text = readString(json, cursor);
                if (text == null) {
                    return null;
                }
                fields.put(key, text);
            } else if (value == '{' || value == '[') {
                if (!skipContainer(json, cursor)) {
                    return null;
                }
            } else {
                int start = cursor.i;
                while (cursor.i < json.length()) {
                    char ch = json.charAt(cursor.i);
                    if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                        break;
                    }
                    cursor.i++;
                }
                if (start == cursor.i) {
                    return null;
                }
                fields.put(key, json.substring(start, cursor.i));
            }
            cursor.i = skipWs(json, cursor.i);
            if (cursor.i < json.length() && json.charAt(cursor.i) == ',') {
                cursor.i++;
                continue;
            }
            if (cursor.i < json.length() && json.charAt(cursor.i) == '}') {
                return fields;
            }
            return null;
        }
        return null;
    }

    private static boolean skipContainer(String json, Cursor cursor) {
        char open = json.charAt(cursor.i);
        char close = open == '{' ? '}' : ']';
        int depth = 1;
        cursor.i++;
        while (cursor.i < json.length() && depth > 0) {
            char ch = json.charAt(cursor.i);
            if (ch == '"') {
                if (readString(json, cursor) == null) {
                    return false;
                }
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
            }
            cursor.i++;
        }
        return depth == 0;
    }

    private static String readString(String json, Cursor cursor) {
        if (cursor.i >= json.length() || json.charAt(cursor.i) != '"') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        cursor.i++;
        while (cursor.i < json.length()) {
            char ch = json.charAt(cursor.i);
            if (ch == '\\') {
                cursor.i++;
                if (cursor.i >= json.length()) {
                    return null;
                }
                char escaped = json.charAt(cursor.i);
                if (escaped == '"' || escaped == '\\' || escaped == '/') {
                    builder.append(escaped);
                } else if (escaped == 'n') {
                    builder.append('\n');
                } else if (escaped == 't') {
                    builder.append('\t');
                } else if (escaped == 'u' && cursor.i + 4 < json.length()) {
                    try {
                        builder.append((char) Integer.parseInt(json.substring(cursor.i + 1, cursor.i + 5), 16));
                    } catch (Exception e) {
                        return null;
                    }
                    cursor.i += 4;
                } else {
                    builder.append(escaped);
                }
                cursor.i++;
                continue;
            }
            if (ch == '"') {
                cursor.i++;
                return builder.toString();
            }
            builder.append(ch);
            cursor.i++;
        }
        return null;
    }

    private static int skipWs(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index;
    }

    private static final class Cursor {
        int i;

        Cursor(int i) {
            this.i = i;
        }
    }
}
