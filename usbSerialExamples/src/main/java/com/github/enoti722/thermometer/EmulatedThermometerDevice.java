package com.github.enoti722.thermometer;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Random;

/**
 * Эмулятор термометра для тестирования без физического устройства
 */
public class EmulatedThermometerDevice implements IThermometerDevice {
    private static final String TAG = "EmulatedThermometer";
    /** Id в prefs для режима без USB; совпадает с «виртуальным» сохранением в списке устройств. */
    public static final int EMULATED_USB_DEVICE_ID = 999999;
    private static final int UPDATE_INTERVAL_MS = 1000; // Обновление каждую секунду
    
    private boolean connected = false;
    /** Отменяет отложенный старт после disconnect / нового connect */
    private int connectionGeneration = 0;
    private ThermometerDataListener dataListener;
    private Handler handler;
    private Runnable temperatureUpdateRunnable;
    private Random random;
    
    // Параметры эмуляции температуры
    private float currentTemperature = -5.0f; // Начальная температура
    private float targetTemperature = 22.0f;  // Целевая температура
    private final float minTemp = -20.0f;
    private final float maxTemp = 35.0f;
    private final float changeRate = 0.5f; // Максимальное изменение за итерацию
    
    public EmulatedThermometerDevice() {
        this.handler = new Handler(Looper.getMainLooper());
        this.random = new Random();
    }
    
    @Override
    public boolean connect() {
        if (connected) {
            return true;
        }
        
        Log.d(TAG, "Connecting to emulated thermometer...");
        // Сразу «онлайн»: вызывающий код вызывает sendCommand() сразу после connect().
        final int gen = ++connectionGeneration;
        connected = true;

        handler.postDelayed(() -> {
            if (!connected || gen != connectionGeneration) {
                return;
            }
            notifyStatus("connected (emulated)", "✔");
            startTemperatureUpdates();
        }, 500);

        return true;
    }
    
    @Override
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        Log.d(TAG, "Disconnecting from emulated thermometer...");
        connectionGeneration++;
        connected = false;
        stopTemperatureUpdates();
        notifyStatus("disconnected (emulated)", "❌");
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void sendCommand(String command) {
        if (!connected) {
            notifyError("not connected");
            return;
        }
        
        Log.d(TAG, "Received command: " + command);
        
        // Обрабатываем команду ~W1000 (запрос температуры)
        if (command.startsWith("~W")) {
            // Команда получена, продолжаем отправлять данные
            Log.d(TAG, "Temperature request command received");
        }
    }
    
    @Override
    public int getDeviceId() {
        return EMULATED_USB_DEVICE_ID;
    }
    
    @Override
    public int getPortNum() {
        return 0;
    }
    
    @Override
    public String getDeviceName() {
        return "Emulated Thermometer (Test Device)";
    }
    
    @Override
    public boolean isEmulated() {
        return true;
    }
    
    @Override
    public void setDataListener(ThermometerDataListener listener) {
        this.dataListener = listener;
    }
    
    /**
     * Запускает периодическую отправку данных о температуре
     */
    private void startTemperatureUpdates() {
        if (temperatureUpdateRunnable != null) {
            return;
        }
        
        temperatureUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (connected) {
                    updateTemperature();
                    sendTemperatureData();
                    handler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
        
        handler.post(temperatureUpdateRunnable);
    }
    
    /**
     * Останавливает отправку данных
     */
    private void stopTemperatureUpdates() {
        if (temperatureUpdateRunnable != null) {
            handler.removeCallbacks(temperatureUpdateRunnable);
            temperatureUpdateRunnable = null;
        }
    }
    
    /**
     * Обновляет значение температуры (плавное изменение к целевому значению)
     */
    private void updateTemperature() {
        // Периодически меняем целевую температуру
        if (random.nextFloat() < 0.1f) { // 10% шанс изменить цель
            targetTemperature = minTemp + random.nextFloat() * (maxTemp - minTemp);
            Log.d(TAG, "New target temperature: " + targetTemperature);
        }
        
        // Плавно двигаемся к целевой температуре
        float diff = targetTemperature - currentTemperature;
        if (Math.abs(diff) > 0.01f) {
            float change = Math.signum(diff) * Math.min(Math.abs(diff), changeRate);
            currentTemperature += change;
        }
        
        // Добавляем небольшой шум
        currentTemperature += (random.nextFloat() - 0.5f) * 0.2f;
        
        // Ограничиваем диапазон
        currentTemperature = Math.max(minTemp, Math.min(maxTemp, currentTemperature));
    }
    
    /**
     * Отправляет данные о температуре слушателю
     */
    private void sendTemperatureData() {
        // Форматируем температуру с одним знаком после запятой
        String temperatureStr = String.format("%.1f", currentTemperature);
        
//        Log.d(TAG, "Sending temperature: " + temperatureStr);
        notifyTemperature(temperatureStr);
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
