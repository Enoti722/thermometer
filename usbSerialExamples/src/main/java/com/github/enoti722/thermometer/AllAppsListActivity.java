package com.github.enoti722.thermometer;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Activity для отображения списка всех приложений на телефоне
 */
public class AllAppsListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View loadingContainer;
    private AllAppsAdapter adapter;
    private SettingsManager settingsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Выбрать приложение");
        }

        settingsManager = new SettingsManager(this);

        recyclerView = findViewById(R.id.recycler_view_all_apps);
        loadingContainer = findViewById(R.id.loading_container);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Запускаем асинхронную загрузку
        new LoadAppsTask().execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * AsyncTask для загрузки списка приложений в фоновом потоке
     */
    private class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Показываем индикатор загрузки
            loadingContainer.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        }

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            List<AppInfo> appInfoList = new ArrayList<>();
            for (ApplicationInfo app : apps) {
                // Проверяем, не отменена ли задача
                if (isCancelled()) {
                    break;
                }
                
                // Показываем все приложения, кроме системных без иконки
                try {
                    String appName = pm.getApplicationLabel(app).toString();
                    Drawable icon = pm.getApplicationIcon(app);
                    
                    // Фильтруем системные приложения без launcher activity
                    boolean isSystemApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean hasLauncherIntent = pm.getLaunchIntentForPackage(app.packageName) != null;
                    
                    // Показываем приложение если:
                    // 1. Оно имеет launcher intent (пользовательское приложение)
                    // 2. Или это не системное приложение
                    if (hasLauncherIntent || !isSystemApp) {
                        appInfoList.add(new AppInfo(app.packageName, appName, icon));
                    }
                } catch (Exception e) {
                    // Игнорируем приложения без иконки или с ошибками
                }
            }

            // Сортируем по имени
            Collections.sort(appInfoList, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
            
            return appInfoList;
        }

        @Override
        protected void onPostExecute(List<AppInfo> appInfoList) {
            super.onPostExecute(appInfoList);
            
            // Скрываем индикатор загрузки
            loadingContainer.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            
            // Создаем и устанавливаем адаптер
            adapter = new AllAppsAdapter(appInfoList, appInfo -> {
                // Добавляем приложение в настроенные
                AppSettings newSettings = new AppSettings(appInfo.getPackageName(), appInfo.getAppName());
                settingsManager.addOrUpdateAppSettings(newSettings);
                
                // Возвращаемся назад
                setResult(RESULT_OK);
                finish();
            });
            recyclerView.setAdapter(adapter);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            // Скрываем индикатор загрузки
            loadingContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Класс для хранения информации о приложении
     */
    static class AppInfo {
        private final String packageName;
        private final String appName;
        private final Drawable icon;

        public AppInfo(String packageName, String appName, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getAppName() {
            return appName;
        }

        public Drawable getIcon() {
            return icon;
        }
    }
}
