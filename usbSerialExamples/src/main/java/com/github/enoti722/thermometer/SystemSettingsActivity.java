package com.github.enoti722.thermometer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

/**
 * Настройки системы: масштаб шрифтов оверлея/уведомлений, порог скорости для правила температуры,
 * окно усреднения, поправка термометра и политика автозапуска (BOOT_COMPLETED или referrer из MainActivity).
 */
public class SystemSettingsActivity extends AppCompatActivity {

    private TextView textOverlayPercent;
    private TextView textAlertPercent;
    private SeekBar seekOverlay;
    private SeekBar seekAlert;
    private TextView textTempSpeedGate;
    private SeekBar seekTempSpeedGate;
    private TextView textTempAvgWindow;
    private SeekBar seekTempAvgWindow;
    private TextView textThermometerOffset;
    private SeekBar seekThermometerOffset;
    private TextView textSensorHold;
    private SeekBar seekSensorHold;
    private Spinner spinnerAutostart;
    private EditText editAutostartReferrer;
    private SwitchCompat switchNotificationSound;
    private Spinner spinnerNotificationSoundMode;
    private SwitchCompat switchMediaFgs;
    private SwitchCompat switchRestorePlayback;
    /** Создаётся под кнопку проверки; сервис для этого поднимать не нужно. */
    private NotificationAlertSound alertSoundTester;
    private static final long DELAYED_TEST_MS = 10_000L;
    private final Handler testDelayHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_system_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.system_settings_activity_title);
        }

        prefs = UiScalingPreferences.prefs(this);
        textOverlayPercent = findViewById(R.id.text_overlay_percent);
        textAlertPercent = findViewById(R.id.text_alert_percent);
        seekOverlay = findViewById(R.id.seek_overlay_scale);
        seekAlert = findViewById(R.id.seek_alert_scale);
        textTempSpeedGate = findViewById(R.id.text_temp_speed_gate);
        seekTempSpeedGate = findViewById(R.id.seek_temp_speed_gate);
        textTempAvgWindow = findViewById(R.id.text_temp_avg_window);
        seekTempAvgWindow = findViewById(R.id.seek_temp_avg_window);
        textThermometerOffset = findViewById(R.id.text_thermometer_offset);
        seekThermometerOffset = findViewById(R.id.seek_thermometer_offset);
        textSensorHold = findViewById(R.id.text_sensor_hold);
        seekSensorHold = findViewById(R.id.seek_sensor_hold);
        spinnerAutostart = findViewById(R.id.spinner_autostart_mode);
        editAutostartReferrer = findViewById(R.id.edit_autostart_referrer);
        switchNotificationSound = findViewById(R.id.switch_notification_sound);
        switchNotificationSound.setChecked(SystemSettingsPreferences.notificationAlertSoundEnabled(this));
        switchNotificationSound.setOnCheckedChangeListener((buttonView, isChecked) -> save());
        ArrayAdapter<CharSequence> autostartLabels = ArrayAdapter.createFromResource(
                this,
                R.array.autostart_modes,
                android.R.layout.simple_spinner_item);
        autostartLabels.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAutostart.setAdapter(autostartLabels);
        reloadAutostartControlsFromPrefs();
        spinnerAutostart.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveAutostartPrefs();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        seekOverlay.setProgress(UiScalingPreferences.overlaySeekProgress(this));
        seekAlert.setProgress(UiScalingPreferences.alertSeekProgress(this));
        seekTempSpeedGate.setProgress(Math.round(SystemSettingsPreferences.tempSpeedGateKmh(this)));
        seekTempAvgWindow.setProgress(SystemSettingsPreferences.tempAverageWindowMinutes(this));
        seekThermometerOffset.setProgress(SystemSettingsPreferences.thermometerOffsetSeekProgress(this));
        seekSensorHold.setProgress(SystemSettingsPreferences.sensorHoldSeekProgress(this));

        updateOverlayLabel(seekOverlay.getProgress());
        updateAlertLabel(seekAlert.getProgress());
        updateSpeedGateLabel(seekTempSpeedGate.getProgress());
        updateAvgWindowLabel(seekTempAvgWindow.getProgress());
        updateOffsetLabel(seekThermometerOffset.getProgress());
        updateSensorHoldLabel(seekSensorHold.getProgress());

        SeekBar.OnSeekBarChangeListener saver = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (seekBar == seekOverlay) {
                    updateOverlayLabel(progress);
                } else if (seekBar == seekAlert) {
                    updateAlertLabel(progress);
                } else if (seekBar == seekTempSpeedGate) {
                    updateSpeedGateLabel(progress);
                } else if (seekBar == seekTempAvgWindow) {
                    updateAvgWindowLabel(progress);
                } else if (seekBar == seekThermometerOffset) {
                    updateOffsetLabel(progress);
                } else if (seekBar == seekSensorHold) {
                    updateSensorHoldLabel(progress);
                }
                if (fromUser) {
                    save();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                save();
            }
        };
        seekOverlay.setOnSeekBarChangeListener(saver);
        seekAlert.setOnSeekBarChangeListener(saver);
        seekTempSpeedGate.setOnSeekBarChangeListener(saver);
        seekTempAvgWindow.setOnSeekBarChangeListener(saver);
        seekThermometerOffset.setOnSeekBarChangeListener(saver);
        seekSensorHold.setOnSeekBarChangeListener(saver);

        // Спиннер режима звука сознательно без onItemSelected: Spinner доставляет первое событие
        // отложенно, а save() пишет разом все настройки экрана — до их загрузки это затёрло бы ползунки.
        // Режим сохраняется вместе со всем остальным (onPause / «назад») и принудительно перед проверкой.
        spinnerNotificationSoundMode = findViewById(R.id.spinner_notification_sound_mode);
        ArrayAdapter<CharSequence> soundModeLabels = ArrayAdapter.createFromResource(
                this,
                R.array.notification_sound_modes,
                android.R.layout.simple_spinner_item);
        soundModeLabels.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNotificationSoundMode.setAdapter(soundModeLabels);
        spinnerNotificationSoundMode.setSelection(
                SystemSettingsPreferences.notificationSoundMode(this), false);

        findViewById(R.id.button_notification_sound_test).setOnClickListener(v -> playTestSound());
        findViewById(R.id.button_notification_sound_test_delayed).setOnClickListener(v -> {
            // Настоящее уведомление приходит поверх плеера, а не поверх этого экрана — даём время уйти туда.
            Toast.makeText(this, R.string.system_settings_notification_sound_test_delayed_toast,
                    Toast.LENGTH_LONG).show();
            save();
            testDelayHandler.postDelayed(this::playTestSound, DELAYED_TEST_MS);
        });

        switchMediaFgs = findViewById(R.id.switch_media_fgs);
        switchMediaFgs.setChecked(SystemSettingsPreferences.declareMediaPlaybackService(this));
        switchMediaFgs.setOnCheckedChangeListener((buttonView, isChecked) -> save());

        switchRestorePlayback = findViewById(R.id.switch_restore_playback);
        switchRestorePlayback.setChecked(SystemSettingsPreferences.restorePlaybackAfterAlert(this));
        switchRestorePlayback.setOnCheckedChangeListener((buttonView, isChecked) -> save());

        Button batteryWhitelist = findViewById(R.id.button_battery_whitelist);
        batteryWhitelist.setOnClickListener(v -> {
            if (BackgroundExecutionHelper.isIgnoringBatteryOptimizations(this)) {
                Toast.makeText(this, R.string.system_settings_battery_already_whitelisted, Toast.LENGTH_SHORT).show();
                return;
            }
            BackgroundExecutionHelper.requestIgnoreBatteryOptimizations(this);
        });
        findViewById(R.id.button_app_info).setOnClickListener(
                v -> BackgroundExecutionHelper.openApplicationDetails(this));
    }

    private void updateOverlayLabel(int seekProgress) {
        textOverlayPercent.setText(formatPercent(seekProgress));
    }

    private void updateAlertLabel(int seekProgress) {
        textAlertPercent.setText(formatPercent(seekProgress));
    }

    private void updateSpeedGateLabel(int kmh) {
        textTempSpeedGate.setText(getString(R.string.system_settings_speed_gate_value, kmh));
    }

    private void updateAvgWindowLabel(int minutes) {
        textTempAvgWindow.setText(getString(R.string.system_settings_avg_window_value, minutes));
    }

    private void updateOffsetLabel(int seekProgress) {
        float deg = SystemSettingsPreferences.seekProgressToOffsetDegrees(seekProgress);
        String num = String.format(Locale.US, "%+.1f", deg);
        textThermometerOffset.setText(getString(R.string.system_settings_offset_value, num));
    }

    private void updateSensorHoldLabel(int seekProgress) {
        int totalSec = SystemSettingsPreferences.seekProgressToSensorHoldSeconds(seekProgress);
        int min = totalSec / 60;
        int sec = totalSec % 60;
        if (min == 0) {
            textSensorHold.setText(getString(R.string.system_settings_sensor_hold_value_sec, sec));
        } else if (sec == 0) {
            textSensorHold.setText(getString(R.string.system_settings_sensor_hold_value_min, min));
        } else {
            textSensorHold.setText(getString(R.string.system_settings_sensor_hold_value_min_sec, min, sec));
        }
    }

    private static String formatPercent(int seekProgress) {
        return seekProgress + "%";
    }

    private void reloadAutostartControlsFromPrefs() {
        String rp = AutostartPreferences.getReferrerPattern(this);
        editAutostartReferrer.setText(rp != null ? rp : "");
        int mode = AutostartPreferences.getMode(this);
        ArrayAdapter<?> ad = (ArrayAdapter<?>) spinnerAutostart.getAdapter();
        int n = ad != null ? ad.getCount() : 0;
        if (mode >= AutostartPreferences.MODE_OFF && mode < n) {
            spinnerAutostart.setSelection(mode, false);
        }
    }

    private void saveAutostartPrefs() {
        int pos = spinnerAutostart.getSelectedItemPosition();
        if (pos == AdapterView.INVALID_POSITION) {
            pos = AutostartPreferences.MODE_BOOT_COMPLETED;
        }
        if (pos < AutostartPreferences.MODE_OFF || pos > AutostartPreferences.MODE_SYSTEM_ACTIVITY_REFERRER) {
            pos = AutostartPreferences.MODE_BOOT_COMPLETED;
        }
        AutostartPreferences.setMode(this, pos);
        AutostartPreferences.setReferrerPattern(this, editAutostartReferrer.getText().toString().trim());
    }

    /**
     * Проверка идёт мимо переключателя «Звук при уведомлениях»: режимы надо успеть сравнить
     * на живой магнитоле до того, как звук включат насовсем.
     */
    private void playTestSound() {
        save();
        if (alertSoundTester == null) {
            alertSoundTester = new NotificationAlertSound(this);
        }
        alertSoundTester.playTest(summary -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            askOwnerVerdict();
        });
    }

    /**
     * Итог режима в лог заносит владелец: на этой магнитоле ни радио, ни Bluetooth, ни флешка не видны
     * Android как воспроизведение, так что автоматический вердикт остаётся только в логе.
     *
     * <p>Никакого {@code setMessage} рядом с {@code setItems}: при заданном тексте список ответов
     * не отрисовывается вовсе, остаётся одна «Отмена».
     */
    private void askOwnerVerdict() {
        String[] answers = getResources().getStringArray(R.array.notification_sound_test_answers);
        new AlertDialog.Builder(this)
                .setTitle(R.string.system_settings_notification_sound_result_title)
                .setItems(answers, (dialog, which) -> {
                    if (alertSoundTester != null) {
                        alertSoundTester.logOwnerVerdict(answers[which]);
                    }
                    Toast.makeText(this, R.string.system_settings_notification_sound_result_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int selectedNotificationSoundMode() {
        int pos = spinnerNotificationSoundMode.getSelectedItemPosition();
        if (pos == AdapterView.INVALID_POSITION) {
            return SystemSettingsPreferences.DEFAULT_NOTIFICATION_SOUND_MODE;
        }
        return pos;
    }

    private void save() {
        saveAutostartPrefs();
        SystemSettingsPreferences.setNotificationSoundMode(this, selectedNotificationSoundMode());
        prefs.edit()
                .putFloat(UiScalingPreferences.KEY_OVERLAY_FONT_SCALE,
                        UiScalingPreferences.seekProgressToScale(seekOverlay.getProgress()))
                .putFloat(UiScalingPreferences.KEY_ALERT_FONT_SCALE,
                        UiScalingPreferences.seekProgressToScale(seekAlert.getProgress()))
                .putFloat(SystemSettingsPreferences.KEY_TEMP_SPEED_GATE_KMH, seekTempSpeedGate.getProgress())
                .putInt(SystemSettingsPreferences.KEY_TEMP_AVG_WINDOW_MIN, seekTempAvgWindow.getProgress())
                .putFloat(SystemSettingsPreferences.KEY_THERMOMETER_OFFSET_C,
                        SystemSettingsPreferences.seekProgressToOffsetDegrees(seekThermometerOffset.getProgress()))
                .putInt(SystemSettingsPreferences.KEY_SENSOR_HOLD_SEC,
                        SystemSettingsPreferences.seekProgressToSensorHoldSeconds(seekSensorHold.getProgress()))
                .putBoolean(SystemSettingsPreferences.KEY_NOTIFICATION_ALERT_SOUND,
                        switchNotificationSound.isChecked())
                .putBoolean(SystemSettingsPreferences.KEY_DECLARE_MEDIA_FGS,
                        switchMediaFgs.isChecked())
                .putBoolean(SystemSettingsPreferences.KEY_RESTORE_PLAYBACK_AFTER_ALERT,
                        switchRestorePlayback.isChecked())
                .apply();
    }

    @Override
    public boolean onSupportNavigateUp() {
        save();
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onDestroy() {
        testDelayHandler.removeCallbacksAndMessages(null);
        if (alertSoundTester != null) {
            alertSoundTester.release();
            alertSoundTester = null;
        }
        super.onDestroy();
    }
}
