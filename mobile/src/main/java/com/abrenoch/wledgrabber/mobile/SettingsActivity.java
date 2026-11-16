package com.abrenoch.wledgrabber.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.EdgeToEdge;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared-config mobile settings form. Every field comes from ConfigFields and
 * saves atomically through SettingsDraftController; no per-field parseInt.
 */
public class SettingsActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_TOAST_KEY = "extra_show_toast_key";
    public static final int EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE = 1;

    private final Map<String, View> inputs = new LinkedHashMap<>();
    private SettingsDraftController controller;
    private TextView errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = new SettingsDraftController(new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator()));
        ValidationResult<ConfigSnapshot> loaded = controller.load();
        buildUi();
        populate();
        if (!loaded.isValid()) {
            showErrors(loaded.errors());
        }
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getInt(EXTRA_SHOW_TOAST_KEY, 0)
                == EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE) {
            Toast.makeText(this, R.string.quick_tile_toast_setup_required,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        errorView = new TextView(this);
        errorView.setTextColor(0xffb00020);
        root.addView(errorView);

        TextView title = new TextView(this);
        title.setText("WLED Grabber settings");
        title.setTextSize(20);
        root.addView(title);

        for (String field : ConfigFields.ALL) {
            if (ConfigFields.SCHEMA_VERSION.equals(field)) {
                continue;
            }
            TextView label = new TextView(this);
            label.setText(labelFor(field));
            root.addView(label);
            View input;
            if (isBoolean(field)) {
                CheckBox checkBox = new CheckBox(this);
                input = checkBox;
            } else {
                EditText editText = new EditText(this);
                editText.setSingleLine(true);
                editText.setInputType(InputType.TYPE_CLASS_TEXT);
                input = editText;
            }
            root.addView(input, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputs.put(field, input);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> save());
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> finish());
        buttons.addView(save, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        buttons.addView(cancel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        setContentView(scroll);
        EdgeToEdge.apply(this, scroll);
    }

    private void populate() {
        for (Map.Entry<String, View> entry : inputs.entrySet()) {
            String value = controller.draft().get(entry.getKey());
            View view = entry.getValue();
            if (view instanceof CheckBox) {
                ((CheckBox) view).setChecked(Boolean.parseBoolean(value));
            } else if (view instanceof EditText) {
                ((EditText) view).setText(value == null ? "" : value);
            }
        }
    }

    private void save() {
        for (Map.Entry<String, View> entry : inputs.entrySet()) {
            View view = entry.getValue();
            String value;
            if (view instanceof CheckBox) {
                value = Boolean.toString(((CheckBox) view).isChecked());
            } else {
                value = ((EditText) view).getText().toString().trim();
            }
            controller.set(entry.getKey(), value);
        }
        ValidationResult<ConfigSnapshot> result = controller.save();
        if (result.isValid()) {
            Toast.makeText(this, "Saved. New settings apply on next start.",
                    Toast.LENGTH_LONG).show();
            finish();
        } else {
            showErrors(result.errors());
        }
    }

    private void showErrors(List<FieldError> errors) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < errors.size() && i < 5; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            FieldError error = errors.get(i);
            builder.append(error.field()).append(": ").append(error.code());
        }
        errorView.setText(builder.toString());
    }

    private static boolean isBoolean(String field) {
        return ConfigFields.LED_REVERSE.equals(field)
                || ConfigFields.USE_AVG_COLOR.equals(field)
                || ConfigFields.BOOT.equals(field);
    }

    private static String labelFor(String field) {
        String label = field.replace("pref_key_", "").replace('_', ' ');
        if (label.isEmpty()) {
            return field;
        }
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (controller != null) {
            controller.cancel();
        }
        super.onBackPressed();
    }
}
