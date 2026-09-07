package com.github.enoti722.thermometer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Адаптер для списка всех приложений
 */
public class AllAppsAdapter extends RecyclerView.Adapter<AllAppsAdapter.ViewHolder> {

    private final List<AllAppsListActivity.AppInfo> appInfoList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(AllAppsListActivity.AppInfo appInfo);
    }

    public AllAppsAdapter(List<AllAppsListActivity.AppInfo> appInfoList, OnItemClickListener listener) {
        this.appInfoList = appInfoList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_all_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AllAppsListActivity.AppInfo appInfo = appInfoList.get(position);
        holder.bind(appInfo, listener);
    }

    @Override
    public int getItemCount() {
        return appInfoList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageIcon;
        private final TextView textAppName;
        private final TextView textPackageName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageIcon = itemView.findViewById(R.id.image_app_icon);
            textAppName = itemView.findViewById(R.id.text_app_name);
            textPackageName = itemView.findViewById(R.id.text_package_name);
        }

        public void bind(AllAppsListActivity.AppInfo appInfo, OnItemClickListener listener) {
            imageIcon.setImageDrawable(appInfo.getIcon());
            textAppName.setText(appInfo.getAppName());
            textPackageName.setText(appInfo.getPackageName());

            itemView.setOnClickListener(v -> listener.onItemClick(appInfo));
        }
    }
}
