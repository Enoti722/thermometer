package com.github.enoti722.thermometer;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DevicesFragment extends ListFragment {

    private static final String TAG_DF = "DevicesFragment";
    private static final long USB_RETRY_DELAY_MS = 350L;
    private static final int USB_RETRY_MAX_ATTEMPTS = 30;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> saveDiagLogLauncher;
    private Runnable pendingAfterLocationPermission;
    private final Handler connectHandler = new Handler(Looper.getMainLooper());
    private final Runnable deferredUsbPrefsConnect = this::attemptConnectFromStoredPreferences;
    private int usbConnectAttemptsRemaining;

    static class ListItem {
        UsbDevice device;
        int port;
        UsbSerialDriver driver;
        boolean isEmulated;

        ListItem(UsbDevice device, int port, UsbSerialDriver driver) {
            this.device = device;
            this.port = port;
            this.driver = driver;
            this.isEmulated = false;
        }
        
        // Конструктор для эмулированного устройства
        ListItem(boolean isEmulated) {
            this.device = null;
            this.port = 0;
            this.driver = null;
            this.isEmulated = isEmulated;
        }
    }

    private final ArrayList<ListItem> listItems = new ArrayList<>();
    private ArrayAdapter<ListItem> listAdapter;
    private int baudRate = 19200;
    private boolean withIoManager = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Runnable pending = pendingAfterLocationPermission;
                    pendingAfterLocationPermission = null;
                    if (pending != null && Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                        pending.run();
                    }
                });
        saveDiagLogLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/plain"),
                uri -> {
                    Context ctx = getContext();
                    if (uri == null || ctx == null) {
                        return;
                    }
                    copyDiagLogFileToUri(ctx.getApplicationContext(), uri);
                });
        setHasOptionsMenu(true);
        listAdapter = new ArrayAdapter<ListItem>(getActivity(), 0, listItems) {
            @NonNull
            @Override
            public View getView(int position, View view, @NonNull ViewGroup parent) {
                ListItem item = listItems.get(position);
                if (view == null)
                    view = getActivity().getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                
                if(item.isEmulated) {
                    text1.setText("Emulated Thermometer");
                    text2.setText("Test Device (No Physical Hardware)");
                } else if(item.driver == null) {
                    text1.setText("<no driver>");
                    text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                } else if(item.driver.getPorts().size() == 1) {
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver",""));
                    text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                } else {
                    text1.setText(item.driver.getClass().getSimpleName().replace("SerialDriver","")+", Port "+item.port);
                    text2.setText(String.format(Locale.US, "Vendor %04X, Product %04X", item.device.getVendorId(), item.device.getProductId()));
                }
                return view;
            }
        };
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(null);
        View header = getActivity().getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("<no USB devices found>");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);
    }

    private void cancelDeferredUsbConnect() {
        connectHandler.removeCallbacks(deferredUsbPrefsConnect);
    }

    private void scheduleDeferredConnectFromStoredPreferences() {
        cancelDeferredUsbConnect();
        usbConnectAttemptsRemaining = USB_RETRY_MAX_ATTEMPTS;
        connectHandler.post(deferredUsbPrefsConnect);
    }

    /** Сервис после холодной загрузки: эмулятор сразу; реальный USB после появления в списке. */
    private void attemptConnectFromStoredPreferences() {
        if (!isAdded() || getActivity() == null) {
            return;
        }
        ThermometerPrefsMigration.tryMigrateLegacyDeviceId(requireContext());
        SharedPreferences sp = getActivity().getSharedPreferences(ThermometerPrefsKeys.NAME, MODE_PRIVATE);
        if (!BootCompletedReceiver.hasConfiguredThermometerDevice(sp)) {
            return;
        }
        boolean emulated = sp.getBoolean(ThermometerPrefsKeys.IS_EMULATED, false);
        Context app = requireContext().getApplicationContext();
        if (emulated) {
            LocalDiagLog.i(app, TAG_DF, "prefs: emulated → UsbServiceStarter");
            cancelDeferredUsbConnect();
            runAfterLocationPermissionGranted(() -> UsbServiceStarter.start(requireContext()));
            return;
        }
        String storedSerial = sp.getString(ThermometerPrefsKeys.DEVICE_SERIAL, "");
        UsbManager usbManager = (UsbManager) requireContext().getSystemService(Context.USB_SERVICE);
        UsbDevice found = UsbDeviceBySerial.findDevice(usbManager, storedSerial);
        if (found != null) {
            LocalDiagLog.i(app, TAG_DF,
                    "prefs: saved serial matched USB " + UsbDeviceBySerial.fingerprintForLog(found));
            requestDevicePermission(found);
            return;
        }
        usbConnectAttemptsRemaining--;
        if (usbConnectAttemptsRemaining > 0) {
            LocalDiagLog.i(app, TAG_DF, "prefs: no USB for saved serial "
                    + (TextUtils.isEmpty(storedSerial) ? "(empty)" : ("len=" + storedSerial.trim().length()))
                    + ", retry " + usbConnectAttemptsRemaining);
            connectHandler.postDelayed(deferredUsbPrefsConnect, USB_RETRY_DELAY_MS);
        } else {
            LocalDiagLog.i(app, TAG_DF, "prefs: no USB for saved serial after retries");
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
    }

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";

    private void requestDevicePermission(UsbDevice device) {
        cancelDeferredUsbConnect();
        if (device == null || getActivity() == null) {
            return;
        }
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        }
        if (usbManager.hasPermission(device)) {
            runAfterLocationPermissionGranted(() -> UsbServiceStarter.start(requireContext()));
        } else {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            Intent intent = new Intent(INTENT_ACTION_GRANT_USB);
            intent.setPackage(getActivity().getPackageName());
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, intent, flags);
            usbManager.requestPermission(device, usbPermissionIntent);
        }
    }

    private static final int PERMISSION_REQUEST_CODE = 1;

    private void runAfterLocationPermissionGranted(Runnable action) {
        if (!UsbService.ENABLE_GPS_SPEED) {
            action.run();
            return;
        }
        if (SpeedEmulationPreferences.isEmulationEnabled(requireContext())) {
            action.run();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingAfterLocationPermission = action;
        locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION});
    }

    @Override
    public void onPause() {
        super.onPause();
        cancelDeferredUsbConnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();

        if (!Settings.canDrawOverlays(getActivity())) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(settingsIntent, PERMISSION_REQUEST_CODE);
        }

        scheduleDeferredConnectFromStoredPreferences();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.refresh) {
            refresh();
            return true;
        } else if (id ==R.id.baud_rate) {
            final String[] values = getResources().getStringArray(R.array.baud_rates);
            int pos = java.util.Arrays.asList(values).indexOf(String.valueOf(baudRate));
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Baud rate");
            builder.setSingleChoiceItems(values, pos, (dialog, which) -> {
                baudRate = Integer.parseInt(values[which]);
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id ==R.id.read_mode) {
            final String[] values = getResources().getStringArray(R.array.read_modes);
            int pos = withIoManager ? 0 : 1; // read_modes[0]=event/io-manager, read_modes[1]=direct
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Read mode");
            builder.setSingleChoiceItems(values, pos, (dialog, which) -> {
                withIoManager = (which == 0);
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.speed_emulation) {
            startActivity(new Intent(getActivity(), SpeedEmulationActivity.class));
            return true;
        } else if (id == R.id.settings) {
            Intent intent = new Intent(getActivity(), ConfiguredAppsListActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.system_settings) {
            startActivity(new Intent(getActivity(), SystemSettingsActivity.class));
            return true;
        } else if (id == R.id.export_diag_log) {
            exportDiagnosticLog();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void exportDiagnosticLog() {
        Context app = requireContext().getApplicationContext();
        LocalDiagLog.i(app, TAG_DF, "save log to disk requested");
        File logFile = LocalDiagLog.getLogFile(app);
        if (!logFile.exists() || logFile.length() == 0) {
            Toast.makeText(requireContext(), R.string.export_log_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        String outName = "usb_diag_" + stamp + ".txt";
        try {
            saveDiagLogLauncher.launch(outName);
        } catch (ActivityNotFoundException e) {
            /* Многие магнитолы не содержат DocumentsUI — ACTION_CREATE_DOCUMENT некому обработать. */
            LocalDiagLog.w(app, TAG_DF, "CreateDocument: no handler, fallback to Downloads", e);
            if (copyDiagLogToDownloadsMediaStore(app, outName)) {
                Context ctx = getContext();
                if (ctx != null) {
                    Toast.makeText(ctx, R.string.export_log_saved_downloads, Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(requireContext(), R.string.export_log_save_failed, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Запасной экспорт без системного пикера SAF (часто отсутствует на авто-прошивках).
     */
    private boolean copyDiagLogToDownloadsMediaStore(Context appContext, String displayName) {
        File src = LocalDiagLog.getLogFile(appContext);
        if (!src.exists() || src.length() == 0) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/USBThermometer");
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri item = appContext.getContentResolver().insert(collection, values);
        if (item == null) {
            return false;
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = appContext.getContentResolver().openOutputStream(item)) {
            if (out == null) {
                return false;
            }
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            LocalDiagLog.i(appContext, TAG_DF, "log copied to MediaStore Downloads");
            return true;
        } catch (IOException e) {
            LocalDiagLog.w(appContext, TAG_DF, "MediaStore Downloads export failed", e);
            try {
                appContext.getContentResolver().delete(item, null, null);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private void copyDiagLogFileToUri(Context appContext, Uri destUri) {
        File src = LocalDiagLog.getLogFile(appContext);
        if (!src.exists() || src.length() == 0) {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, R.string.export_log_empty, Toast.LENGTH_LONG).show();
            }
            return;
        }
        try (InputStream in = new FileInputStream(src);
             OutputStream out = appContext.getContentResolver().openOutputStream(destUri)) {
            if (out == null) {
                throw new IOException("openOutputStream null");
            }
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, R.string.export_log_saved, Toast.LENGTH_SHORT).show();
            }
            LocalDiagLog.i(appContext, TAG_DF, "log copied to picker uri");
        } catch (IOException e) {
            Context ctx = getContext();
            if (ctx != null) {
                Toast.makeText(ctx, R.string.export_log_save_failed, Toast.LENGTH_LONG).show();
            }
            LocalDiagLog.w(appContext, TAG_DF, "copy diagnostic log failed", e);
        }
    }

    void refresh() {
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        UsbSerialProber usbDefaultProber = UsbSerialProber.getDefaultProber();
        UsbSerialProber usbCustomProber = CustomProber.getCustomProber();
        listItems.clear();
        
        // Добавляем эмулированное устройство в начало списка
        listItems.add(new ListItem(true));
        
        for(UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = usbDefaultProber.probeDevice(device);
            if(driver == null) {
                driver = usbCustomProber.probeDevice(device);
            }
            if(driver != null) {
                for(int port = 0; port < driver.getPorts().size(); port++)
                    listItems.add(new ListItem(device, port, driver));
            } else {
                listItems.add(new ListItem(device, 0, null));
            }
        }
        listAdapter.notifyDataSetChanged();
    }

//    private void requestPermissions() {
//        ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
//                new ActivityResultContracts.RequestMultiplePermissions(),
//                permissions -> {
//                    if (permissions.get(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)) {
//                        return
//                    }
//                }
//        );
//
//        permissionLauncher.launch(new String[]{
//                Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE
//        });
//    }


    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        ListItem item = listItems.get(position-1);
        
        // Обработка эмулированного устройства
        if(item.isEmulated) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences(ThermometerPrefsKeys.NAME, MODE_PRIVATE);

            SharedPreferences.Editor editor = sharedPreferences.edit();

            editor.remove(ThermometerPrefsKeys.DEVICE_SERIAL)
                    .remove(ThermometerPrefsKeys.LEGACY_DEVICE_INT);
            editor.putInt(ThermometerPrefsKeys.PORT, 0);
            editor.putInt(ThermometerPrefsKeys.BAUD, baudRate);
            editor.putBoolean(ThermometerPrefsKeys.WITH_IO_MANAGER, withIoManager);
            editor.putBoolean(ThermometerPrefsKeys.IS_EMULATED, true); // Флаг эмуляции
            editor.commit();
            
            // Остановить текущий сервис
            getActivity().stopService(new Intent(getActivity().getApplication(), UsbService.class));
            runAfterLocationPermissionGranted(() -> UsbServiceStarter.start(requireContext()));

            Toast.makeText(getActivity(), "Emulated device selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if(item.driver == null) {
            Toast.makeText(getActivity(), "no driver", Toast.LENGTH_SHORT).show();
        } else {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences(ThermometerPrefsKeys.NAME, MODE_PRIVATE);

            SharedPreferences.Editor editor = sharedPreferences.edit();

            String sn = item.device.getSerialNumber();
            editor.putString(ThermometerPrefsKeys.DEVICE_SERIAL, sn != null ? sn : "");
            editor.putInt(ThermometerPrefsKeys.PORT, item.port);
            editor.putInt(ThermometerPrefsKeys.BAUD, baudRate);
            editor.putBoolean(ThermometerPrefsKeys.WITH_IO_MANAGER, withIoManager);
            editor.putBoolean(ThermometerPrefsKeys.IS_EMULATED, false); // Реальное устройство
            editor.remove(ThermometerPrefsKeys.LEGACY_DEVICE_INT);

            editor.commit(); // синхронное сохранение

            if (TextUtils.isEmpty(sn)) {
                LocalDiagLog.line(requireContext().getApplicationContext(), "W", TAG_DF,
                        "выбрано USB без серийного номера (getSerialNumber null): отличить несколько датчиков нельзя");
            }

            requestDevicePermission(item.device);

            // Остановить текущий сервис
            getActivity().stopService(new Intent(getActivity().getApplication(), UsbService.class));
            runAfterLocationPermissionGranted(() -> UsbServiceStarter.start(requireContext()));


//            Bundle args = new Bundle();
//            args.putInt("device", item.device.getDeviceId());
//            args.putInt("port", item.port);
//            args.putInt("baud", baudRate);
//            args.putBoolean("withIoManager", withIoManager);
//            Fragment fragment = new TerminalFragment();
//            fragment.setArguments(args);
//            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        }
    }

}
