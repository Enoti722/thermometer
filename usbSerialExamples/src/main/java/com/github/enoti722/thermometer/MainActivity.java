package com.github.enoti722.thermometer;

import android.app.AppOpsManager;
import static android.content.Context.MODE_PRIVATE;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;

import java.util.Set;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (savedInstanceState == null && trySilentAutostart(intent)) {
            return;
        }
        setContentView(R.layout.activity_main);
        logMainActivityLaunchContext("onCreate", intent);
        LocalDiagLog.i(this, TAG, "MainActivity onCreate");
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();

        // К сожалению запускает совсем не то, что надо было
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
//                startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException e) {
                // Обрабатываем, если экрана нет
            }
        }
        if (!hasUsageStatsPermission()) {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (ActivityNotFoundException e) {
                // Обрабатываем, если экрана нет
            }
        }
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
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

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        logMainActivityLaunchContext("onNewIntent", intent);
        if("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            TerminalFragment terminal = (TerminalFragment)getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null)
                terminal.status("USB device detected");
        }
        super.onNewIntent(intent);
    }

    /**
     * Дебаг: referrer, launchedFromPackage, категории и флаги intent — для различения запуска пользователем vs автозапуск ГУ.
     */
    private void logMainActivityLaunchContext(String phase, Intent intent) {
        if (intent == null) {
            LocalDiagLog.line(this, "W", TAG, phase + ": intent=null");
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append(phase);
        sb.append(": action=").append(intent.getAction());
        Set<String> cats = intent.getCategories();
        sb.append(" categories=").append(cats == null ? "null" : cats.toString());
        sb.append(" flags=0x").append(Integer.toHexString(intent.getFlags()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Uri ref = getReferrer();
            sb.append(" referrer=").append(ref != null ? ref.toString() : "null");
        } else {
            sb.append(" referrer=n/a_api<22");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sb.append(" launchedFromPackage=").append(getLaunchedFromPackage());
        } else {
            sb.append(" launchedFromPackage=n/a_api<34");
        }
        sb.append(" component=").append(intent.getComponent());
        LocalDiagLog.i(this, TAG, sb.toString());
    }

    /**
     * Режим «системный автозапуск»: запуск опознан как автоматический и в prefs выбрано устройство —
     * поднимаем сервис и закрываем UI. Не трогаем батарейные/overlay экраны, чтобы автозапуск был «тихим».
     */
    private boolean trySilentAutostart(Intent intent) {
        if (AutostartPreferences.getMode(this) != AutostartPreferences.MODE_SYSTEM_ACTIVITY_REFERRER) {
            LocalDiagLog.i(this, TAG, "silent autostart: mode not enabled");
            return false;
        }

        logMainActivityLaunchContext("onCreate(systemAutostart_check)", intent);
        ThermometerPrefsMigration.tryMigrateLegacyDeviceId(this);

        String reason = silentAutostartReason(intent);
        if (reason == null) {
            LocalDiagLog.i(this, TAG, "silent autostart: launch looks user-initiated — opening UI");
            return false;
        }

        SharedPreferences sp = getSharedPreferences(ThermometerPrefsKeys.NAME, MODE_PRIVATE);
        if (!BootCompletedReceiver.hasConfiguredThermometerDevice(sp)) {
            LocalDiagLog.i(this, TAG,
                    "silent autostart (" + reason + ") but thermometer not configured — opening UI");
            return false;
        }

        LocalDiagLog.i(this, TAG,
                "silent autostart (" + reason + "): starting UsbService and finishing Activity");
        UsbServiceStarter.start(this);
        finish();
        // Гасим анимацию закрытия, чтобы автозапуск не мигал окном приложения на весь экран.
        overridePendingTransition(0, 0);
        return true;
    }

    /**
     * Признак автоматического запуска, либо {@code null}, если запуск похож на ручной.
     * <p>
     * Основной критерий — форма самого intent, а не то, кто его прислал: тап по иконке приложения система
     * всегда оформляет как {@code ACTION_MAIN} + {@code CATEGORY_LAUNCHER}, а автозапуск ГУ и запуск по
     * подключению USB-устройства приходят прямым обращением к компоненту (в журнале — {@code action=null
     * categories=null}). Этот признак не зависит от того, какой пакет на прошивке отвечает за автозапуск.
     * <p>
     * Шаблон referrer остаётся вторым, независимым правилом — на случай, когда автозапуск всё же приходит
     * в виде launcher-интента, но от системного пакета.
     */
    @Nullable
    private String silentAutostartReason(Intent intent) {
        if (intent == null) {
            // Сверять не с чем — рисковать закрытием окна не будем.
            return null;
        }
        if (!isLauncherStyleLaunch(intent)) {
            return "non-launcher intent, action=" + intent.getAction();
        }
        Uri refUri = getReferrer();
        String refStr = refUri != null ? refUri.toString() : "";
        if (AutostartPreferences.referrerMatches(this, refStr)) {
            return "referrer matched: " + refStr;
        }
        return null;
    }

    /** Так и только так система оформляет запуск по тапу пользователя на иконке приложения. */
    private static boolean isLauncherStyleLaunch(Intent intent) {
        return Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
    }

}
