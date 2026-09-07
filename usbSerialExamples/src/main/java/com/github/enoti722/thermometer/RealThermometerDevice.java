package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Реализация работы с реальным USB термометром
 */
public class RealThermometerDevice implements IThermometerDevice, SerialInputOutputManager.Listener {
    private static final String TAG = "RealThermometerDevice";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    
    private final Context context;
    /** Сохранённый {@link UsbDevice#getSerialNumber()}; сопоставление через {@link UsbDeviceBySerial}. */
    private final String deviceSerial;
    private final int portNum;
    private final int baudRate;
    private final boolean withIoManager;
    private final Handler mainLooper;
    
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager usbIoManager;
    private boolean connected = false;
    private ThermometerDataListener dataListener;
    /** После успешного поиска устройства по серийнику — для {@link #getDeviceId()}. */
    private int resolvedDeviceId = -1;
    
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private UsbPermission usbPermission = UsbPermission.Unknown;
    
    private final BroadcastReceiver broadcastReceiver;
    /** Receiver снимается в {@link #disconnect()} и ставится заново в {@link #connect()} — иначе после переподключения запрос прав уходит «в никуда». */
    private boolean receiverRegistered = false;
    /** Отказ «устройство не найдено» уже записан в диагностический лог — повторы подряд не дублируем. */
    private boolean deviceNotFoundLogged = false;

    public RealThermometerDevice(Context context, String deviceSerial, int portNum, int baudRate, boolean withIoManager) {
        this.context = context;
        this.deviceSerial = deviceSerial != null ? deviceSerial : "";
        this.portNum = portNum;
        this.baudRate = baudRate;
        this.withIoManager = withIoManager;
        this.mainLooper = new Handler(Looper.getMainLooper());
        
        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
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
        context.registerReceiver(broadcastReceiver, filter);
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
        UsbDevice device = UsbDeviceBySerial.findDevice(usbManager, deviceSerial);
        if (device == null) {
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
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
            intent.setPackage(context.getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return false;
        }
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
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            notifyStatus("connected", "✔");
            connected = true;
            LocalDiagLog.i(context.getApplicationContext(), TAG, "connected " + UsbDeviceBySerial.fingerprintForLog(device));
            return true;
        } catch (Exception e) {
            notifyStatus("connection failed: " + e.getMessage(), "❌");
            disconnect();
            return false;
        }
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
        String dataStr = new String(data);
        if (dataStr.startsWith("~G")) {
            String[] splitted = dataStr.split("G");
            if (splitted.length == 2) {
                Log.d(TAG, "G response: " + dataStr);
                notifyTemperature(splitted[1]);
            }
            else {
                Log.i(TAG, "unknown G response: " + dataStr);
            }
        }
        else {
            Log.i(TAG, "unknown response: " + dataStr);
        }
    }
    
    private void notifyTemperature(String temperature) {
        if (dataListener != null) {
            dataListener.onTemperatureReceived(temperature);
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
