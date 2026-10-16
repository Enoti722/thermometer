package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

/**
 * Однократная миграция: старый ключ {@code device} (getDeviceId) → {@link ThermometerPrefsKeys#DEVICE_SERIAL},
 * если устройство уже видно в списке USB и серийник известен.
 */
final class ThermometerPrefsMigration {
    private ThermometerPrefsMigration() {}

    static void tryMigrateLegacyDeviceId(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences sp = app.getSharedPreferences(ThermometerPrefsKeys.NAME, Context.MODE_PRIVATE);
        if (sp.getBoolean(ThermometerPrefsKeys.IS_EMULATED, false)) {
            return;
        }
        String existing = sp.getString(ThermometerPrefsKeys.DEVICE_SERIAL, "");
        if (existing != null && !existing.trim().isEmpty()) {
            return;
        }
        int legacyId = sp.getInt(ThermometerPrefsKeys.LEGACY_DEVICE_INT, 0);
        if (legacyId == 0 || legacyId == EmulatedThermometerDevice.EMULATED_USB_DEVICE_ID) {
            return;
        }
        UsbManager usbManager = (UsbManager) app.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        }
        UsbDevice match = null;
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            if (v.getDeviceId() == legacyId) {
                match = v;
                break;
            }
        }
        if (match == null) {
            return;
        }
        if (!usbManager.hasPermission(match)) {
            LocalDiagLog.i(app, "ThermometerPrefsMigration",
                    "legacy device id=" + legacyId + " visible, USB permission is required before reading serial");
            return;
        }
        String sn = UsbDeviceBySerial.serialNumberIfPermitted(usbManager, match);
        SharedPreferences.Editor ed = sp.edit()
                .putInt(ThermometerPrefsKeys.DEVICE_VENDOR_ID, match.getVendorId())
                .putInt(ThermometerPrefsKeys.DEVICE_PRODUCT_ID, match.getProductId())
                .remove(ThermometerPrefsKeys.LEGACY_DEVICE_INT);
        if (!TextUtils.isEmpty(sn)) {
            ed.putString(ThermometerPrefsKeys.DEVICE_SERIAL, sn);
            ed.commit();
            LocalDiagLog.i(app, "ThermometerPrefsMigration",
                    "migrated legacy device id=" + legacyId + " → serial (len=" + sn.length() + ")");
        } else {
            ed.putString(ThermometerPrefsKeys.DEVICE_SERIAL, "");
            ed.commit();
            LocalDiagLog.i(app, "ThermometerPrefsMigration",
                    "migrated legacy device id=" + legacyId + " → VID/PID (serial absent)");
        }
    }
}
