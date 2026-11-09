/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi short links. Generated links use tell.cc unless the server published
 * a non-Telegram me_url_prefix. Incoming t.me, telegram.me, and telegram.dog
 * links still parse.
 */

package org.telegram.messenger;

import java.util.Locale;

public final class TellomiLinks {

    public static final String DEFAULT_HOST = "tell.cc";
    private static final String[] LEGACY_HOSTS = new String[]{
            "t.me",
            "telegram.me",
            "telegram.dog"
    };

    private TellomiLinks() {
    }

    public static boolean isLegacyLinkHost(String host) {
        if (host == null) {
            return false;
        }
        for (int i = 0; i < LEGACY_HOSTS.length; i++) {
            if (LEGACY_HOSTS[i].equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLinkHost(String host, String linkHost) {
        if (host == null) {
            return false;
        }
        String primary = primaryHost(linkHost);
        return host.equalsIgnoreCase(primary) || isLegacyLinkHost(host);
    }

    /**
     * Force generated links onto {@code linkHost}. Telegram hosts from
     * help.getConfig are rewritten. Any other host is a Tellomi host and is kept.
     */
    public static String normalizeLinkPrefix(String prefix, String linkHost) {
        String primary = primaryHost(linkHost);
        if (prefix == null) {
            return primary;
        }
        String host = prefix.trim();
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.regionMatches(true, 0, "https://", 0, 8)) {
            host = host.substring(8);
        } else if (host.regionMatches(true, 0, "http://", 0, 7)) {
            host = host.substring(7);
        }
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int at = host.indexOf('@');
        if (at >= 0) {
            host = host.substring(at + 1);
        }
        if (host.isEmpty() || isLegacyLinkHost(host) || host.equalsIgnoreCase(primary)) {
            return primary;
        }
        return host;
    }

    public static String normalizeLinkPrefix(String prefix) {
        return normalizeLinkPrefix(prefix, DEFAULT_HOST);
    }

    /** https://{normalized-host}/{path}. Path may be empty or start with /. */
    public static String https(String prefix, String linkHost, String path) {
        String host = normalizeLinkPrefix(prefix, linkHost);
        if (path == null || path.isEmpty()) {
            return "https://" + host + "/";
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return "https://" + host + "/" + path;
    }

    public static String https(String prefix, String path) {
        return https(prefix, DEFAULT_HOST, path);
    }

    /** Host shown in the UI, without a scheme. */
    public static String displayHost(String prefix, String linkHost) {
        return normalizeLinkPrefix(prefix, linkHost);
    }

    /**
     * Remainder after http(s)://host/ or a bare host/. Host may be the Tellomi
     * host or a legacy Telegram short-link host. Null when the URL is not one of those.
     */
    public static String pathAfterKnownHost(String url, String linkHost) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        String[] hosts = knownHosts(linkHost);
        for (int i = 0; i < hosts.length; i++) {
            String host = hosts[i].toLowerCase(Locale.US);
            String https = "https://" + host + "/";
            String http = "http://" + host + "/";
            String bare = host + "/";
            if (lower.startsWith(https)) {
                return trimmed.substring(https.length());
            }
            if (lower.startsWith(http)) {
                return trimmed.substring(http.length());
            }
            if (lower.startsWith(bare)) {
                return trimmed.substring(bare.length());
            }
        }
        return null;
    }

    /** Title for an invite link: tell.cc/+code, accepting legacy https://t.me/+code. */
    public static String invitePlusTitle(String prefix, String linkHost, String inviteLink) {
        String path = pathAfterKnownHost(inviteLink, linkHost);
        if (path != null && path.startsWith("+")) {
            return displayHost(prefix, linkHost) + "/" + path;
        }
        return null;
    }

    /** Hash shown for a joinchat invite, or null. */
    public static String inviteJoinchatTitle(String linkHost, String inviteLink) {
        String path = pathAfterKnownHost(inviteLink, linkHost);
        if (path != null && path.startsWith("joinchat/")) {
            return path.substring("joinchat/".length());
        }
        return null;
    }

    public static boolean isPublicChatInvite(String link, String linkHost) {
        String path = pathAfterKnownHost(link, linkHost);
        return "+PublicChat".equals(path);
    }

    /** True when a text-url entity is just https://host/username for an @username mention. */
    public static boolean isMentionLink(String url, String visibleText, String linkHost) {
        if (url == null || visibleText == null || !visibleText.startsWith("@") || visibleText.length() < 2) {
            return false;
        }
        String path = pathAfterKnownHost(url, linkHost);
        return path != null && path.equals(visibleText.substring(1));
    }

    public static boolean containsInstantView(String lowerUrl, String linkHost) {
        if (lowerUrl == null || lowerUrl.isEmpty()) {
            return false;
        }
        String[] hosts = knownHosts(linkHost);
        for (int i = 0; i < hosts.length; i++) {
            if (lowerUrl.contains(hosts[i].toLowerCase(Locale.US) + "/iv")) {
                return true;
            }
        }
        return false;
    }

    /** https://tell.cc/m/slug and the legacy https://t.me/m/slug form. */
    public static boolean isBusinessMessageLink(String link, String slug, String linkHost) {
        if (link == null || slug == null) {
            return false;
        }
        String[] hosts = knownHosts(linkHost);
        for (int i = 0; i < hosts.length; i++) {
            if (link.equalsIgnoreCase("https://" + hosts[i] + "/m/" + slug)
                    || link.equalsIgnoreCase("http://" + hosts[i] + "/m/" + slug)) {
                return true;
            }
        }
        return false;
    }

    private static String primaryHost(String linkHost) {
        if (linkHost == null || linkHost.isEmpty()) {
            return DEFAULT_HOST;
        }
        return linkHost;
    }

    private static String[] knownHosts(String linkHost) {
        String primary = primaryHost(linkHost);
        String[] hosts = new String[LEGACY_HOSTS.length + 1];
        hosts[0] = primary;
        System.arraycopy(LEGACY_HOSTS, 0, hosts, 1, LEGACY_HOSTS.length);
        return hosts;
    }
}
