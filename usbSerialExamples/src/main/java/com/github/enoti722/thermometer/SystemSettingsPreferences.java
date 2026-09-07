package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Дополнительные системные настройки (тот же файл prefs, что и {@link UiScalingPreferences}).
 */
public final class SystemSettingsPreferences {

    public static final String KEY_TEMP_SPEED_GATE_KMH = "temp_speed_gate_kmh";
    public static final String KEY_TEMP_AVG_WINDOW_MIN = "temp_avg_window_min";
    public static final String KEY_THERMOMETER_OFFSET_C = "thermometer_offset_c";
    public static final String KEY_NOTIFICATION_ALERT_SOUND = "notification_alert_sound";
    public static final String KEY_NOTIFICATION_SOUND_MODE = "notification_sound_mode";
    /** Сколько держать на экране последнее показание и пытаться переподключиться после пропажи сигнала (сек.). */
    public static final String KEY_SENSOR_HOLD_SEC = "sensor_signal_hold_sec";

    /**
     * Варианты проигрывания сигнала. Значения хранятся в prefs, поэтому нумерация неизменна,
     * а новые режимы дописываются только в конец; порядок совпадает с {@code R.array.notification_sound_modes}.
     */
    /** Как исторически: USAGE_NOTIFICATION и кратковременный аудиофокус MAY_DUCK. */
    public static final int SOUND_MODE_NOTIFICATION_FOCUS = 0;
    /** Без запроса аудиофокуса, USAGE_ASSISTANCE_SONIFICATION. */
    public static final int SOUND_MODE_SONIFICATION = 1;
    /** Без запроса аудиофокуса, прямо в медиаканал (USAGE_MEDIA). */
    public static final int SOUND_MODE_MEDIA = 2;
    /** USAGE_ASSISTANCE_NAVIGATION_GUIDANCE: канал, который магнитолы обычно подмешивают к источнику. */
    public static final int SOUND_MODE_NAVIGATION = 3;
    /** USAGE_ALARM: отдельный канал будильника со своей громкостью. */
    public static final int SOUND_MODE_ALARM = 4;
    /** USAGE_ASSISTANCE_SONIFICATION + FLAG_AUDIBILITY_ENFORCED: канал «обязательно слышимых» звуков. */
    public static final int SOUND_MODE_SYSTEM_ENFORCED = 5;
    /**
     * Не свой звуковой файл, а системный эффект интерфейса ({@code AudioManager.playSoundEffect}):
     * единственный вариант, где приложение вообще не открывает собственный аудиопоток.
     */
    public static final int SOUND_MODE_UI_EFFECT = 6;
    /**
     * Тоже без своего аудиопотока: сигнал проигрывает система как звук обычного уведомления
     * ({@link android.app.NotificationChannel#setSound}), но файл при этом наш.
     */
    public static final int SOUND_MODE_SYSTEM_NOTIFICATION = 7;
    /**
     * Пустой опыт: кнопка проверки проходит весь путь, но звука не проигрывает вообще.
     * Нужен, чтобы отделить «музыку роняет наш звук» от «музыку роняет что-то другое в приложении».
     */
    public static final int SOUND_MODE_SILENT_CONTROL = 8;

    private static final int SOUND_MODE_MAX = SOUND_MODE_SILENT_CONTROL;

    /** Объявлять ли сервис медиаплеером (FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK). */
    public static final String KEY_DECLARE_MEDIA_FGS = "declare_media_playback_service";
    public static final boolean DEFAULT_DECLARE_MEDIA_FGS = true;

    /** Слать ли плееру команду «играть» после того, как сигнал отзвучал. */
    public static final String KEY_RESTORE_PLAYBACK_AFTER_ALERT = "restore_playback_after_alert";
    public static final boolean DEFAULT_RESTORE_PLAYBACK_AFTER_ALERT = false;

    public static final float DEFAULT_TEMP_SPEED_GATE_KMH = 30f;
    public static final boolean DEFAULT_NOTIFICATION_ALERT_SOUND = false;
    /**
     * По умолчанию — режим без аудиофокуса: именно фокус на части магнитол ставил штатный плеер
     * на паузу без возврата. Разница между режимами видна только на живом ГУ, см. {@link NotificationAlertSound}.
     */
    public static final int DEFAULT_NOTIFICATION_SOUND_MODE = SOUND_MODE_SONIFICATION;
    public static final int DEFAULT_TEMP_AVG_WINDOW_MIN = 5;
    public static final float DEFAULT_THERMOMETER_OFFSET_C = 0f;

    public static final int SPEED_GATE_SEEK_MAX = 120;
    public static final int TEMP_AVG_MIN_MINUTES = 1;
    public static final int TEMP_AVG_MAX_MINUTES = 30;

    /** SeekBar 0…200 → −10…+10 °C с шагом 0,1. */
    public static final int OFFSET_SEEK_MAX = 200;

    public static final int DEFAULT_SENSOR_HOLD_SEC = 3 * 60;
    /** Шаг ползунка удержания: даёт и полминуты «на проверку», и крупные значения в минутах. */
    public static final int SENSOR_HOLD_STEP_SEC = 30;
    /** SeekBar 1…30 с шагом 30 с → 30 сек … 15 мин. */
    public static final int SENSOR_HOLD_SEEK_MIN = 1;
    public static final int SENSOR_HOLD_SEEK_MAX = 30;

    private SystemSettingsPreferences() {}

    public static SharedPreferences prefs(Context context) {
        return UiScalingPreferences.prefs(context);
    }

    public static float tempSpeedGateKmh(Context context) {
        float v = prefs(context).getFloat(KEY_TEMP_SPEED_GATE_KMH, DEFAULT_TEMP_SPEED_GATE_KMH);
        return Math.max(0f, Math.min(SPEED_GATE_SEEK_MAX, v));
    }

    public static int tempAverageWindowMinutes(Context context) {
        int m = prefs(context).getInt(KEY_TEMP_AVG_WINDOW_MIN, DEFAULT_TEMP_AVG_WINDOW_MIN);
        return Math.max(TEMP_AVG_MIN_MINUTES, Math.min(TEMP_AVG_MAX_MINUTES, m));
    }

    public static long tempAverageWindowMs(Context context) {
        return tempAverageWindowMinutes(context) * 60_000L;
    }

    public static float thermometerOffsetDegrees(Context context) {
        float o = prefs(context).getFloat(KEY_THERMOMETER_OFFSET_C, DEFAULT_THERMOMETER_OFFSET_C);
        return Math.max(-10f, Math.min(10f, o));
    }

    public static int thermometerOffsetSeekProgress(Context context) {
        return Math.round((thermometerOffsetDegrees(context) + 10f) * 10f);
    }

    public static float seekProgressToOffsetDegrees(int progress) {
        float o = progress / 10f - 10f;
        return Math.max(-10f, Math.min(10f, o));
    }

    /**
     * Длительность удержания последнего показания при пропаже сигнала. Значение из prefs подтягивается
     * к сетке ползунка, поэтому не зависит от того, чем оно было записано.
     */
    public static int sensorSignalHoldSeconds(Context context) {
        int sec = prefs(context).getInt(KEY_SENSOR_HOLD_SEC, DEFAULT_SENSOR_HOLD_SEC);
        return seekProgressToSensorHoldSeconds(Math.round(sec / (float) SENSOR_HOLD_STEP_SEC));
    }

    public static long sensorSignalHoldMs(Context context) {
        return sensorSignalHoldSeconds(context) * 1000L;
    }

    public static int sensorHoldSeekProgress(Context context) {
        return sensorSignalHoldSeconds(context) / SENSOR_HOLD_STEP_SEC;
    }

    public static int seekProgressToSensorHoldSeconds(int progress) {
        int p = Math.max(SENSOR_HOLD_SEEK_MIN, Math.min(SENSOR_HOLD_SEEK_MAX, progress));
        return p * SENSOR_HOLD_STEP_SEC;
    }

    /** Звук при показе окон предупреждений поверх экрана. */
    public static boolean notificationAlertSoundEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATION_ALERT_SOUND, DEFAULT_NOTIFICATION_ALERT_SOUND);
    }

    /**
     * Применяется при следующем поднятии сервиса в foreground, то есть после его перезапуска.
     */
    public static boolean declareMediaPlaybackService(Context context) {
        return prefs(context).getBoolean(KEY_DECLARE_MEDIA_FGS, DEFAULT_DECLARE_MEDIA_FGS);
    }

    public static boolean restorePlaybackAfterAlert(Context context) {
        return prefs(context).getBoolean(KEY_RESTORE_PLAYBACK_AFTER_ALERT,
                DEFAULT_RESTORE_PLAYBACK_AFTER_ALERT);
    }

    /** Способ проигрывания сигнала; неизвестное значение из старых prefs откатывается к умолчанию. */
    public static int notificationSoundMode(Context context) {
        int mode = prefs(context).getInt(KEY_NOTIFICATION_SOUND_MODE, DEFAULT_NOTIFICATION_SOUND_MODE);
        if (mode < SOUND_MODE_NOTIFICATION_FOCUS || mode > SOUND_MODE_MAX) {
            return DEFAULT_NOTIFICATION_SOUND_MODE;
        }
        return mode;
    }

    public static void setNotificationSoundMode(Context context, int mode) {
        int safe = (mode < SOUND_MODE_NOTIFICATION_FOCUS || mode > SOUND_MODE_MAX)
                ? DEFAULT_NOTIFICATION_SOUND_MODE
                : mode;
        prefs(context).edit().putInt(KEY_NOTIFICATION_SOUND_MODE, safe).apply();
    }
}
