package org.telegram.ui.tellomi.feed;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import java.util.List;
import java.util.Locale;

/**
 * City-only location. The coordinate is used once for {@link Geocoder} and then
 * dropped. Only {@link Address#getLocality()} is read, so a missing geocoder
 * (no Google services) falls through to the manual city list.
 */
public final class TellomiDiscoverLocation {

    public static final int REQUEST_CODE = 44031;

    public interface Callback {
        void onCity(String code);

        void onDenied();
    }

    private TellomiDiscoverLocation() {
    }

    public static boolean hasCoarse(Activity activity) {
        if (activity == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCoarse(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 23) {
            return;
        }
        activity.requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE);
    }

    /** Network or passive provider only. GPS is not requested. */
    public static void locateCity(final Context context, final Callback callback) {
        if (context == null) {
            callback.onCity(null);
            return;
        }
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            callback.onCity(null);
            return;
        }
        String provider = null;
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                provider = LocationManager.NETWORK_PROVIDER;
            } else if (manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                provider = LocationManager.PASSIVE_PROVIDER;
            }
        } catch (Exception ignored) {
            callback.onCity(null);
            return;
        }
        if (provider == null) {
            callback.onCity(null);
            return;
        }
        try {
            Location last = manager.getLastKnownLocation(provider);
            if (last != null) {
                geocode(context, last.getLatitude(), last.getLongitude(), callback);
                return;
            }
            manager.requestSingleUpdate(provider, location -> {
                if (location == null) {
                    callback.onCity(null);
                    return;
                }
                geocode(context, location.getLatitude(), location.getLongitude(), callback);
            }, Looper.getMainLooper());
        } catch (SecurityException ignored) {
            callback.onDenied();
        } catch (Exception ignored) {
            callback.onCity(null);
        }
    }

    private static void geocode(final Context context, final double latitude, final double longitude, final Callback callback) {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                String code = null;
                try {
                    if (Geocoder.isPresent()) {
                        Geocoder geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address address = addresses.get(0);
                            code = TellomiDiscoverCity.codeFromLocality(address.getLocality(), address.getCountryCode());
                        }
                    }
                } catch (Throwable ignored) {
                    code = null;
                }
                final String resolved = code;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onCity(resolved);
                    }
                });
            }
        });
    }
}
