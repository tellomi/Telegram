package org.telegram.ui.tellomi.feed;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Loads a stored cover on the phone. Not an Open Graph scrape, and not a server proxy. */
final class TellomiDiscoverLinkImage {

    private static final int MAX_BYTES = 512 * 1024;
    private static final int TIMEOUT_MS = 8000;
    private static final int CACHE_MAX = 8;
    private static final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > CACHE_MAX;
        }
    };

    private TellomiDiscoverLinkImage() {
    }

    static void load(final ImageView view, final String url) {
        if (view == null) {
            return;
        }
        final String safe = TellomiDiscoverLink.fetchable(url) ? url : null;
        view.setTag(safe);
        if (safe == null) {
            view.setImageBitmap(null);
            view.setVisibility(ImageView.GONE);
            return;
        }
        Bitmap hit;
        synchronized (cache) {
            hit = cache.get(safe);
        }
        if (hit != null) {
            view.setImageBitmap(hit);
            view.setVisibility(ImageView.VISIBLE);
            return;
        }
        view.setVisibility(ImageView.GONE);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = fetch(safe);
                if (bitmap == null) {
                    return;
                }
                synchronized (cache) {
                    cache.put(safe, bitmap);
                }
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!safe.equals(view.getTag())) {
                            return;
                        }
                        view.setImageBitmap(bitmap);
                        view.setVisibility(ImageView.VISIBLE);
                    }
                });
            }
        }, "tellomi-link-image");
        thread.setDaemon(true);
        thread.start();
    }

    private static Bitmap fetch(String url) {
        if (!TellomiDiscoverLinkFetch.reachablePublic(url)) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "image/*");
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String next = connection.getHeaderField("Location");
                if (next == null) {
                    return null;
                }
                String absolute = java.net.URI.create(url).resolve(next).toString();
                if (!absolute.equals(url) && TellomiDiscoverLink.fetchable(absolute)) {
                    return fetchHop(absolute, 1);
                }
                return null;
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            String type = connection.getContentType();
            if (type != null && !type.toLowerCase(Locale.US).startsWith("image/")) {
                return null;
            }
            return decode(read(connection.getInputStream()));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Bitmap fetchHop(String url, int hop) {
        if (hop > 3 || !TellomiDiscoverLinkFetch.reachablePublic(url)) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "image/*");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String next = connection.getHeaderField("Location");
                if (next == null) {
                    return null;
                }
                String absolute = java.net.URI.create(url).resolve(next).toString();
                return TellomiDiscoverLink.fetchable(absolute) ? fetchHop(absolute, hop + 1) : null;
            }
            if (status < 200 || status >= 300) {
                return null;
            }
            String type = connection.getContentType();
            if (type != null && !type.toLowerCase(Locale.US).startsWith("image/")) {
                return null;
            }
            return decode(read(connection.getInputStream()));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] read(InputStream stream) throws Exception {
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
                if (total > MAX_BYTES) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (bounds.outWidth / sample > 256 && bounds.outHeight / sample > 256) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }
}
