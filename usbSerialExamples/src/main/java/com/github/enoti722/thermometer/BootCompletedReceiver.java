package com.github.enoti722.thermometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

/**
 * Поднимает {@link UsbService} после загрузки (и после обновления APK), если выбрано устройство.
 * <p>
 * Логирует каждый intent, который система доставила этому receiver — для этого его action нужно объявить
 * в AndroidManifest.xml. OEM-события с другими именами сюда не попадут, пока явно не добавите отдельный intent-filter с их именем action.
 * <p>
 * См. {@link Intent}: на каждое событие логируем TIME_CHANGED (manifest: TIME_SET), TIMEZONE_CHANGED, питание;
 * SCREEN_ON/USER_PRESENT — одна содержательная строка до перезапуска процесса (обычно = холодная перезагрузка ГУ).
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    static boolean hasConfiguredThermometerDevice(SharedPreferences sp) {
        if (sp.getBoolean(ThermometerPrefsKeys.IS_EMULATED, false)) {
            return true;
        }
        String serial = sp.getString(ThermometerPrefsKeys.DEVICE_SERIAL, "");
        if (serial != null && !serial.trim().isEmpty()) {
            return true;
        }
        int legacy = sp.getInt(ThermometerPrefsKeys.LEGACY_DEVICE_INT, 0);
        return legacy != 0 && legacy != EmulatedThermometerDevice.EMULATED_USB_DEVICE_ID;
    }

    private static volatile boolean diagScreenOnLoggedThisProcess;
    private static volatile boolean diagUserPresentLoggedThisProcess;

    /**
     * После полной загрузки / замены пакета / QUICKBOOT — когда CE-хранилище обычно доступно.
     * Не путать с питанием и USER_INITIALIZE: те только диагностируются в лог.
     */
    private static boolean shouldAutoStartUsbService(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
    }

    /** События «косвенно связаны с включением» / временем — без автоподъёма UsbService. */
    private static boolean handleIndirectBootHints(Context app, String action, Intent intent) {
        if (Intent.ACTION_TIME_CHANGED.equals(action)) {
            LocalDiagLog.i(app, TAG, "TIME_SET/TIME_CHANGED (косвенный признак) — UsbService не стартуем");
            return true;
        }
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            String tz = intent.getStringExtra(Intent.EXTRA_TIMEZONE);
            LocalDiagLog.i(app, TAG, "TIMEZONE_CHANGED (косвенный признак) zone=" + tz + " — не стартуем UsbService");
            return true;
        }
        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            if (!diagScreenOnLoggedThisProcess) {
                diagScreenOnLoggedThisProcess = true;
                LocalDiagLog.i(app, TAG,
                        "SCREEN_ON (дебаг, первый в этом процессе после загрузки) — UsbService не стартуем");
            }
            return true;
        }
        if (Intent.ACTION_USER_PRESENT.equals(action)) {
            if (!diagUserPresentLoggedThisProcess) {
                diagUserPresentLoggedThisProcess = true;
                LocalDiagLog.i(app, TAG,
                        "USER_PRESENT (дебаг, первый в этом процессе после загрузки) — не стартуем UsbService");
            }
            return true;
        }
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            LocalDiagLog.i(app, TAG,
                    "POWER_CONNECTED (косвенный признак) EXTRA_PLUGGED=" + plugged + " — UsbService не стартуем");
            return true;
        }
        if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            LocalDiagLog.i(app, TAG, "POWER_DISCONNECTED (косвенный признак) — UsbService не стартуем");
            return true;
        }
        if (Intent.ACTION_USER_INITIALIZE.equals(action)) {
            LocalDiagLog.i(app, TAG,
                    "USER_INITIALIZE (косвенный признак) — UsbService не стартуем; на многих устройствах broadcast только для привилегированных receiver");
            return true;
        }
        return false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();

        String actionForLog = intent != null ? intent.getAction() : null;
        int extraKeys = 0;
        if (intent != null) {
            Bundle ex = intent.getExtras();
            if (ex != null) {
                extraKeys = ex.size();
            }
        }
        LocalDiagLog.i(app, TAG, "onReceive invoked action=" + actionForLog + ", extraKeys=" + extraKeys);

        if (intent == null) {
            LocalDiagLog.line(app, "W", TAG, "intent is null");
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            LocalDiagLog.i(app, TAG, "ignored: null action");
            return;
        }

        // Direct Boot: prefs в CE могут быть пустые; не дергаем UsbService, только наблюдаем в логе.
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            LocalDiagLog.i(app, TAG,
                    "LOCKED_BOOT_COMPLETED — автозапуск сервиса пропускаем (credential-encrypted prefs ещё могут быть недоступны)");
            return;
        }

        if (handleIndirectBootHints(app, action, intent)) {
            return;
        }

        if (!shouldAutoStartUsbService(action)) {
            LocalDiagLog.i(app, TAG, "received but not handled for UsbService start: " + action);
            return;
        }

        LocalDiagLog.i(app, TAG, "handling auto-start trigger: " + action);

        if (!AutostartPreferences.isBootCompletedPathEnabled(app)) {
            LocalDiagLog.i(app, TAG, "BootCompleted autostart skipped (mode is not BOOT_COMPLETED)");
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(ThermometerPrefsKeys.NAME, Context.MODE_PRIVATE);
        if (!hasConfiguredThermometerDevice(sp)) {
            Log.d(TAG, "Auto-start skipped: no USB/emulated device configured");
            LocalDiagLog.i(app, TAG, "auto-start skipped (no device in prefs)");
            return;
        }

        UsbServiceStarter.start(context);
        Log.i(TAG, "UsbService start requested after " + action);
        LocalDiagLog.i(app, TAG, "UsbServiceStarter invoked after " + action);
    }
}
