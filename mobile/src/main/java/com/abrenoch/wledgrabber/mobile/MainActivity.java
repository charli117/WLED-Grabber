package com.abrenoch.wledgrabber.mobile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.startup.StartupSetupActivity;
import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.EdgeToEdge;
import com.abrenoch.wledgrabber.common.util.NotificationPermission;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

public class MainActivity extends AppCompatActivity implements ImageView.OnClickListener,
        ImageView.OnFocusChangeListener {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }

    public static final int REQUEST_MEDIA_PROJECTION = 1;
    public static final int REQUEST_NOTIFICATIONS = 2;
    private static final String TAG = "DEBUG";
    private boolean mRecorderRunning = false;
    private static MediaProjectionManager mMediaProjectionManager;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean checked = intent.getBooleanExtra(WledScreenService.BROADCAST_TAG, false);
            mRecorderRunning = checked;
            String error = intent.getStringExtra(WledScreenService.BROADCAST_ERROR);
            if (error != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.N ||
                            !WledTileService.isListening())) {
                Toast.makeText(getBaseContext(), error, Toast.LENGTH_LONG).show();
            }
            if (!checked && intent.getBooleanExtra(
                    WledScreenService.BROADCAST_RELEASE_KNOWN, false)) {
                boolean confirmed = intent.getBooleanExtra(
                        WledScreenService.BROADCAST_RELEASE_CONFIRMED, false);
                Toast.makeText(getBaseContext(),
                        confirmed ? com.abrenoch.wledgrabber.common.R.string.toast_release_confirmed
                                : com.abrenoch.wledgrabber.common.R.string.toast_release_unconfirmed,
                        Toast.LENGTH_LONG).show();
            }
            setImageViews(checked, checked);
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.startup_settings).setOnClickListener(view -> StartupSetupActivity.open(this));
        EdgeToEdge.apply(this, findViewById(android.R.id.content));
        mMediaProjectionManager = (MediaProjectionManager)
                                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ImageView iv = findViewById(R.id.power_toggle);
        iv.setOnClickListener(this);
        iv.setOnFocusChangeListener(this);
        iv.setFocusable(true);
        iv.requestFocus();

        setImageViews(mRecorderRunning, false);

        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter(WledScreenService.BROADCAST_FILTER));
        checkForInstance();
        if (savedInstanceState == null) StartupSetupActivity.showIfNeeded(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View view) {
        if (!mRecorderRunning) {
            if (!isStartConfigured()) {
                startActivity(new Intent(this, SettingsActivity.class));
                return;
            }
            if (NotificationPermission.requestIfNeeded(this, REQUEST_NOTIFICATIONS)) {
                return;
            }
            startProjection();
        } else {
            stopScreenRecorder();
            mRecorderRunning = false;
        }
    }

    /** Empty/incomplete configuration goes to settings first, not to consent. */
    private boolean isStartConfigured() {
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        return loaded.isValid()
                && new ConfigValidator().validateForStart(loaded.value()).isValid();
    }

    @Override
    public void onFocusChange(View view, boolean focused) {
        if (focused) {
            ((ImageView) view).setColorFilter(Color.argb(255, 0, 0, 150));
        } else {
            ((ImageView) view).setColorFilter(Color.argb(255, 0, 0, 0));
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, com.abrenoch.wledgrabber.common.R.string.toast_must_give_permission, Toast.LENGTH_SHORT).show();
                if (mRecorderRunning) {
                    stopScreenRecorder();
                }
                mRecorderRunning = false;
                setImageViews(false, true);
                return;
            }
            Log.i(TAG, "Starting screen capture");
            startScreenRecorder(resultCode, (Intent) data.clone());
            mRecorderRunning = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (grantResults.length == 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        com.abrenoch.wledgrabber.common.R.string.notification_permission_hint,
                        Toast.LENGTH_LONG).show();
            }
            startProjection();
        }
    }

    private void startProjection() {
        mRecorderRunning = true;
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                Intent intent=new Intent(this,SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.preview:
                startActivity(new Intent(this,
                        com.abrenoch.wledgrabber.common.views.LayoutPreviewActivity.class));
                break;
            case R.id.test_patterns:
                startActivity(new Intent(this,
                        com.abrenoch.wledgrabber.common.views.TestPatternActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkForInstance() {
        if (isServiceRunning()) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.GET_STATUS);
            startService(intent);
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

    private void setImageViews(boolean running, boolean animated) {
        View rainbow = findViewById(R.id.sweepGradientView);
        View message = findViewById(R.id.grabberStartedText);
        View buttonImage = findViewById(R.id.power_toggle);
        if (running) {
            if (animated){
                fadeView(rainbow, true);
                fadeView(message, true);
            } else {
                rainbow.setVisibility(View.VISIBLE);
                message.setVisibility(View.VISIBLE);
            }
            buttonImage.setAlpha((float) 1);
        } else {
            if (animated){
                fadeView(rainbow, false);
                fadeView(message, false);
            } else {
                rainbow.setVisibility(View.INVISIBLE);
                message.setVisibility(View.INVISIBLE);
            }
            buttonImage.setAlpha((float) 0.25);
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
