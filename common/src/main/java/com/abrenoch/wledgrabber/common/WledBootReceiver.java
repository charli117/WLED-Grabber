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

import com.abrenoch.wledgrabber.common.util.Preferences;

/**
 * Boot only posts a user-visible reminder. It must never start MediaProjection,
 * a foreground capture service, or an Activity directly from the background.
 */
public class WledBootReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "com.abrenoch.wledgrabber.boot";
    private static final int NOTIFICATION_ID = 42;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Preferences preferences = new Preferences(context);
        if (preferences.getBoolean(R.string.pref_key_boot)) {
            postReminder(context);
        }
    }

    private static void postReminder(Context context) {
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
        Intent launch = new Intent(context, BootActivity.class);
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
