/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Chooser for map apps installed on the device. One app opens directly.
 * None falls back to the AMap web marker page. This does not replace the
 * in-app map view.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class TellomiMapApps {

    private static final String[] KNOWN_PACKAGES = new String[]{
            TellomiMapUris.AMAP,
            TellomiMapUris.BAIDU,
            TellomiMapUris.TENCENT,
            TellomiMapUris.PETAL,
            TellomiMapUris.GOOGLE
    };

    private TellomiMapApps() {
    }

    /**
     * Google Play services is what lets the bundled Google map view draw.
     * Other map providers (Huawei Map Kit) do not need it. When this is false,
     * a location tap opens the installed-app chooser instead of the
     * "Install Google Maps?" dialog.
     */
    public static boolean inAppMapCanRender() {
        try {
            IMapsProvider provider = ApplicationLoader.getMapsProvider();
            if (provider == null) {
                return false;
            }
            if (!TellomiMapUris.GOOGLE.equals(provider.getMapsAppPackageName())) {
                return true;
            }
            if (ApplicationLoader.applicationContext == null) {
                return false;
            }
            int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ApplicationLoader.applicationContext);
            return status == ConnectionResult.SUCCESS;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    public static void showMapAppChooser(BaseFragment fragment, double latitude, double longitude, String name, boolean directions) {
        if (fragment == null) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        List<InstalledMap> apps;
        try {
            apps = installedMaps(activity.getPackageManager(), activity.getPackageName(), latitude, longitude, name, directions);
        } catch (Throwable t) {
            FileLog.e(t);
            apps = new ArrayList<>();
        }
        if (apps.isEmpty()) {
            open(activity, null, TellomiMapUris.webFallback(latitude, longitude, name), latitude, longitude, name);
            return;
        }
        if (apps.size() == 1) {
            InstalledMap only = apps.get(0);
            open(activity, only.packageName, only.uri, latitude, longitude, name);
            return;
        }
        showSheet(fragment, activity, apps, latitude, longitude, name, directions);
    }

    private static void showSheet(BaseFragment fragment, Activity activity, List<InstalledMap> apps, double latitude, double longitude, String name, boolean directions) {
        Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        BottomSheet[] sheet = new BottomSheet[1];
        for (int i = 0; i < apps.size(); i++) {
            InstalledMap app = apps.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Theme.getSelectorDrawable(false, resourcesProvider));
            int pad = AndroidUtilities.dp(16);
            row.setPadding(pad, 0, pad, 0);

            ImageView icon = new ImageView(activity);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (app.icon != null) {
                icon.setImageDrawable(app.icon);
            }
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            iconLp.rightMargin = AndroidUtilities.dp(16);
            row.addView(icon, iconLp);

            TextView label = new TextView(activity);
            label.setText(app.label);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            label.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            row.setOnClickListener(v -> {
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
                open(activity, app.packageName, app.uri, latitude, longitude, name);
            });
            list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(activity, false, resourcesProvider);
        builder.setTitle(LocaleController.getString(directions ? R.string.GetDirections : R.string.OpenInMaps));
        builder.setCustomView(list);
        sheet[0] = builder.create();
        if (fragment.showDialog(sheet[0]) == null) {
            try {
                sheet[0].show();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }

    private static List<InstalledMap> installedMaps(PackageManager pm, String selfPackage, double latitude, double longitude, String name, boolean directions) {
        ArrayList<InstalledMap> apps = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < KNOWN_PACKAGES.length; i++) {
            String pkg = KNOWN_PACKAGES[i];
            if (!isInstalled(pm, pkg)) {
                continue;
            }
            String uri = TellomiMapUris.uriFor(pkg, latitude, longitude, name, directions);
            add(apps, seen, pm, pkg, null, uri);
        }
        try {
            Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse(TellomiMapUris.geoUri(latitude, longitude, name)));
            List<ResolveInfo> resolved = pm.queryIntentActivities(geo, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null) {
                for (int i = 0; i < resolved.size(); i++) {
                    ResolveInfo info = resolved.get(i);
                    if (info == null || info.activityInfo == null) {
                        continue;
                    }
                    String pkg = info.activityInfo.packageName;
                    if (pkg == null || pkg.equals(selfPackage)) {
                        continue;
                    }
                    add(apps, seen, pm, pkg, info, TellomiMapUris.geoUri(latitude, longitude, name));
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return apps;
    }

    private static void add(ArrayList<InstalledMap> apps, HashSet<String> seen, PackageManager pm, String pkg, ResolveInfo info, String uri) {
        if (pkg == null || uri == null || !seen.add(pkg)) {
            return;
        }
        CharSequence label = pkg;
        try {
            if (info != null) {
                CharSequence loaded = info.loadLabel(pm);
                if (!TextUtils.isEmpty(loaded)) {
                    label = loaded;
                }
            } else {
                CharSequence loaded = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                if (!TextUtils.isEmpty(loaded)) {
                    label = loaded;
                }
            }
        } catch (Throwable ignored) {
        }
        Drawable icon = null;
        try {
            icon = pm.getApplicationIcon(pkg);
        } catch (Throwable ignored) {
        }
        apps.add(new InstalledMap(pkg, label, icon, uri));
    }

    private static boolean isInstalled(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void open(Activity activity, String packageName, String uri, double latitude, double longitude, String name) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            if (!TextUtils.isEmpty(packageName)) {
                intent.setPackage(packageName);
            }
            activity.startActivity(intent);
        } catch (Throwable t) {
            FileLog.e(t);
            if (TextUtils.isEmpty(packageName)) {
                return;
            }
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TellomiMapUris.webFallback(latitude, longitude, name))));
            } catch (Throwable again) {
                FileLog.e(again);
            }
        }
    }

    private static final class InstalledMap {
        final String packageName;
        final CharSequence label;
        final Drawable icon;
        final String uri;

        InstalledMap(String packageName, CharSequence label, Drawable icon, String uri) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.uri = uri;
        }
    }
}
