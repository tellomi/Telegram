package org.telegram.ui.tellomi.feed;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Which chat links the phone may fetch itself.
 * Internal hosts stay on {@code messages.getWebPage} / {@code sendMessage}.
 * The rules match {@code docs/api/client-link-preview.md} on teamgram-server.
 */
public final class TellomiChatLink {

    public static final String UNVERIFIED = "unverified";
    public static final int TITLE_RUNES = 128;
    public static final int DESCRIPTION_RUNES = 400;
    public static final int SITE_RUNES = 64;
    public static final int URL_BYTES = 2048;
    /** flags.3 title, flags.4 description, flags.5 site name, flags.6 photo. */
    public static final int FLAG_TITLE = 1 << 3;
    public static final int FLAG_DESCRIPTION = 1 << 4;
    public static final int FLAG_SITE = 1 << 5;
    public static final int FLAG_PHOTO = 1 << 6;

    private TellomiChatLink() {
    }

    /** {@code tell.cc}, {@code www.tell.cc}, legacy Telegram hosts, and the configured link host. */
    public static boolean internalHost(String host, String linkHost) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        String name = host.toLowerCase(Locale.US);
        while (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if ("tell.cc".equals(name) || "www.tell.cc".equals(name)
                || "t.me".equals(name) || "telegram.me".equals(name) || "telegram.dog".equals(name)) {
            return true;
        }
        return linkHost != null && !linkHost.isEmpty() && name.equals(linkHost.toLowerCase(Locale.US));
    }

    /**
     * True when the sending phone should read Open Graph.
     * A private, loopback, or link-local target is not fetchable.
     */
    public static boolean clientFetch(String raw, String linkHost) {
        String url = TellomiDiscoverLink.httpUrl(raw);
        if (url == null || !TellomiDiscoverLink.fetchable(url)) {
            return false;
        }
        if (url.getBytes(StandardCharsets.UTF_8).length > URL_BYTES) {
            return false;
        }
        return !internalHost(TellomiDiscoverLink.host(url), linkHost);
    }

    /** The bubble must not print the server's unverified marker. */
    public static boolean hideAuthor(String author) {
        if (author == null) {
            return false;
        }
        return UNVERIFIED.equals(author.trim().toLowerCase(Locale.US));
    }

    public static String clampRunes(String value, int limit) {
        if (value == null || limit <= 0) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, limit));
    }

    /** Wire flags for a client card. Empty strings are left unset, matching the server. */
    public static int extensionFlags(String title, String description, String site, boolean photo) {
        int flags = 0;
        if (title != null && !title.isEmpty()) {
            flags |= FLAG_TITLE;
        }
        if (description != null && !description.isEmpty()) {
            flags |= FLAG_DESCRIPTION;
        }
        if (site != null && !site.isEmpty()) {
            flags |= FLAG_SITE;
        }
        if (photo) {
            flags |= FLAG_PHOTO;
        }
        return flags;
    }
}
