package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;

import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Locale;

/**
 * Реализация работы с реальным USB термометром
 */
public class RealThermometerDevice implements IThermometerDevice, SerialInputOutputManager.Listener {
    private static final String TAG = "RealThermometerDevice";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB_SERVICE";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    
    private final Context context;
    /** Сохранённый {@link UsbDevice#getSerialNumber()}; сопоставление через {@link UsbDeviceBySerial}. */
    private final String deviceSerial;
    private final int deviceVendorId;
    private final int deviceProductId;
    private final int legacyDeviceId;
    private final int portNum;
    private final int baudRate;
    private final boolean withIoManager;
    private final Handler mainLooper;
    private final ThermometerFrameParser frameParser = new ThermometerFrameParser();
    
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager usbIoManager;
    private boolean connected = false;
    private ThermometerDataListener dataListener;
    /** После успешного поиска устройства по серийнику — для {@link #getDeviceId()}. */
    private int resolvedDeviceId = -1;
    
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private int pendingPermissionDeviceId = -1;
    
    private final BroadcastReceiver broadcastReceiver;
    /** Receiver снимается в {@link #disconnect()} и ставится заново в {@link #connect()} — иначе после переподключения запрос прав уходит «в никуда». */
    private boolean receiverRegistered = false;
    /** Отказ «устройство не найдено» уже записан в диагностический лог — повторы подряд не дублируем. */
    private boolean deviceNotFoundLogged = false;

    public RealThermometerDevice(Context context, String deviceSerial, int portNum, int baudRate,
                                 boolean withIoManager) {
        this(context, deviceSerial, -1, -1, 0, portNum, baudRate, withIoManager);
    }

    public RealThermometerDevice(Context context, String deviceSerial, int deviceVendorId,
                                 int deviceProductId, int portNum, int baudRate,
                                 boolean withIoManager) {
        this(context, deviceSerial, deviceVendorId, deviceProductId, 0,
                portNum, baudRate, withIoManager);
    }

    public RealThermometerDevice(Context context, String deviceSerial, int deviceVendorId,
                                 int deviceProductId, int legacyDeviceId, int portNum,
                                 int baudRate, boolean withIoManager) {
        this.context = context;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.deviceVendorId = deviceVendorId;
        this.deviceProductId = deviceProductId;
        this.legacyDeviceId = legacyDeviceId;
        this.portNum = portNum;
        this.baudRate = baudRate;
        this.withIoManager = withIoManager;
        this.mainLooper = new Handler(Looper.getMainLooper());
        
        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent != null && INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    @SuppressWarnings("deprecation")
                    UsbDevice resultDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (resultDevice != null && pendingPermissionDeviceId >= 0
                            && resultDevice.getDeviceId() != pendingPermissionDeviceId) {
                        LocalDiagLog.i(RealThermometerDevice.this.context.getApplicationContext(), TAG,
                                "USB permission result ignored: deviceId mismatch");
                        return;
                    }
                    pendingPermissionDeviceId = -1;
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    if (usbPermission == UsbPermission.Granted) {
                        connect();
                    } else {
                        notifyStatus("connection failed: permission denied", "❌");
                    }
                }
            }
        };
        
        registerUsbPermissionReceiver();
    }

    private void registerUsbPermissionReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(INTENT_ACTION_GRANT_USB);
        ContextCompat.registerReceiver(context, broadcastReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void unregisterUsbPermissionReceiver() {
        if (!receiverRegistered) {
            return;
        }
        receiverRegistered = false;
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {
            Log.e(TAG, "unreg receiver ex: " + e);
        }
    }

    @Override
    public boolean connect() {
        registerUsbPermissionReceiver();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            notifyStatus("connection failed: USB manager not available", "❌");
            return false;
        }
        // Пустой serial не является идентификатором: поиск "по пустой строке"
        // мог выбрать постороннее разрешённое USB-устройство без serial.
        UsbDevice device = deviceSerial.isEmpty()
                ? null : UsbDeviceBySerial.findDevice(usbManager, deviceSerial);
        if (device == null && deviceSerial.isEmpty() && legacyDeviceId != 0
                && legacyDeviceId != EmulatedThermometerDevice.EMULATED_USB_DEVICE_ID) {
            device = findPermittedLegacyDevice(usbManager);
        }
        if (device == null && deviceSerial.isEmpty()) {
            device = findUniquePermittedDeviceByHints(usbManager);
        }
        if (device == null) {
            UsbDevice permissionCandidate = findUniquePermissionCandidate(usbManager);
            if (permissionCandidate != null && !usbManager.hasPermission(permissionCandidate)
                    && usbPermission == UsbPermission.Unknown) {
                requestUsbPermission(usbManager, permissionCandidate);
                return false;
            }
            resolvedDeviceId = -1;
            // Цикл переподключения зовёт connect() снова и снова — в диагностический лог пишем только первый отказ.
            if (!deviceNotFoundLogged) {
                deviceNotFoundLogged = true;
                LocalDiagLog.line(context, "W", TAG, "connect: no USB device for serial="
                        + logSerialHint(deviceSerial));
            }
            notifyStatus("connection failed: device not found", "❌");
            return false;
        }
        deviceNotFoundLogged = false;
        resolvedDeviceId = device.getDeviceId();
        rememberIdentityHints(device);
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            notifyStatus("connection failed: no driver for device", "❌");
            return false;
        }
        if (portNum < 0 || driver.getPorts().size() <= portNum) {
            notifyStatus("connection failed: not enough ports at device", "❌");
            return false;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                notifyStatus("connection failed: permission denied", "❌");
            else
                notifyStatus("connection failed: open failed", "❌");
            return false;
        }

        try {
            usbSerialPort.open(usbConnection);
            try{
                usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            }catch (UnsupportedOperationException e){
                notifyStatus("unsupport setparameters", "❌");
            }
            if(withIoManager) {
                frameParser.reset();
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            connected = true;
            notifyStatus("connected", "✔");
            notifyConnected();
            LocalDiagLog.i(context.getApplicationContext(), TAG, "connected " + UsbDeviceBySerial.fingerprintForLog(device));
            return true;
        } catch (Exception e) {
            notifyStatus("connection failed: " + e.getMessage(), "❌");
            disconnect();
            return false;
        }
    }

    /**
     * До USB permission доступны VID/PID, но не serial. Автоматически показываем
     * системный запрос только для единственного поддерживаемого кандидата, чтобы
     * случайно не дать доступ другому одинаковому устройству.
     */
    private UsbDevice findUniquePermissionCandidate(UsbManager usbManager) {
        UsbDevice match = null;
        int count = 0;
        for (UsbDevice candidate : usbManager.getDeviceList().values()) {
            if (deviceSerial.isEmpty() && legacyDeviceId != 0
                    && legacyDeviceId != EmulatedThermometerDevice.EMULATED_USB_DEVICE_ID
                    && candidate.getDeviceId() != legacyDeviceId) {
                continue;
            }
            if (deviceVendorId >= 0 && candidate.getVendorId() != deviceVendorId) {
                continue;
            }
            if (deviceProductId >= 0 && candidate.getProductId() != deviceProductId) {
                continue;
            }
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(candidate);
            if (driver == null) {
                driver = CustomProber.getCustomProber().probeDevice(candidate);
            }
            if (driver == null || portNum < 0 || driver.getPorts().size() <= portNum) {
                continue;
            }
            // Разрешённые устройства уже были проверены точным поиском по serial выше.
            // Здесь нужен только следующий кандидат для системного permission dialog.
            if (usbManager.hasPermission(candidate)) {
                continue;
            }
            match = candidate;
            count++;
        }
        return count == 1 ? match : null;
    }

    private UsbDevice findPermittedLegacyDevice(UsbManager usbManager) {
        for (UsbDevice candidate : usbManager.getDeviceList().values()) {
            if (candidate.getDeviceId() == legacyDeviceId && usbManager.hasPermission(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Для устройств без serial сохранённые VID/PID позволяют отличить выбранный
     * датчик от другого уже разрешённого USB-устройства. При неоднозначности
     * ничего не выбираем автоматически.
     */
    private UsbDevice findUniquePermittedDeviceByHints(UsbManager usbManager) {
        if (deviceVendorId < 0 || deviceProductId < 0) {
            return null;
        }
        UsbDevice match = null;
        int count = 0;
        for (UsbDevice candidate : usbManager.getDeviceList().values()) {
            if (!usbManager.hasPermission(candidate)
                    || candidate.getVendorId() != deviceVendorId
                    || candidate.getProductId() != deviceProductId) {
                continue;
            }
            String serial = UsbDeviceBySerial.serialNumberIfPermitted(usbManager, candidate);
            if (serial != null && !serial.isEmpty()) {
                continue;
            }
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(candidate);
            if (driver == null) {
                driver = CustomProber.getCustomProber().probeDevice(candidate);
            }
            if (driver == null || portNum < 0 || driver.getPorts().size() <= portNum) {
                continue;
            }
            match = candidate;
            count++;
        }
        return count == 1 ? match : null;
    }

    private void requestUsbPermission(UsbManager usbManager, UsbDevice device) {
        usbPermission = UsbPermission.Requested;
        resolvedDeviceId = device.getDeviceId();
        pendingPermissionDeviceId = device.getDeviceId();
        Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
        intent.setPackage(context.getPackageName());
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            usbManager.requestPermission(device, usbPermissionIntent);
            notifyStatus("waiting for USB permission", "…");
        } catch (RuntimeException e) {
            usbPermission = UsbPermission.Unknown;
            pendingPermissionDeviceId = -1;
            LocalDiagLog.w(context.getApplicationContext(), TAG, "USB permission request failed", e);
            notifyStatus("connection failed: USB permission request", "❌");
        }
    }

    /** После успешной проверки serial дополняем старые prefs безопасными VID/PID hints. */
    private void rememberIdentityHints(UsbDevice device) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        String serial = UsbDeviceBySerial.serialNumberIfPermitted(usbManager, device);
        context.getSharedPreferences(ThermometerPrefsKeys.NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ThermometerPrefsKeys.DEVICE_SERIAL, serial != null ? serial : "")
                .putInt(ThermometerPrefsKeys.DEVICE_VENDOR_ID, device.getVendorId())
                .putInt(ThermometerPrefsKeys.DEVICE_PRODUCT_ID, device.getProductId())
                .remove(ThermometerPrefsKeys.LEGACY_DEVICE_INT)
                .apply();
    }

    private static String logSerialHint(String s) {
        if (s == null || s.isEmpty()) {
            return "(empty)";
        }
        return "len=" + s.length();
    }
    
    @Override
    public void disconnect() {
        connected = false;
        // Отказ пользователя действует до пересоздания объекта. Иначе сервисный
        // reconnect-цикл снова показывал бы permission dialog каждые несколько секунд.
        if (usbPermission != UsbPermission.Denied) {
            usbPermission = UsbPermission.Unknown;
        }
        pendingPermissionDeviceId = -1;
        frameParser.reset();
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
            }
        } catch (IOException ignored) {}
        usbSerialPort = null;

        unregisterUsbPermissionReceiver();
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void sendCommand(String command) {
        if(!connected) {
            notifyError("not connected");
            return;
        }
        try {
            byte[] data = (command + '\n').getBytes();
            usbSerialPort.write(data, WRITE_WAIT_MILLIS);
        } catch (Exception e) {
            onRunError(e);
        }
    }
    
    @Override
    public int getDeviceId() {
        return resolvedDeviceId >= 0 ? resolvedDeviceId : 0;
    }
    
    @Override
    public int getPortNum() {
        return portNum;
    }
    
    @Override
    public String getDeviceName() {
        if (resolvedDeviceId >= 0) {
            return String.format(Locale.US, "USB Thermometer (serial saved, runtime id=%d)", resolvedDeviceId);
        }
        return "USB Thermometer (serial)";
    }
    
    @Override
    public boolean isEmulated() {
        return false;
    }
    
    @Override
    public void setDataListener(ThermometerDataListener listener) {
        this.dataListener = listener;
    }
    
    // SerialInputOutputManager.Listener implementation
    @Override
    public void onNewData(byte[] data) {
        mainLooper.post(() -> {
            receive(data);
        });
    }
    
    @Override
    public void onRunError(Exception e) {
        mainLooper.post(() -> {
            // Сначала ошибка, потом статус: слушатель по ошибке включает удержание показания,
            // иначе смена статуса успевает стереть последнее значение с экрана.
            notifyError(e.getMessage());
            notifyStatus("connection lost: " + e.getMessage(), "❌");
            disconnect();
        });
    }
    
    private void receive(byte[] data) {
        for (String temperature : frameParser.append(data)) {
            Log.d(TAG, "G response: " + temperature);
            notifyTemperature(temperature);
        }
    }
    
    private void notifyTemperature(String temperature) {
        if (dataListener != null) {
            dataListener.onTemperatureReceived(temperature);
        }
    }

    private void notifyConnected() {
        if (dataListener != null) {
            dataListener.onConnected();
        }
    }
    
    private void notifyError(String error) {
        if (dataListener != null) {
            dataListener.onError(error);
        }
    }
    
    private void notifyStatus(String status, String statusIcon) {
        if (dataListener != null) {
            dataListener.onStatusChanged(status, statusIcon);
        }
    }
}
