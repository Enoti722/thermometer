package com.github.enoti722.thermometer;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Activity для настроек конкретного приложения
 */
public class AppSpecificSettingsActivity extends AppCompatActivity {

    private SettingsManager settingsManager;
    private String packageName;
    private String appName;

    private ImageView imageAppIcon;
    private TextView textAppName;
    private Switch switchOverlay;
    private Switch switchNotifications;
    private Switch switchOverlaySpeedVisibility;
    private View layoutOverlaySpeedVisibility;
    private View dividerOverlaySpeedVisibility;
    private SeekBar seekBarOverlayAlphaTop;
    private SeekBar seekBarOverlayAlphaBottom;
    private TextView textOverlayAlphaTopValue;
    private TextView textOverlayAlphaBottomValue;
    private SeekBar seekBarX;
    private SeekBar seekBarY;
    private EditText editTextX;
    private EditText editTextY;
    private Button btnResetOverlayPosition;

    private boolean isUpdatingX = false;
    private boolean isUpdatingY = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_specific_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        settingsManager = new SettingsManager(this);

        // Получаем данные из Intent
        packageName = getIntent().getStringExtra("packageName");
        appName = getIntent().getStringExtra("appName");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Настройки: " + appName);
        }

        imageAppIcon = findViewById(R.id.image_app_icon);
        textAppName = findViewById(R.id.text_app_name);
        switchOverlay = findViewById(R.id.switch_overlay);
        switchNotifications = findViewById(R.id.switch_notifications);
        switchOverlaySpeedVisibility = findViewById(R.id.switch_overlay_speed_visibility);
        layoutOverlaySpeedVisibility = findViewById(R.id.layout_overlay_speed_visibility);
        dividerOverlaySpeedVisibility = findViewById(R.id.divider_overlay_speed_visibility);
        seekBarOverlayAlphaTop = findViewById(R.id.seekbar_overlay_alpha_top);
        seekBarOverlayAlphaBottom = findViewById(R.id.seekbar_overlay_alpha_bottom);
        textOverlayAlphaTopValue = findViewById(R.id.text_overlay_alpha_top_value);
        textOverlayAlphaBottomValue = findViewById(R.id.text_overlay_alpha_bottom_value);
        seekBarX = findViewById(R.id.seekbar_position_x);
        seekBarY = findViewById(R.id.seekbar_position_y);
        editTextX = findViewById(R.id.edittext_position_x);
        editTextY = findViewById(R.id.edittext_position_y);
        btnResetOverlayPosition = findViewById(R.id.btn_reset_overlay_position);

        seekBarX.setMax(AppSettings.OVERLAY_POSITION_SEEK_MAX);
        seekBarY.setMax(AppSettings.OVERLAY_POSITION_SEEK_MAX);

        textAppName.setText(appName);

        // Загружаем иконку приложения
        if (AppSettings.SYSTEM_PACKAGE_NAME.equals(packageName)) {
            imageAppIcon.setImageResource(android.R.drawable.ic_menu_manage);
        } else {
            try {
                Drawable icon = getPackageManager().getApplicationIcon(packageName);
                imageAppIcon.setImageDrawable(icon);
            } catch (PackageManager.NameNotFoundException e) {
                imageAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        }

        // Загружаем текущие настройки
        AppSettings currentSettings = settingsManager.getAppSettings(packageName);
        if (currentSettings != null) {
            switchOverlay.setChecked(currentSettings.isOverlayEnabled());
            switchNotifications.setChecked(currentSettings.isNotificationsEnabled());
            switchOverlaySpeedVisibility.setChecked(
                    UsbService.ENABLE_GPS_SPEED && currentSettings.isOverlaySpeedVisible());
            seekBarOverlayAlphaTop.setProgress(currentSettings.getOverlayAlphaTop());
            seekBarOverlayAlphaBottom.setProgress(currentSettings.getOverlayAlphaBottom());
            textOverlayAlphaTopValue.setText(String.valueOf(currentSettings.getOverlayAlphaTop()));
            textOverlayAlphaBottomValue.setText(String.valueOf(currentSettings.getOverlayAlphaBottom()));
            seekBarX.setProgress(AppSettings.seekProgressFromPercent(currentSettings.getOverlayPositionXPercent()));
            seekBarY.setProgress(AppSettings.seekProgressFromPercent(currentSettings.getOverlayPositionYPercent()));
            editTextX.setText(String.valueOf(currentSettings.getOverlayPositionXPercent()));
            editTextY.setText(String.valueOf(currentSettings.getOverlayPositionYPercent()));
        } else {
            // Используем значения по умолчанию
            switchOverlay.setChecked(true);
            switchNotifications.setChecked(true);
            switchOverlaySpeedVisibility.setChecked(false);
            seekBarOverlayAlphaTop.setProgress(AppSettings.DEFAULT_OVERLAY_ALPHA_TOP);
            seekBarOverlayAlphaBottom.setProgress(AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM);
            textOverlayAlphaTopValue.setText(String.valueOf(AppSettings.DEFAULT_OVERLAY_ALPHA_TOP));
            textOverlayAlphaBottomValue.setText(String.valueOf(AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM));
            seekBarX.setProgress(AppSettings.seekProgressFromPercent(50));
            seekBarY.setProgress(AppSettings.seekProgressFromPercent(5));
            editTextX.setText("50");
            editTextY.setText("5");
        }

        if (UsbService.ENABLE_GPS_SPEED) {
            layoutOverlaySpeedVisibility.setVisibility(View.VISIBLE);
            dividerOverlaySpeedVisibility.setVisibility(View.VISIBLE);
        } else {
            layoutOverlaySpeedVisibility.setVisibility(View.GONE);
            dividerOverlaySpeedVisibility.setVisibility(View.GONE);
            switchOverlaySpeedVisibility.setChecked(false);
        }

        // Обработчики
        switchOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        switchOverlaySpeedVisibility.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        seekBarOverlayAlphaTop.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOverlayAlphaTopValue.setText(String.valueOf(progress));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        seekBarOverlayAlphaBottom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textOverlayAlphaBottomValue.setText(String.valueOf(progress));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Синхронизация SeekBar и EditText для X
        seekBarX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isUpdatingX) {
                    isUpdatingX = true;
                    editTextX.setText(String.valueOf(AppSettings.percentFromSeekProgress(progress)));
                    isUpdatingX = false;
                    if (fromUser) {
                        saveSettings();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        editTextX.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingX && s.length() > 0) {
                    String raw = s.toString().trim();
                    if (raw.equals("-")) {
                        return;
                    }
                    isUpdatingX = true;
                    try {
                        int value = Integer.parseInt(raw);
                        value = AppSettings.clampOverlayPositionPercent(value);
                        seekBarX.setProgress(AppSettings.seekProgressFromPercent(value));
                        saveSettings();
                    } catch (NumberFormatException e) {
                        // Игнорируем некорректный ввод
                    }
                    isUpdatingX = false;
                }
            }
        });

        // Синхронизация SeekBar и EditText для Y
        seekBarY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!isUpdatingY) {
                    isUpdatingY = true;
                    editTextY.setText(String.valueOf(AppSettings.percentFromSeekProgress(progress)));
                    isUpdatingY = false;
                    if (fromUser) {
                        saveSettings();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        editTextY.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!isUpdatingY && s.length() > 0) {
                    String raw = s.toString().trim();
                    if (raw.equals("-")) {
                        return;
                    }
                    isUpdatingY = true;
                    try {
                        int value = Integer.parseInt(raw);
                        value = AppSettings.clampOverlayPositionPercent(value);
                        seekBarY.setProgress(AppSettings.seekProgressFromPercent(value));
                        saveSettings();
                    } catch (NumberFormatException e) {
                        // Игнорируем некорректный ввод
                    }
                    isUpdatingY = false;
                }
            }
        });

        btnResetOverlayPosition.setOnClickListener(v -> {
            seekBarX.setProgress(AppSettings.seekProgressFromPercent(50));
            seekBarY.setProgress(AppSettings.seekProgressFromPercent(5));
            editTextX.setText("50");
            editTextY.setText("5");
            saveSettings();
        });
    }

    private void saveSettings() {
        int xPercent = AppSettings.percentFromSeekProgress(seekBarX.getProgress());
        int yPercent = AppSettings.percentFromSeekProgress(seekBarY.getProgress());
        
        AppSettings settings = new AppSettings(
                packageName,
                appName,
                switchOverlay.isChecked(),
                switchNotifications.isChecked(),
                UsbService.ENABLE_GPS_SPEED && switchOverlaySpeedVisibility.isChecked(),
                AppSettings.clampOverlayAlpha(seekBarOverlayAlphaTop.getProgress()),
                AppSettings.clampOverlayAlpha(seekBarOverlayAlphaBottom.getProgress()),
                xPercent,
                yPercent
        );
        settingsManager.addOrUpdateAppSettings(settings);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_app_specific_settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            showDeleteConfirmationDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Подтверждение удаления")
            .setMessage("Вы уверены, что хотите удалить настройки для приложения \"" + appName + "\"?")
            .setPositiveButton("Удалить", (dialog, which) -> {
                settingsManager.removeAppSettings(packageName);
                finish();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
