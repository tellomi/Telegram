/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Mainland phones do not have Google Maps. Opening a
 * point or a route lists the map apps that are actually installed.
 * See TMessagesProj/tellomi/UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GoogleMapsProvider;
import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Chooser for a WGS-84 point Telegram already stored.
 * Each vendor URI carries a coordinate-type flag instead of a GCJ-02 conversion:
 * Amap {@code dev=1}, Baidu {@code coord_type=wgs84}, Tencent {@code coord_type=1}
 * (GPS), Google and {@code geo:} are WGS-84 already, and the Amap web marker uses
 * {@code coordinate=gps}. Converting here as well would shift the pin twice.
 */
public final class TellomiMaps {

    public static final String PKG_AMAP = "com.autonavi.minimap";
    public static final String PKG_BAIDU = "com.baidu.BaiduMap";
    public static final String PKG_TENCENT = "com.tencent.map";
    public static final String PKG_GOOGLE = "com.google.android.apps.maps";

    private static final int KIND_AMAP = 1;
    private static final int KIND_BAIDU = 2;
    private static final int KIND_TENCENT = 3;
    private static final int KIND_GOOGLE = 4;
    private static final int KIND_GEO = 5;

    private static final String SRC = "tellomi";
    private static final String BAIDU_SRC = "andr.tellomi.chat";

    private TellomiMaps() {
    }

    /** Venue title, otherwise the address line. */
    public static String placeName(TLRPC.MessageMedia media) {
        if (media == null) {
            return null;
        }
        if (!TextUtils.isEmpty(media.title)) {
            return media.title;
        }
        if (!TextUtils.isEmpty(media.address)) {
            return media.address;
        }
        return null;
    }

    /**
     * True when LocationActivity can draw tiles. The default provider is
     * Google Maps SDK, which stays blank without Play services. A non-Google
     * provider (the Huawei build) is left to its own SDK.
     */
    public static boolean canShowInAppMap(Context context) {
        try {
            IMapsProvider provider = ApplicationLoader.getMapsProvider();
            if (provider != null && !(provider instanceof GoogleMapsProvider)) {
                return true;
            }
        } catch (Throwable t) {
            FileLog.e(t, false);
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return false;
        }
        try {
            return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            FileLog.e(t, false);
            return false;
        }
    }

    /**
     * Static pin, and the in-app map cannot draw: open an external map and
     * tell the caller not to push LocationActivity. Live locations stay in
     * LocationActivity so the moving markers still have a screen.
     * Returns false when the caller should keep the upstream screen.
     */
    public static boolean openReceivedPin(BaseFragment fragment, MessageObject message) {
        if (fragment == null || message == null || message.isLiveLocation()) {
            return false;
        }
        TLRPC.MessageMedia media = message.messageOwner != null ? message.messageOwner.media : null;
        if (media == null || media.geo == null || media.geo instanceof TLRPC.TL_geoPointEmpty) {
            return false;
        }
        return openReceivedPin(fragment, media.geo.lat, media.geo._long, placeName(media), false);
    }

    public static boolean openReceivedPin(BaseFragment fragment, double lat, double lon, String name, boolean live) {
        if (fragment == null || live || fragment.getParentActivity() == null) {
            return false;
        }
        if (canShowInAppMap(fragment.getParentActivity())) {
            return false;
        }
        openPoint(fragment.getParentActivity(), fragment, fragment.getResourceProvider(), lat, lon, name);
        return true;
    }

    public static void openPoint(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, double lat, double lon, String name) {
        open(context, fragment, resourcesProvider, new Place(lat, lon, name, false, null, null));
    }

    public static void openRoute(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, double lat, double lon, String name, Double fromLat, Double fromLon) {
        open(context, fragment, resourcesProvider, new Place(lat, lon, name, true, fromLat, fromLon));
    }

    private static void open(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, Place place) {
        if (!place.hasCoord && TextUtils.isEmpty(place.name)) {
            return;
        }
        Activity activity = AndroidUtilities.findActivity(context);
        if (activity == null && fragment != null) {
            activity = fragment.getParentActivity();
        }
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            openWeb(context != null ? context : ApplicationLoader.applicationContext, place);
            return;
        }
        List<MapApp> apps = installed(activity);
        if (apps.isEmpty()) {
            openWeb(activity, place);
            return;
        }
        showChooser(activity, fragment, resourcesProvider, apps, place);
    }

    private static void showChooser(Activity activity, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, List<MapApp> apps, Place place) {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        if (!TextUtils.isEmpty(place.name)) {
            TextView subtitle = new TextView(activity);
            subtitle.setText(place.name);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider));
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setPadding(dp(16), 0, dp(16), dp(8));
            list.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final BottomSheet[] holder = new BottomSheet[1];
        for (int i = 0; i < apps.size(); i++) {
            MapApp app = apps.get(i);
            BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(activity, 0, resourcesProvider);
            cell.setTextAndIcon(app.label, app.icon);
            if (app.icon != null) {
                cell.getImageView().setColorFilter(null);
                cell.getImageView().setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                int inset = dp(16);
                cell.getImageView().setPadding(inset, dp(12), inset, dp(12));
            }
            cell.setOnClickListener(v -> {
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                launch(activity, app, place);
            });
            list.addView(cell, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }

        ScrollView scroll = new ScrollView(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(dp(360), android.view.View.MeasureSpec.AT_MOST));
            }
        };
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        BottomSheet.Builder builder = new BottomSheet.Builder(activity, false, resourcesProvider);
        builder.setTitle(getString(place.route ? "TellomiMapChooserRoute" : "TellomiMapChooserPoint", place.route ? R.string.TellomiMapChooserRoute : R.string.TellomiMapChooserPoint));
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();
        holder[0] = sheet;
        if (fragment != null && fragment.getParentActivity() != null) {
            if (fragment.showDialog(sheet) == null) {
                sheet.show();
            }
        } else {
            sheet.show();
        }
    }

    private static List<MapApp> installed(Context context) {
        PackageManager pm = context.getPackageManager();
        ArrayList<MapApp> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        addKnown(pm, apps, seen, PKG_AMAP, KIND_AMAP, "TellomiMapAmap", R.string.TellomiMapAmap);
        addKnown(pm, apps, seen, PKG_BAIDU, KIND_BAIDU, "TellomiMapBaidu", R.string.TellomiMapBaidu);
        addKnown(pm, apps, seen, PKG_TENCENT, KIND_TENCENT, "TellomiMapTencent", R.string.TellomiMapTencent);
        addKnown(pm, apps, seen, PKG_GOOGLE, KIND_GOOGLE, "TellomiMapGoogle", R.string.TellomiMapGoogle);

        String self = context.getPackageName();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"));
        List<ResolveInfo> handlers;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                handlers = pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0));
            } else {
                handlers = pm.queryIntentActivities(probe, 0);
            }
        } catch (Throwable t) {
            FileLog.e(t, false);
            handlers = Collections.emptyList();
        }
        ArrayList<MapApp> others = new ArrayList<>();
        if (handlers != null) {
            for (int i = 0; i < handlers.size(); i++) {
                ResolveInfo info = handlers.get(i);
                if (info == null || info.activityInfo == null) {
                    continue;
                }
                String pkg = info.activityInfo.packageName;
                if (TextUtils.isEmpty(pkg) || pkg.equals(self) || !seen.add(pkg)) {
                    continue;
                }
                String label = applicationLabel(pm, pkg);
                if (TextUtils.isEmpty(label)) {
                    CharSequence loaded = info.loadLabel(pm);
                    label = loaded != null ? loaded.toString() : pkg;
                }
                others.add(new MapApp(pkg, label, applicationIcon(pm, pkg), KIND_GEO));
            }
        }
        if (others.size() > 1) {
            Collator collator = Collator.getInstance();
            Collections.sort(others, (a, b) -> collator.compare(a.label, b.label));
        }
        apps.addAll(others);
        return apps;
    }

    private static void addKnown(PackageManager pm, List<MapApp> apps, HashSet<String> seen, String pkg, int kind, String labelKey, int labelRes) {
        if (!isInstalled(pm, pkg) || !seen.add(pkg)) {
            return;
        }
        String label = getString(labelKey, labelRes);
        if (TextUtils.isEmpty(label) || label.startsWith("LOC_ERR")) {
            label = applicationLabel(pm, pkg);
        }
        if (TextUtils.isEmpty(label)) {
            label = pkg;
        }
        apps.add(new MapApp(pkg, label, applicationIcon(pm, pkg), kind));
    }

    private static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return info != null && info.enabled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String applicationLabel(PackageManager pm, String pkg) {
        try {
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label != null ? label.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Drawable applicationIcon(PackageManager pm, String pkg) {
        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            return icon != null ? icon.mutate() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void launch(Activity activity, MapApp app, Place place) {
        Uri uri = uriFor(app.kind, place);
        if (start(activity, app.packageName, uri)) {
            return;
        }
        if (place.hasCoord && app.kind != KIND_GEO && start(activity, app.packageName, geoUri(place))) {
            return;
        }
        openWeb(activity, place);
    }

    private static boolean start(Activity activity, String pkg, Uri uri) {
        if (uri == null) {
            return false;
        }
        // Some map filters omit CATEGORY_DEFAULT. Try with it, then without.
        if (startOnce(activity, pkg, uri, true)) {
            return true;
        }
        return startOnce(activity, pkg, uri, false);
    }

    private static boolean startOnce(Activity activity, String pkg, Uri uri, boolean withDefaultCategory) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(pkg);
            if (withDefaultCategory) {
                intent.addCategory(Intent.CATEGORY_DEFAULT);
            }
            activity.startActivity(intent);
            return true;
        } catch (android.content.ActivityNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            FileLog.e(t, false);
            return false;
        }
    }

    private static void openWeb(Context context, Place place) {
        if (context == null) {
            return;
        }
        try {
            Browser.openUrlInSystemBrowser(context, webUri(place).toString());
        } catch (Throwable t) {
            FileLog.e(t, false);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, webUri(place));
                if (!(context instanceof Activity)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(intent);
            } catch (Throwable t2) {
                FileLog.e(t2, false);
            }
        }
    }

    static Uri uriFor(int kind, Place place) {
        if (!place.hasCoord) {
            return searchUri(kind, place.name);
        }
        if (place.route) {
            return routeUri(kind, place);
        }
        return pointUri(kind, place);
    }

    private static Uri pointUri(int kind, Place place) {
        switch (kind) {
            case KIND_AMAP:
                // lat/lon are WGS-84. dev=1 asks Amap to apply the GCJ-02 offset.
                return parse("androidamap://viewMap?sourceApplication=" + SRC
                    + "&lat=" + coord(place.lat)
                    + "&lon=" + coord(place.lon)
                    + "&dev=1"
                    + nameParam("&poiname=", place.name));
            case KIND_BAIDU:
                return parse("baidumap://map/marker?location=" + coord(place.lat) + "," + coord(place.lon)
                    + nameParam("&title=", place.name)
                    + nameParam("&content=", place.name)
                    + "&coord_type=wgs84&src=" + BAIDU_SRC);
            case KIND_TENCENT: {
                String marker = "coord:" + coord(place.lat) + "," + coord(place.lon);
                if (!TextUtils.isEmpty(place.name)) {
                    marker += ";title:" + enc(place.name);
                }
                return parse("qqmap://map/marker?marker=" + marker + "&coord_type=1&referer=" + SRC);
            }
            case KIND_GOOGLE:
            case KIND_GEO:
            default:
                return geoUri(place);
        }
    }

    private static Uri routeUri(int kind, Place place) {
        switch (kind) {
            case KIND_AMAP: {
                StringBuilder sb = new StringBuilder("androidamap://route?sourceApplication=");
                sb.append(SRC);
                if (place.hasFrom) {
                    sb.append("&slat=").append(coord(place.fromLat));
                    sb.append("&slon=").append(coord(place.fromLon));
                    sb.append("&sname=").append(enc(getString("TellomiMapCurrentLocation", R.string.TellomiMapCurrentLocation)));
                }
                sb.append("&dlat=").append(coord(place.lat));
                sb.append("&dlon=").append(coord(place.lon));
                sb.append(nameParam("&dname=", place.name));
                sb.append("&dev=1&t=0");
                return parse(sb.toString());
            }
            case KIND_BAIDU: {
                String dest = "latlng:" + coord(place.lat) + "," + coord(place.lon);
                if (!TextUtils.isEmpty(place.name)) {
                    dest = "name:" + enc(place.name) + "|" + dest;
                }
                StringBuilder sb = new StringBuilder("baidumap://map/direction?");
                if (place.hasFrom) {
                    sb.append("origin=name:").append(enc(getString("TellomiMapCurrentLocation", R.string.TellomiMapCurrentLocation)));
                    sb.append("|latlng:").append(coord(place.fromLat)).append(',').append(coord(place.fromLon));
                    sb.append('&');
                }
                sb.append("destination=").append(dest);
                sb.append("&mode=driving&coord_type=wgs84&src=").append(BAIDU_SRC);
                return parse(sb.toString());
            }
            case KIND_TENCENT: {
                StringBuilder sb = new StringBuilder("qqmap://map/routeplan?type=drive");
                if (place.hasFrom) {
                    sb.append("&fromcoord=").append(coord(place.fromLat)).append(',').append(coord(place.fromLon));
                    sb.append("&from=").append(enc(getString("TellomiMapCurrentLocation", R.string.TellomiMapCurrentLocation)));
                } else {
                    sb.append("&fromcoord=CurrentLocation");
                }
                sb.append("&tocoord=").append(coord(place.lat)).append(',').append(coord(place.lon));
                sb.append(nameParam("&to=", place.name));
                sb.append("&coord_type=1&referer=").append(SRC);
                return parse(sb.toString());
            }
            case KIND_GOOGLE: {
                // Same host upstream used, plus the place name on daddr.
                String daddr = labeled(place.lat, place.lon, place.name);
                StringBuilder sb = new StringBuilder("https://maps.google.com/maps?");
                if (place.hasFrom) {
                    sb.append("saddr=").append(coord(place.fromLat)).append(',').append(coord(place.fromLon)).append('&');
                }
                sb.append("daddr=").append(daddr);
                return parse(sb.toString());
            }
            case KIND_GEO:
            default:
                // geo: is the only documented URI these handlers share. It opens the point.
                return geoUri(place);
        }
    }

    private static Uri searchUri(int kind, String name) {
        String q = enc(name);
        switch (kind) {
            case KIND_AMAP:
                return parse("androidamap://poi?sourceApplication=" + SRC + "&keywords=" + q + "&dev=0");
            case KIND_BAIDU:
                return parse("baidumap://map/place/search?query=" + q + "&src=" + BAIDU_SRC);
            case KIND_TENCENT:
                return parse("qqmap://map/search?keyword=" + q + "&referer=" + SRC);
            case KIND_GOOGLE:
            case KIND_GEO:
            default:
                return parse("geo:0,0?q=" + q);
        }
    }

    private static Uri geoUri(Place place) {
        return parse("geo:0,0?q=" + labeled(place.lat, place.lon, place.name));
    }

    /** Amap web marker. position is longitude,latitude. coordinate=gps is WGS-84. */
    static Uri webUri(Place place) {
        if (!place.hasCoord) {
            return parse("https://uri.amap.com/search?keyword=" + enc(place.name) + "&src=" + SRC + "&callnative=0");
        }
        return parse("https://uri.amap.com/marker?position=" + coord(place.lon) + "," + coord(place.lat)
            + nameParam("&name=", place.name)
            + "&src=" + SRC + "&coordinate=gps&callnative=0");
    }

    private static String labeled(double lat, double lon, String name) {
        String point = coord(lat) + "," + coord(lon);
        if (TextUtils.isEmpty(name)) {
            return point;
        }
        return point + "(" + enc(name) + ")";
    }

    private static String nameParam(String key, String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        return key + enc(name);
    }

    private static String coord(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String enc(String value) {
        return value == null ? "" : Uri.encode(value);
    }

    private static Uri parse(String raw) {
        return Uri.parse(raw);
    }

    static final class Place {
        final boolean hasCoord;
        final double lat;
        final double lon;
        final String name;
        final boolean route;
        final boolean hasFrom;
        final double fromLat;
        final double fromLon;

        Place(double lat, double lon, String name, boolean route, Double fromLat, Double fromLon) {
            this.hasCoord = !Double.isNaN(lat) && !Double.isNaN(lon);
            this.lat = lat;
            this.lon = lon;
            this.name = name;
            this.route = route && hasCoord;
            this.hasFrom = fromLat != null && fromLon != null && !Double.isNaN(fromLat) && !Double.isNaN(fromLon);
            this.fromLat = this.hasFrom ? fromLat : 0;
            this.fromLon = this.hasFrom ? fromLon : 0;
        }
    }

    private static final class MapApp {
        final String packageName;
        final String label;
        final Drawable icon;
        final int kind;

        MapApp(String packageName, String label, Drawable icon, int kind) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.kind = kind;
        }
    }
}
