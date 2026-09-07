package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Настройки эмуляции скорости (отдельно от термометра {@code termometer_sp}).
 */
public final class SpeedEmulationPreferences {

    public static final String PREFS_NAME = "speed_emulation_sp";
    public static final String KEY_ENABLED = "emulation_enabled";
    public static final String KEY_MIN_KMH = "min_kmh";
    public static final String KEY_MAX_KMH = "max_kmh";

    public static final float DEFAULT_MIN_KMH = 0f;
    public static final float DEFAULT_MAX_KMH = 120f;

    private SpeedEmulationPreferences() {}

    @NonNull
    public static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEmulationEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static float getMinKmh(@NonNull Context context) {
        return prefs(context).getFloat(KEY_MIN_KMH, DEFAULT_MIN_KMH);
    }

    public static float getMaxKmh(@NonNull Context context) {
        return prefs(context).getFloat(KEY_MAX_KMH, DEFAULT_MAX_KMH);
    }
}
