package com.github.enoti722.thermometer;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class UsbService extends Service implements SerialInputOutputManager.Listener {
    private static final String TAG = "UsbService";
    private static final int OVERLAY_WIDTH_WITH_SPEED = 450;
    private static final int OVERLAY_WIDTH_NO_SPEED = 300;
    /** Базовая высота окна оверлея (px), совпадает с layout до масштабирования. */
    private static final int OVERLAY_HEIGHT_PX = 100;

    /** Последние применённые проценты якоря (для пересчёта при смене масштаба UI). */
    private int lastOverlayPositionXPercent = 50;
    private int lastOverlayPositionYPercent = 5;

    /**
     * Показывать блок скорости (км/ч). Источник: GPS ({@link GpsSpeedProvider}) или эмуляция ({@link EmulatedSpeedProvider}, {@link SpeedEmulationActivity}).
     * При реальном GPS нужен {@link Manifest.permission#ACCESS_FINE_LOCATION} и на API 34+ тип FGS location (не используется при включённой эмуляции).
     */
    public static final boolean ENABLE_GPS_SPEED = true;
    /** Показывать и считывать напряжение батареи в оверлее/уведомлении. */
    public static final boolean ENABLE_BATTERY_VOLTAGE = false;

    /**
     * Скорость по GPS ниже этого значения (км/ч после пересчёта м/с→км/ч) считается покоем и показывается как 0,
     * как у многих навигаторов (убирает шум 0,3–0,5 м/с ~1 км/ч при остановке).
     */
    private static final float SPEED_DISPLAY_STATIONARY_CLAMP_KMH = 2f;

    private static final String INFO_TITLE_WARNING = "Информация";
    private static final String ALERT_TITLE_WARNING = "Предупреждение";
    private static final String ALERT_TITLE_ERROR = "Ошибка";
    private static final String MSG_AIR_TEMP_COLD =
            "Температура воздуха за бортом ниже +5°С, будьте осторожны, возможно обледенение дорожного полотна!";
    private static final String MSG_SENSOR_LINK_LOST =
            "Потеряна связь с датчиком температуры! Информация о температуре воздуха за бортом временно не доступна.";
    private static final String MSG_TEMP_UNRELIABLE =
            "Зафиксирована недостоверная информация о температуре воздуха за бортом! Информация о температуре воздуха за бортом может быть недостоверна, рекомендуется проверить датчик температуры.";
    private static final String MSG_SENSOR_LINK_RESTORED =
            "Сигнал от датчика внешней температуры был потерян, переподключение прошло успешно. Рекомендуется проверка электропроводки или датчика";
    /** Длительность показа оверлей-уведомлений по событиям (сек.) */
    private static final int TEMP_ALERT_DURATION_SEC = 10;
    private static final float AIR_TEMP_COLD_THRESHOLD_C = 5f;
    /** Минимальная скорость (км/ч), при которой считаем «движение» для предупреждения о холоде */
    private static final float MOTION_MIN_SPEED_KMH = 3f;
    private static final long TEMP_RAPID_CHANGE_WINDOW_MS = 5 * 60 * 1000L;
    private static final float TEMP_RAPID_CHANGE_DELTA_C = 10f;
    private static final long SENSOR_LOST_DEBOUNCE_MS = 2500L;

    /** Пауза между попытками переподключения. Попытки идут только во время удержания показания. */
    private static final long SENSOR_RECONNECT_INTERVAL_MS = 5000L;
    /** Датчик отдаёт кадр раз в секунду (подписка «~W1000»); такая тишина в эфире считается потерей сигнала. */
    private static final long SENSOR_DATA_TIMEOUT_MS = 8000L;
    private static final long SENSOR_DATA_WATCHDOG_PERIOD_MS = 2000L;
    /** Значок в шторке, пока на экране удерживается последнее показание с потерянного датчика. */
    private static final String SENSOR_HOLD_STATUS_ICON = "⚠";

    /** Процессы, которые часто идут первыми в RunningAppProcesses, но не являются «верхним» приложением пользователя */
    private static final Set<String> FOREGROUND_PROCESS_IGNORE = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui"
    ));
    private static final int NOTIFICATION_ID = 10101;
//    private UsbManager usbManager;
//    private UsbDevice device;
//    private UsbDeviceConnection connection;
    private NotificationManager notificationManager;
    private Notification notification;
    private SettingsManager settingsManager;
    private String currentForegroundApp = "";
    /**
     * Исключительное начало интервала для {@link UsageStatsManager#queryEvents}: инкрементальный опрос без 2‑минутного окна,
     * чтобы после старта сессии считался «sticky» топ — пока не придёт следующее FG/Resume событие.
     */
    private long usageEventsNextRangeStartUtcMs = 0L;
    /** Текущее переднее приложение по цепочке UsageEvents ({@link #queryForegroundPackageFromUsageEvents}); при пустом инкременте не сбрасывается. */
    private String stickyForegroundFromUsageEvents = "";
    private static final long USAGE_EVENTS_BOOTSTRAP_WINDOW_MS = 30 * 60 * 1000L;
    /** Поддержка события, помеченного deprecated после API 29 и по-прежнему встречающегося в логах на части OEM. */
    @SuppressWarnings("deprecation")
    private static final int USAGE_EVENTS_MOVE_TO_FOREGROUND = UsageEvents.Event.MOVE_TO_FOREGROUND;
    /** Чтобы не засорять logcat дампом процессов при каждом тике */
    private long lastForegroundDiagLogMs;
    private Handler appCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable appCheckRunnable;



    private String deviceSerial = "";
    private int portNum, baudRate;
    private boolean withIoManager;
    private boolean isEmulated;
    private IThermometerDevice thermometerDevice;
    private UsbSerialPort usbSerialPort;
    private enum UsbPermission { Unknown, Requested, Granted, Denied }
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    private static final int WRITE_WAIT_MILLIS = 2000;
    private static final int READ_WAIT_MILLIS = 2000;
    private UsbPermission usbPermission = UsbPermission.Unknown;
    private SerialInputOutputManager usbIoManager;
    private boolean connected = false;
    private final BroadcastReceiver broadcastReceiver;
    private final Handler mainLooper;

    private ISpeedProvider speedProvider;
    private SharedPreferences.OnSharedPreferenceChangeListener speedEmulationPrefsListener;
    private SharedPreferences.OnSharedPreferenceChangeListener uiScalingPrefsListener;
    private TextView tvSpeed;
    private String lastSpeedText = ENABLE_GPS_SPEED ? "— км/ч" : "";
    private float lastSpeedKmh = 0f;
    private int currentOverlayAlphaTop = AppSettings.DEFAULT_OVERLAY_ALPHA_TOP;
    private int currentOverlayAlphaBottom = AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM;
    private String lastTemperatureDisplay = "";
    /** Сырая строка с датчика (до поправки), для пересчёта UI при смене настроек или скорости. */
    private String lastRawTemperatureStringFromDevice = "";
    private String lastStatusIconForReading = "✔";
    /** При включённом GPS первый успешный кадр без фильтра; сброс при потере температуры или новой сессии датчика. */
    private boolean initialTemperatureShownOnScreen = false;

    /** Первое валидное значение после успешного подключения — сценарий «магнитола только включилась». */
    private boolean pendingColdStartTempCheck = false;
    private Float previousReadingCelsius = null;
    private long lastSensorLostAlertElapsedMs = 0L;

    /** Идёт эпизод потери сигнала: на экране удерживается последнее показание, в фоне идут переподключения. */
    private boolean sensorSignalLost = false;
    /**
     * Окно удержания ({@link SystemSettingsPreferences#sensorSignalHoldMs}) истекло:
     * показание с экрана снято, уведомление о потере уже выдано.
     */
    private boolean sensorHoldExpired = false;
    /**
     * Последнее показание, реально пришедшее с датчика. В отличие от {@link #lastTemperatureDisplay}
     * не стирается сменой статуса — именно его удерживаем на экране, пока идёт переподключение.
     */
    private String lastKnownTemperatureDisplay = "";
    private String lastKnownRawTemperatureFromDevice = "";
    /** Начало текущего эпизода потери — чтобы пересчитать остаток окна при смене настройки на лету. */
    private long sensorLossStartedElapsedMs = 0L;
    private Runnable sensorReconnectRunnable;
    private Runnable sensorHoldExpireRunnable;
    private Runnable sensorDataWatchdogRunnable;
    /** {@link SystemClock#elapsedRealtime()} последнего кадра с датчика; 0 — за сессию кадров ещё не было. */
    private long lastSensorFrameElapsedMs = 0L;
    private final ArrayDeque<TempSample> recentTemperatures = new ArrayDeque<>();
    private final ArrayDeque<SpeedSample> recentSpeeds = new ArrayDeque<>();
    private boolean unreliableTempAlertLatch = false;

    private static final class TempSample {
        final long timeMs;
        final float celsius;

        TempSample(long timeMs, float celsius) {
            this.timeMs = timeMs;
            this.celsius = celsius;
        }
    }

    private static final class SpeedSample {
        final long timeMs;
        final float kmh;

        SpeedSample(long timeMs, float kmh) {
            this.timeMs = timeMs;
            this.kmh = kmh;
        }
    }

    public UsbService() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            if(INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        ? UsbPermission.Granted : UsbPermission.Denied;
                connect();
            }
            }
        };
        mainLooper = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocalDiagLog.i(this, TAG, "onCreate");
//        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        settingsManager = new SettingsManager(this);

        ThermometerPrefsMigration.tryMigrateLegacyDeviceId(this);

        SharedPreferences sharedPreferences = getSharedPreferences(ThermometerPrefsKeys.NAME, Context.MODE_PRIVATE);
        deviceSerial = sharedPreferences.getString(ThermometerPrefsKeys.DEVICE_SERIAL, "");
        if (deviceSerial == null) {
            deviceSerial = "";
        }
        portNum = sharedPreferences.getInt(ThermometerPrefsKeys.PORT, portNum);
        baudRate = sharedPreferences.getInt(ThermometerPrefsKeys.BAUD, baudRate);
        withIoManager = sharedPreferences.getBoolean(ThermometerPrefsKeys.WITH_IO_MANAGER, withIoManager);
        isEmulated = sharedPreferences.getBoolean(ThermometerPrefsKeys.IS_EMULATED, false);

        // Создаем соответствующее устройство
        if (isEmulated) {
            thermometerDevice = new EmulatedThermometerDevice();
        } else {
            thermometerDevice = new RealThermometerDevice(this, deviceSerial, portNum, baudRate, withIoManager);
        }

        LocalDiagLog.i(this, TAG, "prefs: emu=" + isEmulated + " serialLen="
                + (TextUtils.isEmpty(deviceSerial) ? 0 : deviceSerial.trim().length()));

        // Устанавливаем слушателя данных
        thermometerDevice.setDataListener(new IThermometerDevice.ThermometerDataListener() {
            @Override
            public void onTemperatureReceived(String temperature) {
                handleTemperaturePayload(temperature);
            }

            @Override
            public void onError(String error) {
                if ("not connected".equals(error)) {
                    Toast.makeText(UsbService.this, error, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (lastSensorFrameElapsedMs == 0L) {
                    // За сессию кадров ещё не было — удерживать на экране нечего, ведём себя как раньше.
                    notifySensorLinkLost();
                    return;
                }
                onSensorSignalLost("io error: " + error);
            }

            @Override
            public void onStatusChanged(String status, String statusIcon) {
                UsbService.this.status(status, statusIcon, null);
            }
        });

        // Создаем уведомление
//        createNotification();
        initOverlay();

        // Запускаем периодическую проверку активного приложения
        startAppMonitoring();

        // ВАЖНО: Применяем начальные настройки для текущего приложения
        String initialApp = getForegroundApp();
        if (initialApp != null && !initialApp.isEmpty()) {
            currentForegroundApp = initialApp;
            applySettingsForApp(initialApp);
            Log.d(TAG, "Initial settings applied for: " + initialApp);
        }

        speedEmulationPrefsListener = (sp, key) -> mainLooper.post(this::restartSpeedTracking);
        SpeedEmulationPreferences.prefs(this).registerOnSharedPreferenceChangeListener(speedEmulationPrefsListener);

        uiScalingPrefsListener = (sp, key) -> mainLooper.post(() -> {
            if (UiScalingPreferences.KEY_OVERLAY_FONT_SCALE.equals(key)) {
                reloadOverlayDimensionsAndPosition();
            }
            if (UiScalingPreferences.KEY_ALERT_FONT_SCALE.equals(key)) {
                applyFloatingNotificationTextSizes();
            }
            if (SystemSettingsPreferences.KEY_TEMP_SPEED_GATE_KMH.equals(key)
                    || SystemSettingsPreferences.KEY_TEMP_AVG_WINDOW_MIN.equals(key)
                    || SystemSettingsPreferences.KEY_THERMOMETER_OFFSET_C.equals(key)) {
                trimTemperatureHistoryDeque();
                trimSpeedHistoryDeque();
                refreshCalibratedTemperatureDisplayFromCache();
            }
            if (SystemSettingsPreferences.KEY_SENSOR_HOLD_SEC.equals(key)) {
                rescheduleSensorHoldExpiry();
            }
        });
        UiScalingPreferences.prefs(this).registerOnSharedPreferenceChangeListener(uiScalingPrefsListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalDiagLog.i(this, TAG, "onStartCommand flags=" + flags + " startId=" + startId + " emu=" + isEmulated + " serialLen="
                + (TextUtils.isEmpty(deviceSerial) ? 0 : deviceSerial.trim().length()));
        createNotification();
        try {
            startForegroundCompat();
            LocalDiagLog.i(this, TAG, "foreground: тип медиаплеера "
                    + (SystemSettingsPreferences.declareMediaPlaybackService(this) ? "объявлен" : "НЕ объявлен"));

            // Используем новый интерфейс для подключения.
            // Пока идёт удержание, устройством распоряжается цикл переподключения — не мешаем ему.
            // А вот после отказа от попыток это единственный путь вернуть датчик: сюда приводит
            // USB_DEVICE_ATTACHED (через MainActivity) и ручной перезапуск.
            if (thermometerDevice != null && !thermometerDevice.isConnected() && !isSensorHoldActive()) {
                mainLooper.post(() -> {
                    if (thermometerDevice.connect()) {
                        connected = true;
                        // Отправляем команду запроса данных
                        thermometerDevice.sendCommand("~W1000");
                        resetTemperatureAlertSessionState();

                        // Показываем уведомление о подключении
                        String deviceType = thermometerDevice.isEmulated() ? "эмулированному" : "реальному";
                        showNotification(INFO_TITLE_WARNING, "Успешно подключено к " + deviceType + " устройству", 5);
                    }
                });
            }
            
            restartSpeedTracking();

            return START_STICKY;
        }
        catch (Exception ex) {
            Log.e(TAG, ex.toString());
            throw ex;
        }
    }

    private static final String CHANNEL_ID = "usb_service_channel";

    private void createNotification() {
//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "USB Service Channel",
                NotificationManager.IMPORTANCE_LOW
        );
        notificationManager.createNotificationChannel(channel);

//        createOverlay();

        updateNotification("Подключение к устройству...", null);

//        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("USB Service")
//                .setContentText("Подключение к устройству...")
//                .setSmallIcon(R.drawable.ic_usb)
//                .setContentIntent(pendingIntent)
//                .build();
    }

    @Override
    public void onDestroy() {
        // Снимаем сторож эфира и незавершённый эпизод потери сигнала до сброса статуса,
        // иначе удержание не даст очистить показание.
        stopSensorDataWatchdog();
        cancelSensorReconnectAttempts();
        cancelSensorHoldExpiry();
        sensorSignalLost = false;
        sensorHoldExpired = false;

        status("disconnected", "❌", null);

        // Останавливаем мониторинг приложений
        stopAppMonitoring();

        // Отключаем устройство через интерфейс (безопасно)
        if (thermometerDevice != null) {
            try {
                thermometerDevice.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting thermometer device: " + e.getMessage());
            }
        }
        
        // Безопасное удаление оверлея
        try {
            if (windowManager != null && overlayView != null) {
                windowManager.removeView(overlayView);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Ошибка при удалении оверлея", e);
        }
        
        // Безопасная очистка окна уведомлений
        try {
            if (timerRunnable != null) {
                notificationHandler.removeCallbacks(timerRunnable);
            }
            if (notificationWindowManager != null && notificationView != null) {
                notificationWindowManager.removeView(notificationView);
            }
        }
        catch (Exception e) {
            Log.e(TAG, "Ошибка при удалении окна уведомлений", e);
        }
        
        // Безопасный вызов старого метода disconnect (для совместимости)
        try {
            disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Error in disconnect: " + e.getMessage());
        }
        
        // Безопасная отмена регистрации receiver
        try {
            this.unregisterReceiver(broadcastReceiver);
        }
        catch (Exception e) {
            Log.e(TAG, "unreg receiver ex: " + e);
        }

        if (speedEmulationPrefsListener != null) {
            SpeedEmulationPreferences.prefs(this).unregisterOnSharedPreferenceChangeListener(speedEmulationPrefsListener);
        }
        if (uiScalingPrefsListener != null) {
            UiScalingPreferences.prefs(this).unregisterOnSharedPreferenceChangeListener(uiScalingPrefsListener);
        }
        stopSpeedTracking();
        if (alertSound != null) {
            alertSound.release();
        }

        LocalDiagLog.i(this, TAG, "onDestroy");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*
     * Serial + UI
     * Эти методы теперь не используются напрямую, так как данные обрабатываются через IThermometerDevice
     */

    @Override
    public void onNewData(byte[] data) {
        // Этот метод вызывается только для реального устройства через RealThermometerDevice
        mainLooper.post(() -> {
            receive(data);
        });
    }

    @Override
    public void onRunError(Exception e) {
        // Этот метод вызывается только для реального устройства через RealThermometerDevice
        mainLooper.post(() -> {
            status("connection lost: " + e.getMessage(), "❌", null);
            disconnect();
        });
    }

    private String speedSuffix() {
        if (!ENABLE_GPS_SPEED) {
            return "";
        }
        if (!isSpeedVisibleForCurrentApp()) {
            return "";
        }
        return " " + lastSpeedText;
    }

    private String voltageSuffix() {
        if (!ENABLE_BATTERY_VOLTAGE) {
            return "";
        }
        if (lastVoltageText == null || lastVoltageText.isEmpty()) {
            return "";
        }
        return " " + lastVoltageText;
    }

    private boolean isSpeedVisibleForCurrentApp() {
        if (!ENABLE_GPS_SPEED || settingsManager == null) {
            return false;
        }
        if (currentForegroundApp == null || currentForegroundApp.isEmpty()) {
            return false;
        }
        return settingsManager.isOverlaySpeedVisibleForApp(currentForegroundApp);
    }

    private void onGpsSpeedUpdated() {
        if (!ENABLE_GPS_SPEED) {
            return;
        }
        mainLooper.post(() -> {
            if (tvSpeed != null) {
                tvSpeed.setText(lastSpeedText);
            }
            refreshCalibratedTemperatureDisplayFromCache();
        });
    }

    private int foregroundServiceTypesMask() {
        // Тип «медиаплеер» отключаемый: есть подозрение, что именно по нему прошивка магнитолы
        // считает нас медиаприложением и на любой наш звук переключает источник усилителя.
        int types = SystemSettingsPreferences.declareMediaPlaybackService(this)
                ? FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                : 0;
        if (ENABLE_GPS_SPEED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!SpeedEmulationPreferences.isEmulationEnabled(this)) {
                types |= FOREGROUND_SERVICE_TYPE_LOCATION;
            }
        }
        return types;
    }

    /**
     * Всегда трёхаргументный вызов, даже с нулевой маской: у двухаргументного тип берётся из манифеста
     * ({@code mediaPlayback|location}), и настройкой «не объявлять медиаплеером» его было бы не снять.
     */
    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceTypesMask());
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void restartSpeedTracking() {
        stopSpeedTracking();
        if (!ENABLE_GPS_SPEED) {
            return;
        }
        if (SpeedEmulationPreferences.isEmulationEnabled(this)) {
            speedProvider = new EmulatedSpeedProvider(this);
        } else {
            speedProvider = new GpsSpeedProvider(this);
        }
        speedProvider.setListener(this::applySpeedReading);
        speedProvider.start();
        syncForegroundTypesWithSpeedSource();
    }

    /** После смены GPS ↔ эмуляция на Android 14+ нужно актуализировать бит-тмаску типа foreground service. */
    private void syncForegroundTypesWithSpeedSource() {
        if (!ENABLE_GPS_SPEED || notification == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForegroundCompat();
            } catch (Exception e) {
                Log.w(TAG, "syncForegroundTypesWithSpeedSource: " + e.getMessage());
            }
        }
    }

    /**
     * Как на типичном навигаторе: целые км/ч, мелкий шум покоя — ноль.
     * {@link #lastSpeedKmh} совпадает с тем, что показано в оверлее.
     */
    private static float normalizeSpeedKmhForDisplay(float rawKmh) {
        if (rawKmh < SPEED_DISPLAY_STATIONARY_CLAMP_KMH) {
            return 0f;
        }
        return Math.round(rawKmh);
    }

    private void applySpeedReading(float kmh) {
        if (!ENABLE_GPS_SPEED) {
            return;
        }
        if (Float.isNaN(kmh)) {
            lastSpeedKmh = 0f;
            lastSpeedText = "— км/ч";
            onGpsSpeedUpdated();
            return;
        }
        float shown = normalizeSpeedKmhForDisplay(kmh);
        lastSpeedKmh = shown;
        lastSpeedText = String.format(Locale.US, "%.0f км/ч", shown);
        recentSpeeds.addLast(new SpeedSample(System.currentTimeMillis(), shown));
        trimSpeedHistoryDeque();
        onGpsSpeedUpdated();
    }

    private void stopSpeedTracking() {
        if (!ENABLE_GPS_SPEED) {
            return;
        }
        if (speedProvider != null) {
            speedProvider.stop();
            speedProvider = null;
        }
    }

    private static final float TEMP_EPS = 2e-3f;

    /**
     * При средней скорости ниже порога пользовательское правило удерживает на экране прошлый показатель
     * и не показывает рост температуры до тех пор, пока она явно не снизится.
     */
    private boolean isLowAverageSpeedThermalHoldActive(String rawTrimInstant) {
        if (!ENABLE_GPS_SPEED) {
            return false;
        }
        if (!initialTemperatureShownOnScreen) {
            return false;
        }
        long win = SystemSettingsPreferences.tempAverageWindowMs(this);
        float speedGate = SystemSettingsPreferences.tempSpeedGateKmh(this);
        Float avgSpeed = averageSpeedOverLastMillis(win);
        float speedForDecision = avgSpeed != null ? avgSpeed : lastSpeedKmh;
        if (speedForDecision >= speedGate) {
            return false;
        }
        Float nt = parseTemperatureCelsius(rawTrimInstant.trim());
        Float prevShown = parseTemperatureCelsius(lastTemperatureDisplay);
        if (nt == null || lastTemperatureDisplay.isEmpty() || prevShown == null) {
            return false;
        }
        if (nt < prevShown - TEMP_EPS) {
            return false;
        }
        return true;
    }

    private String resolveTemperatureOverlayDisplay(String rawTrimInstant) {
        if (!ENABLE_GPS_SPEED) {
            return rawTrimInstant;
        }
        if (!initialTemperatureShownOnScreen) {
            return rawTrimInstant;
        }
        if (isLowAverageSpeedThermalHoldActive(rawTrimInstant)) {
            return lastTemperatureDisplay;
        }
        return rawTrimInstant;
    }

    // update notification info
    void status(String statusStr, String statusIcon, String tempVal) {
//        Log.d(TAG, "status() called: statusStr=" + statusStr + ", statusIcon=" + statusIcon + ", tempVal=" + tempVal);

        final boolean hasTemp = tempVal != null && !tempVal.isEmpty();

        if (!hasTemp) {
            if (isSensorHoldActive() && !lastTemperatureDisplay.isEmpty()) {
                // Удержание после потери сигнала: значение на экране не трогаем, обновляем только строку в шторке.
                updateNotification(lastStatusIconForReading + " " + lastTemperatureDisplay + " °C"
                        + voltageSuffix() + speedSuffix(), lastTemperatureDisplay);

                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.notify(NOTIFICATION_ID, notification);
            } else {
                lastTemperatureDisplay = "";
                lastRawTemperatureStringFromDevice = "";
                if (ENABLE_GPS_SPEED) {
                    initialTemperatureShownOnScreen = false;
                }
                updateNotification(statusIcon + " " + statusStr, tempVal);

                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.notify(NOTIFICATION_ID, notification);

                if (ENABLE_GPS_SPEED && tvTemperature != null) {
                    tvTemperature.setText("— °C");
                    if (overlayTemperatureIcon != null) {
                        overlayTemperatureIcon.setImageResource(R.drawable.thermometer);
                    }
                }
            }
        } else {
            String rawTrim = tempVal.trim();
            String displayNumber = resolveTemperatureOverlayDisplay(rawTrim);
            lastTemperatureDisplay = displayNumber;
            lastKnownTemperatureDisplay = displayNumber;
            lastStatusIconForReading = statusIcon;
            if (ENABLE_GPS_SPEED) {
                initialTemperatureShownOnScreen = true;
            }
            updateNotification(statusIcon + " " + displayNumber + " °C" + voltageSuffix() + speedSuffix(), displayNumber);

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, notification);

            if (tvTemperature != null) {
                tvTemperature.setText(displayNumber + " °C");
                updateOverlayTemperatureIcon(displayNumber);
            }
        }

        if (tvVoltage != null) {
            if (ENABLE_BATTERY_VOLTAGE) {
                tvVoltage.setVisibility(View.VISIBLE);
                if (lastVoltageText != null && !lastVoltageText.isEmpty()) {
                    tvVoltage.setText(lastVoltageText);
                }
            } else {
                tvVoltage.setVisibility(View.GONE);
            }
        }
        if (ENABLE_GPS_SPEED && tvSpeed != null) {
            tvSpeed.setText(lastSpeedText);
        }
    }

    WindowManager windowManager = null;
    View overlayView = null;
    ImageView overlayTemperatureIcon = null;
    TextView tvTemperature = null;
    TextView tvVoltage = null;

    // Notification window variables
    private WindowManager notificationWindowManager = null;
    private View notificationView = null;
    private TextView notificationTitle = null;
    private TextView notificationBody = null;
    private TextView timerText = null;
    private ImageButton closeButton = null;
    /** Кнопка подтверждения для окон {@link NotificationMessage#requiresAcknowledgement}. */
    private Button notificationOkButton = null;
    /** Обычная высота окна событий (40% экрана); окно с кнопкой «Ок» растягивается по содержимому. */
    private int notificationWindowBaseHeightPx = 0;
    
    private Queue<NotificationMessage> notificationQueue = new LinkedList<>();
    private NotificationMessage currentNotification = null;
    private Handler notificationHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = null;
    private int remainingSeconds = 0;
    private boolean isNotificationShowing = false;
    /** Сигнал окна предупреждения; способ проигрывания выбирается в настройках, см. {@link NotificationAlertSound}. */
    @Nullable private NotificationAlertSound alertSound;

    // Класс для хранения данных уведомления
    private static class NotificationMessage {
        String title;
        String body;
        int durationSeconds;
        /** Звук в raw — только для предупреждений и ошибок, не для информационных окон. */
        boolean playAlertSound;
        /** Окно без таймера и крестика: закрывается только кнопкой «Ок», чтобы событие точно не прошло мимо. */
        boolean requiresAcknowledgement;

        NotificationMessage(String title, String body, int durationSeconds, boolean playAlertSound) {
            this(title, body, durationSeconds, playAlertSound, false);
        }

        NotificationMessage(String title, String body, int durationSeconds, boolean playAlertSound,
                            boolean requiresAcknowledgement) {
            this.title = title;
            this.body = body;
            this.durationSeconds = durationSeconds;
            this.playAlertSound = playAlertSound;
            this.requiresAcknowledgement = requiresAcknowledgement;
        }
    }

    private void openMainActivityFromOverlay() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "openMainActivityFromOverlay failed", e);
        }
    }

    private static Float parseTemperatureCelsius(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            String s = raw.trim().replace(',', '.');
            int space = s.indexOf(' ');
            if (space > 0) {
                s = s.substring(0, space);
            }
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long temperatureHistoryRetentionMs() {
        return Math.max(TEMP_RAPID_CHANGE_WINDOW_MS, SystemSettingsPreferences.tempAverageWindowMs(this));
    }

    private void trimTemperatureHistoryDeque() {
        long now = System.currentTimeMillis();
        long keep = temperatureHistoryRetentionMs();
        while (!recentTemperatures.isEmpty() && now - recentTemperatures.peekFirst().timeMs > keep) {
            recentTemperatures.removeFirst();
        }
    }

    private void trimSpeedHistoryDeque() {
        long now = System.currentTimeMillis();
        long keep = temperatureHistoryRetentionMs();
        while (!recentSpeeds.isEmpty() && now - recentSpeeds.peekFirst().timeMs > keep) {
            recentSpeeds.removeFirst();
        }
    }

    private Float averageSpeedOverLastMillis(long windowMs) {
        if (recentSpeeds.isEmpty() || windowMs <= 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        double sum = 0;
        int n = 0;
        for (SpeedSample s : recentSpeeds) {
            if (now - s.timeMs <= windowMs) {
                sum += s.kmh;
                n++;
            }
        }
        return n > 0 ? (float) (sum / n) : null;
    }

    private static String formatThermometerReadingC(float celsius) {
        return String.format(Locale.US, "%.1f", celsius);
    }

    /**
     * Одна точка входа для показаний с датчика: поправка, алерты по истории (калиброванная °C), пайплайн оверлея.
     */
    private void handleTemperaturePayload(String raw) {
        if (raw == null) {
            return;
        }
        lastSensorFrameElapsedMs = SystemClock.elapsedRealtime();
        if (sensorSignalLost) {
            onSensorSignalRestored();
        }
        startSensorDataWatchdog();
        lastRawTemperatureStringFromDevice = raw.trim();
        lastKnownRawTemperatureFromDevice = lastRawTemperatureStringFromDevice;
        Float t = parseTemperatureCelsius(lastRawTemperatureStringFromDevice);
        if (t != null) {
            float calibrated = t + SystemSettingsPreferences.thermometerOffsetDegrees(this);
            String disp = formatThermometerReadingC(calibrated);
            onTemperatureReadingForAlerts(calibrated, disp);
            status(disp, "✔", disp);
        } else {
            status(lastRawTemperatureStringFromDevice, "✔", lastRawTemperatureStringFromDevice);
        }
    }

    /** Перерисовка без новой точки в истории (скорость или настройки изменились). */
    private void refreshCalibratedTemperatureDisplayFromCache() {
        if (lastRawTemperatureStringFromDevice == null || lastRawTemperatureStringFromDevice.isEmpty()) {
            if (!lastTemperatureDisplay.isEmpty()) {
                String data = lastStatusIconForReading + " " + lastTemperatureDisplay + " °C" + voltageSuffix() + speedSuffix();
                updateNotification(data, lastTemperatureDisplay);
                if (notificationManager != null && notification != null) {
                    notificationManager.notify(NOTIFICATION_ID, notification);
                }
            }
            return;
        }
        Float t = parseTemperatureCelsius(lastRawTemperatureStringFromDevice);
        if (t == null) {
            return;
        }
        float calibrated = t + SystemSettingsPreferences.thermometerOffsetDegrees(this);
        String disp = formatThermometerReadingC(calibrated);
        status(disp, lastStatusIconForReading, disp);
    }

    private void resetTemperatureAlertSessionState() {
        pendingColdStartTempCheck = true;
        previousReadingCelsius = null;
        recentTemperatures.clear();
        unreliableTempAlertLatch = false;
        initialTemperatureShownOnScreen = false;
        lastRawTemperatureStringFromDevice = "";
        // Новая сессия датчика: удерживать и «терять» пока нечего, сторож взведёт первый кадр.
        stopSensorDataWatchdog();
        lastSensorFrameElapsedMs = 0L;
        lastKnownTemperatureDisplay = "";
        lastKnownRawTemperatureFromDevice = "";
    }

    private boolean isVehicleInMotion() {
        return ENABLE_GPS_SPEED && lastSpeedKmh >= MOTION_MIN_SPEED_KMH;
    }

    /**
     * События из спецификации: холод при старте/в движении, скачок за 5 минут.
     * Предупреждение о недостоверности (скачки) при удержании «старой» температуры по правилу скорости отключается —
     * иначе датчик нагревается конвективно, экран морозится, история смешивает разные режимы.
     */
    private void onTemperatureReadingForAlerts(float celsius, String formattedCalibratedDisp) {
        long now = System.currentTimeMillis();

        if (pendingColdStartTempCheck) {
            pendingColdStartTempCheck = false;
            if (celsius < AIR_TEMP_COLD_THRESHOLD_C) {
                showNotification(ALERT_TITLE_WARNING, MSG_AIR_TEMP_COLD, TEMP_ALERT_DURATION_SEC, true);
            }
        } else if (previousReadingCelsius != null && isVehicleInMotion()
                && previousReadingCelsius >= AIR_TEMP_COLD_THRESHOLD_C
                && celsius < AIR_TEMP_COLD_THRESHOLD_C) {
            showNotification(ALERT_TITLE_WARNING, MSG_AIR_TEMP_COLD, TEMP_ALERT_DURATION_SEC, true);
        }

        previousReadingCelsius = celsius;

        if (isLowAverageSpeedThermalHoldActive(formattedCalibratedDisp)) {
            return;
        }

        recentTemperatures.addLast(new TempSample(now, celsius));
        trimTemperatureHistoryDeque();
        float minT = celsius;
        float maxT = celsius;
        for (TempSample s : recentTemperatures) {
            minT = Math.min(minT, s.celsius);
            maxT = Math.max(maxT, s.celsius);
        }
        float span = maxT - minT;
        if (span > TEMP_RAPID_CHANGE_DELTA_C) {
            if (!unreliableTempAlertLatch) {
                unreliableTempAlertLatch = true;
                showNotification(ALERT_TITLE_WARNING, MSG_TEMP_UNRELIABLE, TEMP_ALERT_DURATION_SEC, true);
            }
        } else if (span < TEMP_RAPID_CHANGE_DELTA_C - 1f) {
            unreliableTempAlertLatch = false;
        }
    }

    private void notifySensorLinkLost() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSensorLostAlertElapsedMs < SENSOR_LOST_DEBOUNCE_MS) {
            return;
        }
        lastSensorLostAlertElapsedMs = now;
        // Без таймера автозакрытия: потерю датчика пропускать нельзя так же, как и успешное переподключение.
        showAcknowledgeNotification(ALERT_TITLE_ERROR, MSG_SENSOR_LINK_LOST, true);
    }

    /** Удержание активно: сигнала нет, но окно {@link SystemSettingsPreferences#sensorSignalHoldMs} ещё не истекло. */
    private boolean isSensorHoldActive() {
        return sensorSignalLost && !sensorHoldExpired;
    }

    /**
     * Начало эпизода потери сигнала: последнее показание остаётся на экране всё окно удержания
     * ({@link SystemSettingsPreferences#sensorSignalHoldMs}, настраивается в «Настройках системы»),
     * параллельно идут переподключения. Эпизод закрывается либо {@link #onSensorSignalRestored()},
     * либо {@link #onSensorHoldExpired()}.
     */
    private void onSensorSignalLost(String reason) {
        if (sensorSignalLost) {
            return;
        }
        sensorSignalLost = true;
        sensorHoldExpired = false;
        sensorLossStartedElapsedMs = SystemClock.elapsedRealtime();
        long holdMs = SystemSettingsPreferences.sensorSignalHoldMs(this);
        LocalDiagLog.line(this, "W", TAG, "sensor signal lost (" + reason + "), holding last reading for "
                + holdMs + " ms");

        // Прочерки не показываем: возвращаем на экран последнее реально измеренное значение.
        // Смена статуса могла успеть его стереть — тянем из отдельного кеша.
        restoreHeldTemperatureDisplay();

        stopSensorDataWatchdog();
        cancelSensorHoldExpiry();
        sensorHoldExpireRunnable = this::onSensorHoldExpired;
        mainLooper.postDelayed(sensorHoldExpireRunnable, holdMs);

        scheduleSensorReconnectAttempt(0L);
    }

    /**
     * Возвращает на экран последнее известное показание и помечает его значком «несвежее».
     * Проходит по обычному пайплайну {@link #status}, поэтому оверлей, иконка и шторка сходятся между собой.
     */
    private void restoreHeldTemperatureDisplay() {
        if (lastKnownTemperatureDisplay.isEmpty()) {
            return;
        }
        lastRawTemperatureStringFromDevice = lastKnownRawTemperatureFromDevice;
        status(lastKnownTemperatureDisplay, SENSOR_HOLD_STATUS_ICON, lastKnownTemperatureDisplay);
    }

    /**
     * Кадр с датчика после эпизода потери. Показываем окно с обязательным подтверждением: пользователь
     * должен узнать, что связь пропадала, даже если сейчас всё снова работает.
     */
    private void onSensorSignalRestored() {
        boolean holdWasExpired = sensorHoldExpired;
        sensorSignalLost = false;
        sensorHoldExpired = false;
        cancelSensorReconnectAttempts();
        cancelSensorHoldExpiry();
        LocalDiagLog.line(this, "I", TAG, "sensor signal restored (holdExpired=" + holdWasExpired + ")");
        showAcknowledgeNotification(ALERT_TITLE_WARNING, MSG_SENSOR_LINK_RESTORED, true);
    }

    /** Окно удержания истекло, датчик не вернулся: только теперь показываем прочерки и сообщаем о потере связи. */
    private void onSensorHoldExpired() {
        sensorHoldExpireRunnable = null;
        if (!sensorSignalLost) {
            return;
        }
        sensorHoldExpired = true;
        lastKnownTemperatureDisplay = "";
        lastKnownRawTemperatureFromDevice = "";
        // Датчик признан потерянным: дальше не долбимся в порт. Вернуть его может подключение USB
        // (USB_DEVICE_ATTACHED поднимает сервис заново) или перезапуск приложения.
        cancelSensorReconnectAttempts();
        LocalDiagLog.line(this, "W", TAG, "sensor hold expired, reporting link loss, reconnect attempts stopped");
        status("sensor signal lost", "❌", null);
        notifySensorLinkLost();
    }

    /**
     * Новая длительность из настроек применяется и к уже идущему удержанию: иначе проверить настройку
     * можно было бы только дождавшись конца текущего эпизода.
     */
    private void rescheduleSensorHoldExpiry() {
        if (!isSensorHoldActive()) {
            return;
        }
        cancelSensorHoldExpiry();
        long elapsed = SystemClock.elapsedRealtime() - sensorLossStartedElapsedMs;
        long remaining = SystemSettingsPreferences.sensorSignalHoldMs(this) - elapsed;
        sensorHoldExpireRunnable = this::onSensorHoldExpired;
        mainLooper.postDelayed(sensorHoldExpireRunnable, Math.max(0L, remaining));
    }

    private void cancelSensorHoldExpiry() {
        if (sensorHoldExpireRunnable != null) {
            mainLooper.removeCallbacks(sensorHoldExpireRunnable);
            sensorHoldExpireRunnable = null;
        }
    }

    private void scheduleSensorReconnectAttempt(long delayMs) {
        cancelSensorReconnectAttempts();
        sensorReconnectRunnable = this::attemptSensorReconnect;
        mainLooper.postDelayed(sensorReconnectRunnable, delayMs);
    }

    private void cancelSensorReconnectAttempts() {
        if (sensorReconnectRunnable != null) {
            mainLooper.removeCallbacks(sensorReconnectRunnable);
            sensorReconnectRunnable = null;
        }
    }

    /**
     * Одна попытка: полный цикл disconnect → connect → подписка на кадры. Успехом считается не сам
     * {@link IThermometerDevice#connect()}, а первый пришедший кадр — порт может открыться и при оборванной проводке.
     * Попытки идут только пока держим показание: после {@link #onSensorHoldExpired()} датчик признан
     * потерянным, и дёргать порт до конца поездки незачем.
     */
    private void attemptSensorReconnect() {
        sensorReconnectRunnable = null;
        if (!isSensorHoldActive() || thermometerDevice == null) {
            return;
        }
        try {
            thermometerDevice.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "sensor reconnect: disconnect failed: " + e.getMessage());
        }
        connected = false;

        boolean opened = false;
        try {
            opened = thermometerDevice.connect();
        } catch (Exception e) {
            Log.w(TAG, "sensor reconnect: connect failed: " + e.getMessage());
        }
        if (opened) {
            connected = true;
            thermometerDevice.sendCommand("~W1000");
        }
        LocalDiagLog.line(this, "I", TAG, "sensor reconnect attempt: opened=" + opened);
        scheduleSensorReconnectAttempt(SENSOR_RECONNECT_INTERVAL_MS);
    }

    /**
     * Сторож тишины в эфире. Взводится первым кадром: обрыв проводки датчика не всегда даёт ошибку USB —
     * переходник остаётся живым, а кадры просто перестают приходить.
     */
    private void startSensorDataWatchdog() {
        if (sensorDataWatchdogRunnable != null) {
            return;
        }
        sensorDataWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastSensorFrameElapsedMs != 0L && !sensorSignalLost
                        && SystemClock.elapsedRealtime() - lastSensorFrameElapsedMs > SENSOR_DATA_TIMEOUT_MS) {
                    onSensorSignalLost("no frames for " + SENSOR_DATA_TIMEOUT_MS + " ms");
                    return;
                }
                mainLooper.postDelayed(this, SENSOR_DATA_WATCHDOG_PERIOD_MS);
            }
        };
        mainLooper.postDelayed(sensorDataWatchdogRunnable, SENSOR_DATA_WATCHDOG_PERIOD_MS);
    }

    private void stopSensorDataWatchdog() {
        if (sensorDataWatchdogRunnable != null) {
            mainLooper.removeCallbacks(sensorDataWatchdogRunnable);
            sensorDataWatchdogRunnable = null;
        }
    }

    private void updateOverlayTemperatureIcon(String tempVal) {
        if (overlayTemperatureIcon == null) {
            return;
        }
        Float t = parseTemperatureCelsius(tempVal);
        if (t == null) {
            return;
        }
        boolean cold = t < 5f;
        overlayTemperatureIcon.setImageResource(cold ? R.drawable.snowflake : R.drawable.thermometer);
    }

    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Инфлейтим layout
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        overlayView = inflater.inflate(R.layout.overlay_layout, null);
        overlayTemperatureIcon = overlayView.findViewById(R.id.overlay_temperature_icon);
        tvTemperature = overlayView.findViewById(R.id.overlay_content_1);
        tvVoltage = overlayView.findViewById(R.id.overlay_content_2);
        tvSpeed = overlayView.findViewById(R.id.overlay_speed);
        if (tvVoltage != null && !ENABLE_BATTERY_VOLTAGE) {
            tvVoltage.setVisibility(View.GONE);
        }
        if (tvSpeed != null) {
            tvSpeed.setVisibility(View.GONE);
            if (ENABLE_GPS_SPEED) {
                tvSpeed.setText(lastSpeedText);
            }
        }

        if (overlayTemperatureIcon != null) {
            overlayTemperatureIcon.setOnClickListener(v -> openMainActivityFromOverlay());
        }

        // Создаем параметры окна
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

//        int statusBarHeight = 0;
//        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
//        if (resourceId > 0) statusBarHeight = getResources().getDimensionPixelSize(resourceId);

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.TRANSLUCENT;
        params.width = scaledOverlayWidthPx(false);
        params.height = scaledOverlayHeightPx();
//        params.verticalMargin = 25;
        // Центр оверлея привязывается к точке на экране; x,y — смещение от центра экрана.
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        // Добавляем view в WindowManager
        windowManager.addView(overlayView, params);
        applyOverlayChromeLayout();
        applyOverlayTextSizes();

        // Инициализируем окно уведомлений
        initNotificationWindow();
    }

    private float overlayUiScaleFactor() {
        return UiScalingPreferences.overlayFontScale(this);
    }

    private int scaledOverlayWidthPx(boolean includeSpeedColumn) {
        int base = includeSpeedColumn ? OVERLAY_WIDTH_WITH_SPEED : OVERLAY_WIDTH_NO_SPEED;
        return Math.max(1, Math.round(base * overlayUiScaleFactor()));
    }

    private int scaledOverlayHeightPx() {
        return Math.max(1, Math.round(OVERLAY_HEIGHT_PX * overlayUiScaleFactor()));
    }

    private int dpToRoundedPx(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    /** Отступы корня, размер иконки и промежутки — вместе со шрифтом (масштаб из настроек). */
    private void applyOverlayChromeLayout() {
        if (overlayView == null) {
            return;
        }
        float sc = overlayUiScaleFactor();
        overlayView.setPadding(
                dpToRoundedPx(14f * sc),
                dpToRoundedPx(10f * sc),
                dpToRoundedPx(14f * sc),
                dpToRoundedPx(12f * sc));

        if (overlayTemperatureIcon != null) {
            ViewGroup.LayoutParams lp = overlayTemperatureIcon.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                int side = dpToRoundedPx(24f * sc);
                mlp.width = side;
                mlp.height = side;
                mlp.setMarginEnd(dpToRoundedPx(4f * sc));
                overlayTemperatureIcon.setLayoutParams(lp);
            }
        }

        overlaySetInlineMarginEnd(tvTemperature, 4f * sc);
        overlaySetInlineMarginEnd(tvVoltage, 4f * sc);
    }

    private void overlaySetInlineMarginEnd(@Nullable TextView tv, float marginDpOrZero) {
        if (tv == null || marginDpOrZero <= 0f || tv.getVisibility() == View.GONE) {
            return;
        }
        ViewGroup.LayoutParams lp = tv.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginEnd(dpToRoundedPx(marginDpOrZero));
            tv.setLayoutParams(lp);
        }
    }

    /** Пересчёт размеров окна и якорной позиции после смены масштаба оверлея. */
    private void reloadOverlayDimensionsAndPosition() {
        if (overlayView == null || windowManager == null || settingsManager == null) {
            return;
        }
        applyOverlayChromeLayout();
        applyOverlayTextSizes();
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
            boolean overlayOn = overlayView.getVisibility() == View.VISIBLE;
            boolean speedColumn = overlayOn && ENABLE_GPS_SPEED && !currentForegroundApp.isEmpty()
                    && settingsManager.isOverlaySpeedVisibleForApp(currentForegroundApp);
            params.width = scaledOverlayWidthPx(speedColumn);
            params.height = scaledOverlayHeightPx();
            windowManager.updateViewLayout(overlayView, params);
            if (overlayOn) {
                updateOverlayPosition(lastOverlayPositionXPercent, lastOverlayPositionYPercent);
            }
        } catch (Exception e) {
            Log.e(TAG, "reloadOverlayDimensionsAndPosition: " + e.getMessage());
        }
    }

    private void applyOverlayTextSizes() {
        if (overlayView == null) {
            return;
        }
        float scale = UiScalingPreferences.overlayFontScale(this);
        setOverlayTextSizeSp(tvTemperature, UiScalingPreferences.OVERLAY_PRIMARY_SP * scale);
        setOverlayTextSizeSp(tvVoltage, UiScalingPreferences.OVERLAY_PRIMARY_SP * scale);
        setOverlayTextSizeSp(tvSpeed, UiScalingPreferences.OVERLAY_PRIMARY_SP * scale);
    }

    private static void setOverlayTextSizeSp(@Nullable TextView textView, float sizeSp) {
        if (textView == null) {
            return;
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
    }

    private void applyFloatingNotificationTextSizes() {
        if (notificationTitle == null || notificationBody == null || timerText == null) {
            return;
        }
        float scale = UiScalingPreferences.alertFontScale(this);
        setOverlayTextSizeSp(notificationTitle, UiScalingPreferences.ALERT_TITLE_SP * scale);
        setOverlayTextSizeSp(timerText, UiScalingPreferences.ALERT_TIMER_SP * scale);
        setOverlayTextSizeSp(notificationBody, UiScalingPreferences.ALERT_BODY_SP * scale);
        setOverlayTextSizeSp(notificationOkButton, UiScalingPreferences.ALERT_BUTTON_SP * scale);
    }
    
    /**
     * Запускает периодическую проверку активного приложения
     */
    private void startAppMonitoring() {
        appCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndApplySettings();
                // Проверяем каждые 500 мс
                appCheckHandler.postDelayed(this, 500);
            }
        };
        appCheckHandler.post(appCheckRunnable);
    }
    
    /**
     * Останавливает мониторинг активного приложения
     */
    private void stopAppMonitoring() {
        if (appCheckRunnable != null) {
            appCheckHandler.removeCallbacks(appCheckRunnable);
        }
    }
    
    private static String normalizeProcessToPackage(String processName) {
        if (processName == null || processName.isEmpty()) {
            return "";
        }
        int idx = processName.indexOf(':');
        return idx > 0 ? processName.substring(0, idx) : processName;
    }

    private boolean isIgnorableForegroundNoise(String pkg) {
        return pkg.isEmpty() || FOREGROUND_PROCESS_IGNORE.contains(pkg);
    }

    /**
     * Процесс «выше» типичного фонового сервиса: часто так помечено активное приложение на OEM-прошивках,
     * где не выставляют строго IMPORTANCE_FOREGROUND / VISIBLE.
     */
    private static boolean isInteractiveImportance(int importance) {
        return importance > 0 && importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
    }

    private void maybeLogRunningProcessesDiagnostics(List<ActivityManager.RunningAppProcessInfo> processes) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastForegroundDiagLogMs < 30_000L) {
            return;
        }
        lastForegroundDiagLogMs = now;
        int total = processes.size();
        int show = Math.min(total, 30);
        Log.w(TAG, "RunningAppProcesses diagnostic (" + show + "/" + total + "). Grant usage stats for reliable detection.");
        for (int i = 0; i < show; i++) {
            ActivityManager.RunningAppProcessInfo proc = processes.get(i);
            Log.w(TAG, "  importance=" + proc.importance + " proc=" + proc.processName);
        }
    }

    /**
     * Если в списке ровно одно приложение из пользовательских настроек — используем его (крайний случай при странных importance).
     */
    private String pickSingleConfiguredPackageFromProcesses(List<ActivityManager.RunningAppProcessInfo> processes) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (ActivityManager.RunningAppProcessInfo proc : processes) {
            String base = normalizeProcessToPackage(proc.processName);
            if (isIgnorableForegroundNoise(base)) {
                continue;
            }
            if (settingsManager.getAppSettings(base) != null) {
                seen.add(base);
                if (seen.size() > 1) {
                    return "";
                }
            }
        }
        return seen.isEmpty() ? "" : seen.iterator().next();
    }
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int uid = android.os.Process.myUid();
        String pkg = getPackageName();
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg);
        if (mode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg);
            return mode == AppOpsManager.MODE_ALLOWED;
        }
        return false;
    }

    /**
     * Переднее приложение по {@link UsageStatsManager#queryEvents(long, long)} инкрементально с последнего успешного вызова.
     * Если новых точек событий нет — возвращает предыдущий «sticky», без просадки в пустое окно 2 мин как раньше.
     * <p>
     * API 29+: предпочтительные типы событий — {@link UsageEvents.Event#ACTIVITY_RESUMED} /
     * {@link UsageEvents.Event#ACTIVITY_PAUSED}; устаревшие {@code MOVE_TO_*} сохранены как подстраховка на некоторых прошивках.
     * Это именно активность приложения на переднем плане, не путать с {@link UsageEvents.Event#SCREEN_INTERACTIVE}.
     */
    private String queryForegroundPackageFromUsageEvents() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return stickyForegroundFromUsageEvents;
        }
        final long rangeEndUtcMs = System.currentTimeMillis();
        long rangeBeginUtcMs = usageEventsNextRangeStartUtcMs;
        if (rangeBeginUtcMs <= 0L) {
            rangeBeginUtcMs = Math.max(0L, rangeEndUtcMs - USAGE_EVENTS_BOOTSTRAP_WINDOW_MS);
        }
        if (rangeBeginUtcMs >= rangeEndUtcMs) {
            return stickyForegroundFromUsageEvents;
        }

        UsageEvents events = usm.queryEvents(rangeBeginUtcMs, rangeEndUtcMs);
        long maxSeenTs = Long.MIN_VALUE;
        boolean hadEventPayload = false;
        if (events != null) {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                hadEventPayload = true;
                maxSeenTs = Math.max(maxSeenTs, event.getTimeStamp());

                int type = event.getEventType();
                if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == USAGE_EVENTS_MOVE_TO_FOREGROUND) {
                    String come = event.getPackageName();
                    if (come != null && !come.isEmpty()) {
                        stickyForegroundFromUsageEvents = come;
                    }
                }
                // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND намеренно не меняют sticky: смена activity в том же приложении даёт только pause,
                // а фактическое приложение задаёт следующий ACTIVITY_RESUMED (другого пакета или того же после роутера).
            }
        }

        if (hadEventPayload && maxSeenTs != Long.MIN_VALUE) {
            usageEventsNextRangeStartUtcMs = maxSeenTs + 1L;
        } else {
            usageEventsNextRangeStartUtcMs = rangeEndUtcMs + 1L;
        }
        return stickyForegroundFromUsageEvents;
    }

    private boolean isHomePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null
                    && resolved.activityInfo != null
                    && packageName.equals(resolved.activityInfo.packageName);
        } catch (Exception e) {
            Log.w(TAG, "isHomePackage check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fallback без статистики использования: собираем процессы с importance ниже типичного «service»,
     * игнорируем system UI; отдельно приоритетно учитываем пакеты из сохранённых настроек.
     */
    private String resolveForegroundFromRunningProcesses() {
        try {
            ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (activityManager == null) {
                return "";
            }
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            if (processes == null || processes.isEmpty()) {
                Log.d(TAG, "getRunningAppProcesses null or empty");
                return "";
            }

            String hostPackage = getPackageName();

            // Среди настроенных приложений выбираем процесс с минимальным importance — обычно ближе к «верхнему» окну.
            String configuredBest = null;
            int configuredBestImp = Integer.MAX_VALUE;
            for (ActivityManager.RunningAppProcessInfo proc : processes) {
                String base = normalizeProcessToPackage(proc.processName);
                if (isIgnorableForegroundNoise(base)) {
                    continue;
                }
                if (settingsManager.getAppSettings(base) == null) {
                    continue;
                }
                if (!isInteractiveImportance(proc.importance)) {
                    continue;
                }
                if (proc.importance < configuredBestImp) {
                    configuredBestImp = proc.importance;
                    configuredBest = base;
                }
            }
            if (configuredBest != null) {
                Log.d(TAG, "Foreground app (configured + interactive importance=" + configuredBestImp + "): " + configuredBest);
                return configuredBest;
            }

            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            for (ActivityManager.RunningAppProcessInfo proc : processes) {
                String base = normalizeProcessToPackage(proc.processName);
                if (isIgnorableForegroundNoise(base)) {
                    continue;
                }
                if (isInteractiveImportance(proc.importance)) {
                    candidates.add(base);
                }
            }

            if (candidates.isEmpty()) {
                String loneConfigured = pickSingleConfiguredPackageFromProcesses(processes);
                if (!loneConfigured.isEmpty()) {
                    Log.d(TAG, "Foreground app (single configured package in process list, relaxed importance): " + loneConfigured);
                    return loneConfigured;
                }
                maybeLogRunningProcessesDiagnostics(processes);
                Log.d(TAG, "No foreground candidates from RunningAppProcesses after filtering");
                return "";
            }

            for (String pkg : candidates) {
                if (settingsManager.getAppSettings(pkg) != null) {
                    Log.d(TAG, "Foreground app (running proc, matches saved settings): " + pkg);
                    return pkg;
                }
            }

            for (String pkg : candidates) {
                if (!pkg.equals(hostPackage)) {
                    Log.d(TAG, "Foreground app (running proc, non-host heuristic): " + pkg);
                    return pkg;
                }
            }

            // Только наш процесс: типично из‑за startForeground() (сервис в foreground) и/или того, что
            // getRunningAppProcesses() для сторонних приложений часто видит лишь свой UID. Это не значит,
            // что пользователь сейчас в MainActivity — оверлей к этому почти не привязан.
            Log.d(TAG, "Foreground ambiguous: sole interactive candidate is host ("
                    + hostPackage + "). Foreground service elevates process importance; grant usage stats "
                    + "or expect no app switch detection via RunningAppProcesses.");
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Error resolving foreground from running processes: " + e.getMessage());
            return "";
        }
    }
    /**
     * Package текущего приложения пользователя (для применения настроек оверлея).
     */
    private String getForegroundApp() {
        if (hasUsageStatsPermission()) {
            try {
                String fromUsage = queryForegroundPackageFromUsageEvents();
                if (fromUsage != null && !fromUsage.isEmpty()) {
                    if (isHomePackage(fromUsage)) {
                        Log.d(TAG, "Foreground is launcher/home, mapping to SYSTEM settings");
                        return AppSettings.SYSTEM_PACKAGE_NAME;
                    }
                    Log.d(TAG, "Foreground app detected (usage events): " + fromUsage);
                    return fromUsage;
                }
            } catch (Exception e) {
                Log.e(TAG, "Usage events foreground detection failed: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "Usage stats not granted; using RunningAppProcesses heuristic");
        }

        String resolved = resolveForegroundFromRunningProcesses();
        if (!resolved.isEmpty()) {
            if (isHomePackage(resolved)) {
                Log.d(TAG, "Foreground is launcher/home, mapping to SYSTEM settings");
                return AppSettings.SYSTEM_PACKAGE_NAME;
            }
            return resolved;
        }

        Log.d(TAG, "No foreground app detected");
        return "";
    }
    
    /**
     * Проверяет активное приложение и применяет настройки
     */
    private void checkAndApplySettings() {
        String foregroundApp = getForegroundApp();
        if (foregroundApp.isEmpty()) {
            // При наличии usage stats пустой результат обычно означает возврат на launcher/home.
            if (hasUsageStatsPermission() && !AppSettings.SYSTEM_PACKAGE_NAME.equals(currentForegroundApp)) {
                Log.d(TAG, "No foreground app resolved, applying SYSTEM settings");
                currentForegroundApp = AppSettings.SYSTEM_PACKAGE_NAME;
                applySettingsForApp(currentForegroundApp);
            }
            return;
        }

        // Если приложение изменилось, применяем новые настройки
        if (!foregroundApp.equals(currentForegroundApp)) {
            Log.d(TAG, "App changed: " + currentForegroundApp + " -> " + foregroundApp);
            currentForegroundApp = foregroundApp;
            applySettingsForApp(foregroundApp);
        }
    }
    
    /**
     * Применяет настройки для конкретного приложения
     */
    private void applySettingsForApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.d(TAG, "applySettingsForApp: packageName is null or empty");
            return;
        }
        
        Log.d(TAG, "Applying settings for app: " + packageName);
        
        // Получаем настройки для приложения
        AppSettings appSettings = settingsManager.getAppSettings(packageName);
        boolean overlayEnabled = settingsManager.isOverlayEnabledForApp(packageName);
        boolean notificationsEnabled = settingsManager.isNotificationsEnabledForApp(packageName);
        boolean overlaySpeedVisible = settingsManager.isOverlaySpeedVisibleForApp(packageName);
        int overlayAlphaTop = settingsManager.getOverlayAlphaTopForApp(packageName);
        int overlayAlphaBottom = settingsManager.getOverlayAlphaBottomForApp(packageName);
        int xPercent = settingsManager.getOverlayPositionXPercentForApp(packageName);
        int yPercent = settingsManager.getOverlayPositionYPercentForApp(packageName);
        lastOverlayPositionXPercent = xPercent;
        lastOverlayPositionYPercent = yPercent;

        // Логируем настройки
        if (appSettings != null) {
            Log.d(TAG, "  Settings found: overlay=" + overlayEnabled + 
                      ", notifications=" + notificationsEnabled + 
                      ", speedVisible=" + overlaySpeedVisible
                      + ", alphaTop=" + overlayAlphaTop
                      + ", alphaBottom=" + overlayAlphaBottom
                      + ", position=" + xPercent + "%," + yPercent + "%");
        } else {
            Log.d(TAG, "  No settings found for this app, using defaults (overlay=false, notifications=false, speedVisible=false, custom alpha defaults)");
        }
        currentOverlayAlphaTop = AppSettings.clampOverlayAlpha(overlayAlphaTop);
        currentOverlayAlphaBottom = AppSettings.clampOverlayAlpha(overlayAlphaBottom);
        
        // Применяем видимость оверлея
        if (overlayView != null) {
            overlayView.setVisibility(overlayEnabled ? View.VISIBLE : View.GONE);
            Log.d(TAG, "  Overlay visibility set to: " + (overlayEnabled ? "VISIBLE" : "GONE"));
        }

        if (tvSpeed != null) {
            if (ENABLE_GPS_SPEED && overlayEnabled && overlaySpeedVisible) {
                tvSpeed.setVisibility(View.VISIBLE);
                tvSpeed.setText(lastSpeedText);
            } else {
                tvSpeed.setVisibility(View.GONE);
            }
        }

        if (overlayView != null && windowManager != null) {
            try {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
                boolean widthWithSpeed = ENABLE_GPS_SPEED && overlayEnabled && overlaySpeedVisible;
                int targetWidth = scaledOverlayWidthPx(widthWithSpeed);
                int targetHeight = scaledOverlayHeightPx();
                if (params.width != targetWidth || params.height != targetHeight) {
                    params.width = targetWidth;
                    params.height = targetHeight;
                    windowManager.updateViewLayout(overlayView, params);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating overlay width: " + e.getMessage());
            }
        }

        applyOverlayChromeLayout();
        applyOverlayTextSizes();

        // Применяем позицию оверлея после изменения ширины/высоты и масштаба контента.
        if (overlayEnabled && overlayView != null && windowManager != null) {
            updateOverlayPosition(xPercent, yPercent);
        }

        if (lastTemperatureDisplay != null && !lastTemperatureDisplay.isEmpty()) {
            String data = lastStatusIconForReading + " " + lastTemperatureDisplay + " °C" + voltageSuffix() + speedSuffix();
            updateNotification(data, lastTemperatureDisplay);
        } else {
            updateNotification("—" + voltageSuffix() + speedSuffix(), null);
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && notification != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
        
        // Сохраняем настройку уведомлений (будет использоваться при показе)
        // notificationsEnabled будет проверяться в showNotification
    }
    
    /**
     * Позиция оверлея: якорь — геометрический центр окна оверлея.
     * Проценты задают точку на экране от левого верхнего угла (0% — край слева/сверху, 50% — середина оси, 100% — край справа/снизу).
     * При (0%, 0%) центр оверлея совпадает с углом экрана — большая часть уходит за пределы экрана.
     * Gravity CENTER: x,y — смещение центра оверлея относительно центра экрана; размер окна берём из {@code params.width/height}.
     */
    private void updateOverlayPosition(int xPercent, int yPercent) {
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();

            android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;

            int overlayW = params.width > 0 ? params.width : overlayView.getWidth();
            int overlayH = params.height > 0 ? params.height : overlayView.getHeight();
            if (overlayW <= 0 || overlayH <= 0) {
                int specW = params.width > 0
                        ? View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST);
                int specH = params.height > 0
                        ? View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST);
                overlayView.measure(specW, specH);
                if (overlayW <= 0) {
                    overlayW = overlayView.getMeasuredWidth();
                }
                if (overlayH <= 0) {
                    overlayH = overlayView.getMeasuredHeight();
                }
            }
            if (overlayW <= 0) {
                boolean withSpeed = tvSpeed != null && tvSpeed.getVisibility() != View.GONE;
                overlayW = scaledOverlayWidthPx(withSpeed && ENABLE_GPS_SPEED);
            }
            if (overlayH <= 0) {
                overlayH = scaledOverlayHeightPx();
            }

            xPercent = AppSettings.clampOverlayPositionPercent(xPercent);
            yPercent = AppSettings.clampOverlayPositionPercent(yPercent);

            params.gravity = Gravity.CENTER;

            float anchorX = screenWidth * xPercent / 100f;
            float anchorY = screenHeight * yPercent / 100f;
            float screenCx = screenWidth / 2f;
            float screenCy = screenHeight / 2f;

            params.x = Math.round(anchorX - screenCx);
            params.y = Math.round(anchorY - screenCy);

            int windowLeft = Math.round(screenCx + params.x - overlayW / 2f);
            int windowTop = Math.round(screenCy + params.y - overlayH / 2f);

            updateOverlayCornerRadii(windowLeft, windowTop, overlayW, overlayH, screenWidth, screenHeight);
            windowManager.updateViewLayout(overlayView, params);

            Log.d(TAG, "Overlay position (center anchor): X=" + xPercent + "%, Y=" + yPercent + "% "
                    + "→ centerOffset=(" + params.x + "," + params.y + "), leftTop=(" + windowLeft + "," + windowTop + "), anchor=(" + anchorX + "," + anchorY + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error updating overlay position: " + e.getMessage());
        }
    }

    private void updateOverlayCornerRadii(int left, int top, int overlayW, int overlayH, int screenW, int screenH) {
        if (overlayView == null) {
            return;
        }

        boolean touchesTop = top <= 0;
        boolean touchesBottom = top + overlayH >= screenH;
        boolean touchesLeft = left <= 0;
        boolean touchesRight = left + overlayW >= screenW;

        boolean roundTopLeft = true;
        boolean roundTopRight = true;
        boolean roundBottomRight = true;
        boolean roundBottomLeft = true;

        if (touchesTop) {
            roundTopLeft = false;
            roundTopRight = false;
        }
        if (touchesBottom) {
            roundBottomLeft = false;
            roundBottomRight = false;
        }
        if (touchesLeft) {
            roundTopLeft = false;
            roundBottomLeft = false;
        }
        if (touchesRight) {
            roundTopRight = false;
            roundBottomRight = false;
        }

        float r = getResources().getDimension(R.dimen.overlay_corner_radius) * overlayUiScaleFactor();
        float[] radii = new float[] {
                roundTopLeft ? r : 0f, roundTopLeft ? r : 0f,
                roundTopRight ? r : 0f, roundTopRight ? r : 0f,
                roundBottomRight ? r : 0f, roundBottomRight ? r : 0f,
                roundBottomLeft ? r : 0f, roundBottomLeft ? r : 0f
        };

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        applyOverlayAlpha(ContextCompat.getColor(this, R.color.overlay_background_top), currentOverlayAlphaTop),
                        applyOverlayAlpha(ContextCompat.getColor(this, R.color.overlay_background_bottom), currentOverlayAlphaBottom)
                }
        );
        bg.setCornerRadii(radii);
        overlayView.setBackground(bg);
    }

    private static int applyOverlayAlpha(int color, int alpha) {
        int a = AppSettings.clampOverlayAlpha(alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void initNotificationWindow() {
        notificationWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Инфлейтим layout для уведомления
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        notificationView = inflater.inflate(R.layout.custom_notification_layout, null);
        
        // Получаем ссылки на элементы
        notificationTitle = notificationView.findViewById(R.id.notificationTitle);
        notificationBody = notificationView.findViewById(R.id.notificationBody);
        timerText = notificationView.findViewById(R.id.timerText);
        closeButton = notificationView.findViewById(R.id.closeButton);
        notificationOkButton = notificationView.findViewById(R.id.notificationOkButton);

        // Устанавливаем обработчик нажатия на кнопку закрытия
        closeButton.setOnClickListener(v -> hideNotification());
        if (notificationOkButton != null) {
            notificationOkButton.setOnClickListener(v -> hideNotification());
        }

        // Создаем параметры окна
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.format = PixelFormat.TRANSLUCENT;
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        notificationWindowManager.getDefaultDisplay().getRealMetrics(dm);
        params.width = Math.round(dm.widthPixels * 0.6f);
        notificationWindowBaseHeightPx = Math.round(dm.heightPixels * 0.4f);
        params.height = notificationWindowBaseHeightPx;
        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        // Добавляем view в WindowManager (но пока скрываем)
        notificationView.setVisibility(View.GONE);
        notificationWindowManager.addView(notificationView, params);
        applyFloatingNotificationTextSizes();
    }

    /**
     * Показать уведомление с заданными параметрами
     * @param title Заголовок уведомления
     * @param body Текст уведомления
     * @param durationSeconds Время показа в секундах
     * @param playAlertSound проигрывать звук (только для предупреждений и ошибок)
     */
    public void showNotification(String title, String body, int durationSeconds, boolean playAlertSound) {
        // Проверяем, включены ли уведомления для текущего приложения
        if (!currentForegroundApp.isEmpty()) {
            boolean notificationsEnabled = settingsManager.isNotificationsEnabledForApp(currentForegroundApp);
            if (!notificationsEnabled) {
                Log.d(TAG, "Notifications disabled for app: " + currentForegroundApp);
                return;
            }
        }

        notificationQueue.offer(new NotificationMessage(title, body, durationSeconds, playAlertSound));
        
        // Если сейчас ничего не показывается, показываем сразу
        if (!isNotificationShowing) {
            displayNextNotification();
        }
    }

    /** Информационное окно без звука. */
    public void showNotification(String title, String body, int durationSeconds) {
        showNotification(title, body, durationSeconds, false);
    }

    /**
     * Окно, которое нельзя пропустить: без таймера автозакрытия и крестика, висит до нажатия «Ок».
     * Настройка «уведомления для приложения» уважается так же, как и для обычных окон.
     */
    public void showAcknowledgeNotification(String title, String body, boolean playAlertSound) {
        if (!currentForegroundApp.isEmpty() && settingsManager != null
                && !settingsManager.isNotificationsEnabledForApp(currentForegroundApp)) {
            Log.d(TAG, "Notifications disabled for app: " + currentForegroundApp);
            return;
        }
        if (isAcknowledgeNotificationPending(body)) {
            // Пользователь ещё не подтвердил прошлое такое же окно — второе поверх него бессмысленно.
            return;
        }

        notificationQueue.offer(new NotificationMessage(title, body, 0, playAlertSound, true));

        if (!isNotificationShowing) {
            displayNextNotification();
        }
    }

    private boolean isAcknowledgeNotificationPending(String body) {
        if (currentNotification != null && currentNotification.requiresAcknowledgement
                && TextUtils.equals(currentNotification.body, body)) {
            return true;
        }
        for (NotificationMessage queued : notificationQueue) {
            if (queued.requiresAcknowledgement && TextUtils.equals(queued.body, body)) {
                return true;
            }
        }
        return false;
    }

    private void displayNextNotification() {
        // Проверяем, есть ли уведомления в очереди
        if (notificationQueue.isEmpty()) {
            isNotificationShowing = false;
            return;
        }

        // Берем следующее уведомление из очереди
        currentNotification = notificationQueue.poll();
        if (currentNotification == null) {
            isNotificationShowing = false;
            return;
        }

        isNotificationShowing = true;
        remainingSeconds = currentNotification.durationSeconds;

        // Обновляем UI
        final boolean mustAcknowledge = currentNotification.requiresAcknowledgement;
        notificationTitle.setText(currentNotification.title);
        notificationBody.setText(currentNotification.body);
        if (mustAcknowledge) {
            // Ни таймера, ни крестика: единственный выход — кнопка «Ок».
            timerText.setVisibility(View.GONE);
        } else {
            timerText.setVisibility(View.VISIBLE);
            timerText.setText(remainingSeconds + "s");
        }
        if (closeButton != null) {
            closeButton.setVisibility(mustAcknowledge ? View.GONE : View.VISIBLE);
        }
        if (notificationOkButton != null) {
            notificationOkButton.setVisibility(mustAcknowledge ? View.VISIBLE : View.GONE);
        }
        applyNotificationWindowHeight(mustAcknowledge);

        // Показываем окно
        notificationView.setVisibility(View.VISIBLE);
        if (currentNotification.playAlertSound
                && SystemSettingsPreferences.notificationAlertSoundEnabled(this)) {
            alertSound().play(currentNotification.title);
        }

        if (!mustAcknowledge) {
            // Запускаем таймер
            startTimer();
        }
    }

    /**
     * У окна с кнопкой «Ок» высота идёт по содержимому: фиксированные 40% экрана на низких экранах
     * магнитолы срезали бы кнопку, а закрыть окно тогда было бы нечем.
     */
    private void applyNotificationWindowHeight(boolean wrapContent) {
        if (notificationWindowManager == null || notificationView == null
                || notificationWindowBaseHeightPx <= 0) {
            return;
        }
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) notificationView.getLayoutParams();
            int target = wrapContent
                    ? WindowManager.LayoutParams.WRAP_CONTENT
                    : notificationWindowBaseHeightPx;
            if (params.height != target) {
                params.height = target;
                notificationWindowManager.updateViewLayout(notificationView, params);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyNotificationWindowHeight: " + e.getMessage());
        }
    }

    private void startTimer() {
        // Останавливаем предыдущий таймер, если он был
        if (timerRunnable != null) {
            notificationHandler.removeCallbacks(timerRunnable);
        }

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                remainingSeconds--;
                
                if (remainingSeconds > 0) {
                    // Обновляем текст таймера
                    timerText.setText(remainingSeconds + "s");
                    // Запускаем следующий тик через 1 секунду
                    notificationHandler.postDelayed(this, 1000);
                } else {
                    // Время вышло, скрываем уведомление
                    hideNotification();
                }
            }
        };

        // Запускаем первый тик через 1 секунду
        notificationHandler.postDelayed(timerRunnable, 1000);
    }

    private void hideNotification() {
        // Останавливаем таймер
        if (timerRunnable != null) {
            notificationHandler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
        if (alertSound != null) {
            alertSound.release();
        }

        // Скрываем окно
        if (notificationView != null) {
            notificationView.setVisibility(View.GONE);
        }

        isNotificationShowing = false;
        currentNotification = null;

        // Показываем следующее уведомление из очереди, если есть
        if (!notificationQueue.isEmpty()) {
            notificationHandler.postDelayed(this::displayNextNotification, 300); // Небольшая задержка перед следующим
        }
    }

    /** Проигрыватель сигнала создаётся лениво: при выключенном звуке он не нужен вовсе. */
    private NotificationAlertSound alertSound() {
        if (alertSound == null) {
            alertSound = new NotificationAlertSound(this);
        }
        return alertSound;
    }

//    private void createOverlay() {
//        int statusBarHeight = 0;
//        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
//        if (resourceId > 0) statusBarHeight = getResources().getDimensionPixelSize(resourceId);
//
//        final WindowManager.LayoutParams parameters = new WindowManager.LayoutParams(
//                WindowManager.LayoutParams.WRAP_CONTENT,
//                statusBarHeight,
//                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,   // Allows the view to be on top of the StatusBar
//                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,    // Keeps the button presses from going to the background window and Draws over status bar
//                PixelFormat.TRANSLUCENT);
//        parameters.gravity = Gravity.TOP | Gravity.CENTER;
//
//        createLinearLayoutForOverlay();
//
//        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
//        windowManager.addView(llForOverlay, parameters);
//    }
//
//    LinearLayout llForOverlay = null;
//    TextView tvForOverlay = null;
//
//    @NonNull
//    private LinearLayout createLinearLayoutForOverlay() {
//        llForOverlay = new LinearLayout(this);
//        llForOverlay.setBackgroundColor(Color.TRANSPARENT);
//        LinearLayout.LayoutParams layoutParameteres = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
//        llForOverlay.setLayoutParams(layoutParameteres);
//
//        tvForOverlay = new TextView(this);
//        ViewGroup.LayoutParams tvParameters = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
//        tvForOverlay.setLayoutParams(tvParameters);
//        tvForOverlay.setTextColor(Color.WHITE);
//        tvForOverlay.setGravity(Gravity.CENTER);
//        tvForOverlay.setText("123");
//        llForOverlay.addView(tvForOverlay);
//        return llForOverlay;
//    }

//    private Bitmap createBitmapFromString(String inputNumber) {
//
//        Paint paint = new Paint();
//        paint.setAntiAlias(true);
//        paint.setTextSize(120);
//        paint.setTextAlign(Paint.Align.CENTER);
//
//        Rect textBounds = new Rect();
//        paint.getTextBounds(inputNumber, 0, inputNumber.length(), textBounds);
//
//        Bitmap bitmap = Bitmap.createBitmap(textBounds.width() + 10, 150,
//                Bitmap.Config.ARGB_8888);
//
//        Canvas canvas = new Canvas(bitmap);
//        canvas.drawText(inputNumber, textBounds.width() / 2 + 5, 100, paint);
//        return bitmap;
//    }

    private String lastVoltageText = "";

    public void getBatteryVoltage() {
        if (!ENABLE_BATTERY_VOLTAGE) {
            return;
        }
        // Создаем фильтр намерений
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        // Получаем текущее состояние батареи
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            // Получаем напряжение в милливольтах
            int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            if (voltage > 0) {
                // Переводим в вольты для удобства
                float voltageV = voltage / 1000.0f;
//                Log.d("BatteryInfo", "Напряжение батареи: " + voltageV + "V");
                lastVoltageText = String.format(Locale.US, "%.1f", voltageV) + "V";
            } else {
                Log.e("BatteryInfo", "Не удалось получить напряжение батареи");
            }
        }
    }


    private void updateNotification(String data, String tempVal) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_MUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplication(), CHANNEL_ID)
                .setContentTitle("Temperature")
                .setContentText(data)
                .setSmallIcon(R.drawable.ic_usb)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationManager.IMPORTANCE_LOW)
//                .setContentInfo(data)
                .setTicker(data);

        // Обновление оверлея перенесено в метод status()
        // чтобы избежать дублирования
        if (ENABLE_BATTERY_VOLTAGE && tempVal != null) {
            getBatteryVoltage(); // Обновляем напряжение батареи
        }

        notification = builder.build();
    }


    private void send(String str) {
        Log.d(TAG, "send() called with: " + str);
        Log.d(TAG, "  thermometerDevice: " + (thermometerDevice != null ? "not null" : "null"));
        if (thermometerDevice != null) {
            Log.d(TAG, "  thermometerDevice.isConnected(): " + thermometerDevice.isConnected());
        }
        
        if (thermometerDevice != null && thermometerDevice.isConnected()) {
            thermometerDevice.sendCommand(str);
            Log.d(TAG, "  Command sent successfully");
        } else {
            Log.w(TAG, "  Cannot send: device not connected");
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void read() {
        if(!connected) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Проверка на null для безопасности
        if (usbSerialPort == null) {
            Log.e(TAG, "USB serial port is null");
            Toast.makeText(this, "USB port not initialized", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            byte[] buffer = new byte[8192];
            int len = usbSerialPort.read(buffer, READ_WAIT_MILLIS);
            receive(Arrays.copyOf(buffer, len));
        } catch (IOException e) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            status("connection lost: " + e.getMessage(), "❌", null);
            disconnect();
        }
    }

    private void receive(byte[] data) {
        String dataStr = new String(data);
        if (dataStr.startsWith("~G")) {
            String[] splitted = dataStr.split("G");
            if (splitted.length == 2) {
                Log.d(TAG, "G response: " + dataStr);
                handleTemperaturePayload(splitted[1]);
            }
            else {
                Log.i(TAG, "unknown G response: " + dataStr);
            }
        }
        else {
            Log.i(TAG, "unknown response: " + dataStr);
        }
    }

    /**
     * Старый метод connect - используется только внутри RealThermometerDevice
     * Оставлен для обратной совместимости
     * ВАЖНО: Добавлены дополнительные проверки для безопасности
     */
    private void connect() {
        UsbManager usbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            status("connection failed: USB manager not available", "❌", null);
            return;
        }

        UsbDevice device = UsbDeviceBySerial.findDevice(usbManager, deviceSerial);

        if(device == null) {
            status("connection failed: device not found", "❌", null);
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device", "❌", null);
            return;
        }
        if(driver.getPorts().size() <= portNum) {
            status("connection failed: not enough ports at device", "❌", null);
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && usbPermission == UsbPermission.Unknown && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
            intent.setPackage(this.getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied", "❌", null);
            else
                status("connection failed: open failed", "❌", null);
            return;
        }

        try {
            usbSerialPort.open(usbConnection);
            try{
                usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            }catch (UnsupportedOperationException e){
                status("unsupport setparameters", "❌", null);
            }
            if(withIoManager) {
                usbIoManager = new SerialInputOutputManager(usbSerialPort, this);
                usbIoManager.start();
            }
            status("connected", "✔", null);
            connected = true;

            // Пример использования showNotification
            showNotification(INFO_TITLE_WARNING, "Успешно подключено к устройству", 5);

            send("~W1000");
//            controlLines.start();
        } catch (Exception e) {
            status("connection failed: " + e.getMessage(), "❌", null);
            disconnect();
        }
    }

    /**
     * Старый метод disconnect - используется только внутри RealThermometerDevice
     * Оставлен для обратной совместимости
     * ВАЖНО: Добавлены проверки на null для безопасности
     */
    private void disconnect() {
        connected = false;
//        controlLines.stop();
        if(usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        
        // Безопасное закрытие порта с проверкой на null
        if (usbSerialPort != null) {
            try {
                usbSerialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing USB serial port: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error closing USB serial port: " + e.getMessage());
            }
        }
        usbSerialPort = null;
    }
}
