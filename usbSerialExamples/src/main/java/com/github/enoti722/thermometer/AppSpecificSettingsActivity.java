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

import java.util.Locale;

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
    private Switch switchOverlayVertical;
    private SeekBar seekBarDataSize;
    private TextView textDataSizeValue;
    private SeekBar seekBarContentOffsetX;
    private SeekBar seekBarContentOffsetY;
    private TextView textContentOffsetXValue;
    private TextView textContentOffsetYValue;
    private SeekBar seekBarPlateWidth;
    private SeekBar seekBarPlateHeight;
    private TextView textPlateWidthValue;
    private TextView textPlateHeightValue;
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
        switchOverlayVertical = findViewById(R.id.switch_overlay_vertical);
        seekBarDataSize = findViewById(R.id.seekbar_data_size);
        textDataSizeValue = findViewById(R.id.text_data_size_value);
        seekBarContentOffsetX = findViewById(R.id.seekbar_content_offset_x);
        seekBarContentOffsetY = findViewById(R.id.seekbar_content_offset_y);
        textContentOffsetXValue = findViewById(R.id.text_content_offset_x_value);
        textContentOffsetYValue = findViewById(R.id.text_content_offset_y_value);
        seekBarPlateWidth = findViewById(R.id.seekbar_plate_width);
        seekBarPlateHeight = findViewById(R.id.seekbar_plate_height);
        textPlateWidthValue = findViewById(R.id.text_plate_width_value);
        textPlateHeightValue = findViewById(R.id.text_plate_height_value);
        seekBarOverlayAlphaTop = findViewById(R.id.seekbar_overlay_alpha_top);
        seekBarOverlayAlphaBottom = findViewById(R.id.seekbar_overlay_alpha_bottom);
        textOverlayAlphaTopValue = findViewById(R.id.text_overlay_alpha_top_value);
        textOverlayAlphaBottomValue = findViewById(R.id.text_overlay_alpha_bottom_value);
        seekBarX = findViewById(R.id.seekbar_position_x);
        seekBarY = findViewById(R.id.seekbar_position_y);
        editTextX = findViewById(R.id.edittext_position_x);
        editTextY = findViewById(R.id.edittext_position_y);
        btnResetOverlayPosition = findViewById(R.id.btn_reset_overlay_position);

        seekBarX.setMax(AppSettings.OVERLAY_POSITION_X_SEEK_MAX);
        seekBarY.setMax(AppSettings.OVERLAY_POSITION_Y_SEEK_MAX);
        seekBarDataSize.setMax(AppSettings.OVERLAY_DATA_SIZE_SEEK_MAX);
        seekBarContentOffsetX.setMax(AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX);
        seekBarContentOffsetY.setMax(AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX);
        seekBarPlateWidth.setMax(AppSettings.OVERLAY_PLATE_SIZE_SEEK_MAX);
        seekBarPlateHeight.setMax(AppSettings.OVERLAY_PLATE_SIZE_SEEK_MAX);

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
            switchOverlayVertical.setChecked(currentSettings.isOverlayVertical());
            seekBarDataSize.setProgress(AppSettings.dataSizeSeekProgressFromScale(currentSettings.getOverlayDataSizeScale()));
            textDataSizeValue.setText(formatDataSizeScale(currentSettings.getOverlayDataSizeScale()));
            seekBarContentOffsetX.setProgress(
                    AppSettings.overlayContentOffsetSeekProgressFromPercent(currentSettings.getOverlayContentOffsetXPercent()));
            seekBarContentOffsetY.setProgress(
                    AppSettings.overlayContentOffsetSeekProgressFromPercent(currentSettings.getOverlayContentOffsetYPercent()));
            textContentOffsetXValue.setText(formatContentOffsetPercent(currentSettings.getOverlayContentOffsetXPercent()));
            textContentOffsetYValue.setText(formatContentOffsetPercent(currentSettings.getOverlayContentOffsetYPercent()));
            seekBarPlateWidth.setProgress(
                    AppSettings.overlayPlateSizeSeekProgressFromPercent(currentSettings.getOverlayPlateWidthPercent()));
            seekBarPlateHeight.setProgress(
                    AppSettings.overlayPlateSizeSeekProgressFromPercent(currentSettings.getOverlayPlateHeightPercent()));
            textPlateWidthValue.setText(formatPlatePercent(currentSettings.getOverlayPlateWidthPercent()));
            textPlateHeightValue.setText(formatPlatePercent(currentSettings.getOverlayPlateHeightPercent()));
            seekBarOverlayAlphaTop.setProgress(currentSettings.getOverlayAlphaTop());
            seekBarOverlayAlphaBottom.setProgress(currentSettings.getOverlayAlphaBottom());
            textOverlayAlphaTopValue.setText(String.valueOf(currentSettings.getOverlayAlphaTop()));
            textOverlayAlphaBottomValue.setText(String.valueOf(currentSettings.getOverlayAlphaBottom()));
            seekBarX.setProgress(AppSettings.seekXProgressFromPercent(currentSettings.getOverlayPositionXPercent()));
            seekBarY.setProgress(AppSettings.seekYProgressFromPercent(currentSettings.getOverlayPositionYPercent()));
            editTextX.setText(String.valueOf(currentSettings.getOverlayPositionXPercent()));
            editTextY.setText(String.valueOf(currentSettings.getOverlayPositionYPercent()));
        } else {
            // Используем значения по умолчанию
            switchOverlay.setChecked(true);
            switchNotifications.setChecked(true);
            switchOverlaySpeedVisibility.setChecked(false);
            switchOverlayVertical.setChecked(false);
            seekBarDataSize.setProgress(AppSettings.dataSizeSeekProgressFromScale(AppSettings.DEFAULT_OVERLAY_DATA_SIZE_SCALE));
            textDataSizeValue.setText(formatDataSizeScale(AppSettings.DEFAULT_OVERLAY_DATA_SIZE_SCALE));
            seekBarContentOffsetX.setProgress(
                    AppSettings.overlayContentOffsetSeekProgressFromPercent(0));
            seekBarContentOffsetY.setProgress(
                    AppSettings.overlayContentOffsetSeekProgressFromPercent(0));
            textContentOffsetXValue.setText(formatContentOffsetPercent(0));
            textContentOffsetYValue.setText(formatContentOffsetPercent(0));
            seekBarPlateWidth.setProgress(
                    AppSettings.overlayPlateSizeSeekProgressFromPercent(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
            seekBarPlateHeight.setProgress(
                    AppSettings.overlayPlateSizeSeekProgressFromPercent(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
            textPlateWidthValue.setText(formatPlatePercent(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
            textPlateHeightValue.setText(formatPlatePercent(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
            seekBarOverlayAlphaTop.setProgress(AppSettings.DEFAULT_OVERLAY_ALPHA_TOP);
            seekBarOverlayAlphaBottom.setProgress(AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM);
            textOverlayAlphaTopValue.setText(String.valueOf(AppSettings.DEFAULT_OVERLAY_ALPHA_TOP));
            textOverlayAlphaBottomValue.setText(String.valueOf(AppSettings.DEFAULT_OVERLAY_ALPHA_BOTTOM));
            seekBarX.setProgress(AppSettings.seekXProgressFromPercent(50));
            seekBarY.setProgress(AppSettings.seekYProgressFromPercent(5));
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
        switchOverlayVertical.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());

        seekBarDataSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textDataSizeValue.setText(formatDataSizeScale(AppSettings.dataSizeScaleFromSeekProgress(progress)));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarContentOffsetX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textContentOffsetXValue.setText(formatContentOffsetPercent(
                        AppSettings.overlayContentOffsetFromSeekProgress(progress)));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarContentOffsetY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textContentOffsetYValue.setText(formatContentOffsetPercent(
                        AppSettings.overlayContentOffsetFromSeekProgress(progress)));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarPlateWidth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPlateWidthValue.setText(formatPlatePercent(
                        AppSettings.overlayPlateSizeFromSeekProgress(progress)));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarPlateHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textPlateHeightValue.setText(formatPlatePercent(
                        AppSettings.overlayPlateSizeFromSeekProgress(progress)));
                if (fromUser) {
                    saveSettings();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

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
                    editTextX.setText(String.valueOf(AppSettings.percentXFromSeekProgress(progress)));
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
                        value = AppSettings.clampOverlayPositionXPercent(value);
                        seekBarX.setProgress(AppSettings.seekXProgressFromPercent(value));
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
                    editTextY.setText(String.valueOf(AppSettings.percentYFromSeekProgress(progress)));
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
                        value = AppSettings.clampOverlayPositionYPercent(value);
                        seekBarY.setProgress(AppSettings.seekYProgressFromPercent(value));
                        saveSettings();
                    } catch (NumberFormatException e) {
                        // Игнорируем некорректный ввод
                    }
                    isUpdatingY = false;
                }
            }
        });

        btnResetOverlayPosition.setOnClickListener(v -> {
            seekBarX.setProgress(AppSettings.seekXProgressFromPercent(50));
            seekBarY.setProgress(AppSettings.seekYProgressFromPercent(5));
            editTextX.setText("50");
            editTextY.setText("5");
            saveSettings();
        });
    }

    private void saveSettings() {
        int xPercent = AppSettings.percentXFromSeekProgress(seekBarX.getProgress());
        int yPercent = AppSettings.percentYFromSeekProgress(seekBarY.getProgress());

        AppSettings settings = new AppSettings(packageName, appName);
        settings.setOverlayEnabled(switchOverlay.isChecked());
        settings.setNotificationsEnabled(switchNotifications.isChecked());
        settings.setOverlaySpeedVisible(UsbService.ENABLE_GPS_SPEED && switchOverlaySpeedVisibility.isChecked());
        settings.setOverlayAlphaTop(AppSettings.clampOverlayAlpha(seekBarOverlayAlphaTop.getProgress()));
        settings.setOverlayAlphaBottom(AppSettings.clampOverlayAlpha(seekBarOverlayAlphaBottom.getProgress()));
        settings.setOverlayPositionXPercent(xPercent);
        settings.setOverlayPositionYPercent(yPercent);
        settings.setOverlayVertical(switchOverlayVertical.isChecked());
        settings.setOverlayDataSizeScale(AppSettings.dataSizeScaleFromSeekProgress(seekBarDataSize.getProgress()));
        settings.setOverlayContentOffsetXPercent(
                AppSettings.overlayContentOffsetFromSeekProgress(seekBarContentOffsetX.getProgress()));
        settings.setOverlayContentOffsetYPercent(
                AppSettings.overlayContentOffsetFromSeekProgress(seekBarContentOffsetY.getProgress()));
        settings.setOverlayPlateWidthPercent(
                AppSettings.overlayPlateSizeFromSeekProgress(seekBarPlateWidth.getProgress()));
        settings.setOverlayPlateHeightPercent(
                AppSettings.overlayPlateSizeFromSeekProgress(seekBarPlateHeight.getProgress()));
        settingsManager.addOrUpdateAppSettings(settings);
    }

    /** Подпись масштаба данных: «x1», «x0.25», «x1.25» … */
    private static String formatDataSizeScale(float scale) {
        float clamped = AppSettings.clampOverlayDataSizeScale(scale);
        if (clamped == Math.round(clamped)) {
            return String.format(Locale.US, "x%.0f", clamped);
        }
        return String.format(Locale.US, "x%.2f", clamped);
    }

    /** Подпись смещения содержимого: «-50%», «0%», «75%» … */
    private static String formatContentOffsetPercent(int percent) {
        return String.format(Locale.US, "%d%%", AppSettings.clampOverlayContentOffsetPercent(percent));
    }

    /** Подпись размера плашки: «100%», «50%», «300%» … */
    private static String formatPlatePercent(int percent) {
        return String.format(Locale.US, "%d%%", AppSettings.clampOverlayPlateSizePercent(percent));
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
