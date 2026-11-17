package com.abrenoch.wledgrabber.common;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.abrenoch.wledgrabber.common.startup.StartCaptureActivity;

/** Secondary launcher surface: stop a running capture or enter the shared start gate. */
public class ToggleActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkForInstance()) {
            stopService();
            finish();
        } else {
            startActivity(new Intent(this, StartCaptureActivity.class));
            finish();
        }
    }

    /** @return whether the service is running */
    private boolean checkForInstance() {
        if (isServiceRunning()) {
            Intent intent = new Intent(this, WledScreenService.class);
            intent.setAction(WledScreenService.GET_STATUS);
            startService(intent);
            return true;
        }
        return false;
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

    private void stopService() {
        Intent stopIntent = new Intent(ToggleActivity.this, WledScreenService.class);
        stopIntent.setAction(WledScreenService.ACTION_EXIT);
        startService(stopIntent);
    }
}
