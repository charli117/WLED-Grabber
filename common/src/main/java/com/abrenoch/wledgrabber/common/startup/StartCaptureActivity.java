package com.abrenoch.wledgrabber.common.startup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.network.wled.DeviceReadiness;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.ReadyDevice;
import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared start gate: explicit WLED addressing confirmations, one fresh probe and
 * a READY evaluation before the system screen-capture consent is requested.
 * Every capture entry point (TV, mobile, tile, notification) opens this page.
 */
public class StartCaptureActivity extends AppCompatActivity {
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    /** Returning users skip the one-time addressing confirmations. */
    public static final String EXTRA_AUTO_START = "extra_auto_start";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wled-start-verify");
        thread.setDaemon(true);
        return thread;
    });
    private final List<CheckBox> required = new ArrayList<>();
    private ConfigSnapshot snapshot;
    private StartConfirmationStore store;
    private TextView status;
    private TextView title;
    private TextView description;
    private Button startButton;
    private Button cancelButton;
    private View root;
    private CheckBox manualWhite;
    private WledDevice observed;
    private boolean verifying;
    private boolean autoStart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_capture);
        store = new StartConfirmationStore(this);
        observed = BootActivity.observedFrom(savedInstanceState);
        title = findViewById(R.id.start_capture_title);
        description = findViewById(R.id.start_capture_description);
        root = findViewById(R.id.start_capture_root);
        status = findViewById(R.id.start_capture_status);
        startButton = findViewById(R.id.start_capture_button);
        cancelButton = findViewById(R.id.start_capture_cancel);
        manualWhite = findViewById(R.id.start_confirm_manual_white);
        required.add(findViewById(R.id.start_confirm_offset));
        required.add(findViewById(R.id.start_confirm_segment));
        required.add(findViewById(R.id.start_confirm_dmx));
        required.add(findViewById(R.id.start_confirm_timeout));
        required.add(findViewById(R.id.start_confirm_brightness));
        required.add(findViewById(R.id.start_confirm_senders));
        required.add(findViewById(R.id.start_confirm_sync));

        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        ValidationResult<com.abrenoch.wledgrabber.common.led.ValidatedConfig> startable =
                loaded.isValid() && loaded.value() != null
                        ? new ConfigValidator().validateForStart(loaded.value())
                        : ValidationResult.fail(loaded.errors());
        if (!startable.isValid()) {
            snapshot = null;
            status.setText(R.string.wled_start_config_invalid);
            startButton.setEnabled(false);
        } else {
            snapshot = loaded.value();
            boolean rgbw = snapshot.device().rgbwMode() == RgbwMode.RGBW;
            manualWhite.setVisibility(rgbw ? View.VISIBLE : View.GONE);
            if (rgbw) {
                required.add(manualWhite);
            }
            prefill();
            autoStart = getIntent().getBooleanExtra(EXTRA_AUTO_START, false)
                    && store.load() != null;
            if (autoStart) {
                enterAutoStart();
            }
        }

        startButton.setOnClickListener(view -> beginVerification());
        cancelButton.setOnClickListener(view -> finish());
    }

    /**
     * The one-time WLED addressing confirmations were already stored for this
     * device; hide them and verify immediately so returning users never see the
     * addressing page again.
     */
    private void enterAutoStart() {
        // The gate is intentionally invisible: a translucent, preview-less window
        // avoids the white flash between the remote press and the consent dialog.
        root.setBackgroundColor(Color.TRANSPARENT);
        title.setText(R.string.wled_start_title_auto);
        title.setVisibility(View.GONE);
        description.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        startButton.setVisibility(View.GONE);
        for (CheckBox checkBox : required) {
            checkBox.setVisibility(View.GONE);
        }
        startButton.post(this::beginVerification);
    }

    private void showAutoFailure() {
        root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                this, R.color.start_capture_scrim));
        title.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.VISIBLE);
    }

    private void showAddressingConfirmations() {
        autoStart = false;
        root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                this, R.color.start_capture_scrim));
        title.setText(R.string.wled_start_title);
        title.setVisibility(View.VISIBLE);
        description.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        manualWhite.setVisibility(
                snapshot != null && snapshot.device().rgbwMode() == RgbwMode.RGBW
                        ? View.VISIBLE : View.GONE);
        for (CheckBox checkBox : required) {
            checkBox.setVisibility(View.VISIBLE);
        }
        cancelButton.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.VISIBLE);
    }

    private void prefill() {
        StartConfirmation stored = store.load();
        if (stored == null) {
            return;
        }
        required.get(0).setChecked(stored.offsetZeroConfirmed());
        required.get(1).setChecked(stored.mainSegmentOnlyConfirmed());
        required.get(2).setChecked(stored.dmxStartOneConfirmed());
        required.get(3).setChecked(stored.finiteTimeoutConfirmed());
        required.get(4).setChecked(stored.maxBrightnessOverrideOffConfirmed());
        required.get(5).setChecked(stored.otherSendersCheckedConfirmed());
        required.get(6).setChecked(stored.syncDisabledConfirmed());
        if (manualWhite.getVisibility() == View.VISIBLE) {
            manualWhite.setChecked(stored.manualWhiteConfirmed());
        }
    }

    private void beginVerification() {
        if (snapshot == null || verifying) {
            return;
        }
        boolean allChecked = true;
        for (CheckBox checkBox : required) {
            if (!checkBox.isChecked()) {
                allChecked = false;
                break;
            }
        }
        if (!allChecked) {
            if (autoStart) {
                // A stored confirmation is incomplete; fall back to the explicit
                // one-time checks instead of silently starting.
                showAddressingConfirmations();
            }
            status.setText(R.string.wled_start_confirm_all);
            return;
        }
        verifying = true;
        startButton.setEnabled(false);
        status.setText(R.string.wled_start_probing);
        final ConfigSnapshot pending = snapshot;
        final boolean rgbw = pending.device().rgbwMode() == RgbwMode.RGBW;
        final boolean manualWhiteChecked = manualWhite.isChecked();
        worker.execute(() -> {
            ProbeResult probe;
            try (WledHttpClient client = new WledHttpClient()) {
                probe = client.probe(pending.device().host(), pending.device().httpPort());
            }
            ReadyDevice ready = null;
            String failure = null;
            if (!probe.isSuccess()) {
                failure = String.valueOf(probe.errorType());
            } else {
                StartConfirmation confirmation = new StartConfirmation(
                        probe.device().deviceId(), rgbw && manualWhiteChecked,
                        true, true, true, true, true, true, true);
                ValidationResult<ReadyDevice> evaluation = new DeviceReadiness()
                        .evaluate(pending.device(), probe.device(), confirmation);
                if (evaluation.isValid()) {
                    ready = evaluation.value();
                } else {
                    failure = describe(evaluation.errors().get(0));
                }
            }
            final ReadyDevice verified = ready;
            final String reason = failure;
            runOnUiThread(() -> {
                verifying = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                startButton.setEnabled(true);
                if (verified == null) {
                    status.setText(getString(R.string.wled_start_failed, reason));
                    if (autoStart) {
                        showAutoFailure();
                    }
                    return;
                }
                observed = verified.observed();
                store.save(new StartConfirmation(observed.deviceId(), rgbw && manualWhiteChecked,
                        true, true, true, true, true, true, true));
                status.setText(R.string.wled_start_ready);
                requestConsent();
            });
        });
    }

    private void requestConsent() {
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            status.setText(R.string.wled_start_config_invalid);
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return;
        }
        if (resultCode == Activity.RESULT_OK && data != null) {
            BootActivity.startScreenRecorder(this, resultCode, data, observed);
            finish();
        } else {
            status.setText(R.string.wled_start_consent_cancelled);
        }
    }

    private static String describe(FieldError error) {
        if (error == null) {
            return "";
        }
        return error.detail() != null ? error.detail() : error.code().name();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        // The observation is safe to restore; the consent token is never stored.
        BootActivity.putObserved(state, observed);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
