package com.abrenoch.wledgrabber.common.views;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/** Shared preview host; host apps provide theme and navigation. */
public class LayoutPreviewActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    private LedLayoutPreviewView preview;
    private TextView status;
    private ConfigSnapshot snapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        root.addView(status);
        preview = new LedLayoutPreviewView(this);
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        snapshot = repository.committedSnapshot();
        if (!loaded.isValid()) {
            preview.setError(loaded.errors().isEmpty() ? "invalid config"
                    : loaded.errors().get(0).toString());
            status.setText("configuration invalid");
            return;
        }
        if (snapshot == null) {
            preview.setError("no committed config");
            return;
        }
        int physical = Math.max(1, snapshot.device().physicalLedCount());
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), physical, 640, 360);
        if (!compiled.isValid()) {
            preview.setError(compiled.errors().get(0).toString());
            status.setText("layout invalid");
            return;
        }
        preview.setLayout(compiled.value(), 0);
        status.setText("physical 0");
        preview.setOnIndexSelectedListener(index -> {
            int logical = compiled.value().mapping().physicalToLogical(index);
            status.setText("physical " + index + " -> logical " + logical);
        });
    }
}
