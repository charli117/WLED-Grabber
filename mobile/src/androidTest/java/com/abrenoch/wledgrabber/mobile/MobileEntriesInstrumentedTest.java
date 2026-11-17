package com.abrenoch.wledgrabber.mobile;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.service.quicksettings.TileService;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.views.LayoutPreviewActivity;
import com.abrenoch.wledgrabber.common.views.TestPatternActivity;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MOB-2 device-level entries: the quick-settings tile and the preview/test
 * Activities are declared and resolvable, and the notification channels the
 * foreground/reminder flows rely on exist. These are the entry points that must
 * all funnel into the same capture implementation.
 */
public class MobileEntriesInstrumentedTest {

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void quickSettingsTileIsDeclaredAndEnabled() {
        org.junit.Assume.assumeTrue("TileService requires API 24+",
                android.os.Build.VERSION.SDK_INT >= 24);
        PackageManager manager = context().getPackageManager();
        Intent intent = new Intent(context(), WledTileService.class)
                .setAction(TileService.ACTION_QS_TILE);
        List<ResolveInfo> resolved = manager.queryIntentServices(intent, 0);
        assertNotNull(resolved);
        assertTrue("quick-settings tile service must resolve", resolved.size() >= 1);
        assertTrue("tile service must be enabled",
                resolved.get(0).serviceInfo.enabled);
    }

    @Test
    public void previewAndTestActivitiesResolve() {
        PackageManager manager = context().getPackageManager();
        Intent preview = new Intent(context(), LayoutPreviewActivity.class);
        Intent test = new Intent(context(), TestPatternActivity.class);
        assertNotNull(manager.resolveActivity(preview, 0));
        assertNotNull(manager.resolveActivity(test, 0));
    }

    @Test
    public void notificationChannelsExist() {
        org.junit.Assume.assumeTrue("Notification channels require API 26+",
                android.os.Build.VERSION.SDK_INT >= 26);
        NotificationManager manager = (NotificationManager)
                context().getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(manager);
        // The capture channel is created with the foreground notification when the
        // service starts; a rejected start still builds it before validation.
        Intent start = new Intent(context(), com.abrenoch.wledgrabber.common.WledScreenService.class);
        start.setAction(com.abrenoch.wledgrabber.common.WledScreenService.ACTION_START);
        start.putExtra(com.abrenoch.wledgrabber.common.WledScreenService.EXTRA_RESULT_CODE,
                android.app.Activity.RESULT_CANCELED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context().startForegroundService(start);
        } else {
            context().startService(start);
        }
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        NotificationChannel capture = manager.getNotificationChannel(
                "com.abrenoch.wledgrabber.notification");
        assertNotNull("capture notification channel must exist", capture);

    }

    @Test
    public void bootReceiverIsRegisteredForBootCompleted() {
        // BOOT_COMPLETED is a protected broadcast, so the test asserts the receiver
        // is registered for it rather than sending the broadcast itself.
        android.content.pm.PackageManager manager = context().getPackageManager();
        Intent boot = new Intent(Intent.ACTION_BOOT_COMPLETED)
                .setPackage(context().getPackageName());
        List<android.content.pm.ResolveInfo> receivers =
                manager.queryBroadcastReceivers(boot, 0);
        boolean found = false;
        for (android.content.pm.ResolveInfo info : receivers) {
            if (info.activityInfo != null && info.activityInfo.name != null
                    && info.activityInfo.name.endsWith("WledBootReceiver")) {
                found = true;
            }
        }
        assertTrue("WledBootReceiver must be registered for BOOT_COMPLETED", found);
    }

    @Test
    public void mainAndSettingsEntriesResolve() {
        PackageManager manager = context().getPackageManager();
        assertNotNull(manager.resolveActivity(new Intent(context(), MainActivity.class), 0));
        assertNotNull(manager.resolveActivity(new Intent(context(), SettingsActivity.class), 0));
    }
}
