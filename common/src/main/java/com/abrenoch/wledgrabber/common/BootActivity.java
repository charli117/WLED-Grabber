package com.abrenoch.wledgrabber.common;

import android.app.Activity;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.abrenoch.wledgrabber.common.network.wled.Capabilities;
import com.abrenoch.wledgrabber.common.network.wled.ChannelHint;
import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;
import com.abrenoch.wledgrabber.common.network.wled.DeviceReadiness;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.ReadyDevice;
import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.startup.StartConfirmationStore;
import com.abrenoch.wledgrabber.common.startup.StartupAccess;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    public static final String EXTRA_AUTOMATIC_BOOT = "automatic_boot";
    public static final String EXTRA_OBSERVED_ID = "observed_device_id";
    public static final String EXTRA_OBSERVED_HOST = "observed_host";
    public static final String EXTRA_OBSERVED_VERSION = "observed_version";
    public static final String EXTRA_OBSERVED_COUNT = "observed_count";
    public static final String EXTRA_OBSERVED_UDP_PORT = "observed_udp_port";
    public static final String EXTRA_OBSERVED_CHANNEL = "observed_channel";
    public static final String EXTRA_OBSERVED_AT = "observed_at";
    private static final String STATE_START_DEADLINE = "start_deadline";
    private static final String STATE_AWAITING_CONSENT = "awaiting_consent";
    private static final String STATE_OBSERVED_ID = "state_observed_id";
    private static final String STATE_OBSERVED_HOST = "state_observed_host";
    private static final String STATE_OBSERVED_VERSION = "state_observed_version";
    private static final String STATE_OBSERVED_COUNT = "state_observed_count";
    private static final String STATE_OBSERVED_UDP_PORT = "state_observed_udp_port";
    private static final String STATE_OBSERVED_CHANNEL = "state_observed_channel";
    private static final String STATE_OBSERVED_AT = "state_observed_at";
    private boolean awaitingConsent;
    private long startDeadline;
    private final Handler timeout = new Handler(Looper.getMainLooper());
    private final ExecutorService validator = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wled-boot-verify");
        thread.setDaemon(true);
        return thread;
    });
    private boolean automatic;
    private boolean observingStart;
    private boolean queryingExistingService;
    private WledDevice bootObserved;
    private final BroadcastReceiver startStatus = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(WledScreenService.BROADCAST_TAG, false)) {
                WledBootReceiver.dismissReminder(BootActivity.this);
                finish();
            } else if (queryingExistingService) {
                stopObservingStart();
                if (automatic) {
                    WledBootReceiver.postReminder(BootActivity.this);
                    finish();
                } else {
                    verifyDeviceThenConsent();
                }
            } else if (intent.getStringExtra(WledScreenService.BROADCAST_ERROR) != null) {
                WledBootReceiver.postReminder(BootActivity.this);
                finish();
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        automatic = getIntent().getBooleanExtra(EXTRA_AUTOMATIC_BOOT, false);
        awaitingConsent = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_AWAITING_CONSENT, false);
        // Let Android deliver the outstanding result before querying an idle service again.
        if (awaitingConsent) {
            bootObserved = observedFrom(savedInstanceState);
            return;
        }
        startDeadline = savedInstanceState == null ? 0
                : savedInstanceState.getLong(STATE_START_DEADLINE, 0);
        bootObserved = savedInstanceState == null ? null : observedFrom(savedInstanceState);
        StartupAccess access = new StartupAccess(this);
        if (startDeadline > 0 || access.captureServiceRunning()) {
            // Presence prevents duplicate starts; only an actual running status is success.
            queryingExistingService = true;
            observeStart();
            try {
                startService(new Intent(this, WledScreenService.class)
                        .setAction(WledScreenService.GET_STATUS));
            } catch (RuntimeException unavailable) {
                if (automatic) WledBootReceiver.postReminder(this);
                finish();
            }
            return;
        }
        if (automatic && (!access.bootEnabled() || !access.hasCompletedSetup()
                || !access.configurationReady() || !access.status().automaticCaptureAvailable())) {
            WledBootReceiver.postReminder(this);
            finish();
            return;
        }
        // The system restores an outstanding consent result; never request it twice on rotation.
        if (savedInstanceState != null) return;
        verifyDeviceThenConsent();
    }

    /**
     * A start needs a fresh observation that matches the stored user confirmations;
     * boot never reuses an old probe result or an old consent token.
     */
    private void verifyDeviceThenConsent() {
        validator.execute(() -> {
            ConfigRepository repository = new ConfigRepository(
                    new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
            ValidationResult<ConfigSnapshot> loaded = repository.load();
            StartConfirmation confirmation = new StartConfirmationStore(this).load();
            ReadyDevice ready = null;
            String failure = "configuration incomplete";
            if (loaded.isValid() && loaded.value() != null && confirmation != null) {
                ConfigSnapshot snapshot = loaded.value();
                DeviceEndpoint endpoint = snapshot.device();
                try (WledHttpClient client = new WledHttpClient()) {
                    ProbeResult probe = client.probe(endpoint.host(), endpoint.httpPort());
                    if (!probe.isSuccess()) {
                        failure = String.valueOf(probe.errorType());
                    } else {
                        ValidationResult<ReadyDevice> evaluation = new DeviceReadiness()
                                .evaluate(endpoint, probe.device(), confirmation);
                        if (evaluation.isValid()) {
                            ready = evaluation.value();
                        } else {
                            failure = evaluation.errors().get(0).detail() == null
                                    ? evaluation.errors().get(0).code().name()
                                    : evaluation.errors().get(0).detail();
                        }
                    }
                } catch (RuntimeException e) {
                    failure = String.valueOf(e.getMessage());
                }
            } else if (confirmation == null) {
                failure = "WLED addressing has not been confirmed";
            }
            final ReadyDevice verified = ready;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || verified == null) {
                    if (automatic) WledBootReceiver.postReminder(this);
                    finish();
                    return;
                }
                bootObserved = verified.observed();
                requestConsent();
            });
        });
    }

    private void requestConsent() {
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            awaitingConsent = true;
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        } else {
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            awaitingConsent = false;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (automatic) {
                    queryingExistingService = false;
                    observeStart();
                }
                try {
                    startScreenRecorder(this, resultCode, data, bootObserved);
                    if (!automatic) finish();
                } catch (RuntimeException denied) {
                    if (automatic) WledBootReceiver.postReminder(this);
                    finish();
                }
            } else {
                if (automatic) WledBootReceiver.postReminder(this);
                finish();
            }
        }
    }

    private void observeStart() {
        if (observingStart) return;
        observingStart = true;
        if (startDeadline == 0) startDeadline = SystemClock.elapsedRealtime() + 10_000L;
        LocalBroadcastManager.getInstance(this).registerReceiver(startStatus,
                new IntentFilter(WledScreenService.BROADCAST_FILTER));
        timeout.postDelayed(() -> {
            if (automatic) WledBootReceiver.postReminder(this);
            finish();
        }, Math.max(0, startDeadline - SystemClock.elapsedRealtime()));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        // Restore the waiting phase/deadline, never the screen-capture authorization result.
        state.putBoolean(STATE_AWAITING_CONSENT, awaitingConsent);
        if (observingStart) state.putLong(STATE_START_DEADLINE, startDeadline);
        putObserved(state, bootObserved);
    }

    /** Stores the observation in a Bundle; never a consent token. */
    public static void putObserved(Bundle state, WledDevice device) {
        if (device == null || device.capabilities() == null) {
            return;
        }
        Capabilities capabilities = device.capabilities();
        state.putString(STATE_OBSERVED_ID, capabilities.deviceId());
        state.putString(STATE_OBSERVED_HOST, device.address());
        state.putString(STATE_OBSERVED_VERSION, capabilities.firmwareVersion());
        state.putInt(STATE_OBSERVED_COUNT, capabilities.observedLedCount());
        state.putInt(STATE_OBSERVED_UDP_PORT, capabilities.udpPort());
        state.putString(STATE_OBSERVED_CHANNEL,
                capabilities.channelHint() == null ? null : capabilities.channelHint().name());
        state.putLong(STATE_OBSERVED_AT, capabilities.observedAtMillis());
    }

    public static WledDevice observedFrom(Bundle state) {
        if (state == null) {
            return null;
        }
        String deviceId = state.getString(STATE_OBSERVED_ID);
        if (deviceId == null) {
            return null;
        }
        return buildObserved(state.getString(STATE_OBSERVED_VERSION),
                deviceId,
                state.getString(STATE_OBSERVED_HOST),
                state.getInt(STATE_OBSERVED_COUNT, 0),
                state.getInt(STATE_OBSERVED_UDP_PORT, 0),
                state.getString(STATE_OBSERVED_CHANNEL),
                state.getLong(STATE_OBSERVED_AT, 0));
    }

    /** Reads the observation carried by a start intent; null when absent/incomplete. */
    public static WledDevice observedFrom(Intent intent) {
        if (intent == null) {
            return null;
        }
        return buildObserved(intent.getStringExtra(EXTRA_OBSERVED_VERSION),
                intent.getStringExtra(EXTRA_OBSERVED_ID),
                intent.getStringExtra(EXTRA_OBSERVED_HOST),
                intent.getIntExtra(EXTRA_OBSERVED_COUNT, 0),
                intent.getIntExtra(EXTRA_OBSERVED_UDP_PORT, 0),
                intent.getStringExtra(EXTRA_OBSERVED_CHANNEL),
                intent.getLongExtra(EXTRA_OBSERVED_AT, 0));
    }

    static WledDevice buildObserved(String version, String deviceId, String address, int count,
                                    int udpPort, String channelHint, long observedAt) {
        if (deviceId == null || address == null || address.trim().isEmpty()
                || count < 1 || udpPort < 1) {
            return null;
        }
        ChannelHint hint;
        try {
            hint = channelHint == null ? ChannelHint.UNKNOWN : ChannelHint.valueOf(channelHint);
        } catch (IllegalArgumentException unknown) {
            hint = ChannelHint.UNKNOWN;
        }
        Capabilities capabilities = new Capabilities(version == null ? "" : version,
                deviceId, count, udpPort, hint == ChannelHint.RGBW, hint, "", "",
                false, observedAt);
        return new WledDevice(address, capabilities);
    }

    private void stopObservingStart() {
        timeout.removeCallbacksAndMessages(null);
        if (observingStart) LocalBroadcastManager.getInstance(this).unregisterReceiver(startStatus);
        observingStart = false;
        startDeadline = 0;
    }

    @Override protected void onDestroy() {
        stopObservingStart();
        validator.shutdownNow();
        super.onDestroy();
    }

    public static void startScreenRecorder(Context context, int resultCode, Intent data) {
        startScreenRecorder(context, resultCode, data, null);
    }

    public static void startScreenRecorder(Context context, int resultCode, Intent data,
                                           WledDevice observed) {
        if (context == null || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Intent intent = new Intent(context, WledScreenService.class);
        intent.setAction(WledScreenService.ACTION_START);
        intent.putExtra(WledScreenService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(data);
        if (observed != null && observed.capabilities() != null) {
            Capabilities capabilities = observed.capabilities();
            intent.putExtra(EXTRA_OBSERVED_ID, capabilities.deviceId());
            intent.putExtra(EXTRA_OBSERVED_HOST, observed.address());
            intent.putExtra(EXTRA_OBSERVED_VERSION, capabilities.firmwareVersion());
            intent.putExtra(EXTRA_OBSERVED_COUNT, capabilities.observedLedCount());
            intent.putExtra(EXTRA_OBSERVED_UDP_PORT, capabilities.udpPort());
            intent.putExtra(EXTRA_OBSERVED_CHANNEL,
                    capabilities.channelHint() == null ? null : capabilities.channelHint().name());
            intent.putExtra(EXTRA_OBSERVED_AT, capabilities.observedAtMillis());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
