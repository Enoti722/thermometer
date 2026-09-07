package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер для управления настройками приложения
 */
public class SettingsManager {
    private static final String PREFS_NAME = "AppSettingsPrefs";
    private static final String KEY_APP_SETTINGS = "app_settings";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ensureSystemAppSettings();
    }

    /**
     * Запись «Система» (рабочий стол) существует всегда: список настроенных приложений рисует её
     * и при пустом хранилище — со значениями по умолчанию, где оверлей включён. Но для пакета без
     * записи {@link #isOverlayEnabledForApp} отвечает false, поэтому на чистой установке оверлея
     * на рабочем столе не было, хотя переключатель в настройках стоял включённым. Заводим запись
     * сразу, чтобы сохранённое совпадало с показанным.
     *
     * @return сохранённая запись «Система» (при необходимости только что созданная)
     */
    public AppSettings ensureSystemAppSettings() {
        AppSettings existing = getAppSettings(AppSettings.SYSTEM_PACKAGE_NAME);
        if (existing != null) {
            return existing;
        }
        AppSettings seeded = new AppSettings(AppSettings.SYSTEM_PACKAGE_NAME, AppSettings.SYSTEM_APP_NAME);
        addOrUpdateAppSettings(seeded);
        return seeded;
    }

    // Настройки для конкретных приложений
    public List<AppSettings> getAppSettingsList() {
        String json = prefs.getString(KEY_APP_SETTINGS, "[]");
        List<AppSettings> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new AppSettings(
                        obj.getString("packageName"),
                        obj.getString("appName"),
                        obj.getBoolean("overlayEnabled"),
                        obj.getBoolean("notificationsEnabled"),
                        UsbService.ENABLE_GPS_SPEED && obj.optBoolean("overlaySpeedVisible", false),
                        obj.optInt("overlayAlphaTop", AppSettings.DEFAULT_OVERLAY_ALPHA_TOP),
                        obj.optInt("overlayAlphaBottom", AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM),
                        obj.optInt("overlayPositionXPercent", 50),
                        obj.optInt("overlayPositionYPercent", 5)
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveAppSettingsList(List<AppSettings> list) {
        JSONArray array = new JSONArray();
        try {
            for (AppSettings settings : list) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", settings.getPackageName());
                obj.put("appName", settings.getAppName());
                obj.put("overlayEnabled", settings.isOverlayEnabled());
                obj.put("notificationsEnabled", settings.isNotificationsEnabled());
                if (UsbService.ENABLE_GPS_SPEED) {
                    obj.put("overlaySpeedVisible", settings.isOverlaySpeedVisible());
                }
                obj.put("overlayAlphaTop", settings.getOverlayAlphaTop());
                obj.put("overlayAlphaBottom", settings.getOverlayAlphaBottom());
                obj.put("overlayPositionXPercent", settings.getOverlayPositionXPercent());
                obj.put("overlayPositionYPercent", settings.getOverlayPositionYPercent());
                array.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefs.edit().putString(KEY_APP_SETTINGS, array.toString()).apply();
    }

    public AppSettings getAppSettings(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        List<AppSettings> list = getAppSettingsList();
        for (AppSettings settings : list) {
            if (settings.getPackageName().equals(packageName)) {
                return settings;
            }
        }
        int colon = packageName.indexOf(':');
        if (colon > 0) {
            String basePackage = packageName.substring(0, colon);
            for (AppSettings settings : list) {
                if (settings.getPackageName().equals(basePackage)) {
                    return settings;
                }
            }
        }
        return null;
    }

    public void addOrUpdateAppSettings(AppSettings settings) {
        List<AppSettings> list = getAppSettingsList();
        // Удаляем старую запись, если есть
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getPackageName().equals(settings.getPackageName())) {
                list.remove(i);
                break;
            }
        }
        list.add(settings);
        saveAppSettingsList(list);
    }

    public void removeAppSettings(String packageName) {
        List<AppSettings> list = getAppSettingsList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getPackageName().equals(packageName)) {
                list.remove(i);
                break;
            }
        }
        saveAppSettingsList(list);
    }

    // Получить эффективные настройки для приложения
    // ВАЖНО: Если настройки не заданы, возвращаем false (оверлей скрыт по умолчанию)
    public boolean isOverlayEnabledForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.isOverlayEnabled();
        }
        return false; // по умолчанию ВЫКЛЮЧЕНО для приложений без настроек
    }

    public boolean isNotificationsEnabledForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.isNotificationsEnabled();
        }
        return false; // по умолчанию ВЫКЛЮЧЕНО для приложений без настроек
    }

    public int getOverlayPositionXPercentForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.getOverlayPositionXPercent();
        }
        return 50; // центр по умолчанию
    }

    public int getOverlayPositionYPercentForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.getOverlayPositionYPercent();
        }
        return 5; // верх экрана по умолчанию
    }

    public boolean isOverlaySpeedVisibleForApp(String packageName) {
        if (!UsbService.ENABLE_GPS_SPEED) {
            return false;
        }
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.isOverlaySpeedVisible();
        }
        return false; // по умолчанию ВЫКЛЮЧЕНО для приложений без настроек
    }

    public int getOverlayAlphaTopForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.getOverlayAlphaTop();
        }
        return AppSettings.DEFAULT_OVERLAY_ALPHA_TOP;
    }

    public int getOverlayAlphaBottomForApp(String packageName) {
        AppSettings appSettings = getAppSettings(packageName);
        if (appSettings != null) {
            return appSettings.getOverlayAlphaBottom();
        }
        return AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM;
    }
}
