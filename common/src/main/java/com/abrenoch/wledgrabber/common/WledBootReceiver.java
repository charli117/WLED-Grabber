package com.abrenoch.wledgrabber.common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.abrenoch.wledgrabber.common.startup.StartupAccess;
import com.abrenoch.wledgrabber.common.startup.StartupPolicy;

/**
 * Boot starts a fresh capture only after the user completed setup and current platform
 * grants permit it. Missing capabilities retain the explicit, user-visible reminder.
 */
public class WledBootReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "com.abrenoch.wledgrabber.boot";
    private static final int NOTIFICATION_ID = 42;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        StartupAccess access = new StartupAccess(context);
        if (!access.bootEnabled() || access.captureServiceRunning()) return;
        StartupPolicy.Action action = access.status().onBoot(true,
                access.hasCompletedSetup() && access.configurationReady());
        if (action == StartupPolicy.Action.START_CAPTURE) {
            if (!access.claimBootAttempt()) {
                postReminder(context);
                return;
            }
            // Keep a fallback visible until the service reports actual capture, not just dispatch.
            postReminder(context);
            Intent launch = new Intent(context, BootActivity.class)
                    .putExtra(BootActivity.EXTRA_AUTOMATIC_BOOT, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            try {
                context.startActivity(launch);
            } catch (RuntimeException denied) {
                // Vendor restrictions can be stricter; the reminder remains available.
            }
        } else {
            postReminder(context);
        }
    }

    static void dismissReminder(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    static void postReminder(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.boot_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent launch = new Intent(context,
                com.abrenoch.wledgrabber.common.startup.StartCaptureActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launch, flags);
        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setContentTitle(context.getString(R.string.boot_reminder_title))
                .setContentText(context.getString(R.string.boot_reminder_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // Notification permission denied: do not start capture or an Activity.
        }
    }
}
