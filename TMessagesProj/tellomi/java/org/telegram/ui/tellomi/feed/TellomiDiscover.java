package org.telegram.ui.tellomi.feed;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discover feed client (M3 / C-M3b), aligned with the iOS TellomiDiscover module.
 *
 * {@code tellomi_discover_feed_enabled} defaults off. While it is off, or the
 * account is logged out, nothing here builds a credential RPC or an engine URL.
 * A city is a name, never a coordinate. Visibility is the Stories four-way choice.
 */
public final class TellomiDiscover {

    public static final String ENABLED_KEY = "tellomi_discover_feed_enabled";
    public static final String BASE_URL_KEY = "tellomi_discover_feed_base_url";
    public static final String DEFAULT_BASE_URL = "https://feed.tellomi.cn";
    public static final long CREDENTIAL_REFRESH_MARGIN = 120;
    public static final long AUDIENCE_REFRESH_MARGIN = 60;
    public static final int POST_TEXT_LIMIT = 500;

    private static final Pattern NONCE = Pattern.compile("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$");
    private static final Pattern POST_ID = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final String MEMBER_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private TellomiDiscover() {
    }

    public enum ClientError {
        SIGNED_OUT,
        DISABLED,
        AUDIENCE_INVALID,
        USER_INVALID,
        USER_BLOCKED,
        INVALID_CITY,
        NOT_PUBLISHABLE,
        HIDDEN,
        UNAVAILABLE,
        UNAUTHORIZED,
        NOT_FOUND,
        UNREADABLE
    }

    public static final class Field {
        public enum Kind { MISSING, BOOL, NUMBER, STRING }

        public final Kind kind;
        public final boolean boolValue;
        public final double numberValue;
        public final String stringValue;

        private Field(Kind kind, boolean boolValue, double numberValue, String stringValue) {
            this.kind = kind;
            this.boolValue = boolValue;
            this.numberValue = numberValue;
            this.stringValue = stringValue;
        }

        public static Field missing() {
            return new Field(Kind.MISSING, false, 0, null);
        }

        public static Field bool(boolean value) {
            return new Field(Kind.BOOL, value, 0, null);
        }

        public static Field number(double value) {
            return new Field(Kind.NUMBER, false, value, null);
        }

        public static Field string(String value) {
            return new Field(Kind.STRING, false, 0, value);
        }
    }

    public static boolean enabled(Field field) {
        if (field == null || field.kind == Field.Kind.MISSING) {
            return false;
        }
        if (field.kind == Field.Kind.BOOL) {
            return field.boolValue;
        }
        if (field.kind == Field.Kind.NUMBER) {
            return field.numberValue != 0;
        }
        String value = field.stringValue == null ? "" : field.stringValue.toLowerCase(Locale.US);
        return "1".equals(value) || "true".equals(value) || "yes".equals(value) || "enabled".equals(value) || "on".equals(value);
    }

    public static final class Configuration {
        public final boolean enabled;
        public final String baseUrl;

        public Configuration(boolean enabled, String baseUrl) {
            this.enabled = enabled;
            this.baseUrl = baseUrl;
        }

        /** A missing URL uses feed.tellomi.cn only when the flag is on. */
        public static Configuration resolve(Field enabledField, Field baseUrlField) {
            if (!enabled(enabledField)) {
                return new Configuration(false, null);
            }
            String raw = null;
            if (baseUrlField != null && baseUrlField.kind == Field.Kind.STRING) {
                String trimmed = trim(baseUrlField.stringValue);
                raw = trimmed.isEmpty() ? null : trimmed;
            }
            String base = baseUrl(raw);
            if (base == null) {
                return new Configuration(false, null);
            }
            return new Configuration(true, base);
        }
    }

    public static String baseUrl(String string) {
        String raw = string == null ? "" : trim(string);
        String value = raw.isEmpty() ? DEFAULT_BASE_URL : raw;
        java.net.URI uri;
        try {
            uri = new java.net.URI(value);
        } catch (java.net.URISyntaxException ignored) {
            return null;
        }
        if (uri.getScheme() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.US))) {
            return null;
        }
        if (uri.getUserInfo() != null) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return null;
        }
        String path = uri.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            return null;
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            return null;
        }
        if (uri.getPort() > 0) {
            return "https://" + host + ":" + uri.getPort();
        }
        return "https://" + host;
    }

    public enum FeedKind {
        RECOMMEND("recommend"),
        FOLLOWING("following"),
        NEARBY("nearby");

        public final String raw;

        FeedKind(String raw) {
            this.raw = raw;
        }

        public static FeedKind fromConfig(String name) {
            if ("recommend".equals(name)) {
                return RECOMMEND;
            }
            if ("following".equals(name)) {
                return FOLLOWING;
            }
            if ("nearby".equals(name)) {
                return NEARBY;
            }
            return null;
        }
    }

    public enum Surface {
        LEGACY_EMPTY,
        FEED,
        DETAIL,
        CHOOSE_CITY
    }

    public static final class Chrome {
        public final Surface surface;
        public final boolean postUnavailable;
        public final String detailId;
        public final boolean showsCompose;
        public final boolean loadsFeed;
        public final boolean loadsPost;
        public final boolean failure;

        private Chrome(Surface surface, boolean postUnavailable, String detailId, boolean showsCompose, boolean loadsFeed, boolean loadsPost, boolean failure) {
            this.surface = surface;
            this.postUnavailable = postUnavailable;
            this.detailId = detailId;
            this.showsCompose = showsCompose;
            this.loadsFeed = loadsFeed;
            this.loadsPost = loadsPost;
            this.failure = failure;
        }

        public boolean issuesNetwork() {
            return loadsFeed || loadsPost;
        }
    }

    public static Chrome chrome(boolean loggedIn, boolean enabled, FeedKind kind, String openedPostId, boolean failed, String city) {
        String postId = postId(openedPostId);
        if (!loggedIn || !enabled) {
            boolean unavailable = postId != null || (openedPostId != null && !openedPostId.isEmpty());
            return new Chrome(Surface.LEGACY_EMPTY, unavailable, null, false, false, false, false);
        }
        if (postId != null) {
            return new Chrome(Surface.DETAIL, false, postId, false, false, true, failed);
        }
        if (kind == FeedKind.NEARBY && TellomiDiscoverCity.knownCode(city) == null) {
            return new Chrome(Surface.CHOOSE_CITY, false, null, true, false, false, failed);
        }
        return new Chrome(Surface.FEED, false, null, true, true, false, failed);
    }

    public static final class Credential {
        public final String token;
        public final long expires;
        public final String region;

        public Credential(String token, long expires, String region) {
            this.token = token;
            this.expires = expires;
            this.region = region;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Credential)) {
                return false;
            }
            Credential that = (Credential) other;
            return expires == that.expires && token.equals(that.token) && region.equals(that.region);
        }

        @Override
        public int hashCode() {
            return token.hashCode() ^ region.hashCode() ^ (int) expires;
        }
    }

    public static boolean credentialNeedsRefresh(long expires, long now) {
        return now >= expires - CREDENTIAL_REFRESH_MARGIN;
    }

    public enum StoryBase {
        EVERYONE,
        CONTACTS,
        CLOSE_FRIENDS,
        NOBODY
    }

    public enum Visibility {
        EVERYONE("public"),
        CONTACTS("contacts"),
        CLOSE_FRIENDS("close_friends"),
        INCLUDE("include"),
        EXCLUDE("exclude");

        public final String raw;

        Visibility(String raw) {
            this.raw = raw;
        }
    }

    public static final class AudienceList {
        public static final String TOKEN_TYPE = "tellomi-feed-audience";
        public static final int MEMBER_LIMIT = 500;
        public static final long LIFETIME = 10 * 60;

        public final String kind;
        public final List<String> members;

        public AudienceList(String kind, List<String> members) {
            this.kind = kind;
            this.members = members;
        }
    }

    public static AudienceList audienceList(String kind, List<String> members) {
        if (!Visibility.INCLUDE.raw.equals(kind) && !Visibility.EXCLUDE.raw.equals(kind)) {
            return null;
        }
        if (members == null || members.isEmpty() || members.size() > AudienceList.MEMBER_LIMIT) {
            return null;
        }
        for (int i = 0; i < members.size(); i++) {
            String value = members.get(i);
            if (value == null || value.isEmpty() || value.length() > 128 || !memberChars(value)) {
                return null;
            }
        }
        return new AudienceList(kind, members);
    }

    public static boolean audienceExpiryValid(long expires, long now) {
        return expires > now && expires - now <= AudienceList.LIFETIME;
    }

    public static boolean audienceTokenNeedsRefresh(long expires, long now) {
        return now >= expires - AUDIENCE_REFRESH_MARGIN;
    }

    public static final class AudienceToken {
        public final String token;
        public final long expires;

        public AudienceToken(String token, long expires) {
            this.token = token;
            this.expires = expires;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AudienceToken)) {
                return false;
            }
            AudienceToken that = (AudienceToken) other;
            return expires == that.expires && token.equals(that.token);
        }

        @Override
        public int hashCode() {
            return token.hashCode() ^ (int) expires;
        }
    }

    public static long[] audienceUserIds(String kind, long[] userIds, long selfUserId) {
        if (!Visibility.INCLUDE.raw.equals(kind) && !Visibility.EXCLUDE.raw.equals(kind)) {
            return null;
        }
        if (userIds == null || userIds.length == 0 || userIds.length > AudienceList.MEMBER_LIMIT) {
            return null;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (int i = 0; i < userIds.length; i++) {
            long id = userIds[i];
            if (id <= 0 || id == selfUserId || !seen.add(id)) {
                return null;
            }
        }
        return Arrays.copyOf(userIds, userIds.length);
    }

    public static final class AudienceChoice {
        public final Visibility visibility;
        public final boolean publishable;
        public final String reason;
        public final long[] userIds;

        public AudienceChoice(Visibility visibility, boolean publishable, String reason, long[] userIds) {
            this.visibility = visibility;
            this.publishable = publishable;
            this.reason = reason;
            this.userIds = userIds;
        }
    }

    public static AudienceChoice audience(StoryBase base, long[] includePeerIds, long selfUserId) {
        long[] ids = includePeerIds == null ? new long[0] : includePeerIds;
        if (base == StoryBase.EVERYONE) {
            if (ids.length == 0) {
                return new AudienceChoice(Visibility.EVERYONE, true, null, new long[0]);
            }
            return limited(Visibility.EXCLUDE, ids, selfUserId);
        }
        if (base == StoryBase.CONTACTS) {
            if (ids.length == 0) {
                return new AudienceChoice(Visibility.CONTACTS, true, null, new long[0]);
            }
            return limited(Visibility.EXCLUDE, ids, selfUserId);
        }
        if (base == StoryBase.CLOSE_FRIENDS) {
            return new AudienceChoice(Visibility.CLOSE_FRIENDS, true, null, new long[0]);
        }
        if (ids.length == 0) {
            return null;
        }
        return limited(Visibility.INCLUDE, ids, selfUserId);
    }

    private static AudienceChoice limited(Visibility visibility, long[] userIds, long selfUserId) {
        long[] ids = audienceUserIds(visibility.raw, userIds, selfUserId);
        if (ids != null) {
            return new AudienceChoice(visibility, true, null, ids);
        }
        return new AudienceChoice(visibility, false, "audience_invalid", new long[0]);
    }

    public enum LocationGate {
        NOT_DETERMINED,
        ALLOWED,
        DENIED
    }

    public static final class NearbyGate {
        public final boolean feed;
        public final String city;
        public final boolean requestLocation;

        private NearbyGate(boolean feed, String city, boolean requestLocation) {
            this.feed = feed;
            this.city = city;
            this.requestLocation = requestLocation;
        }

        static NearbyGate feed(String city) {
            return new NearbyGate(true, city, false);
        }

        static NearbyGate choose(boolean requestLocation) {
            return new NearbyGate(false, null, requestLocation);
        }
    }

    /** First entry to the nearby tab is the only time location is requested. */
    public static NearbyGate nearbyGate(String city, LocationGate location, boolean asked) {
        String code = TellomiDiscoverCity.knownCode(city);
        if (code != null) {
            return NearbyGate.feed(code);
        }
        if (location == LocationGate.DENIED) {
            return NearbyGate.choose(false);
        }
        return NearbyGate.choose(!asked);
    }

    public static String postId(String raw) {
        if (raw == null) {
            return null;
        }
        String identifier = trim(raw);
        if (identifier.isEmpty() || identifier.length() > 128) {
            return null;
        }
        if (".".equals(identifier) || "..".equals(identifier) || identifier.contains("..")) {
            return null;
        }
        if (!POST_ID.matcher(identifier).matches()) {
            return null;
        }
        return identifier;
    }

    public static final class Operation {
        public enum Kind { CAPABILITIES, STATUS, ENGINE_CONFIG, FEED, NEARBY, CARDS, PUBLISH }

        public final Kind kind;
        public final FeedKind feedKind;
        public final String cursor;
        public final boolean personalized;
        public final String city;
        public final List<String> ids;
        public final String text;
        public final Visibility visibility;
        public final String audienceToken;
        public final String clientNonce;
        public final int textLimit;

        private Operation(Kind kind, FeedKind feedKind, String cursor, boolean personalized, String city, List<String> ids, String text, Visibility visibility, String audienceToken, String clientNonce, int textLimit) {
            this.kind = kind;
            this.feedKind = feedKind;
            this.cursor = cursor;
            this.personalized = personalized;
            this.city = city;
            this.ids = ids;
            this.text = text;
            this.visibility = visibility;
            this.audienceToken = audienceToken;
            this.clientNonce = clientNonce;
            this.textLimit = textLimit;
        }

        public static Operation capabilities() {
            return new Operation(Kind.CAPABILITIES, null, null, false, null, null, null, null, null, null, 0);
        }

        public static Operation status() {
            return new Operation(Kind.STATUS, null, null, false, null, null, null, null, null, null, 0);
        }

        public static Operation engineConfig() {
            return new Operation(Kind.ENGINE_CONFIG, null, null, false, null, null, null, null, null, null, 0);
        }

        public static Operation feed(FeedKind kind, String cursor, boolean personalized) {
            return new Operation(Kind.FEED, kind, cursor, personalized, null, null, null, null, null, null, 0);
        }

        public static Operation nearby(String city, String cursor) {
            return new Operation(Kind.NEARBY, null, cursor, false, city, null, null, null, null, null, 0);
        }

        public static Operation cards(List<String> ids) {
            return new Operation(Kind.CARDS, null, null, false, null, ids, null, null, null, null, 0);
        }

        public static Operation publish(String text, Visibility visibility, String audienceToken, String clientNonce, int textLimit, String city) {
            return new Operation(Kind.PUBLISH, null, null, false, city, null, text, visibility, audienceToken, clientNonce, textLimit);
        }
    }

    public static final class QueryItem {
        public final String name;
        public final String value;

        public QueryItem(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof QueryItem)) {
                return false;
            }
            QueryItem that = (QueryItem) other;
            return name.equals(that.name) && value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ value.hashCode();
        }
    }

    public static final class HttpCall {
        public final String method;
        public final String url;
        public final byte[] body;
        public final String token;
        public final boolean authorizes;
        public final String contentType;

        public HttpCall(String method, String url, byte[] body, String token, boolean authorizes, String contentType) {
            this.method = method;
            this.url = url;
            this.body = body;
            this.token = token;
            this.authorizes = authorizes;
            this.contentType = contentType;
        }

        public String path() {
            int scheme = url.indexOf("://");
            int start = scheme < 0 ? 0 : url.indexOf('/', scheme + 3);
            if (start < 0) {
                return "";
            }
            int query = url.indexOf('?', start);
            return query < 0 ? url.substring(start) : url.substring(start, query);
        }

        public String rawQuery() {
            int query = url.indexOf('?');
            if (query < 0 || query == url.length() - 1) {
                return null;
            }
            return url.substring(query + 1);
        }

        public List<QueryItem> queryItems() {
            String raw = rawQuery();
            if (raw == null) {
                return null;
            }
            String[] parts = raw.split("&");
            List<QueryItem> items = new ArrayList<>();
            for (int i = 0; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                String name = eq < 0 ? parts[i] : parts[i].substring(0, eq);
                String value = eq < 0 ? "" : parts[i].substring(eq + 1);
                try {
                    items.add(new QueryItem(URLDecoder.decode(name, "UTF-8"), URLDecoder.decode(value, "UTF-8")));
                } catch (java.io.UnsupportedEncodingException ignored) {
                    return null;
                }
            }
            return items;
        }
    }

    public static boolean needsCredential(Operation operation) {
        return operation.kind != Operation.Kind.CAPABILITIES;
    }

    public static HttpCall httpCall(String base, Operation operation, String token) {
        String trimmed = token == null ? "" : trim(token);
        if (needsCredential(operation) && trimmed.isEmpty()) {
            return null;
        }
        if (operation.kind == Operation.Kind.CAPABILITIES) {
            String url = url(base, "/v1/capabilities", null);
            return url == null ? null : new HttpCall("GET", url, null, "", false, null);
        }
        if (operation.kind == Operation.Kind.STATUS) {
            String url = url(base, "/v1/feed/status", null);
            return url == null ? null : new HttpCall("GET", url, null, trimmed, true, null);
        }
        if (operation.kind == Operation.Kind.ENGINE_CONFIG) {
            String url = url(base, "/v1/feed/config", null);
            return url == null ? null : new HttpCall("GET", url, null, trimmed, true, null);
        }
        if (operation.kind == Operation.Kind.FEED) {
            String url = feedUrl(base, operation.feedKind, operation.cursor, operation.personalized);
            return url == null ? null : new HttpCall("GET", url, null, trimmed, true, null);
        }
        if (operation.kind == Operation.Kind.CARDS) {
            byte[] body = cardsBody(operation.ids);
            if (body == null) {
                return null;
            }
            String url = url(base, "/v1/feed/cards", null);
            return url == null ? null : new HttpCall("POST", url, body, trimmed, true, "application/json");
        }
        if (operation.kind == Operation.Kind.NEARBY) {
            String url = nearbyUrl(base, operation.city, operation.cursor);
            return url == null ? null : new HttpCall("GET", url, null, trimmed, true, null);
        }
        byte[] body = publishBody(operation.text, operation.visibility, operation.audienceToken, operation.clientNonce, operation.textLimit, operation.city);
        if (body == null) {
            return null;
        }
        String url = url(base, "/v1/feed/posts", null);
        return url == null ? null : new HttpCall("POST", url, body, trimmed, true, "application/json");
    }

    public static String nearbyUrl(String base, String city, String cursor) {
        String code = TellomiDiscoverCity.knownCode(city);
        if (code == null) {
            return null;
        }
        List<QueryItem> query = new ArrayList<>();
        query.add(new QueryItem("city", code));
        if (cursor != null && !cursor.isEmpty()) {
            query.add(new QueryItem("cursor", cursor));
        }
        return url(base, "/v1/feed/nearby", query);
    }

    public static String feedUrl(String base, FeedKind kind, String cursor, boolean personalized) {
        List<QueryItem> query = new ArrayList<>();
        if (kind == FeedKind.RECOMMEND) {
            if (!personalized) {
                query.add(new QueryItem("personalized", "false"));
            }
        } else if (kind == FeedKind.FOLLOWING) {
            if (cursor != null && !cursor.isEmpty()) {
                query.add(new QueryItem("cursor", cursor));
            }
        } else {
            return null;
        }
        return url(base, "/v1/feed/" + kind.raw, query);
    }

    public static byte[] cardsBody(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty() || identifiers.size() > 100) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < identifiers.size(); i++) {
            String id = postId(identifiers.get(i));
            if (id == null) {
                return null;
            }
            ids.add(id);
        }
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("ids", ids);
        return TellomiJson.write(object);
    }

    public static byte[] publishBody(String text, Visibility visibility, String audienceToken, String clientNonce, int textLimit, String city) {
        String body = text == null ? "" : trim(text);
        int limit = textLimit > 0 ? textLimit : POST_TEXT_LIMIT;
        if (body.isEmpty() || body.codePointCount(0, body.length()) > limit) {
            return null;
        }
        if (!clientNonce(clientNonce)) {
            return null;
        }
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("kind", "post");
        object.put("text", body);
        object.put("visibility", visibility.raw);
        object.put("client_nonce", clientNonce);
        if (visibility == Visibility.INCLUDE || visibility == Visibility.EXCLUDE) {
            String token = audienceToken == null ? "" : trim(audienceToken);
            if (token.isEmpty()) {
                return null;
            }
            Map<String, Object> audience = new LinkedHashMap<>();
            audience.put("audience_token", token);
            object.put("audience", audience);
        }
        if (city != null) {
            String trimmed = trim(city);
            if (!trimmed.isEmpty()) {
                String code = TellomiDiscoverCity.knownCode(trimmed);
                if (code == null) {
                    return null;
                }
                object.put("city", code);
            }
        }
        return TellomiJson.write(object);
    }

    public static final class PublishStep {
        public enum Kind { READY, NEEDS_AUDIENCE_TOKEN, REJECTED }

        public final Kind kind;
        public final byte[] body;
        public final String tokenKind;
        public final long[] userIds;
        public final ClientError error;

        private PublishStep(Kind kind, byte[] body, String tokenKind, long[] userIds, ClientError error) {
            this.kind = kind;
            this.body = body;
            this.tokenKind = tokenKind;
            this.userIds = userIds;
            this.error = error;
        }
    }

    public static PublishStep publishStep(String text, Visibility visibility, long[] userIds, long selfUserId, String clientNonce, int textLimit, AudienceToken token, long now, String city) {
        if (city != null && !trim(city).isEmpty() && TellomiDiscoverCity.knownCode(city) == null) {
            return new PublishStep(PublishStep.Kind.REJECTED, null, null, null, ClientError.INVALID_CITY);
        }
        if (visibility == Visibility.INCLUDE || visibility == Visibility.EXCLUDE) {
            long[] ids = audienceUserIds(visibility.raw, userIds, selfUserId);
            if (ids == null) {
                return new PublishStep(PublishStep.Kind.REJECTED, null, null, null, ClientError.AUDIENCE_INVALID);
            }
            if (token != null && token.token != null && !token.token.isEmpty() && !audienceTokenNeedsRefresh(token.expires, now)) {
                byte[] body = publishBody(text, visibility, token.token, clientNonce, textLimit, city);
                if (body == null) {
                    return new PublishStep(PublishStep.Kind.REJECTED, null, null, null, ClientError.NOT_PUBLISHABLE);
                }
                return new PublishStep(PublishStep.Kind.READY, body, null, null, null);
            }
            return new PublishStep(PublishStep.Kind.NEEDS_AUDIENCE_TOKEN, null, visibility.raw, ids, null);
        }
        byte[] body = publishBody(text, visibility, null, clientNonce, textLimit, city);
        if (body == null) {
            return new PublishStep(PublishStep.Kind.REJECTED, null, null, null, ClientError.NOT_PUBLISHABLE);
        }
        return new PublishStep(PublishStep.Kind.READY, body, null, null, null);
    }

    public static ClientError clientError(String description, int code) {
        String text = description == null ? "" : description;
        if (code == 401 || text.contains("AUTH_KEY_UNREGISTERED")) {
            return ClientError.SIGNED_OUT;
        }
        if (text.contains("AUDIENCE_INVALID")) {
            return ClientError.AUDIENCE_INVALID;
        }
        if (text.contains("USER_IS_BLOCKED")) {
            return ClientError.USER_BLOCKED;
        }
        if (text.contains("USER_ID_INVALID")) {
            return ClientError.USER_INVALID;
        }
        if (text.contains("DISCOVER_DISABLED")) {
            return ClientError.DISABLED;
        }
        return ClientError.UNAVAILABLE;
    }

    public static boolean clientNonce(String value) {
        return value != null && NONCE.matcher(value).matches();
    }

    public static final class Capabilities {
        public final int version;
        public final List<String> features;

        public Capabilities(int version, List<String> features) {
            this.version = version;
            this.features = features;
        }
    }

    public static final class EngineConfig {
        public final boolean enabled;
        public final String region;
        public final List<FeedKind> tabs;
        public final String recommendMode;
        public final boolean personalizationToggleVisible;
        public final int postTextChars;

        public EngineConfig(boolean enabled, String region, List<FeedKind> tabs, String recommendMode, boolean personalizationToggleVisible, int postTextChars) {
            this.enabled = enabled;
            this.region = region;
            this.tabs = tabs;
            this.recommendMode = recommendMode;
            this.personalizationToggleVisible = personalizationToggleVisible;
            this.postTextChars = postTextChars;
        }
    }

    public static final class FeedItem {
        public final String id;
        public final String reason;
        public final String repostedBy;

        public FeedItem(String id, String reason, String repostedBy) {
            this.id = id;
            this.reason = reason;
            this.repostedBy = repostedBy;
        }
    }

    public static final class FeedPage {
        public final List<FeedItem> items;
        public final String cursor;

        public FeedPage(List<FeedItem> items, String cursor) {
            this.items = items;
            this.cursor = cursor;
        }
    }

    public static final class Card {
        public final String id;
        public final String state;
        public final Integer version;
        public final String text;
        public final String authorName;
        public final String authorHandle;
        public final String tellomiNumber;
        public final String authorId;
        public final String badge;
        public final boolean aiGenerated;
        public final boolean edited;
        public final boolean shareable;
        public final int likeCount;
        public final int commentCount;
        public final int repostCount;
        public final int imageCount;
        public final String quoteState;
        public final String quoteText;
        public final String ipRegionLabel;
        public final String city;

        public Card(String id, String state, Integer version, String text, String authorName, String authorHandle, String tellomiNumber, String authorId, String badge, boolean aiGenerated, boolean edited, boolean shareable, int likeCount, int commentCount, int repostCount, int imageCount, String quoteState, String quoteText, String ipRegionLabel, String city) {
            this.id = id;
            this.state = state;
            this.version = version;
            this.text = text;
            this.authorName = authorName;
            this.authorHandle = authorHandle;
            this.tellomiNumber = tellomiNumber;
            this.authorId = authorId;
            this.badge = badge;
            this.aiGenerated = aiGenerated;
            this.edited = edited;
            this.shareable = shareable;
            this.likeCount = likeCount;
            this.commentCount = commentCount;
            this.repostCount = repostCount;
            this.imageCount = imageCount;
            this.quoteState = quoteState;
            this.quoteText = quoteText;
            this.ipRegionLabel = ipRegionLabel;
            this.city = city;
        }
    }

    public static final class CreatedPost {
        public final String id;
        public final String state;
        public final int version;

        public CreatedPost(String id, String state, int version) {
            this.id = id;
            this.state = state;
            this.version = version;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CreatedPost)) {
                return false;
            }
            CreatedPost that = (CreatedPost) other;
            return version == that.version && id.equals(that.id) && state.equals(that.state);
        }

        @Override
        public int hashCode() {
            return id.hashCode() ^ state.hashCode() ^ version;
        }
    }

    public static final class Post {
        public final String id;
        public final String text;
        public final String authorName;
        public final String authorHandle;
        public final String tellomiNumber;
        public final boolean unavailable;
        public final boolean aiGenerated;
        public final String badge;
        public final int likeCount;
        public final int commentCount;
        public final int repostCount;
        public final int imageCount;
        public final String quoteState;
        public final String quoteText;
        public final String ipRegionLabel;
        public final String city;

        public Post(String id, String text, String authorName, String authorHandle, String tellomiNumber, boolean unavailable, boolean aiGenerated, String badge, int likeCount, int commentCount, int repostCount, int imageCount, String quoteState, String quoteText, String ipRegionLabel, String city) {
            this.id = id;
            this.text = text;
            this.authorName = authorName;
            this.authorHandle = authorHandle;
            this.tellomiNumber = tellomiNumber;
            this.unavailable = unavailable;
            this.aiGenerated = aiGenerated;
            this.badge = badge;
            this.likeCount = likeCount;
            this.commentCount = commentCount;
            this.repostCount = repostCount;
            this.imageCount = imageCount;
            this.quoteState = quoteState;
            this.quoteText = quoteText;
            this.ipRegionLabel = ipRegionLabel;
            this.city = city;
        }
    }

    public static final class HttpResult {
        public final int status;
        public final byte[] body;
        public final ClientError error;

        public HttpResult(int status, byte[] body) {
            this.status = status;
            this.body = body == null ? new byte[0] : body;
            this.error = null;
        }

        public HttpResult(ClientError error) {
            this.status = 0;
            this.body = new byte[0];
            this.error = error;
        }
    }

    public static final class Decoded {
        public enum Kind { CAPABILITIES, STATUS, ENGINE_CONFIG, FEED, CARDS, CREATED }

        public final Kind kind;
        public final Capabilities capabilities;
        public final boolean status;
        public final EngineConfig engineConfig;
        public final FeedPage feed;
        public final List<Card> cards;
        public final CreatedPost created;

        private Decoded(Kind kind, Capabilities capabilities, boolean status, EngineConfig engineConfig, FeedPage feed, List<Card> cards, CreatedPost created) {
            this.kind = kind;
            this.capabilities = capabilities;
            this.status = status;
            this.engineConfig = engineConfig;
            this.feed = feed;
            this.cards = cards;
            this.created = created;
        }

        static Decoded capabilities(Capabilities value) {
            return new Decoded(Kind.CAPABILITIES, value, false, null, null, null, null);
        }

        static Decoded status(boolean value) {
            return new Decoded(Kind.STATUS, null, value, null, null, null, null);
        }

        static Decoded engineConfig(EngineConfig value) {
            return new Decoded(Kind.ENGINE_CONFIG, null, false, value, null, null, null);
        }

        static Decoded feed(FeedPage value) {
            return new Decoded(Kind.FEED, null, false, null, value, null, null);
        }

        static Decoded cards(List<Card> value) {
            return new Decoded(Kind.CARDS, null, false, null, null, value, null);
        }

        static Decoded created(CreatedPost value) {
            return new Decoded(Kind.CREATED, null, false, null, null, null, value);
        }
    }

    public static final class Outcome {
        public final Decoded value;
        public final ClientError error;

        private Outcome(Decoded value, ClientError error) {
            this.value = value;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }

        static Outcome success(Decoded value) {
            return new Outcome(value, null);
        }

        public static Outcome failure(ClientError error) {
            return new Outcome(null, error);
        }
    }

    public interface CredentialFetch {
        void fetch(CredentialCallback callback);
    }

    public interface CredentialCallback {
        void onResult(Credential credential, ClientError error);
    }

    public interface HttpTransport {
        void perform(HttpCall call, HttpCallback callback);
    }

    public interface HttpCallback {
        void onResult(HttpResult result);
    }

    public interface Clock {
        long now();
    }

    public interface Completion {
        void onResult(Outcome outcome);
    }

    public static final class Loader {
        private final Clock clock;
        private final CredentialFetch fetchCredential;
        private final HttpTransport http;
        private Credential credential;
        private int generation;

        public Loader(Clock clock, Credential credential, CredentialFetch fetchCredential, HttpTransport http) {
            this.clock = clock;
            this.credential = credential;
            this.fetchCredential = fetchCredential;
            this.http = http;
        }

        public void cancel() {
            generation++;
        }

        public void run(boolean loggedIn, Configuration configuration, Operation operation, Completion completion) {
            int ticket = generation;
            if (!loggedIn) {
                completion.onResult(Outcome.failure(ClientError.SIGNED_OUT));
                return;
            }
            if (configuration == null || !configuration.enabled || configuration.baseUrl == null) {
                completion.onResult(Outcome.failure(ClientError.DISABLED));
                return;
            }
            issue(ticket, configuration, operation, true, completion);
        }

        private void issue(final int ticket, final Configuration configuration, final Operation operation, final boolean retryUnauthorized, final Completion completion) {
            if (ticket != generation) {
                return;
            }
            if (configuration.baseUrl == null) {
                completion.onResult(Outcome.failure(ClientError.DISABLED));
                return;
            }
            ClientError local = localFailure(operation);
            if (local != null) {
                completion.onResult(Outcome.failure(local));
                return;
            }
            if (!needsCredential(operation)) {
                send(ticket, configuration.baseUrl, operation, false, completion);
                return;
            }
            Credential current = credential;
            if (current == null || credentialNeedsRefresh(current.expires, clock.now())) {
                fetchCredential.fetch(new CredentialCallback() {
                    @Override
                    public void onResult(Credential fetched, ClientError error) {
                        if (ticket != generation) {
                            return;
                        }
                        if (error != null || fetched == null) {
                            completion.onResult(Outcome.failure(error == null ? ClientError.UNAVAILABLE : error));
                            return;
                        }
                        if (fetched.token == null || fetched.token.isEmpty() || (!"cn".equals(fetched.region) && !"global".equals(fetched.region))) {
                            completion.onResult(Outcome.failure(ClientError.UNREADABLE));
                            return;
                        }
                        credential = fetched;
                        send(ticket, configuration.baseUrl, operation, retryUnauthorized, completion);
                    }
                });
                return;
            }
            send(ticket, configuration.baseUrl, operation, retryUnauthorized, completion);
        }

        private void send(final int ticket, final String baseUrl, final Operation operation, final boolean retryUnauthorized, final Completion completion) {
            if (ticket != generation) {
                return;
            }
            String token = credential == null || credential.token == null ? "" : credential.token;
            HttpCall call = httpCall(baseUrl, operation, token);
            if (call == null) {
                ClientError local = localFailure(operation);
                completion.onResult(Outcome.failure(local == null ? ClientError.UNREADABLE : local));
                return;
            }
            http.perform(call, new HttpCallback() {
                @Override
                public void onResult(HttpResult result) {
                    if (ticket != generation) {
                        return;
                    }
                    if (result == null || result.error != null) {
                        completion.onResult(Outcome.failure(result == null || result.error == null ? ClientError.UNAVAILABLE : result.error));
                        return;
                    }
                    if (needsCredential(operation) && (result.status == 401 || result.status == 403)) {
                        credential = null;
                        if (!retryUnauthorized) {
                            completion.onResult(Outcome.failure(ClientError.UNAUTHORIZED));
                            return;
                        }
                        issue(ticket, new Configuration(true, baseUrl), operation, false, completion);
                        return;
                    }
                    completion.onResult(decodeHttp(operation, result));
                }
            });
        }
    }

    static ClientError localFailure(Operation operation) {
        if (operation.kind == Operation.Kind.CAPABILITIES || operation.kind == Operation.Kind.STATUS || operation.kind == Operation.Kind.ENGINE_CONFIG) {
            return null;
        }
        if (operation.kind == Operation.Kind.FEED) {
            return operation.feedKind == FeedKind.NEARBY ? ClientError.UNAVAILABLE : null;
        }
        if (operation.kind == Operation.Kind.NEARBY) {
            return TellomiDiscoverCity.knownCode(operation.city) == null ? ClientError.INVALID_CITY : null;
        }
        if (operation.kind == Operation.Kind.CARDS) {
            return cardsBody(operation.ids) == null ? ClientError.NOT_FOUND : null;
        }
        if (operation.city != null && !trim(operation.city).isEmpty() && TellomiDiscoverCity.knownCode(operation.city) == null) {
            return ClientError.INVALID_CITY;
        }
        if (publishBody(operation.text, operation.visibility, operation.audienceToken, operation.clientNonce, operation.textLimit, operation.city) == null) {
            return ClientError.NOT_PUBLISHABLE;
        }
        return null;
    }

    public static Outcome decodeHttp(Operation operation, HttpResult result) {
        if (result.status == 404 && "feed_hidden".equals(TellomiDiscoverParse.errorCode(result.body))) {
            return Outcome.failure(ClientError.HIDDEN);
        }
        if (result.status == 400 && "invalid_city".equals(TellomiDiscoverParse.errorCode(result.body))) {
            return Outcome.failure(ClientError.INVALID_CITY);
        }
        if (result.status != 200 && result.status != 201) {
            if (result.status == 401 || result.status == 403) {
                return Outcome.failure(ClientError.UNAUTHORIZED);
            }
            if (result.status == 404) {
                return Outcome.failure(ClientError.NOT_FOUND);
            }
            return Outcome.failure(ClientError.UNAVAILABLE);
        }
        if (operation.kind == Operation.Kind.CAPABILITIES) {
            Capabilities value = TellomiDiscoverParse.capabilities(result.body);
            return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.capabilities(value));
        }
        if (operation.kind == Operation.Kind.STATUS) {
            Boolean value = TellomiDiscoverParse.status(result.body);
            return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.status(value));
        }
        if (operation.kind == Operation.Kind.ENGINE_CONFIG) {
            EngineConfig value = TellomiDiscoverParse.engineConfig(result.body);
            return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.engineConfig(value));
        }
        if (operation.kind == Operation.Kind.FEED || operation.kind == Operation.Kind.NEARBY) {
            FeedPage value = TellomiDiscoverParse.feedPage(result.body);
            return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.feed(value));
        }
        if (operation.kind == Operation.Kind.CARDS) {
            List<Card> value = TellomiDiscoverParse.cards(result.body);
            return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.cards(value));
        }
        CreatedPost value = TellomiDiscoverParse.createdPost(result.body);
        return value == null ? Outcome.failure(ClientError.UNREADABLE) : Outcome.success(Decoded.created(value));
    }

    public static final class Copy {
        public final String recommend;
        public final String following;
        public final String nearby;
        public final String compose;
        public final String emptyTitle;
        public final String emptyText;
        public final String unavailable;
        public final String failed;
        public final String cityPrompt;
        public final String cityPlaceholder;
        public final String useLocation;
        public final String confirmCity;
        public final String cityDenied;
        public final String visibility;
        public final String everyone;
        public final String contacts;
        public final String closeFriends;
        public final String selected;
        public final String exclude;
        public final String audienceInvalid;
        public final String userInvalid;
        public final String userBlocked;
        public final String discoverDisabled;
        public final String nearbyEmpty;
        public final String invalidCity;
        public final String aiGenerated;
        public final String quoteDeleted;
        public final String publish;
        public final String legacyEmptyTitle;
        public final String legacyEmptyText;
        public final String legacyPostUnavailable;

        private Copy(String recommend, String following, String nearby, String compose, String emptyTitle, String emptyText, String unavailable, String failed, String cityPrompt, String cityPlaceholder, String useLocation, String confirmCity, String cityDenied, String visibility, String everyone, String contacts, String closeFriends, String selected, String exclude, String audienceInvalid, String userInvalid, String userBlocked, String discoverDisabled, String nearbyEmpty, String invalidCity, String aiGenerated, String quoteDeleted, String publish, String legacyEmptyTitle, String legacyEmptyText, String legacyPostUnavailable) {
            this.recommend = recommend;
            this.following = following;
            this.nearby = nearby;
            this.compose = compose;
            this.emptyTitle = emptyTitle;
            this.emptyText = emptyText;
            this.unavailable = unavailable;
            this.failed = failed;
            this.cityPrompt = cityPrompt;
            this.cityPlaceholder = cityPlaceholder;
            this.useLocation = useLocation;
            this.confirmCity = confirmCity;
            this.cityDenied = cityDenied;
            this.visibility = visibility;
            this.everyone = everyone;
            this.contacts = contacts;
            this.closeFriends = closeFriends;
            this.selected = selected;
            this.exclude = exclude;
            this.audienceInvalid = audienceInvalid;
            this.userInvalid = userInvalid;
            this.userBlocked = userBlocked;
            this.discoverDisabled = discoverDisabled;
            this.nearbyEmpty = nearbyEmpty;
            this.invalidCity = invalidCity;
            this.aiGenerated = aiGenerated;
            this.quoteDeleted = quoteDeleted;
            this.publish = publish;
            this.legacyEmptyTitle = legacyEmptyTitle;
            this.legacyEmptyText = legacyEmptyText;
            this.legacyPostUnavailable = legacyPostUnavailable;
        }

        public static Copy resolve(String languageCode) {
            if (TellomiDiscoverCity.chinese(languageCode)) {
                return new Copy(
                    "推荐", "关注", "同城", "发布",
                    "还没有帖子", "关注的人和推荐会显示在这里。",
                    "这条动态已不可见", "暂时打不开，下拉再试一次。",
                    "选择城市", "输入城市", "使用当前位置", "确定",
                    "可以不定位，直接从列表里选。只保存城市，不保存坐标。",
                    "谁可以看", "所有人", "我的联系人", "密友", "选定的用户", "不给谁看",
                    "名单无效，请重新选择。", "名单里有人无法使用。", "名单里有人已互相拉黑。",
                    "发现暂时不可用。", "这个城市还没有帖子。", "这个城市不能用，请重新选择。",
                    "AI 生成", "原动态已被作者删除", "发布",
                    "还没有内容", "帖子会显示在这里。", "这条帖子暂时无法打开。"
                );
            }
            return new Copy(
                "For You", "Following", "Nearby", "Post",
                "No posts yet", "Posts from people you follow, and recommendations, show up here.",
                "This post isn't available", "Couldn't load this. Pull to try again.",
                "Choose a city", "City", "Use current location", "Done",
                "You can pick a city without location. Only the city is kept.",
                "Who can see this", "Everyone", "My Contacts", "Close Friends", "Selected People", "Exclude",
                "That list can't be used. Choose again.", "Someone on the list can't be added.", "Someone on the list is blocked.",
                "Discover isn't available right now.", "No posts in this city yet.", "That city can't be used. Choose another.",
                "AI generated", "The original post was deleted by its author", "Post",
                "Nothing here yet", "Posts will show up here.", "This post can't be opened yet."
            );
        }

        public String visibilityTitle(Visibility visibility) {
            if (visibility == Visibility.EVERYONE) {
                return everyone;
            }
            if (visibility == Visibility.CONTACTS) {
                return contacts;
            }
            if (visibility == Visibility.CLOSE_FRIENDS) {
                return closeFriends;
            }
            if (visibility == Visibility.INCLUDE) {
                return selected;
            }
            return exclude;
        }

        public String reasonText(String reason) {
            if ("audience_invalid".equals(reason)) {
                return audienceInvalid;
            }
            return null;
        }

        public String failureText(ClientError error) {
            if (error == ClientError.AUDIENCE_INVALID) {
                return audienceInvalid;
            }
            if (error == ClientError.USER_INVALID) {
                return userInvalid;
            }
            if (error == ClientError.USER_BLOCKED) {
                return userBlocked;
            }
            if (error == ClientError.DISABLED) {
                return discoverDisabled;
            }
            if (error == ClientError.INVALID_CITY) {
                return invalidCity;
            }
            return failed;
        }
    }

    public static final class PostSummary {
        public final String title;
        public final String body;
        public final String meta;

        public PostSummary(String title, String body, String meta) {
            this.title = title;
            this.body = body;
            this.meta = meta;
        }
    }

    public static PostSummary postSummary(Post post, Copy copy, boolean list) {
        String title = post.authorName == null ? "" : post.authorName;
        if (post.authorHandle != null && !post.authorHandle.isEmpty()) {
            if (title.isEmpty()) {
                title = "@" + post.authorHandle;
            } else {
                title = title + " @" + post.authorHandle;
            }
        } else if (post.tellomiNumber != null && !post.tellomiNumber.isEmpty()) {
            String label = "推荐".equals(copy.recommend) ? "Tellomi 号：" + post.tellomiNumber : "Tellomi ID: " + post.tellomiNumber;
            title = title.isEmpty() ? label : title + " · " + label;
        }
        String body;
        if (post.unavailable) {
            body = copy.unavailable;
        } else {
            body = post.text == null ? "" : post.text;
            if (list && body.codePointCount(0, body.length()) > 280) {
                body = body.substring(0, body.offsetByCodePoints(0, 280));
            }
            if ("deleted".equals(post.quoteState)) {
                if (!body.isEmpty()) {
                    body = body + "\n";
                }
                body = body + copy.quoteDeleted;
            } else if ("unavailable".equals(post.quoteState)) {
                if (!body.isEmpty()) {
                    body = body + "\n";
                }
                body = body + copy.unavailable;
            } else if (post.quoteText != null && !post.quoteText.isEmpty()) {
                body = body + "\n" + post.quoteText;
            }
        }
        boolean chinese = "推荐".equals(copy.recommend);
        List<String> meta = new ArrayList<>();
        if (post.city != null) {
            String cityName = TellomiDiscoverCity.name(post.city, chinese ? "zh" : "en");
            if (cityName != null) {
                meta.add(cityName);
            }
        }
        if (post.ipRegionLabel != null && !post.ipRegionLabel.isEmpty()) {
            meta.add(post.ipRegionLabel);
        }
        if (post.likeCount > 0) {
            meta.add(chinese ? post.likeCount + " 赞" : post.likeCount + " likes");
        }
        if (post.commentCount > 0) {
            meta.add(chinese ? post.commentCount + " 评论" : post.commentCount + " comments");
        }
        if (post.imageCount > 0) {
            meta.add(chinese ? post.imageCount + " 张图片" : post.imageCount + " photos");
        }
        String badge = visibleBadge(post.badge, post.aiGenerated, copy);
        if (badge != null) {
            meta.add(badge);
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < meta.size(); i++) {
            if (i > 0) {
                joined.append(" · ");
            }
            joined.append(meta.get(i));
        }
        return new PostSummary(title, body, joined.toString());
    }

    public static String visibleBadge(String badge, boolean aiGenerated, Copy copy) {
        if (badge != null) {
            String trimmed = trim(badge);
            String folded = trimmed.toLowerCase(Locale.US);
            boolean hidden = trimmed.contains("审核") || folded.contains("review") || folded.contains("pending") || folded.contains("moderation");
            if (!trimmed.isEmpty() && !hidden) {
                if (aiGenerated) {
                    return trimmed + " · " + copy.aiGenerated;
                }
                return trimmed;
            }
        }
        if (aiGenerated) {
            return copy.aiGenerated;
        }
        return null;
    }

    public static final class Tl {
        public static final int GET_ENGINE_CREDENTIAL_ID = 0xA80A7F76;
        public static final int ENGINE_CREDENTIAL_ID = 0x0578CCAA;
        public static final int GET_AUDIENCE_TOKEN_ID = 0xDC5213C2;
        public static final int AUDIENCE_TOKEN_ID = 0xDA177181;
        public static final int VECTOR_ID = 0x1CB5C415;

        private Tl() {
        }

        public static byte[] encodeRequest() {
            byte[] data = new byte[4];
            writeInt32(data, 0, GET_ENGINE_CREDENTIAL_ID);
            return data;
        }

        public static byte[] encodeAudienceRequest(String kind, long[] userIds) {
            byte[] kindBytes = encodeString(kind);
            byte[] data = new byte[4 + kindBytes.length + 8 + userIds.length * 8];
            int offset = 0;
            writeInt32(data, offset, GET_AUDIENCE_TOKEN_ID);
            offset += 4;
            System.arraycopy(kindBytes, 0, data, offset, kindBytes.length);
            offset += kindBytes.length;
            writeInt32(data, offset, VECTOR_ID);
            offset += 4;
            writeInt32(data, offset, userIds.length);
            offset += 4;
            for (int i = 0; i < userIds.length; i++) {
                writeInt64(data, offset, userIds[i]);
                offset += 8;
            }
            return data;
        }

        public static AudienceToken decodeAudienceToken(byte[] data) {
            int[] offset = new int[] {0};
            Integer constructor = readInt32(data, offset);
            if (constructor == null || constructor != AUDIENCE_TOKEN_ID) {
                return null;
            }
            String token = readString(data, offset);
            Integer expires = readInt32(data, offset);
            if (token == null || token.isEmpty() || expires == null || offset[0] != data.length) {
                return null;
            }
            return new AudienceToken(token, expires.longValue() & 0xffffffffL);
        }

        public static Credential decodeCredential(byte[] data) {
            int[] offset = new int[] {0};
            Integer constructor = readInt32(data, offset);
            if (constructor == null || constructor != ENGINE_CREDENTIAL_ID) {
                return null;
            }
            String token = readString(data, offset);
            Integer expires = readInt32(data, offset);
            String region = readString(data, offset);
            if (token == null || token.isEmpty() || expires == null || region == null) {
                return null;
            }
            if (!"cn".equals(region) && !"global".equals(region)) {
                return null;
            }
            if (offset[0] != data.length) {
                return null;
            }
            return new Credential(token, expires.longValue() & 0xffffffffL, region);
        }

        private static byte[] encodeString(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            int length = bytes.length;
            if (length >= 254) {
                int padding = (4 - (length % 4)) % 4;
                byte[] out = new byte[4 + length + padding];
                out[0] = (byte) 254;
                out[1] = (byte) (length & 0xff);
                out[2] = (byte) ((length >> 8) & 0xff);
                out[3] = (byte) ((length >> 16) & 0xff);
                System.arraycopy(bytes, 0, out, 4, length);
                return out;
            }
            int padding = (4 - ((length + 1) % 4)) % 4;
            byte[] out = new byte[1 + length + padding];
            out[0] = (byte) length;
            System.arraycopy(bytes, 0, out, 1, length);
            return out;
        }

        private static void writeInt32(byte[] data, int offset, int value) {
            data[offset] = (byte) (value & 0xff);
            data[offset + 1] = (byte) ((value >> 8) & 0xff);
            data[offset + 2] = (byte) ((value >> 16) & 0xff);
            data[offset + 3] = (byte) ((value >> 24) & 0xff);
        }

        private static void writeInt64(byte[] data, int offset, long value) {
            for (int i = 0; i < 8; i++) {
                data[offset + i] = (byte) ((value >> (8 * i)) & 0xff);
            }
        }

        private static Integer readInt32(byte[] data, int[] offset) {
            if (offset[0] + 4 > data.length) {
                return null;
            }
            int value = (data[offset[0]] & 0xff)
                | ((data[offset[0] + 1] & 0xff) << 8)
                | ((data[offset[0] + 2] & 0xff) << 16)
                | ((data[offset[0] + 3] & 0xff) << 24);
            offset[0] += 4;
            return value;
        }

        private static String readString(byte[] data, int[] offset) {
            if (offset[0] >= data.length) {
                return null;
            }
            int first = data[offset[0]] & 0xff;
            offset[0]++;
            int length;
            int padding;
            if (first == 254) {
                if (offset[0] + 3 > data.length) {
                    return null;
                }
                length = (data[offset[0]] & 0xff) | ((data[offset[0] + 1] & 0xff) << 8) | ((data[offset[0] + 2] & 0xff) << 16);
                offset[0] += 3;
                padding = (4 - (length % 4)) % 4;
            } else {
                length = first;
                padding = (4 - ((length + 1) % 4)) % 4;
            }
            if (length < 0 || offset[0] + length + padding > data.length) {
                return null;
            }
            String value = new String(data, offset[0], length, StandardCharsets.UTF_8);
            offset[0] += length + padding;
            return value;
        }
    }

    private static String url(String base, String path, List<QueryItem> query) {
        if (base == null || !base.toLowerCase(Locale.US).startsWith("https://")) {
            return null;
        }
        String origin = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        StringBuilder out = new StringBuilder(origin).append(path);
        if (query != null && !query.isEmpty()) {
            out.append('?');
            for (int i = 0; i < query.size(); i++) {
                if (i > 0) {
                    out.append('&');
                }
                out.append(encode(query.get(i).name)).append('=').append(encode(query.get(i).value));
            }
        }
        String absolute = out.toString();
        if (absolute.contains("latitude=") || absolute.contains("longitude=") || absolute.contains("lat=") || absolute.contains("lng=")) {
            return null;
        }
        return absolute;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException ignored) {
            return value;
        }
    }

    private static boolean memberChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (MEMBER_ALPHABET.indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    static String trim(String value) {
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
