package com.abrenoch.wledgrabber.tv.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityOptionsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.startup.StartupSetupActivity;
import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.R;

public class MainActivity extends LeanbackActivity implements ImageView.OnClickListener,
        ImageView.OnFocusChangeListener {
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    public static final int REQUEST_INITIAL_SETUP = 2;
    public static final String BROADCAST_ERROR = "SERVICE_ERROR";
    public static final String BROADCAST_TAG = "SERVICE_STATUS";
    public static final String BROADCAST_FILTER = "SERVICE_FILTER";
    private static final String TAG = "DEBUG";
    private boolean mRecorderRunning = false;
    private static MediaProjectionManager mMediaProjectionManager;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean checked = intent.getBooleanExtra(BROADCAST_TAG, false);
            mRecorderRunning = checked;
            String error = intent.getStringExtra(BROADCAST_ERROR);
            if (error != null) {
                Toast.makeText(getBaseContext(), error, Toast.LENGTH_SHORT).show();
            }
            TextView status = findViewById(R.id.grabberStartedText);
            if (status != null) {
                status.setText(checked
                        ? com.abrenoch.wledgrabber.common.R.string.tv_status_sending
                        : com.abrenoch.wledgrabber.common.R.string.tv_status_stopped);
            }
            setImageViews(checked, true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!initIfConfigured()){
            startSetup();
        }
        if (savedInstanceState == null) StartupSetupActivity.showIfNeeded(this);


    }

    /** @return whether the activity was initialized */
    private boolean initIfConfigured() {
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(getApplicationContext())),
                new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        ConfigSnapshot snapshot = repository.committedSnapshot();
        if (!loaded.isValid() || snapshot == null) {
            return false;
        }
        String host = snapshot.device().host();
        boolean configured = host != null && !host.trim().isEmpty()
                && snapshot.device().physicalLedCount() > 0
                && snapshot.device().rgbwMode() != RgbwMode.UNCONFIRMED;
        if (!configured) {
            return false;
        }
        initActivity();
        return true;
    }

    private void startSetup() {
        // Start onboarding (setup)
        Intent intent = new Intent(this, NetworkScanActivity.class);
        startActivityForResult(intent, REQUEST_INITIAL_SETUP);
    }

    // Prepare activity for display
    private void initActivity() {
        // assume the recorder is not running until we are notified otherwise
        mRecorderRunning = false;

        setContentView(R.layout.activity_main);
        findViewById(R.id.startup_settings).setOnClickListener(view -> StartupSetupActivity.open(this));
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        mMediaProjectionManager = (MediaProjectionManager)
                                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ImageView iv = findViewById(R.id.power_toggle);
        iv.setOnClickListener(this);
        iv.setOnFocusChangeListener(this);
        iv.setFocusable(true);
        iv.requestFocus();

        ImageButton ib = findViewById(R.id.settingsButton);
        ib.setOnClickListener(this);
        ib.setOnFocusChangeListener(this);
        ib.setFocusable(true);

        setImageViews(mRecorderRunning, false);
        TextView status = findViewById(R.id.grabberStartedText);
        if (status != null) {
            status.setText(com.abrenoch.wledgrabber.common.R.string.tv_status_ready);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter(BROADCAST_FILTER));

        // request an update on the running status
        checkForInstance();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.power_toggle:
                if (!mRecorderRunning) {
                    startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(),
                            REQUEST_MEDIA_PROJECTION);
                } else {
                    stopScreenRecorder();
                }
                mRecorderRunning = !mRecorderRunning;
                break;
            case R.id.settingsButton:
                startSettings();
                break;
        }
    }

    @Override
    public void onFocusChange(View view, boolean focused) {
        int clr = Color.argb(255, 0, 0, 150);
        if (!focused) {
            clr = Color.argb(255, 0, 0, 0);
        }
        switch (view.getId()) {
            case R.id.power_toggle:
                ((ImageView) view).setColorFilter(clr);
                break;
            case R.id.settingsButton:
                ((ImageButton) view).setColorFilter(clr);
                break;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INITIAL_SETUP){
            if (resultCode == RESULT_OK){
                if (!initIfConfigured()){
                    startSetup();
                }
            } else {
                finish();
            }

            return;
        }
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, com.abrenoch.wledgrabber.common.R.string.toast_must_give_permission, Toast.LENGTH_SHORT).show();
                if (mRecorderRunning) {
                    stopScreenRecorder();
                }
                setImageViews(false, true);
                return;
            }
            Log.i(TAG, "Starting screen capture");
            startScreenRecorder(resultCode, (Intent) data.clone());
            mRecorderRunning = true;
        }
    }

    private void startSettings() {
        stopScreenRecorder();
        Intent intent = new Intent(this, ManualSetupActivity.class);
        Bundle bundle =
                ActivityOptionsCompat.makeSceneTransitionAnimation(this)
                        .toBundle();
        startActivity(intent, bundle);
    }

    private void checkForInstance() {
        if (isServiceRunning()) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.GET_STATUS);
            startService(intent);
        }
    }

    private void setImageViews(boolean running, boolean animated) {
        View rainbow = findViewById(R.id.sweepGradientView);
        View message = findViewById(R.id.grabberStartedText);
        if (running) {
            if (animated){
                fadeView(rainbow, true);
                fadeView(message, true);
            } else {
                rainbow.setVisibility(View.VISIBLE);
                message.setVisibility(View.VISIBLE);
            }
        } else {
            if (animated){
                fadeView(rainbow, false);
                fadeView(message, false);
            } else {
                rainbow.setVisibility(View.INVISIBLE);
                message.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void startScreenRecorder(int resultCode, Intent data) {
        BootActivity.startScreenRecorder(this, resultCode, data);
    }

    public void stopScreenRecorder() {
        if (mRecorderRunning) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.ACTION_EXIT);
            startService(intent);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WledScreenService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void fadeView(View view, boolean visible){
        float alpha = visible ? 1f : 0f;
        int endVisibility = visible ? View.VISIBLE : View.INVISIBLE;
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(alpha)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(endVisibility);
                    }
                })
                .start();


    }
}
