package com.github.enoti722.thermometer;

/**
 * Ключи {@link android.content.SharedPreferences} для термометра.
 */
final class ThermometerPrefsKeys {
    static final String NAME = "termometer_sp";
    /** Реальный датчик: USB {@link android.hardware.usb.UsbDevice#getSerialNumber() серийный номер}. */
    static final String DEVICE_SERIAL = "device_serial";
    static final String PORT = "port";
    static final String BAUD = "baud";
    static final String WITH_IO_MANAGER = "withIoManager";
    static final String IS_EMULATED = "isEmulated";
    /** Легаси: числовой id из {@link android.hardware.usb.UsbDevice#getDeviceId()}, нестабилен на части прошивок. */
    static final String LEGACY_DEVICE_INT = "device";

    private ThermometerPrefsKeys() {}
}
