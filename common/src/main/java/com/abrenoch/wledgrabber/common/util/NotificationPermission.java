package com.abrenoch.wledgrabber.common.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** API33 notification permission with a safe pre-33 fallback. */
public final class NotificationPermission {
    private NotificationPermission() {
    }

    public static boolean isGranted(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Requests only on API33+; returns true when a request was launched. */
    public static boolean requestIfNeeded(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || isGranted(activity)) {
            return false;
        }
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, requestCode);
        return true;
    }
}
