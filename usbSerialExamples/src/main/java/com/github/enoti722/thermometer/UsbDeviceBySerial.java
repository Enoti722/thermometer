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
            // Начиная с targetSdk 29 getSerialNumber() без USB permission бросает
            // SecurityException. Неподтверждённые устройства здесь нельзя даже
            // сравнивать по серийному номеру: сначала caller должен запросить доступ.
            if (!usbManager.hasPermission(v)) {
                continue;
            }
            String actualSerial;
            try {
                actualSerial = v.getSerialNumber();
            } catch (SecurityException ignored) {
                // Permission was revoked between hasPermission() and descriptor read.
                continue;
            }
            String actual = normalizeSerial(actualSerial);
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

    /**
     * Безопасное чтение защищённого USB descriptor. Разрешение может быть отозвано
     * между hasPermission() и IPC-вызовом, поэтому SecurityException всё равно ловим.
     */
    static String serialNumberIfPermitted(UsbManager usbManager, UsbDevice device) {
        if (usbManager == null || device == null || !usbManager.hasPermission(device)) {
            return null;
        }
        try {
            return device.getSerialNumber();
        } catch (SecurityException ignored) {
            return null;
        }
    }

    /**
     * Находит единственное подключённое устройство с сохранёнными VID/PID.
     * Эти дескрипторы доступны до выдачи USB permission и потому годятся только
     * как hint для системного запроса; окончательная проверка остаётся за serial.
     */
    static UsbDevice findUniqueDeviceByVidPid(UsbManager usbManager, int vendorId, int productId) {
        if (usbManager == null || vendorId < 0 || productId < 0) {
            return null;
        }
        UsbDevice match = null;
        int count = 0;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                match = device;
                count++;
            }
        }
        return count == 1 ? match : null;
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
        boolean serialReadable = true;
        String sn = null;
        try {
            sn = d.getSerialNumber();
        } catch (SecurityException ignored) {
            serialReadable = false;
        }
        boolean hasSn = !TextUtils.isEmpty(sn);
        return "vid=" + d.getVendorId() + " pid=" + d.getProductId()
                + " deviceId=" + d.getDeviceId()
                + " serialReadable=" + serialReadable
                + " serialPresent=" + hasSn;
    }
}
