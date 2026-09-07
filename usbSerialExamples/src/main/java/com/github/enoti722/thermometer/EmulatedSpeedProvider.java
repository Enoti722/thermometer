package com.github.enoti722.thermometer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Random;

/**
 * Эмуляция скорости по тому же принципу, что и температура в {@link EmulatedThermometerDevice}: плавный вход к случайной цели внутри диапазона и небольшой шум.
 */
public final class EmulatedSpeedProvider implements ISpeedProvider {
    private static final String TAG = "EmulatedSpeed";
    private static final int UPDATE_INTERVAL_MS = 1000;
    /** Максимальное изменение км/ч за один такт при движении к цели. */
    private static final float CHANGE_RATE_KMH = 2.5f;
    /** Вероятность задать новую цель за такт (как доля температурного примера). */
    private static final float TARGET_CHANGE_CHANCE = 0.08f;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Listener listener;
    private Runnable tick;
    private boolean started;

    private float currentKmh;
    private float targetKmh;

    public EmulatedSpeedProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        stop();
        if (listener == null) {
            return;
        }
        float min = SpeedEmulationPreferences.getMinKmh(appContext);
        float max = SpeedEmulationPreferences.getMaxKmh(appContext);
        if (max < min) {
            float t = min;
            min = max;
            max = t;
        }
        if (max - min < 0.5f) {
            max = min + 0.5f;
        }
        currentKmh = (min + max) * 0.5f;
        targetKmh = min + random.nextFloat() * (max - min);
        started = true;

        tick = new Runnable() {
            @Override
            public void run() {
                if (!started) {
                    return;
                }
                float lo = SpeedEmulationPreferences.getMinKmh(appContext);
                float hi = SpeedEmulationPreferences.getMaxKmh(appContext);
                if (hi < lo) {
                    float x = lo;
                    lo = hi;
                    hi = x;
                }
                if (hi - lo < 0.5f) {
                    hi = lo + 0.5f;
                }
                if (random.nextFloat() < TARGET_CHANGE_CHANCE) {
                    targetKmh = lo + random.nextFloat() * (hi - lo);
                    Log.d(TAG, "new target speed km/h: " + targetKmh);
                }
                float diff = targetKmh - currentKmh;
                if (Math.abs(diff) > 0.01f) {
                    float step = Math.signum(diff) * Math.min(Math.abs(diff), CHANGE_RATE_KMH);
                    currentKmh += step;
                }
                currentKmh += (random.nextFloat() - 0.5f) * 0.6f;
                currentKmh = Math.max(lo, Math.min(hi, currentKmh));
                if (listener != null) {
                    listener.onSpeedKmh(currentKmh);
                }
                handler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
        handler.post(tick);
    }

    @Override
    public void stop() {
        started = false;
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
    }
}
