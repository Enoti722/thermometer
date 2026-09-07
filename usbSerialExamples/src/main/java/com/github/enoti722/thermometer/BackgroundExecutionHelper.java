package com.github.enoti722.thermometer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Обход ограничений фона на части автомагнитол не делается «тихим» разрешением: нужны действия пользователя
 * в системных экранах. Здесь — безопасные переходы в настройки.
 */
public final class BackgroundExecutionHelper {
    private static final String TAG = "BackgroundExec";

    private BackgroundExecutionHelper() {}

    public static boolean isIgnoringBatteryOptimizations(@NonNull Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** Диалог «Не оптимизировать» или fallback на страницу приложения. Требует {@code REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}. */
    public static void requestIgnoreBatteryOptimizations(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS not available", e);
            openApplicationDetails(context);
        }
    }

    public static void openApplicationDetails(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openApplicationDetails failed", e);
        }
    }
}
