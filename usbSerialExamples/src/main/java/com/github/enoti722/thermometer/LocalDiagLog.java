package com.github.enoti722.thermometer;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Логи без ADB: дописывает в файл во внутренней памяти приложения. Экспорт — пункт меню «Отправить лог».
 */
public final class LocalDiagLog {
    private static final String TAG = "LocalDiagLog";
    static final String DIR = "diagnostics";
    static final String FILE_NAME = "usb_thermometer_diag.log";
    private static final long MAX_BYTES = 400 * 1024;
    private static final Object LOCK = new Object();

    private LocalDiagLog() {}

    public static File getLogFile(Context context) {
        File dir = new File(context.getApplicationContext().getFilesDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "mkdir diagnostics failed");
        }
        return new File(dir, FILE_NAME);
    }

    /** Уровень + зеркало в logcat при подключённой отладке. */
    public static void i(Context context, String tag, String message) {
        line(context, "I", tag, message);
        Log.i(tag, message);
    }

    public static void w(Context context, String tag, String message, Throwable t) {
        line(context, "W", tag, message + " — " + Log.getStackTraceString(t));
        Log.w(tag, message, t);
    }

    public static void line(Context context, String level, String tag, String message) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String ts = fmt.format(new Date());
            File f = getLogFile(app);
            rotateIfHuge(f);
            boolean writeHeader = !f.exists() || f.length() == 0;
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                if (writeHeader) {
                    w.write("# LocalDiagLog — приложение Thermometer\n");
                }
                w.write(ts);
                w.write(' ');
                w.write(level);
                w.write(' ');
                w.write(tag);
                w.write(": ");
                w.write(message);
                w.write('\n');
                w.flush();
            } catch (IOException e) {
                Log.e(TAG, "write failed", e);
            }
        }
    }

    private static void rotateIfHuge(File f) {
        try {
            if (!f.exists() || f.length() <= MAX_BYTES) {
                return;
            }
            File prev = new File(f.getParentFile(), "usb_thermometer_diag.prev.log");
            if (prev.exists() && !prev.delete()) {
                Log.w(TAG, "delete prev log failed");
            }
            if (!f.renameTo(prev)) {
                if (!f.delete()) {
                    Log.w(TAG, "rotate: cannot delete oversized log");
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "rotateIfHuge", e);
        }
    }
}
