package com.abrenoch.wledgrabber.common;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class ToggleActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    public static final int REQUEST_MEDIA_PROJECTION = 1;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean serviceRunning = checkForInstance();

        if (serviceRunning) {
            stopService();
            finish();
        } else {
            requestPermission();
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                startScreenRecorder(this, resultCode, data);
            }

            finish();

        }
    }

    private static void startScreenRecorder(Context context, int resultCode, Intent data) {
        BootActivity.startScreenRecorder(context, resultCode, data);
    }

    /** @return whether the service is running */
    private boolean checkForInstance() {
        if (isServiceRunning()) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.GET_STATUS);
            startService(intent);
            return true;
        } else {
            return false;
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void requestPermission(){
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        }
    }

    /** stop recording & stop service */

     private void stopService() {
        Intent stopIntent = new Intent(ToggleActivity.this, WledScreenService.class);
        stopIntent.setAction(WledScreenService.ACTION_EXIT);
        startService(stopIntent);
    }
}
