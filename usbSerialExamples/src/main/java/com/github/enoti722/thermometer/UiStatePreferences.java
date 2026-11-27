package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;

/** Последний экран UI, чтобы восстановить его на прошивках, очищающих task после Home. */
final class UiStatePreferences {
    private static final String NAME = "ui_state_prefs";
    private static final String KEY_SCREEN = "last_screen";
    private static final String KEY_PACKAGE = "last_app_package";
    private static final String KEY_APP_NAME = "last_app_name";

    static final String SCREEN_CONFIGURED_APPS = "configured_apps";
    static final String SCREEN_ALL_APPS = "all_apps";
    static final String SCREEN_APP_SETTINGS = "app_settings";
    static final String SCREEN_SYSTEM_SETTINGS = "system_settings";
    static final String SCREEN_SPEED = "speed";

    private UiStatePreferences() {}

    static void save(Context context, String screen) {
        prefs(context).edit().putString(KEY_SCREEN, screen).apply();
    }

    static void saveAppSettings(Context context, String packageName, String appName) {
        prefs(context).edit().putString(KEY_SCREEN, SCREEN_APP_SETTINGS)
                .putString(KEY_PACKAGE, packageName).putString(KEY_APP_NAME, appName).apply();
    }

    static String screen(Context context) { return prefs(context).getString(KEY_SCREEN, null); }
    static String packageName(Context context) { return prefs(context).getString(KEY_PACKAGE, null); }
    static String appName(Context context) { return prefs(context).getString(KEY_APP_NAME, null); }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }
}
