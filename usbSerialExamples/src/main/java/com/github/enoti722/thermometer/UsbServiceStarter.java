package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/** Единый старт foreground {@link UsbService} для API 26+ и фона после загрузки. */
public final class UsbServiceStarter {
    private static final String TAG = "UsbServiceStarter";

    private UsbServiceStarter() {}

    public static void start(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        Intent i = new Intent(app, UsbService.class);
        i.setPackage(app.getPackageName());
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(i);
            } else {
                app.startService(i);
            }
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            LocalDiagLog.w(app, TAG, "UsbService start failed", e);
        }
    }
}
