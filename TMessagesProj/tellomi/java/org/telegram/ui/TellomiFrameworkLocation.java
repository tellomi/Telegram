/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Honor and other phones without Google Play Services never deliver a fix
 * through the fused client, so the "send my location" row spins forever.
 * This asks the framework LocationManager (GPS, then network) instead.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.WebFile;
import org.telegram.ui.Components.BackupImageView;

import java.util.List;

public final class TellomiFrameworkLocation {

    public interface Listener {
        void onLocation(Location location);
    }

    private TellomiFrameworkLocation() {
    }

    /** Last cached fix from GPS, network, or passive. Null when nothing is cached. */
    @SuppressLint("MissingPermission")
    public static Location lastKnown() {
        try {
            LocationManager lm = manager();
            if (lm == null) {
                return null;
            }
            Location best = null;
            List<String> providers = lm.getProviders(true);
            if (providers != null) {
                for (int i = providers.size() - 1; i >= 0; i--) {
                    Location location = lm.getLastKnownLocation(providers.get(i));
                    best = newer(best, location);
                }
            }
            best = newer(best, safeLast(lm, LocationManager.GPS_PROVIDER));
            best = newer(best, safeLast(lm, LocationManager.NETWORK_PROVIDER));
            best = newer(best, safeLast(lm, LocationManager.PASSIVE_PROVIDER));
            return best;
        } catch (Throwable t) {
            FileLog.e(t, false);
            return null;
        }
    }

    /**
     * One-shot GPS and network updates. The listener is called on the UI thread.
     * A cached fix is delivered immediately when there is one. Call {@link Watch#stop()}.
     */
    @SuppressLint("MissingPermission")
    /**
     * Static map from the server web-file path ({@code mapProvider} 2, OSM tiles).
     * Used when the Google map view cannot draw.
     */
    public static void showOsm(BackupImageView view, Location location) {
        if (view == null || location == null) {
            return;
        }
        int scale = Math.min(2, (int) Math.ceil(AndroidUtilities.density));
        int w = 360;
        int h = 240;
        WebFile webFile = WebFile.createWithGeoPoint(location.getLatitude(), location.getLongitude(), 0, w, h, 15, scale);
        view.setImage(ImageLocation.getForWebFile(webFile), w + "_" + h, null, 0, null);
    }

    public static Watch watch(Listener listener) {
        Watch watch = new Watch(listener);
        watch.start();
        return watch;
    }

    public static final class Watch {
        private final Listener listener;
        private LocationManager manager;
        private LocationListener gps;
        private LocationListener network;
        private boolean stopped;

        private Watch(Listener listener) {
            this.listener = listener;
        }

        @SuppressLint("MissingPermission")
        private void start() {
            AndroidUtilities.runOnUIThread(() -> {
                Location cached = lastKnown();
                if (cached != null) {
                    deliver(cached);
                }
            });
            try {
                manager = manager();
                if (manager == null) {
                    return;
                }
                gps = listen();
                network = listen();
                request(LocationManager.GPS_PROVIDER, gps);
                request(LocationManager.NETWORK_PROVIDER, network);
            } catch (Throwable t) {
                FileLog.e(t, false);
            }
        }

        @SuppressLint("MissingPermission")
        public void stop() {
            stopped = true;
            if (manager == null) {
                return;
            }
            remove(gps);
            remove(network);
            gps = null;
            network = null;
        }

        @SuppressLint("MissingPermission")
        private void request(String provider, LocationListener listener) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper());
                }
            } catch (Throwable t) {
                FileLog.e(t, false);
            }
        }

        private void remove(LocationListener listener) {
            if (listener == null) {
                return;
            }
            try {
                manager.removeUpdates(listener);
            } catch (Throwable t) {
                FileLog.e(t, false);
            }
        }

        private LocationListener listen() {
            return new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    deliver(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
        }

        private void deliver(Location location) {
            if (stopped || location == null || listener == null) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (!stopped) {
                    listener.onLocation(location);
                }
            });
        }
    }

    private static LocationManager manager() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return (LocationManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.LOCATION_SERVICE);
    }

    private static Location safeLast(LocationManager lm, String provider) {
        try {
            return lm.getLastKnownLocation(provider);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Location newer(Location current, Location next) {
        if (next == null) {
            return current;
        }
        if (current == null || next.getTime() > current.getTime()) {
            return next;
        }
        return current;
    }
}
