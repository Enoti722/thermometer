package com.github.enoti722.thermometer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Реальный GPS: скорость из {@link Location#getSpeed()} (м/с → км/ч).
 */
public final class GpsSpeedProvider implements ISpeedProvider {
    private static final String TAG = "GpsSpeedProvider";

    private final Service service;
    private Listener listener;
    private LocationManager locationManager;
    private boolean started;

    private final LocationListener locationListener = this::dispatchLocation;

    public GpsSpeedProvider(Service service) {
        this.service = service;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void start() {
        stop();
        if (listener == null) {
            return;
        }
        if (service.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onSpeedKmh(Listener.SPEED_UNAVAILABLE);
            return;
        }
        locationManager = (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            listener.onSpeedKmh(Listener.SPEED_UNAVAILABLE);
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f,
                    locationListener, Looper.getMainLooper());
            started = true;
        } catch (Exception e) {
            Log.e(TAG, "requestLocationUpdates failed", e);
            listener.onSpeedKmh(Listener.SPEED_UNAVAILABLE);
        }
    }

    @Override
    public void stop() {
        started = false;
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception e) {
                Log.w(TAG, "removeUpdates: " + e.getMessage());
            }
            locationManager = null;
        }
    }

    private void dispatchLocation(@NonNull Location location) {
        if (listener == null) {
            return;
        }
        float kmh = 0f;
        if (location.hasSpeed()) {
            float speedMs = location.getSpeed();
            // С API 26 величина ниже заявленной погрешности — по сути «не движется» (как в отчётах навигаторов).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                float accMs = location.getSpeedAccuracyMetersPerSecond();
                if (accMs > 0f && Math.abs(speedMs) <= accMs) {
                    speedMs = 0f;
                }
            }
            kmh = speedMs * 3.6f;
        }
        listener.onSpeedKmh(kmh);
    }
}
