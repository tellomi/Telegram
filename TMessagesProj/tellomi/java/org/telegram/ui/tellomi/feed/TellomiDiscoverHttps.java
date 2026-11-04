package org.telegram.ui.tellomi.feed;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/** HTTPS transport for the content engine. The bearer token is never put in the URL. */
final class TellomiDiscoverHttps {

    private static final int MAX_BODY = 2_000_000;
    private static final int TIMEOUT_MS = 20_000;

    void perform(final TellomiDiscover.HttpCall call, final TellomiDiscover.HttpCallback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                callback.onResult(execute(call));
            }
        }, "tellomi-discover");
        thread.setDaemon(true);
        thread.start();
    }

    private TellomiDiscover.HttpResult execute(TellomiDiscover.HttpCall call) {
        String url = call.url;
        for (int redirect = 0; redirect < 3; redirect++) {
            if (url == null || !url.toLowerCase(java.util.Locale.US).startsWith("https://")) {
                return new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNAVAILABLE);
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestMethod(call.method);
                connection.setRequestProperty("Accept", "application/json");
                if (call.authorizes) {
                    connection.setRequestProperty("Authorization", "Bearer " + call.token);
                }
                if (call.contentType != null) {
                    connection.setRequestProperty("Content-Type", call.contentType);
                }
                if (call.body != null) {
                    connection.setDoOutput(true);
                    OutputStream out = connection.getOutputStream();
                    try {
                        out.write(call.body);
                    } finally {
                        out.close();
                    }
                }
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    String next = sameHostHttps(url, location);
                    if (next == null) {
                        return new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNAVAILABLE);
                    }
                    url = next;
                    continue;
                }
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                byte[] body;
                try {
                    body = readLimited(stream);
                } catch (TooLarge ignored) {
                    return new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNREADABLE);
                }
                return new TellomiDiscover.HttpResult(status, body);
            } catch (Exception ignored) {
                return new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNAVAILABLE);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNAVAILABLE);
    }

    private static String sameHostHttps(String current, String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            URI base = URI.create(current);
            URI next = base.resolve(location);
            if (next.getScheme() == null || !"https".equalsIgnoreCase(next.getScheme())) {
                return null;
            }
            if (base.getHost() == null || next.getHost() == null || !base.getHost().equalsIgnoreCase(next.getHost())) {
                return null;
            }
            return next.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] readLimited(InputStream stream) throws Exception {
        if (stream == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_BODY) {
                    throw new TooLarge();
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static final class TooLarge extends Exception {
    }
}
