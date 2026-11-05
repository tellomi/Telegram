package org.telegram.ui.tellomi.feed;

import org.telegram.messenger.AndroidUtilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Open Graph fetch on the phone's own network. This does not call Chengdu,
 * {@code account.getWebPagePreview}, port 8801, or {@code /internal/v1/link-preview}.
 * The map is a short in-memory cache for this process, not a server cache.
 */
final class TellomiDiscoverLinkFetch {

    static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_HOPS = 5;
    private static final int TIMEOUT_MS = 8000;
    private static final int CACHE_MAX = 16;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) TellomiLinkPreview/1.0";

    interface Callback {
        void onResult(TellomiDiscoverLink.Preview preview);
    }

    private static final Map<String, TellomiDiscoverLink.Preview> cache = new LinkedHashMap<String, TellomiDiscoverLink.Preview>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TellomiDiscoverLink.Preview> eldest) {
            return size() > CACHE_MAX;
        }
    };
    private static final Map<String, List<Callback>> inflight = new LinkedHashMap<>();

    private TellomiDiscoverLinkFetch() {
    }

    static TellomiDiscoverLink.Preview peek(String url) {
        if (!TellomiDiscoverLink.fetchable(url)) {
            return null;
        }
        synchronized (cache) {
            return cache.get(url);
        }
    }

    static void load(final String url, final Callback callback) {
        if (!TellomiDiscoverLink.fetchable(url)) {
            deliver(callback, null);
            return;
        }
        TellomiDiscoverLink.Preview hit = peek(url);
        if (hit != null) {
            deliver(callback, hit);
            return;
        }
        synchronized (cache) {
            List<Callback> waiters = inflight.get(url);
            if (waiters != null) {
                waiters.add(callback);
                return;
            }
            waiters = new ArrayList<>();
            waiters.add(callback);
            inflight.put(url, waiters);
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                TellomiDiscoverLink.Preview preview;
                try {
                    preview = fetch(url);
                } catch (Exception ignored) {
                    preview = TellomiDiscoverLink.urlOnly(url);
                }
                if (preview == null) {
                    preview = TellomiDiscoverLink.urlOnly(url);
                }
                List<Callback> waiters;
                synchronized (cache) {
                    if (preview != null && (preview.title != null || preview.description != null || preview.image != null)) {
                        cache.put(url, preview);
                    }
                    waiters = inflight.remove(url);
                }
                if (waiters == null) {
                    return;
                }
                for (int i = 0; i < waiters.size(); i++) {
                    deliver(waiters.get(i), preview);
                }
            }
        }, "tellomi-link-preview");
        thread.setDaemon(true);
        thread.start();
    }

    static boolean reachablePublic(String url) {
        if (!TellomiDiscoverLink.fetchable(url)) {
            return false;
        }
        String host = TellomiDiscoverLink.host(url);
        if (host == null) {
            return false;
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            for (int i = 0; i < addresses.length; i++) {
                if (TellomiDiscoverLink.blockedBytes(addresses[i].getAddress())) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TellomiDiscoverLink.Preview fetch(String pageUrl) throws Exception {
        String current = pageUrl;
        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            if (!reachablePublic(current)) {
                return TellomiDiscoverLink.urlOnly(pageUrl);
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setUseCaches(false);
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String next = redirect(current, connection.getHeaderField("Location"));
                    if (next == null) {
                        return TellomiDiscoverLink.urlOnly(pageUrl);
                    }
                    current = next;
                    continue;
                }
                if (status < 200 || status >= 300) {
                    return TellomiDiscoverLink.urlOnly(pageUrl);
                }
                String type = connection.getContentType();
                if (type != null && type.toLowerCase(Locale.US).startsWith("image/") && TellomiDiscoverLink.fetchable(current)) {
                    return new TellomiDiscoverLink.Preview(current, null, null, current);
                }
                byte[] body = readHead(connection.getInputStream());
                if (type != null) {
                    String lower = type.toLowerCase(Locale.US);
                    if (!lower.contains("html") && !lower.contains("xml") && !lower.contains("text/")) {
                        return TellomiDiscoverLink.urlOnly(pageUrl);
                    }
                }
                Charset charset = TellomiDiscoverLink.charset(body, type);
                String html = new String(body, charset);
                return TellomiDiscoverLink.parseHtml(html, current);
            } finally {
                connection.disconnect();
            }
        }
        return TellomiDiscoverLink.urlOnly(pageUrl);
    }

    private static String redirect(String current, String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            String next = URI.create(current).resolve(location).toString();
            return TellomiDiscoverLink.fetchable(next) ? next : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static byte[] readHead(InputStream stream) throws Exception {
        if (stream == null) {
            return new byte[0];
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                int take = read;
                if (total + take > MAX_BYTES) {
                    take = MAX_BYTES - total;
                }
                if (take > 0) {
                    out.write(buffer, 0, take);
                    total += take;
                }
                if (total >= MAX_BYTES || endsHead(out)) {
                    break;
                }
            }
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static boolean endsHead(ByteArrayOutputStream out) {
        byte[] raw = out.toByteArray();
        String ascii = new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.US);
        return ascii.contains("</head>");
    }

    private static void deliver(final Callback callback, final TellomiDiscoverLink.Preview preview) {
        if (callback == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                callback.onResult(preview);
            }
        });
    }
}
