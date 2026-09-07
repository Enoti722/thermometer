package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Политика автозапуска термосервиса (отдельно от {@link ThermometerPrefsKeys}).
 */
public final class AutostartPreferences {
    private static final String PREFS_NAME = "autostart_settings";

    public static final String KEY_MODE = "autostart_mode";
    public static final String KEY_REFERRER_PATTERN = "autostart_referrer_pattern";

    /**
     * Значение по умолчанию: фрагмент строки {@link android.app.Activity#getReferrer()}, общий для обоих
     * вариантов типичного автозапуска — {@code com.android.settings} и {@code com.android.systemui}.
     * Сравнивается поиском внутри строки, см. {@link #referrerMatches(Context, String)}; полную строку
     * referrer из журнала MainActivity указывать не нужно.
     */
    public static final String DEFAULT_REFERRER_PATTERN = "com[.]android[.](settings|systemui)";

    /** Полностью отключены boot‑receiver и сценарий по referrer в {@link MainActivity}. */
    public static final int MODE_OFF = 0;
    /** {@link BootCompletedReceiver} может поднимать {@link UsbService} после загрузки. */
    public static final int MODE_BOOT_COMPLETED = 1;
    /**
     * Система открывает {@link MainActivity}; если запуск опознан как автоматический — старт сервиса и {@code finish()}.
     * Опознаётся по форме intent (не launcher-запуск) либо по совпадению referrer с шаблоном, см. MainActivity.
     */
    public static final int MODE_SYSTEM_ACTIVITY_REFERRER = 2;

    private AutostartPreferences() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getMode(Context ctx) {
        return prefs(ctx).getInt(KEY_MODE, MODE_SYSTEM_ACTIVITY_REFERRER);
    }

    public static void setMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_MODE, mode).apply();
    }

    @Nullable
    public static String getReferrerPattern(Context ctx) {
        return prefs(ctx).getString(KEY_REFERRER_PATTERN, DEFAULT_REFERRER_PATTERN);
    }

    public static void setReferrerPattern(Context ctx, String pattern) {
        prefs(ctx).edit().putString(KEY_REFERRER_PATTERN, pattern != null ? pattern : "").apply();
    }

    public static boolean isBootCompletedPathEnabled(Context ctx) {
        return getMode(ctx) == MODE_BOOT_COMPLETED;
    }

    /**
     * Сохранённый шаблон: сначала полное совпадение строки referrer, затем поиск шаблона <b>внутри</b> строки
     * ({@link java.util.regex.Matcher#find()}), при ошибке regex — обычная подстрока.
     * <p>
     * Именно поиск фрагмента, а не {@link Pattern#matches}: один и тот же автозапуск даёт разные referrer
     * ({@code android-app://com.android.settings} и {@code android-app://com.android.systemui}), и шаблон
     * задаётся общей для них частью — по всей длине такая строка не совпала бы никогда.
     */
    public static boolean referrerMatches(Context ctx, @Nullable String actualReferrer) {
        String pattern = getReferrerPattern(ctx);
        if (TextUtils.isEmpty(pattern)) {
            return false;
        }
        String p = pattern.trim();
        String a = actualReferrer != null ? actualReferrer : "";
        if (a.equals(p)) {
            return true;
        }
        try {
            return Pattern.compile(p).matcher(a).find();
        } catch (PatternSyntaxException e) {
            return a.contains(p);
        }
    }
}
