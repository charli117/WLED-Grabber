package com.abrenoch.wledgrabber.mobile;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.app.TaskStackBuilder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.NotificationPermission;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

@RequiresApi(api = Build.VERSION_CODES.N)
public class WledTileService extends TileService {
    private final int REMOVE_LISTENER_DELAY = 1000 * 10; // 10 second delay to remove listener
    private static boolean mIsListening = false;
    private Handler mHandle = new Handler();

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Tile tile = getQsTile();
            boolean running = intent.getBooleanExtra(WledScreenService.BROADCAST_TAG, false);
            String error = intent.getStringExtra(WledScreenService.BROADCAST_ERROR);
            tile.setState(running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
            if (error != null) {
                Toast.makeText(getBaseContext(), error, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        mHandle.removeCallbacksAndMessages(null);
        if (!mIsListening) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mMessageReceiver, new IntentFilter(WledScreenService.BROADCAST_FILTER));
            mIsListening = true;
        }
        if (isServiceRunning()) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.GET_STATUS);
            startService(intent);
        } else {
            Tile tile = getQsTile();
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        mHandle.postDelayed(unregisterReceiverRunner, REMOVE_LISTENER_DELAY);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsListening = false;
    }

    @Override
    public void onClick() {
        Tile tile = getQsTile();
        tile.updateTile();
        int tileState = tile.getState();
        if (tileState == Tile.STATE_ACTIVE) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.ACTION_EXIT);
            startService(intent);
        } else {
            if (!NotificationPermission.isGranted(this)) {
                Toast.makeText(this, com.abrenoch.wledgrabber.common.R.string.notification_permission_hint,
                        Toast.LENGTH_LONG).show();
            }
            Runnable runner = () -> {

                boolean setupStarted = startSetupIfNeeded();

                if (!setupStarted){
                    startActivity(new Intent(this,
                            com.abrenoch.wledgrabber.common.startup.StartCaptureActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                }

            };
            if (isLocked()) {
                unlockAndRun(runner);
            } else {
                runner.run();
            }
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

    private Runnable unregisterReceiverRunner = () -> {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        mIsListening = false;
    };

    public static boolean isListening() { return mIsListening; }

    /** Starts the Settings Activity if connection settings are missing
     *
     * @return true if setup was started
     */
    private boolean startSetupIfNeeded(){
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        ConfigSnapshot snapshot = repository.committedSnapshot();
        boolean configured = loaded.isValid() && snapshot != null
                && new ConfigValidator().validateForStart(snapshot).isValid();
        if (!configured){
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            settingsIntent.putExtra(SettingsActivity.EXTRA_SHOW_TOAST_KEY, SettingsActivity.EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Use TaskStackBuilder to make sure the MainActivity opens when the SettingsActivity is closed
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(settingsIntent)
                    .startActivities();


            return true;
        }

        return false;
    }
}
