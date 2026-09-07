package com.github.enoti722.thermometer;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.text.TextUtils;

import java.util.Locale;
import java.util.Objects;

final class UsbDeviceBySerial {
    private UsbDeviceBySerial() {}

    /**
     * Поиск устройства по сохранённому серийнику (после нормализации).
     * Пустая строка в prefs означает «без серийника» — если таких USB устройств больше одного, возвращаем null.
     */
    static UsbDevice findDevice(UsbManager usbManager, String storedSerial) {
        if (usbManager == null) {
            return null;
        }
        String want = normalizeSerial(storedSerial);
        UsbDevice match = null;
        int matchCount = 0;
        for (UsbDevice v : usbManager.getDeviceList().values()) {
            String actual = normalizeSerial(v.getSerialNumber());
            boolean hit = want.isEmpty() ? actual.isEmpty() : Objects.equals(want, actual);
            if (hit) {
                matchCount++;
                match = v;
            }
        }
        if (matchCount == 1) {
            return match;
        }
        return null;
    }

    /** Нормализация под сравнение (без ведущих/хвостовых пробелов, верхний регистр). */
    static String normalizeSerial(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase(Locale.ROOT);
    }

    /** Логируемое представление без утечки лишних данных. */
    static String fingerprintForLog(UsbDevice d) {
        if (d == null) {
            return "null";
        }
        String sn = d.getSerialNumber();
        boolean hasSn = !TextUtils.isEmpty(sn);
        return "vid=" + d.getVendorId() + " pid=" + d.getProductId()
                + " deviceId=" + d.getDeviceId()
                + " serialPresent=" + hasSn;
    }
}
