package com.github.enoti722.thermometer;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Адаптер для списка настроенных приложений
 */
public class ConfiguredAppsAdapter extends RecyclerView.Adapter<ConfiguredAppsAdapter.ViewHolder> {

    private final List<AppSettings> appSettingsList;
    private final OnItemClickListener listener;
    private boolean editMode = false;
    private final PackageManager packageManager;

    public interface OnItemClickListener {
        void onItemClick(AppSettings appSettings);
        void onSelectionChanged(AppSettings appSettings, boolean isSelected);
    }

    public ConfiguredAppsAdapter(List<AppSettings> appSettingsList, OnItemClickListener listener, PackageManager packageManager) {
        this.appSettingsList = appSettingsList;
        this.listener = listener;
        this.packageManager = packageManager;
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void clearAllSelections() {
        for (AppSettings settings : appSettingsList) {
            settings.setSelected(false);
        }
        notifyDataSetChanged();
    }

    public boolean hasSelectedItems() {
        for (AppSettings settings : appSettingsList) {
            if (settings.isSelected()) {
                return true;
            }
        }
        return false;
    }

    public List<AppSettings> getSelectedItems() {
        List<AppSettings> selectedItems = new java.util.ArrayList<>();
        for (AppSettings settings : appSettingsList) {
            if (settings.isSelected()) {
                selectedItems.add(settings);
            }
        }
        return selectedItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_configured_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppSettings appSettings = appSettingsList.get(position);
        holder.bind(appSettings, listener, editMode, packageManager);
    }

    @Override
    public int getItemCount() {
        return appSettingsList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageIcon;
        private final TextView textAppName;
        private final TextView textPackageName;
        private final CheckBox checkBox;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageIcon = itemView.findViewById(R.id.image_app_icon);
            textAppName = itemView.findViewById(R.id.text_app_name);
            textPackageName = itemView.findViewById(R.id.text_package_name);
            checkBox = itemView.findViewById(R.id.checkbox_select);
        }

        public void bind(AppSettings appSettings, OnItemClickListener listener, boolean editMode, PackageManager packageManager) {
            textAppName.setText(appSettings.getAppName());
            textPackageName.setText(appSettings.getPackageName());

            // Загружаем иконку приложения
            if (AppSettings.SYSTEM_PACKAGE_NAME.equals(appSettings.getPackageName())) {
                imageIcon.setImageResource(android.R.drawable.ic_menu_manage);
            } else {
                try {
                    Drawable icon = packageManager.getApplicationIcon(appSettings.getPackageName());
                    imageIcon.setImageDrawable(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    // Если приложение удалено, показываем иконку по умолчанию
                    imageIcon.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            }

            if (editMode) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(appSettings.isSelected());
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    appSettings.setSelected(isChecked);
                    listener.onSelectionChanged(appSettings, isChecked);
                });
                itemView.setOnClickListener(v -> {
                    checkBox.setChecked(!checkBox.isChecked());
                });
            } else {
                checkBox.setVisibility(View.GONE);
                itemView.setOnClickListener(v -> listener.onItemClick(appSettings));
            }
        }
    }
}
