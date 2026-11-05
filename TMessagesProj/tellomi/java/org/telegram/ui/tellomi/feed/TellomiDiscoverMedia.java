package org.telegram.ui.tellomi.feed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discover image posts.
 *
 * The card fixture already stores {@code media:[{type,key}]}. Upload is tus 1.0.0
 * at {@code /v1/feed/media/uploads}. A finished upload's key is the JSON {@code key},
 * then JSON {@code id}, then the last path segment of the tus {@code Location}.
 * {@code POST /v1/feed/posts} sends that key back as {@code media[].key}.
 * Bytes are shown from {@code GET /v1/feed/media/<key>}.
 *
 * content-engine {@code docs/api.md} and {@code api/uploads.go} were not readable
 * from this checkout. These names are the card fixture plus the tus path and the
 * limits given for that route. There is no free-form topic field here.
 */
public final class TellomiDiscoverMedia {

    public static final int MAX_IMAGES = 10;
    public static final int MAX_BYTES = 20 * 1024 * 1024;
    public static final String UPLOAD_PATH = "/v1/feed/media/uploads";
    static final int CHUNK = 1024 * 1024;

    private TellomiDiscoverMedia() {
    }

    public static final class Bytes {
        public final String name;
        public final byte[] data;

        public Bytes(String name, byte[] data) {
            this.name = name == null ? "" : name;
            this.data = data == null ? new byte[0] : data;
        }
    }

    /** Null when the bytes can be uploaded. */
    public static TellomiDiscover.ClientError inspect(byte[] data) {
        if (data == null || data.length == 0) {
            return TellomiDiscover.ClientError.MEDIA_TYPE;
        }
        if (data.length > MAX_BYTES) {
            return TellomiDiscover.ClientError.MEDIA_TOO_LARGE;
        }
        if (mime(data) == null) {
            return TellomiDiscover.ClientError.MEDIA_TYPE;
        }
        return null;
    }

    /** {@code image/jpeg}, {@code image/png}, or {@code image/webp}. Anything else is null. */
    public static String mime(byte[] data) {
        if (data == null) {
            return null;
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 8
            && (data[0] & 0xFF) == 0x89
            && data[1] == 0x50
            && data[2] == 0x4E
            && data[3] == 0x47
            && data[4] == 0x0D
            && data[5] == 0x0A
            && data[6] == 0x1A
            && data[7] == 0x0A) {
            return "image/png";
        }
        if (data.length >= 12
            && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public static boolean validKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 256) {
            return false;
        }
        if (key.charAt(0) == '/' || key.charAt(key.length() - 1) == '/') {
            return false;
        }
        String[] parts = key.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                return false;
            }
            for (int c = 0; c < part.length(); c++) {
                char ch = part.charAt(c);
                boolean ok = (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '_' || ch == ':' || ch == '+' || ch == '=' || ch == '-';
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code GET} URL for a card key. The bearer stays on this host. */
    public static String imageUrl(String base, String key) {
        if (!validKey(key)) {
            return null;
        }
        String[] parts = key.split("/", -1);
        StringBuilder path = new StringBuilder("/v1/feed/media");
        for (int i = 0; i < parts.length; i++) {
            path.append('/').append(encode(parts[i]));
        }
        return TellomiDiscover.url(base, path.toString(), null);
    }

    public static String uploadUrl(String base) {
        return TellomiDiscover.url(base, UPLOAD_PATH, null);
    }

    /**
     * One tus upload, then the next file. {@link #next()} is null when finished
     * or when {@link #error} is set. Drive it with the discover HTTPS transport.
     */
    public static final class Upload {
        private final String base;
        private final String token;
        private final List<Bytes> files;
        private int index;
        private int offset;
        private String location;
        private final List<String> keys = new ArrayList<>();
        public TellomiDiscover.ClientError error;

        private Upload(String base, String token, List<Bytes> files) {
            this.base = base;
            this.token = token == null ? "" : token;
            this.files = files == null ? Collections.<Bytes>emptyList() : files;
        }

        public static Upload start(String base, String token, List<Bytes> files) {
            Upload upload = new Upload(base, token, files);
            if (upload.token.isEmpty() || uploadUrl(base) == null) {
                upload.error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                return upload;
            }
            if (upload.files.size() > MAX_IMAGES) {
                upload.error = TellomiDiscover.ClientError.MEDIA_LIMIT;
                return upload;
            }
            for (int i = 0; i < upload.files.size(); i++) {
                TellomiDiscover.ClientError problem = inspect(upload.files.get(i).data);
                if (problem != null) {
                    upload.error = problem;
                    return upload;
                }
            }
            return upload;
        }

        public boolean finished() {
            return error != null || index >= files.size();
        }

        public List<String> keys() {
            if (error != null || index < files.size()) {
                return null;
            }
            return new ArrayList<>(keys);
        }

        public TellomiDiscover.HttpCall next() {
            if (finished()) {
                return null;
            }
            Bytes file = files.get(index);
            if (location == null) {
                String url = uploadUrl(base);
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("Tus-Resumable", "1.0.0");
                headers.put("Upload-Length", Integer.toString(file.data.length));
                headers.put("Upload-Metadata", metadata(file.name, mime(file.data)));
                return new TellomiDiscover.HttpCall("POST", url, null, token, true, null, headers);
            }
            int end = Math.min(file.data.length, offset + CHUNK);
            byte[] chunk = new byte[end - offset];
            System.arraycopy(file.data, offset, chunk, 0, chunk.length);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Tus-Resumable", "1.0.0");
            headers.put("Upload-Offset", Integer.toString(offset));
            return new TellomiDiscover.HttpCall("PATCH", location, chunk, token, true, "application/offset+octet-stream", headers);
        }

        public void consume(TellomiDiscover.HttpResult result) {
            if (finished()) {
                return;
            }
            if (result == null || result.error != null) {
                error = TellomiDiscover.ClientError.MEDIA_NETWORK;
                return;
            }
            Bytes file = files.get(index);
            if (location == null) {
                if (result.status == 413) {
                    error = TellomiDiscover.ClientError.MEDIA_TOO_LARGE;
                    return;
                }
                if (result.status == 415) {
                    error = TellomiDiscover.ClientError.MEDIA_TYPE;
                    return;
                }
                if (result.status != 201 && result.status != 204) {
                    error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                    return;
                }
                String resolved = resolve(uploadUrl(base), result.header("location"));
                if (resolved == null) {
                    error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                    return;
                }
                int reported = parseOffset(result.header("upload-offset"), 0);
                if (reported < 0 || reported > file.data.length) {
                    error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                    return;
                }
                location = resolved;
                offset = reported;
                if (offset == file.data.length) {
                    finishFile(result);
                }
                return;
            }
            if (result.status == 413) {
                error = TellomiDiscover.ClientError.MEDIA_TOO_LARGE;
                return;
            }
            if (result.status == 415) {
                error = TellomiDiscover.ClientError.MEDIA_TYPE;
                return;
            }
            if (result.status != 204 && result.status != 200 && result.status != 201) {
                error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                return;
            }
            int reported = parseOffset(result.header("upload-offset"), -1);
            if (reported < offset || reported > file.data.length) {
                error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                return;
            }
            offset = reported;
            if (offset == file.data.length) {
                finishFile(result);
            }
        }

        private void finishFile(TellomiDiscover.HttpResult result) {
            String key = uploadedKey(result.body, location);
            if (key == null) {
                error = TellomiDiscover.ClientError.MEDIA_UPLOAD;
                return;
            }
            keys.add(key);
            index++;
            location = null;
            offset = 0;
        }
    }

    static String metadata(String name, String mime) {
        String filename = filename(name, mime);
        String type = mime == null ? "application/octet-stream" : mime;
        return "filename " + base64(filename) + ",filetype " + base64(type);
    }

    static String uploadedKey(byte[] body, String location) {
        TellomiJson json = TellomiJson.parse(body);
        if (json != null && json.type == TellomiJson.Type.OBJECT) {
            String key = stringValue(json.objectValue.get("key"));
            if (validKey(key)) {
                return key;
            }
            String id = stringValue(json.objectValue.get("id"));
            if (validKey(id)) {
                return id;
            }
        }
        String segment = lastSegment(location);
        if (segment == null) {
            return null;
        }
        try {
            segment = java.net.URLDecoder.decode(segment, "UTF-8");
        } catch (java.io.UnsupportedEncodingException ignored) {
            return null;
        }
        return validKey(segment) ? segment : null;
    }

    /** Same-host https, or a path on the upload URL's origin. A foreign host is refused. */
    static String resolve(String requestUrl, String location) {
        if (requestUrl == null || location == null || location.isEmpty()) {
            return null;
        }
        try {
            java.net.URI base = java.net.URI.create(requestUrl);
            java.net.URI next = base.resolve(location);
            if (next.getScheme() == null || !"https".equalsIgnoreCase(next.getScheme())) {
                return null;
            }
            if (base.getHost() == null || next.getHost() == null || !base.getHost().equalsIgnoreCase(next.getHost())) {
                return null;
            }
            if (next.getRawQuery() != null || next.getRawFragment() != null) {
                return null;
            }
            return next.toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stringValue(TellomiJson json) {
        if (json != null && json.type == TellomiJson.Type.STRING) {
            return json.stringValue;
        }
        return null;
    }

    private static String lastSegment(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int slash = url.lastIndexOf('/');
        if (slash < 0 || slash == url.length() - 1) {
            return null;
        }
        return url.substring(slash + 1);
    }

    private static int parseOffset(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) value;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String filename(String name, String mime) {
        String leaf = name == null ? "" : name;
        int slash = Math.max(leaf.lastIndexOf('/'), leaf.lastIndexOf('\\'));
        if (slash >= 0) {
            leaf = leaf.substring(slash + 1);
        }
        leaf = leaf.trim();
        if (leaf.isEmpty() || ".".equals(leaf) || "..".equals(leaf)) {
            leaf = "photo" + extension(mime);
        }
        if (leaf.length() > 120) {
            leaf = leaf.substring(leaf.length() - 120);
        }
        return leaf;
    }

    private static String extension(String mime) {
        if ("image/png".equals(mime)) {
            return ".png";
        }
        if ("image/webp".equals(mime)) {
            return ".webp";
        }
        return ".jpg";
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException ignored) {
            return value;
        }
    }
}
