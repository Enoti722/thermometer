package com.github.enoti722.thermometer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import java.util.Locale;

public class SpeedEmulationActivity extends AppCompatActivity {

    private SwitchCompat switchEmulation;
    private EditText editMin;
    private EditText editMax;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_emulation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.speed_emulation_title);
        }

        switchEmulation = findViewById(R.id.switch_speed_emulation);
        editMin = findViewById(R.id.edit_speed_min);
        editMax = findViewById(R.id.edit_speed_max);
        Button save = findViewById(R.id.button_save_speed_emulation);

        SharedPreferences sp = SpeedEmulationPreferences.prefs(this);
        switchEmulation.setChecked(sp.getBoolean(SpeedEmulationPreferences.KEY_ENABLED, false));
        editMin.setText(String.format(Locale.US, "%.1f", sp.getFloat(SpeedEmulationPreferences.KEY_MIN_KMH, SpeedEmulationPreferences.DEFAULT_MIN_KMH)));
        editMax.setText(String.format(Locale.US, "%.1f", sp.getFloat(SpeedEmulationPreferences.KEY_MAX_KMH, SpeedEmulationPreferences.DEFAULT_MAX_KMH)));

        save.setOnClickListener(v -> persist());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void persist() {
        float min = parseFloatOrNaN(editMin.getText().toString());
        float max = parseFloatOrNaN(editMax.getText().toString());
        if (Float.isNaN(min) || Float.isNaN(max)) {
            Toast.makeText(this, R.string.speed_emulation_invalid_range, Toast.LENGTH_SHORT).show();
            return;
        }
        if (max < min) {
            Toast.makeText(this, R.string.speed_emulation_invalid_range, Toast.LENGTH_SHORT).show();
            return;
        }
        SharedPreferences.Editor ed = SpeedEmulationPreferences.prefs(this).edit();
        ed.putBoolean(SpeedEmulationPreferences.KEY_ENABLED, switchEmulation.isChecked());
        ed.putFloat(SpeedEmulationPreferences.KEY_MIN_KMH, min);
        ed.putFloat(SpeedEmulationPreferences.KEY_MAX_KMH, max);
        ed.apply();
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static float parseFloatOrNaN(String s) {
        if (s == null) {
            return Float.NaN;
        }
        String t = s.trim().replace(',', '.');
        if (t.isEmpty()) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(t);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
