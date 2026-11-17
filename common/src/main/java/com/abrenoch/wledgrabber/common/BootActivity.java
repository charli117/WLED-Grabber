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
    private final Handler timeout = new Handler(Looper.getMainLooper());
    private boolean automatic;
    private boolean observingStart;
    private final BroadcastReceiver startStatus = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.getBooleanExtra(WledScreenService.BROADCAST_TAG, false)) {
                WledBootReceiver.dismissReminder(BootActivity.this);
                finish();
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
        StartupAccess access = new StartupAccess(this);
        if (access.captureServiceRunning()) {
            if (automatic) WledBootReceiver.dismissReminder(this);
            finish();
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
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        } else {
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (automatic) {
                    observingStart = true;
                    LocalBroadcastManager.getInstance(this).registerReceiver(startStatus,
                            new IntentFilter(WledScreenService.BROADCAST_FILTER));
                    timeout.postDelayed(() -> {
                        WledBootReceiver.postReminder(this);
                        finish();
                    }, 10_000L);
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

    @Override protected void onDestroy() {
        timeout.removeCallbacksAndMessages(null);
        if (observingStart) LocalBroadcastManager.getInstance(this).unregisterReceiver(startStatus);
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
