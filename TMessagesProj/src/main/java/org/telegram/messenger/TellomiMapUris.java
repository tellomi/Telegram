/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Deep links for the map apps a Tellomi user may have installed.
 * Telegram stores WGS-84. AMap, Tencent, and Petal Maps take GCJ-02
 * inside the China bounding box. Baidu accepts WGS-84 when coord_type
 * says so. Google Maps and geo: stay on WGS-84.
 */

package org.telegram.messenger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

public final class TellomiMapUris {

    public static final String AMAP = "com.autonavi.minimap";
    public static final String BAIDU = "com.baidu.BaiduMap";
    public static final String TENCENT = "com.tencent.map";
    public static final String PETAL = "com.huawei.maps.app";
    public static final String GOOGLE = "com.google.android.apps.maps";

    private static final String SOURCE = "Tellomi";
    private static final String BAIDU_SRC = "andr.tellomi.chat";
    private static final double CHINA_MIN_LAT = 0.8293;
    private static final double CHINA_MAX_LAT = 55.8271;
    private static final double CHINA_MIN_LON = 72.004;
    private static final double CHINA_MAX_LON = 137.8347;
    private static final double GCJ_A = 6378245.0;
    private static final double GCJ_EE = 0.00669342162296594323;

    public static final class LatLng {
        public final double latitude;
        public final double longitude;

        public LatLng(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private TellomiMapUris() {
    }

    public static boolean outOfChina(double latitude, double longitude) {
        return longitude < CHINA_MIN_LON || longitude > CHINA_MAX_LON || latitude < CHINA_MIN_LAT || latitude > CHINA_MAX_LAT;
    }

    /** WGS-84 to GCJ-02. Points outside the China bounding box are returned unchanged. */
    public static LatLng wgs84ToGcj02(double latitude, double longitude) {
        if (outOfChina(latitude, longitude)) {
            return new LatLng(latitude, longitude);
        }
        double dLat = transformLat(longitude - 105.0, latitude - 35.0);
        double dLon = transformLon(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - GCJ_EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (GCJ_A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new LatLng(latitude + dLat, longitude + dLon);
    }

    /**
     * URI for one of the known map packages. Returns null for any other package;
     * those open with {@link #geoUri}.
     */
    public static String uriFor(String packageName, double latitude, double longitude, String name, boolean directions) {
        if (AMAP.equals(packageName)) {
            return directions ? amapDirections(latitude, longitude, name) : amapView(latitude, longitude, name);
        }
        if (BAIDU.equals(packageName)) {
            return directions ? baiduDirections(latitude, longitude, name) : baiduView(latitude, longitude, name);
        }
        if (TENCENT.equals(packageName)) {
            return directions ? tencentDirections(latitude, longitude, name) : tencentView(latitude, longitude, name);
        }
        if (PETAL.equals(packageName)) {
            return directions ? petalDirections(latitude, longitude) : petalView(latitude, longitude);
        }
        if (GOOGLE.equals(packageName)) {
            return directions ? googleDirections(latitude, longitude) : geoUri(latitude, longitude, name);
        }
        return null;
    }

    public static String geoUri(double latitude, double longitude, String name) {
        String lat = formatCoord(latitude);
        String lon = formatCoord(longitude);
        StringBuilder uri = new StringBuilder();
        uri.append("geo:").append(lat).append(',').append(lon);
        uri.append("?q=").append(lat).append(',').append(lon);
        String encoded = encode(name);
        if (!encoded.isEmpty()) {
            uri.append('(').append(encoded).append(')');
        }
        return uri.toString();
    }

    /** AMap marker page. Used when no map app is installed. Position is longitude,latitude and the coordinate system is WGS-84. */
    public static String webFallback(double latitude, double longitude, String name) {
        StringBuilder uri = new StringBuilder();
        uri.append("https://uri.amap.com/marker?position=");
        uri.append(formatCoord(longitude)).append(',').append(formatCoord(latitude));
        String encoded = encode(name);
        if (!encoded.isEmpty()) {
            uri.append("&name=").append(encoded);
        }
        uri.append("&coordinate=wgs84&src=tellomi");
        return uri.toString();
    }

    private static String amapView(double latitude, double longitude, String name) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        StringBuilder uri = new StringBuilder();
        uri.append("androidamap://viewMap?sourceApplication=").append(SOURCE);
        appendEncoded(uri, "poiname", name);
        uri.append("&lat=").append(formatCoord(gcj.latitude));
        uri.append("&lon=").append(formatCoord(gcj.longitude));
        uri.append("&dev=0");
        return uri.toString();
    }

    private static String amapDirections(double latitude, double longitude, String name) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        StringBuilder uri = new StringBuilder();
        uri.append("amapuri://route/plan/?sourceApplication=").append(SOURCE);
        uri.append("&dlat=").append(formatCoord(gcj.latitude));
        uri.append("&dlon=").append(formatCoord(gcj.longitude));
        appendEncoded(uri, "dname", name);
        uri.append("&dev=0&t=0");
        return uri.toString();
    }

    private static String baiduView(double latitude, double longitude, String name) {
        StringBuilder uri = new StringBuilder();
        uri.append("baidumap://map/marker?location=");
        uri.append(formatCoord(latitude)).append(',').append(formatCoord(longitude));
        appendEncoded(uri, "title", name);
        uri.append("&coord_type=wgs84&src=").append(BAIDU_SRC);
        return uri.toString();
    }

    private static String baiduDirections(double latitude, double longitude, String name) {
        StringBuilder uri = new StringBuilder();
        uri.append("baidumap://map/direction?destination=latlng:");
        uri.append(formatCoord(latitude)).append(',').append(formatCoord(longitude));
        String encoded = encode(name);
        if (!encoded.isEmpty()) {
            uri.append("|name:").append(encoded);
        }
        uri.append("&coord_type=wgs84&mode=driving&src=").append(BAIDU_SRC);
        return uri.toString();
    }

    private static String tencentView(double latitude, double longitude, String name) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        StringBuilder uri = new StringBuilder();
        uri.append("qqmap://map/marker?marker=coord:");
        uri.append(formatCoord(gcj.latitude)).append(',').append(formatCoord(gcj.longitude));
        String encoded = encode(name);
        if (!encoded.isEmpty()) {
            uri.append(";title:").append(encoded);
        }
        uri.append("&referer=").append(SOURCE);
        return uri.toString();
    }

    private static String tencentDirections(double latitude, double longitude, String name) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        StringBuilder uri = new StringBuilder();
        uri.append("qqmap://map/routeplan?type=drive&fromcoord=CurrentLocation");
        appendEncoded(uri, "to", name);
        uri.append("&tocoord=").append(formatCoord(gcj.latitude)).append(',').append(formatCoord(gcj.longitude));
        uri.append("&referer=").append(SOURCE);
        return uri.toString();
    }

    private static String petalView(double latitude, double longitude) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        return "petalmaps://poidetail?marker=" + formatCoord(gcj.latitude) + "," + formatCoord(gcj.longitude) + "&utm_source=" + SOURCE;
    }

    private static String petalDirections(double latitude, double longitude) {
        LatLng gcj = wgs84ToGcj02(latitude, longitude);
        return "petalmaps://navigation?daddr=" + formatCoord(gcj.latitude) + "," + formatCoord(gcj.longitude) + "&type=drive&utm_source=" + SOURCE;
    }

    private static String googleDirections(double latitude, double longitude) {
        return "google.navigation:q=" + formatCoord(latitude) + "," + formatCoord(longitude);
    }

    private static void appendEncoded(StringBuilder uri, String key, String name) {
        String encoded = encode(name);
        if (!encoded.isEmpty()) {
            uri.append('&').append(key).append('=').append(encoded);
        }
    }

    static String formatCoord(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    static String encode(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String cleaned = trimmed.replace('|', ' ').replace(';', ' ').replace('(', ' ').replace(')', ' ');
        try {
            return URLEncoder.encode(cleaned, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
