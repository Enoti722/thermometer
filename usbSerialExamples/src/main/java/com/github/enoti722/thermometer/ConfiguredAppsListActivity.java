package com.github.enoti722.thermometer;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity для отображения списка настроенных приложений
 */
public class ConfiguredAppsListActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SELECT_APP = 1001;

    private RecyclerView recyclerView;
    private ConfiguredAppsAdapter adapter;
    private SettingsManager settingsManager;
    private boolean editMode = false;
    private Menu menu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configured_apps_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.apps_settings_activity_title);
        }

        settingsManager = new SettingsManager(this);

        recyclerView = findViewById(R.id.recycler_view_configured_apps);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadConfiguredApps();
    }

    private void loadConfiguredApps() {
        // Именно сохранённая запись, а не собранная на лету: иначе показанное на экране состояние
        // «Системы» расходится с тем, что читает сервис. Пересоздаём её и после удаления в списке.
        AppSettings systemSettings = settingsManager.ensureSystemAppSettings();
        systemSettings.setAppName(AppSettings.SYSTEM_APP_NAME);
        List<AppSettings> appSettingsList = settingsManager.getAppSettingsList();
        appSettingsList.removeIf(s -> AppSettings.SYSTEM_PACKAGE_NAME.equals(s.getPackageName()));
        appSettingsList.add(0, systemSettings);
        adapter = new ConfiguredAppsAdapter(appSettingsList, new ConfiguredAppsAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(AppSettings appSettings) {
                if (!editMode) {
                    // Открываем настройки конкретного приложения
                    Intent intent = new Intent(ConfiguredAppsListActivity.this, AppSpecificSettingsActivity.class);
                    intent.putExtra("packageName", appSettings.getPackageName());
                    intent.putExtra("appName", appSettings.getAppName());
                    startActivity(intent);
                }
            }

            @Override
            public void onSelectionChanged(AppSettings appSettings, boolean isSelected) {
                // Обновляем видимость кнопки удаления
                updateDeleteButtonVisibility();
            }
        }, getPackageManager());
        recyclerView.setAdapter(adapter);
    }

    private void updateDeleteButtonVisibility() {
        if (menu != null && adapter != null) {
            MenuItem deleteItem = menu.findItem(R.id.action_delete);
            if (deleteItem != null) {
                boolean hasSelection = adapter.hasSelectedItems();
                deleteItem.setEnabled(hasSelection);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.menu_configured_apps, menu);
        updateMenuForEditMode();
        return true;
    }

    private void updateMenuForEditMode() {
        if (menu == null) return;

        MenuItem addItem = menu.findItem(R.id.action_add_app);
        MenuItem editItem = menu.findItem(R.id.action_edit);
        MenuItem deleteItem = menu.findItem(R.id.action_delete);
        MenuItem cancelItem = menu.findItem(R.id.action_cancel);

        if (editMode) {
            addItem.setVisible(false);
            editItem.setVisible(false);
            deleteItem.setVisible(true);
            cancelItem.setVisible(true);
            updateDeleteButtonVisibility();
        } else {
            addItem.setVisible(true);
            editItem.setVisible(true);
            deleteItem.setVisible(false);
            cancelItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (editMode) {
                cancelEditMode();
            } else {
                finish();
            }
            return true;
        } else if (item.getItemId() == R.id.action_add_app) {
            // Открываем список всех приложений
            Intent intent = new Intent(this, AllAppsListActivity.class);
            startActivityForResult(intent, REQUEST_CODE_SELECT_APP);
            return true;
        } else if (item.getItemId() == R.id.action_edit) {
            // Включаем режим редактирования
            editMode = true;
            adapter.setEditMode(true);
            updateMenuForEditMode();
            return true;
        } else if (item.getItemId() == R.id.action_cancel) {
            // Крестик на месте карандаша: выходим из режима редактирования, не покидая экран
            cancelEditMode();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            // Показываем диалог подтверждения удаления
            showDeleteConfirmationDialog();
            return true;
        } else if (item.getItemId() == R.id.action_usage_access) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Не удалось открыть настройки доступа к статистике", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void cancelEditMode() {
        editMode = false;
        adapter.setEditMode(false);
        // Снимаем все выделения через адаптер
        adapter.clearAllSelections();
        updateMenuForEditMode();
    }

    private void showDeleteConfirmationDialog() {
        if (adapter == null) {
            return;
        }

        List<AppSettings> selectedApps = adapter.getSelectedItems();

        if (selectedApps.isEmpty()) {
            return;
        }

        String message = selectedApps.size() == 1 
            ? "Вы уверены, что хотите удалить настройки для приложения \"" + selectedApps.get(0).getAppName() + "\"?"
            : "Вы уверены, что хотите удалить настройки для " + selectedApps.size() + " приложений?";

        new AlertDialog.Builder(this)
            .setTitle("Подтверждение удаления")
            .setMessage(message)
            .setPositiveButton("Удалить", (dialog, which) -> {
                // Удаляем выбранные приложения
                for (AppSettings settings : selectedApps) {
                    settingsManager.removeAppSettings(settings.getPackageName());
                }
                // Выходим из режима редактирования
                editMode = false;
                // Перезагружаем список
                loadConfiguredApps();
                updateMenuForEditMode();
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_APP && resultCode == RESULT_OK) {
            // Перезагружаем список после добавления нового приложения
            loadConfiguredApps();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Перезагружаем список при возврате из настроек конкретного приложения
        loadConfiguredApps();
    }

    @Override
    public void onBackPressed() {
        if (editMode) {
            cancelEditMode();
        } else {
            super.onBackPressed();
        }
    }
}
