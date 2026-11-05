package org.telegram.ui.tellomi.feed;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Discover link cards. The phone reads Open Graph itself. Nothing here calls
 * teamgram linkpreview, port 8801, or {@code POST /internal/v1/link-preview}.
 */
public final class TellomiDiscoverLink {

    public static final int TITLE_LIMIT = 200;
    public static final int DESCRIPTION_LIMIT = 300;
    static final int URL_LIMIT = 2048;

    private TellomiDiscoverLink() {
    }

    /** Fields stored with a post. Absent strings are omitted from {@link #publishFields}. */
    public static final class Preview {
        public final String url;
        public final String title;
        public final String description;
        public final String image;

        public Preview(String url, String title, String description, String image) {
            this.url = url;
            this.title = title;
            this.description = description;
            this.image = image;
        }

        public String titleLine() {
            if (title != null && !title.isEmpty()) {
                return title;
            }
            String name = host(url);
            if (name != null && !name.isEmpty()) {
                return name;
            }
            return url == null ? "" : url;
        }

        /** Host under a real title. Empty when the title line is already the host. */
        public String siteLine() {
            if (title == null || title.isEmpty()) {
                return null;
            }
            String name = host(url);
            if (name == null || name.isEmpty() || name.equalsIgnoreCase(title)) {
                return null;
            }
            return name;
        }
    }

    /** First http(s) URL in {@code text}. An email is not a URL. */
    public static String firstUrl(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int from = 0;
        while (from < text.length()) {
            int http = indexOfScheme(text, "http://", from);
            int https = indexOfScheme(text, "https://", from);
            int start;
            if (http < 0) {
                start = https;
            } else if (https < 0) {
                start = http;
            } else {
                start = Math.min(http, https);
            }
            if (start < 0) {
                return null;
            }
            if (start > 0 && glued(text.charAt(start - 1))) {
                from = start + 1;
                continue;
            }
            int end = start;
            while (end < text.length() && !stop(text.charAt(end))) {
                end++;
            }
            String raw = trimTail(text.substring(start, end));
            if (httpUrl(raw) != null) {
                return raw;
            }
            from = start + 1;
        }
        return null;
    }

    /**
     * True when the phone may open this URL. Literal private, loopback, and
     * link-local hosts are refused. A public name is allowed; the fetch checks
     * DNS before connecting.
     */
    public static boolean fetchable(String raw) {
        String url = httpUrl(raw);
        if (url == null) {
            return false;
        }
        String name = host(url);
        if (name == null || name.isEmpty() || blockedName(name) || blockedLiteral(name)) {
            return false;
        }
        return name.indexOf('.') >= 0 || name.indexOf(':') >= 0;
    }

    public static String host(String raw) {
        String url = httpUrl(raw);
        if (url == null) {
            return null;
        }
        try {
            String name = new URI(url).getHost();
            if (name == null) {
                return null;
            }
            name = name.toLowerCase(Locale.US);
            while (name.endsWith(".")) {
                name = name.substring(0, name.length() - 1);
            }
            return name.isEmpty() ? null : name;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    /** http(s) URL with a host and no user info. The original spelling is kept. */
    public static String httpUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = trim(raw);
        if (value.isEmpty() || value.length() > URL_LIMIT || hasSpace(value)) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException ignored) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        if (uri.getUserInfo() != null || uri.getRawUserInfo() != null) {
            return null;
        }
        String name = uri.getHost();
        if (name == null || name.isEmpty()) {
            return null;
        }
        return value;
    }

    public static Preview urlOnly(String raw) {
        String url = httpUrl(raw);
        if (url == null || !fetchable(url)) {
            return null;
        }
        return new Preview(url, null, null, null);
    }

    /**
     * Open Graph, then Twitter card, then {@code <title>} and
     * {@code meta name=description}. {@code pageUrl} is the final page address.
     */
    public static Preview parseHtml(String html, String pageUrl) {
        if (!fetchable(pageUrl)) {
            return null;
        }
        if (html == null) {
            html = "";
        }
        String title = meta(html, "og:title", TITLE_LIMIT);
        if (title == null) {
            title = meta(html, "twitter:title", TITLE_LIMIT);
        }
        if (title == null) {
            title = titleTag(html);
        }
        String description = meta(html, "og:description", DESCRIPTION_LIMIT);
        if (description == null) {
            description = meta(html, "twitter:description", DESCRIPTION_LIMIT);
        }
        if (description == null) {
            description = meta(html, "description", DESCRIPTION_LIMIT);
        }
        String image = imageMeta(html, pageUrl, "og:image:secure_url");
        if (image == null) {
            image = imageMeta(html, pageUrl, "og:image");
        }
        if (image == null) {
            image = imageMeta(html, pageUrl, "twitter:image");
        }
        if (image == null) {
            image = imageMeta(html, pageUrl, "twitter:image:src");
        }
        return new Preview(pageUrl, clip(title, TITLE_LIMIT), clip(description, DESCRIPTION_LIMIT), image);
    }

    /** One stored object, the shape {@code POST /v1/feed/posts} sends. */
    public static Preview parseStored(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.OBJECT) {
            return null;
        }
        String url = httpUrl(text(json.objectValue.get("url")));
        if (!fetchable(url)) {
            return null;
        }
        String image = absoluteImage(url, text(json.objectValue.get("image")));
        return new Preview(url, clip(text(json.objectValue.get("title")), TITLE_LIMIT), clip(text(json.objectValue.get("description")), DESCRIPTION_LIMIT), image);
    }

    /** {@code card.link_preview}, then the same object beside the card. */
    public static Preview stored(TellomiJson body, TellomiJson entry) {
        Preview preview = body == null ? null : parseStored(body.objectValue.get("link_preview"));
        if (preview != null) {
            return preview;
        }
        return entry == null ? null : parseStored(entry.objectValue.get("link_preview"));
    }

    /**
     * Body for {@code link_preview}. Null when there is no public URL.
     * Only {@code url}, {@code title}, {@code description}, and {@code image}.
     */
    public static Map<String, String> publishFields(Preview preview) {
        if (preview == null || !fetchable(preview.url)) {
            return null;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("url", preview.url);
        if (preview.title != null && !preview.title.isEmpty()) {
            fields.put("title", clip(preview.title, TITLE_LIMIT));
        }
        if (preview.description != null && !preview.description.isEmpty()) {
            fields.put("description", clip(preview.description, DESCRIPTION_LIMIT));
        }
        String image = absoluteImage(preview.url, preview.image);
        if (image != null) {
            fields.put("image", image);
        }
        return fields;
    }

    public static String charsetName(byte[] data, String contentType) {
        String header = charsetToken(contentType);
        if (header != null) {
            return header;
        }
        if (data == null || data.length == 0) {
            return "UTF-8";
        }
        int length = Math.min(data.length, 2048);
        String ascii = new String(data, 0, length, StandardCharsets.ISO_8859_1).toLowerCase(Locale.US);
        int marker = ascii.indexOf("charset");
        if (marker < 0) {
            return "UTF-8";
        }
        int eq = ascii.indexOf('=', marker);
        if (eq < 0 || eq > marker + 16) {
            return "UTF-8";
        }
        int start = eq + 1;
        while (start < ascii.length() && (ascii.charAt(start) == ' ' || ascii.charAt(start) == '"' || ascii.charAt(start) == '\'')) {
            start++;
        }
        int end = start;
        while (end < ascii.length()) {
            char c = ascii.charAt(end);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                end++;
            } else {
                break;
            }
        }
        return end == start ? "UTF-8" : knownCharset(ascii.substring(start, end));
    }

    public static Charset charset(byte[] data, String contentType) {
        try {
            return Charset.forName(charsetName(data, contentType));
        } catch (RuntimeException ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    public static boolean blockedLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        byte[] v4 = ipv4(host);
        if (v4 != null) {
            return blockedBytes(v4);
        }
        if (host.indexOf(':') < 0) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            byte[] raw = address.getAddress();
            if (raw == null || raw.length != 16) {
                return true;
            }
            return blockedBytes(raw);
        } catch (Exception ignored) {
            return true;
        }
    }

    public static boolean blockedName(String host) {
        if (host == null) {
            return false;
        }
        String name = host.toLowerCase(Locale.US);
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        return "localhost".equals(name) || name.endsWith(".localhost") || name.endsWith(".local");
    }

    static boolean blockedBytes(byte[] raw) {
        if (raw == null) {
            return true;
        }
        if (raw.length == 4) {
            int a = raw[0] & 0xff;
            int b = raw[1] & 0xff;
            if (a == 0 || a == 10 || a == 127) {
                return true;
            }
            if (a == 169 && b == 254) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            if (a == 100 && b >= 64 && b <= 127) {
                return true;
            }
            if (a == 192 && b == 0 && (raw[2] & 0xff) == 0) {
                return true;
            }
            if (a == 192 && b == 0 && (raw[2] & 0xff) == 2) {
                return true;
            }
            if (a == 198 && b == 51 && (raw[2] & 0xff) == 100) {
                return true;
            }
            if (a == 203 && b == 0 && (raw[2] & 0xff) == 113) {
                return true;
            }
            if (a == 198 && (b == 18 || b == 19)) {
                return true;
            }
            return a >= 224;
        }
        if (raw.length == 16) {
            int a = raw[0] & 0xff;
            if (a == 0xff) {
                return true;
            }
            if ((a & 0xfe) == 0xfc) {
                return true;
            }
            if (a == 0xfe && (raw[1] & 0xc0) == 0x80) {
                return true;
            }
            boolean mapped = raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
            for (int i = 0; mapped && i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                }
            }
            if (mapped) {
                return blockedBytes(new byte[] {raw[12], raw[13], raw[14], raw[15]});
            }
            boolean zeros = true;
            for (int i = 0; i < 15; i++) {
                if (raw[i] != 0) {
                    zeros = false;
                    break;
                }
            }
            return zeros && (raw[15] == 0 || raw[15] == 1);
        }
        return true;
    }

    static String absoluteImage(String pageUrl, String raw) {
        if (raw == null) {
            return null;
        }
        String ref = trim(raw);
        if (ref.isEmpty() || ref.length() > URL_LIMIT) {
            return null;
        }
        String lower = ref.toLowerCase(Locale.US);
        if (lower.startsWith("data:") || lower.startsWith("javascript:") || lower.startsWith("blob:")) {
            return null;
        }
        String absolute;
        if (lower.startsWith("//")) {
            absolute = "https:" + ref;
        } else if (httpUrl(ref) != null) {
            absolute = ref;
        } else if (pageUrl == null) {
            return null;
        } else {
            try {
                absolute = new URI(pageUrl).resolve(ref).toString();
            } catch (URISyntaxException ignored) {
                return null;
            }
        }
        return fetchable(absolute) ? absolute : null;
    }

    private static String imageMeta(String html, String pageUrl, String key) {
        int from = 0;
        String wanted = key.toLowerCase(Locale.US);
        while (from < html.length()) {
            int start = indexOfIgnoreCase(html, "<meta", from);
            if (start < 0) {
                return null;
            }
            int end = tagEnd(html, start);
            if (end < 0) {
                return null;
            }
            Map<String, String> attributes = attributes(html.substring(start + 5, end));
            String property = attributes.get("property");
            String name = attributes.get("name");
            String content = attributes.get("content");
            boolean match = (property != null && property.equalsIgnoreCase(wanted)) || (name != null && name.equalsIgnoreCase(wanted));
            if (match && content != null) {
                String image = absoluteImage(pageUrl, clip(content, URL_LIMIT));
                if (image != null) {
                    return image;
                }
            }
            from = end + 1;
        }
        return null;
    }

    private static String meta(String html, String key, int limit) {
        int from = 0;
        String wanted = key.toLowerCase(Locale.US);
        while (from < html.length()) {
            int start = indexOfIgnoreCase(html, "<meta", from);
            if (start < 0) {
                return null;
            }
            int end = tagEnd(html, start);
            if (end < 0) {
                return null;
            }
            Map<String, String> attributes = attributes(html.substring(start + 5, end));
            String property = attributes.get("property");
            String name = attributes.get("name");
            String content = attributes.get("content");
            if (content != null && ((property != null && property.equalsIgnoreCase(wanted)) || (name != null && name.equalsIgnoreCase(wanted)))) {
                String text = clip(content, limit);
                if (text != null) {
                    return text;
                }
            }
            from = end + 1;
        }
        return null;
    }

    private static String titleTag(String html) {
        int start = indexOfIgnoreCase(html, "<title", 0);
        if (start < 0) {
            return null;
        }
        int open = html.indexOf('>', start);
        int close = indexOfIgnoreCase(html, "</title>", open < 0 ? start : open);
        if (open < 0 || close < 0 || close < open) {
            return null;
        }
        return clip(html.substring(open + 1, close), TITLE_LIMIT);
    }

    private static Map<String, String> attributes(String tag) {
        Map<String, String> values = new LinkedHashMap<>();
        int i = 0;
        while (i < tag.length()) {
            while (i < tag.length() && Character.isWhitespace(tag.charAt(i))) {
                i++;
            }
            if (i >= tag.length() || tag.charAt(i) == '/' || tag.charAt(i) == '>') {
                break;
            }
            int nameStart = i;
            while (i < tag.length()) {
                char c = tag.charAt(i);
                if (c == '=' || Character.isWhitespace(c) || c == '/' || c == '>') {
                    break;
                }
                i++;
            }
            String name = tag.substring(nameStart, i).toLowerCase(Locale.US);
            while (i < tag.length() && Character.isWhitespace(tag.charAt(i))) {
                i++;
            }
            if (i >= tag.length() || tag.charAt(i) != '=') {
                if (!name.isEmpty()) {
                    values.put(name, "");
                }
                continue;
            }
            i++;
            while (i < tag.length() && Character.isWhitespace(tag.charAt(i))) {
                i++;
            }
            if (i >= tag.length()) {
                break;
            }
            char quote = tag.charAt(i);
            String value;
            if (quote == '"' || quote == '\'') {
                int valueStart = ++i;
                while (i < tag.length() && tag.charAt(i) != quote) {
                    i++;
                }
                value = tag.substring(valueStart, i);
                if (i < tag.length()) {
                    i++;
                }
            } else {
                int valueStart = i;
                while (i < tag.length() && !Character.isWhitespace(tag.charAt(i)) && tag.charAt(i) != '>') {
                    i++;
                }
                value = tag.substring(valueStart, i);
            }
            if (!name.isEmpty()) {
                values.put(name, value);
            }
        }
        return values;
    }

    private static int tagEnd(String html, int start) {
        boolean quoted = false;
        char quote = 0;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quoted) {
                if (c == quote) {
                    quoted = false;
                }
            } else if (c == '"' || c == '\'') {
                quoted = true;
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static String clip(String raw, int limit) {
        if (raw == null) {
            return null;
        }
        String decoded = decode(raw);
        StringBuilder collapsed = new StringBuilder(decoded.length());
        boolean space = false;
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (Character.isWhitespace(c) || c == '\u00a0') {
                space = collapsed.length() > 0;
                continue;
            }
            if (space) {
                collapsed.append(' ');
                space = false;
            }
            collapsed.append(c);
        }
        String value = trim(collapsed.toString());
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > limit) {
            value = value.substring(0, value.offsetByCodePoints(0, limit));
        }
        return value;
    }

    static String decode(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '&') {
                out.append(c);
                continue;
            }
            int semi = raw.indexOf(';', i + 1);
            if (semi < 0 || semi > i + 12) {
                out.append(c);
                continue;
            }
            String token = raw.substring(i + 1, semi);
            String replaced = entity(token);
            if (replaced == null) {
                out.append(c);
                continue;
            }
            out.append(replaced);
            i = semi;
        }
        return out.toString();
    }

    private static String entity(String token) {
        if ("amp".equals(token)) {
            return "&";
        }
        if ("lt".equals(token)) {
            return "<";
        }
        if ("gt".equals(token)) {
            return ">";
        }
        if ("quot".equals(token)) {
            return "\"";
        }
        if ("apos".equals(token)) {
            return "'";
        }
        if ("nbsp".equals(token)) {
            return " ";
        }
        try {
            int code;
            if (token.startsWith("#x") || token.startsWith("#X")) {
                code = Integer.parseInt(token.substring(2), 16);
            } else if (token.startsWith("#")) {
                code = Integer.parseInt(token.substring(1));
            } else {
                return null;
            }
            if (code <= 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                return null;
            }
            return new String(Character.toChars(code));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String knownCharset(String token) {
        if ("utf8".equals(token) || "utf-8".equals(token)) {
            return "UTF-8";
        }
        if ("gbk".equals(token) || "gb2312".equals(token) || "gb18030".equals(token)) {
            return token.toUpperCase(Locale.US);
        }
        if ("iso-8859-1".equals(token) || "latin1".equals(token)) {
            return "ISO-8859-1";
        }
        if ("windows-1252".equals(token)) {
            return "windows-1252";
        }
        if ("utf-16".equals(token) || "utf-16le".equals(token) || "utf-16be".equals(token)) {
            return token.toUpperCase(Locale.US);
        }
        return "UTF-8";
    }

    private static String charsetToken(String contentType) {
        if (contentType == null) {
            return null;
        }
        String lower = contentType.toLowerCase(Locale.US);
        int marker = lower.indexOf("charset");
        if (marker < 0) {
            return null;
        }
        int eq = lower.indexOf('=', marker);
        if (eq < 0) {
            return null;
        }
        String token = trim(lower.substring(eq + 1));
        int semi = token.indexOf(';');
        if (semi >= 0) {
            token = token.substring(0, semi);
        }
        token = trim(token).replace("\"", "").replace("'", "");
        return token.isEmpty() ? null : knownCharset(token);
    }

    private static byte[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] raw = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int value = 0;
            for (int c = 0; c < part.length(); c++) {
                char ch = part.charAt(c);
                if (ch < '0' || ch > '9') {
                    return null;
                }
                value = value * 10 + (ch - '0');
            }
            if (value > 255) {
                return null;
            }
            raw[i] = (byte) value;
        }
        return raw;
    }

    private static String text(TellomiJson json) {
        if (json == null || json.type != TellomiJson.Type.STRING) {
            return null;
        }
        return json.stringValue;
    }

    private static boolean glued(char c) {
        return c == '@' || c == '_' || (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean stop(char c) {
        if (Character.isWhitespace(c) || c == '<' || c == '>' || c == '"' || c == '\'' || c == '`') {
            return true;
        }
        return c == '。' || c == '，' || c == '、' || c == '！' || c == '？' || c == '）' || c == '」' || c == '》' || c == '】';
    }

    private static String trimTail(String raw) {
        int end = raw.length();
        while (end > 0 && tail(raw.charAt(end - 1))) {
            end--;
        }
        int open = 0;
        int close = 0;
        for (int i = 0; i < end; i++) {
            if (raw.charAt(i) == '(') {
                open++;
            } else if (raw.charAt(i) == ')') {
                close++;
            }
        }
        while (end > 0 && close > open && raw.charAt(end - 1) == ')') {
            end--;
            close--;
        }
        return raw.substring(0, end);
    }

    private static boolean tail(char c) {
        return c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?' || c == ']' || c == '}' || c == '\'' || c == '"' || c == '…';
    }

    private static int indexOfScheme(String text, String scheme, int from) {
        return indexOfIgnoreCase(text, scheme, from);
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        if (from < 0) {
            from = 0;
        }
        int last = text.length() - needle.length();
        for (int i = from; i <= last; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasSpace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }
}
