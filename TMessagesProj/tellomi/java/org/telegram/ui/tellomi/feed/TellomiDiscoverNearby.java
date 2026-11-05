package org.telegram.ui.tellomi.feed;

import org.telegram.ui.TellomiPack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Nearby tab (D-11 / D-12). The bar is always 推荐 / 关注 / 同城.
 * A post stores a catalog city code in the existing {@code city} field.
 * A coordinate never becomes that code.
 */
public final class TellomiDiscoverNearby {

    private TellomiDiscoverNearby() {
    }

    /** 推荐, 关注, 同城. Engine config does not add or remove one of these. */
    public static List<TellomiDiscover.FeedKind> homeTabs() {
        List<TellomiDiscover.FeedKind> tabs = new ArrayList<>();
        tabs.add(TellomiDiscover.FeedKind.RECOMMEND);
        tabs.add(TellomiDiscover.FeedKind.FOLLOWING);
        tabs.add(TellomiDiscover.FeedKind.NEARBY);
        return Collections.unmodifiableList(tabs);
    }

    /** True when {@code GET /v1/feed/config} did not name the nearby tab. */
    public static boolean engineOmitsNearby(List<TellomiDiscover.FeedKind> engineTabs) {
        if (engineTabs == null) {
            return true;
        }
        for (int i = 0; i < engineTabs.size(); i++) {
            if (engineTabs.get(i) == TellomiDiscover.FeedKind.NEARBY) {
                return false;
            }
        }
        return true;
    }

    /** Catalog code for {@code city} on publish and on {@code GET /v1/feed/nearby}. */
    public static String publishCity(String raw) {
        return TellomiDiscoverCity.knownCode(raw);
    }

    public static String displayName(String raw, String languageCode, TellomiPack.Source source) {
        String code = publishCity(raw);
        if (code == null) {
            return null;
        }
        return TellomiDiscoverCity.name(code, languageCode, source);
    }

    /** City-level line shown before posting. A missing code uses the "no city" sentence. */
    public static String composeLine(String raw, String languageCode, TellomiPack.Source source) {
        String name = displayName(raw, languageCode, source);
        if (name == null) {
            return TellomiPack.text("Tellomi.Discover.ComposeCityNone", languageCode, source);
        }
        String pattern = TellomiPack.text("Tellomi.Discover.ComposeCity", languageCode, source);
        return format(pattern, name);
    }

    /** The nearby route itself is absent. An empty 200 is a normal feed, not this. */
    public static boolean missingEndpoint(int status) {
        return status == 404 || status == 405 || status == 501;
    }

    /** Names allowed on {@code GET /v1/feed/nearby}. Latitude is not one of them. */
    public static boolean nearbyQueryName(String name) {
        return "city".equals(name) || "cursor".equals(name);
    }

    private static String format(String pattern, String value) {
        if (pattern == null || pattern.isEmpty()) {
            return value;
        }
        try {
            return String.format(Locale.US, pattern.replace("%@", "%s"), value);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}
