# Примеры использования API

## Создание и использование устройств

### Пример 1: Создание эмулятора

```java
// Создание эмулятора
IThermometerDevice emulator = new EmulatedThermometerDevice();

// Установка слушателя данных
emulator.setDataListener(new IThermometerDevice.ThermometerDataListener() {
    @Override
    public void onTemperatureReceived(String temperature) {
        Log.d("Temperature", "Received: " + temperature + "°C");
        // Обновить UI
        updateTemperatureDisplay(temperature);
    }

    @Override
    public void onError(String error) {
        Log.e("Temperature", "Error: " + error);
        // Показать ошибку пользователю
        showErrorDialog(error);
    }

    @Override
    public void onStatusChanged(String status, String statusIcon) {
        Log.i("Temperature", statusIcon + " " + status);
        // Обновить статус в UI
        updateStatusBar(status, statusIcon);
    }
});

// Подключение
if (emulator.connect()) {
    Log.d("Temperature", "Connected to emulator");
    // Отправка команды
    emulator.sendCommand("~W1000");
} else {
    Log.e("Temperature", "Failed to connect");
}

// Отключение (когда больше не нужно)
emulator.disconnect();
```

### Пример 2: Создание реального устройства

```java
Context context = getApplicationContext();
int deviceId = 12345; // ID USB устройства
int portNum = 0;
int baudRate = 19200;
boolean withIoManager = true;

// Создание реального устройства
IThermometerDevice realDevice = new RealThermometerDevice(
    context, 
    deviceId, 
    portNum, 
    baudRate, 
    withIoManager
);

// Установка слушателя
realDevice.setDataListener(new IThermometerDevice.ThermometerDataListener() {
    @Override
    public void onTemperatureReceived(String temperature) {
        // Обработка температуры
        float temp = Float.parseFloat(temperature);
        if (temp > 30.0f) {
            showWarning("High temperature!");
        }
    }

    @Override
    public void onError(String error) {
        // Обработка ошибки
        if (error.contains("permission denied")) {
            requestUsbPermission();
        }
    }

    @Override
    public void onStatusChanged(String status, String statusIcon) {
        // Обновление статуса
    }
});

// Подключение
realDevice.connect();
```

### Пример 3: Универсальная функция работы с устройством

```java
public class ThermometerManager {
    private IThermometerDevice device;
    
    public void initDevice(boolean useEmulator, Context context, int deviceId, int portNum, int baudRate) {
        // Создаем нужный тип устройства
        if (useEmulator) {
            device = new EmulatedThermometerDevice();
        } else {
            device = new RealThermometerDevice(context, deviceId, portNum, baudRate, true);
        }
        
        // Настраиваем слушателя
        device.setDataListener(new IThermometerDevice.ThermometerDataListener() {
            @Override
            public void onTemperatureReceived(String temperature) {
                handleTemperature(temperature);
            }

            @Override
            public void onError(String error) {
                handleError(error);
            }

            @Override
            public void onStatusChanged(String status, String statusIcon) {
                handleStatus(status, statusIcon);
            }
        });
    }
    
    public boolean connect() {
        if (device != null) {
            return device.connect();
        }
        return false;
    }
    
    public void disconnect() {
        if (device != null) {
            device.disconnect();
        }
    }
    
    public void requestTemperature() {
        if (device != null && device.isConnected()) {
            device.sendCommand("~W1000");
        }
    }
    
    public String getDeviceInfo() {
        if (device != null) {
            return device.getDeviceName() + 
                   " (ID: " + device.getDeviceId() + 
                   ", Port: " + device.getPortNum() + 
                   ", Emulated: " + device.isEmulated() + ")";
        }
        return "No device";
    }
    
    private void handleTemperature(String temperature) {
        // Ваша логика обработки
    }
    
    private void handleError(String error) {
        // Ваша логика обработки ошибок
    }
    
    private void handleStatus(String status, String statusIcon) {
        // Ваша логика обработки статуса
    }
}

// Использование
ThermometerManager manager = new ThermometerManager();

// Для эмулятора
manager.initDevice(true, context, 0, 0, 0);
manager.connect();

// Для реального устройства
manager.initDevice(false, context, 12345, 0, 19200);
manager.connect();
```

## Интеграция в Activity

### Пример 4: Использование в Activity

```java
public class TemperatureActivity extends AppCompatActivity {
    private IThermometerDevice thermometer;
    private TextView temperatureText;
    private TextView statusText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_temperature);
        
        temperatureText = findViewById(R.id.temperature_text);
        statusText = findViewById(R.id.status_text);
        
        // Читаем настройки
        SharedPreferences prefs = getSharedPreferences("thermometer_prefs", MODE_PRIVATE);
        boolean isEmulated = prefs.getBoolean("isEmulated", true);
        
        // Создаем устройство
        if (isEmulated) {
            thermometer = new EmulatedThermometerDevice();
        } else {
            int deviceId = prefs.getInt("deviceId", 0);
            int portNum = prefs.getInt("portNum", 0);
            int baudRate = prefs.getInt("baudRate", 19200);
            thermometer = new RealThermometerDevice(this, deviceId, portNum, baudRate, true);
        }
        
        // Настраиваем слушателя
        thermometer.setDataListener(new IThermometerDevice.ThermometerDataListener() {
            @Override
            public void onTemperatureReceived(String temperature) {
                runOnUiThread(() -> {
                    temperatureText.setText(temperature + "°C");
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(TemperatureActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onStatusChanged(String status, String statusIcon) {
                runOnUiThread(() -> {
                    statusText.setText(statusIcon + " " + status);
                });
            }
        });
        
        // Подключаемся
        thermometer.connect();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (thermometer != null) {
            thermometer.disconnect();
        }
    }
    
    public void onRequestTemperatureClick(View view) {
        if (thermometer != null && thermometer.isConnected()) {
            thermometer.sendCommand("~W1000");
        }
    }
}
```

## Работа с настройками

### Пример 5: Сохранение и загрузка настроек

```java
public class DeviceSettings {
    private static final String PREFS_NAME = "thermometer_settings";
    
    public static void saveDeviceSettings(Context context, boolean isEmulated, 
                                         int deviceId, int portNum, int baudRate) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putBoolean("isEmulated", isEmulated);
        editor.putInt("deviceId", deviceId);
        editor.putInt("portNum", portNum);
        editor.putInt("baudRate", baudRate);
        
        editor.apply();
    }
    
    public static IThermometerDevice loadDevice(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        boolean isEmulated = prefs.getBoolean("isEmulated", true);
        
        if (isEmulated) {
            return new EmulatedThermometerDevice();
        } else {
            int deviceId = prefs.getInt("deviceId", 0);
            int portNum = prefs.getInt("portNum", 0);
            int baudRate = prefs.getInt("baudRate", 19200);
            return new RealThermometerDevice(context, deviceId, portNum, baudRate, true);
        }
    }
}

// Использование
// Сохранение
DeviceSettings.saveDeviceSettings(context, true, 0, 0, 0);

// Загрузка
IThermometerDevice device = DeviceSettings.loadDevice(context);
device.setDataListener(listener);
device.connect();
```

## Расширенные примеры

### Пример 6: Логирование данных

```java
public class TemperatureLogger {
    private List<TemperatureReading> readings = new ArrayList<>();
    
    public static class TemperatureReading {
        public final String temperature;
        public final long timestamp;
        
        public TemperatureReading(String temperature, long timestamp) {
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
    
    public IThermometerDevice.ThermometerDataListener createLoggingListener() {
        return new IThermometerDevice.ThermometerDataListener() {
            @Override
            public void onTemperatureReceived(String temperature) {
                // Логируем данные
                readings.add(new TemperatureReading(temperature, System.currentTimeMillis()));
                Log.d("TempLogger", "Logged: " + temperature + "°C at " + 
                      new Date(System.currentTimeMillis()));
            }

            @Override
            public void onError(String error) {
                Log.e("TempLogger", "Error: " + error);
            }

            @Override
            public void onStatusChanged(String status, String statusIcon) {
                Log.i("TempLogger", "Status: " + status);
            }
        };
    }
    
    public List<TemperatureReading> getReadings() {
        return new ArrayList<>(readings);
    }
    
    public void exportToCsv(File file) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write("Timestamp,Temperature\n");
        
        for (TemperatureReading reading : readings) {
            writer.write(reading.timestamp + "," + reading.temperature + "\n");
        }
        
        writer.close();
    }
}

// Использование
TemperatureLogger logger = new TemperatureLogger();
IThermometerDevice device = new EmulatedThermometerDevice();
device.setDataListener(logger.createLoggingListener());
device.connect();

// Позже экспортируем
File csvFile = new File(getExternalFilesDir(null), "temperature_log.csv");
logger.exportToCsv(csvFile);
```

### Пример 7: Мониторинг с уведомлениями

```java
public class TemperatureMonitor {
    private IThermometerDevice device;
    private float thresholdHigh = 30.0f;
    private float thresholdLow = 15.0f;
    
    public void startMonitoring(Context context, IThermometerDevice device) {
        this.device = device;
        
        device.setDataListener(new IThermometerDevice.ThermometerDataListener() {
            @Override
            public void onTemperatureReceived(String temperature) {
                try {
                    float temp = Float.parseFloat(temperature);
                    
                    if (temp > thresholdHigh) {
                        showNotification(context, "High Temperature", 
                                       "Temperature is " + temp + "°C (threshold: " + thresholdHigh + "°C)");
                    } else if (temp < thresholdLow) {
                        showNotification(context, "Low Temperature", 
                                       "Temperature is " + temp + "°C (threshold: " + thresholdLow + "°C)");
                    }
                } catch (NumberFormatException e) {
                    Log.e("Monitor", "Invalid temperature: " + temperature);
                }
            }

            @Override
            public void onError(String error) {
                showNotification(context, "Device Error", error);
            }

            @Override
            public void onStatusChanged(String status, String statusIcon) {
                // Можно игнорировать или логировать
            }
        });
        
        device.connect();
    }
    
    private void showNotification(Context context, String title, String message) {
        NotificationManager manager = (NotificationManager) 
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "temp_channel")
            .setSmallIcon(R.drawable.ic_thermometer)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH);
        
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
```

### Пример 8: Создание собственного типа устройства

```java
public class NetworkThermometerDevice implements IThermometerDevice {
    private String serverUrl;
    private boolean connected = false;
    private ThermometerDataListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    
    public NetworkThermometerDevice(String serverUrl) {
        this.serverUrl = serverUrl;
    }
    
    @Override
    public boolean connect() {
        // Подключение к серверу
        try {
            // Ваша логика подключения
            connected = true;
            startPolling();
            return true;
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Connection failed: " + e.getMessage());
            }
            return false;
        }
    }
    
    @Override
    public void disconnect() {
        connected = false;
        stopPolling();
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void sendCommand(String command) {
        // Отправка команды на сервер
        // Ваша реализация
    }
    
    @Override
    public int getDeviceId() {
        return serverUrl.hashCode();
    }
    
    @Override
    public int getPortNum() {
        return 0;
    }
    
    @Override
    public String getDeviceName() {
        return "Network Thermometer (" + serverUrl + ")";
    }
    
    @Override
    public boolean isEmulated() {
        return false;
    }
    
    @Override
    public void setDataListener(ThermometerDataListener listener) {
        this.listener = listener;
    }
    
    private void startPolling() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (connected) {
                    fetchTemperatureFromServer();
                    handler.postDelayed(this, 2000);
                }
            }
        };
        handler.post(updateRunnable);
    }
    
    private void stopPolling() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
    
    private void fetchTemperatureFromServer() {
        // Запрос к серверу
        // В реальности это должно быть в фоновом потоке
        new Thread(() -> {
            try {
                // HTTP запрос
                String temperature = makeHttpRequest(serverUrl + "/temperature");
                
                if (listener != null) {
                    handler.post(() -> listener.onTemperatureReceived(temperature));
                }
            } catch (Exception e) {
                if (listener != null) {
                    handler.post(() -> listener.onError(e.getMessage()));
                }
            }
        }).start();
    }
    
    private String makeHttpRequest(String url) throws IOException {
        // Ваша реализация HTTP запроса
        return "25.5";
    }
}

// Использование
IThermometerDevice networkDevice = new NetworkThermometerDevice("http://192.168.1.100:8080");
networkDevice.setDataListener(listener);
networkDevice.connect();
```

## Заключение

Эти примеры показывают гибкость архитектуры на основе интерфейса `IThermometerDevice`. Вы можете:

- Легко переключаться между типами устройств
- Создавать собственные реализации
- Логировать и мониторить данные
- Интегрировать в любую часть приложения
- Расширять функциональность без изменения существующего кода
