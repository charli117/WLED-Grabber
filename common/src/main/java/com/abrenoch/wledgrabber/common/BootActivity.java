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
import com.abrenoch.wledgrabber.common.startup.StartupAccess;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class BootActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    public static final String EXTRA_AUTOMATIC_BOOT = "automatic_boot";
    private static final String STATE_START_DEADLINE = "start_deadline";
    private static final String STATE_AWAITING_CONSENT = "awaiting_consent";
    private boolean awaitingConsent;
    private long startDeadline;
    private final Handler timeout = new Handler(Looper.getMainLooper());
    private boolean automatic;
    private boolean observingStart;
    private boolean queryingExistingService;
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
                    requestConsent();
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
        if (awaitingConsent) return;
        startDeadline = savedInstanceState == null ? 0
                : savedInstanceState.getLong(STATE_START_DEADLINE, 0);
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
        requestConsent();
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
                    startScreenRecorder(this, resultCode, data);
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
    }

    private void stopObservingStart() {
        timeout.removeCallbacksAndMessages(null);
        if (observingStart) LocalBroadcastManager.getInstance(this).unregisterReceiver(startStatus);
        observingStart = false;
        startDeadline = 0;
    }

    @Override protected void onDestroy() {
        stopObservingStart();
        super.onDestroy();
    }

    public static void startScreenRecorder(Context context, int resultCode, Intent data) {
        if (context == null || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Intent intent = new Intent(context, WledScreenService.class);
        intent.setAction(WledScreenService.ACTION_START);
        intent.putExtra(WledScreenService.EXTRA_RESULT_CODE, resultCode);
        intent.putExtras(data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
