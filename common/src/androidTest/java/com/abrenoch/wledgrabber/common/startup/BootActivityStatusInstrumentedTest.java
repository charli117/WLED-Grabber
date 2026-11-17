package com.abrenoch.wledgrabber.common.startup;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.concurrent.atomic.AtomicReference;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.WledBootReceiver;
import com.abrenoch.wledgrabber.common.WledScreenService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Actual Activity/service presence, with no projection token or physical device access. */
@SdkSuppress(minSdkVersion = 23)
public class BootActivityStatusInstrumentedTest {
    private Context context;
    private Activity foreground;
    private Activity boot;
    private NotificationManager notifications;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.abrenoch.wledgrabber.common.test", context.getPackageName());
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit());
        assertTrue(context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().clear().commit());
        notifications = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notifications.cancelAll();
        BootStatusHarnessActivity.statusQueries.set(0);
        BootStatusHarnessActivity.consentRequests.set(0);
    }

    @After public void tearDown() {
        if (boot != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(boot::finish);
        if (foreground != null) InstrumentationRegistry.getInstrumentation().runOnMainSync(foreground::finish);
        context.stopService(new Intent(context, WledScreenService.class));
        notifications.cancelAll();
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit());
        assertTrue(context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().clear().commit());
    }

    private void startIdleServiceWithReminder() {
        new WledBootReceiver().onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        awaitReminder();
        foreground = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, StartupSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        // Public status requests can create an idle service; that is not CAPTURING.
        context.startService(new Intent(context, WledScreenService.class).setAction(WledScreenService.GET_STATUS));
        long deadline = SystemClock.elapsedRealtime() + 2_000;
        while (!new StartupAccess(context).captureServiceRunning() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20);
        }
        assertTrue(new StartupAccess(context).captureServiceRunning());
    }

    @Test public void idleServiceDoesNotCountAsSuccessfulAutomaticCapture() {
        startIdleServiceWithReminder();
        boot = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, BootActivity.class)
                        .putExtra(BootActivity.EXTRA_AUTOMATIC_BOOT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(100);
        assertEquals("An idle service must leave the fallback reminder visible", 1,
                notifications.getActiveNotifications().length);
    }

    @Test public void recreatingStatusWaitDoesNotResetDeadlineOrRequestConsentAgain() {
        startIdleServiceWithReminder();
        long began = SystemClock.elapsedRealtime();
        boot = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, BootStatusHarnessActivity.class)
                        .putExtra(BootActivity.EXTRA_AUTOMATIC_BOOT, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertEquals(1, BootStatusHarnessActivity.statusQueries.get());
        assertFalse(boot.isFinishing());
        SystemClock.sleep(5_500);
        recreateBoot();
        while (!boot.isFinishing() && SystemClock.elapsedRealtime() < began + 11_000) {
            SystemClock.sleep(20);
        }
        assertTrue("Recreation must retain the original 10-second status deadline", boot.isFinishing());
        assertEquals(0, BootStatusHarnessActivity.consentRequests.get());
        assertEquals(1, notifications.getActiveNotifications().length);
    }

    @Test public void explicitRunningStatusIsRequiredBeforeDismissingReminder() {
        launchStatusHarness(true);
        assertEquals(1, notifications.getActiveNotifications().length);
        assertFalse(boot.isFinishing());
        sendStatus(true, null);
        long deadline = SystemClock.elapsedRealtime() + 2_000;
        while (notifications.getActiveNotifications().length != 0 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20);
        }
        assertTrue(boot.isFinishing());
        assertEquals(0, notifications.getActiveNotifications().length);
        assertEquals(0, BootStatusHarnessActivity.consentRequests.get());
    }

    @Test public void failedExistingServiceKeepsAutomaticReminder() {
        launchStatusHarness(true);
        sendStatus(false, "capture unavailable");
        assertTrue(boot.isFinishing());
        assertEquals(1, notifications.getActiveNotifications().length);
        assertEquals(0, BootStatusHarnessActivity.consentRequests.get());
    }

    @Test public void manualEntryCanRequestFreshConsentWhenExistingServiceIsIdle() {
        launchStatusHarness(false);
        sendStatus(false, null);
        assertEquals(1, BootStatusHarnessActivity.consentRequests.get());
        assertEquals(1, BootStatusHarnessActivity.statusQueries.get());
        assertFalse(boot.isFinishing());
    }

    @Test public void manualConsentWaitSurvivesRecreationWithoutASecondRequest() {
        launchStatusHarness(false);
        sendStatus(false, null);
        assertEquals(1, BootStatusHarnessActivity.consentRequests.get());
        recreateBoot();
        sendStatus(false, null);
        assertEquals("Recreation while awaiting consent must not request it again", 1,
                BootStatusHarnessActivity.consentRequests.get());
    }

    private void recreateBoot() {
        Activity previous = boot;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(previous::recreate);
        AtomicReference<Activity> replacement = new AtomicReference<>();
        long recreationDeadline = SystemClock.elapsedRealtime() + 2_000;
        while (replacement.get() == null && SystemClock.elapsedRealtime() < recreationDeadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (candidate instanceof BootStatusHarnessActivity && candidate != previous) {
                        replacement.set(candidate);
                    }
                }
            });
            SystemClock.sleep(20);
        }
        assertNotNull("Activity recreation completes", replacement.get());
        boot = replacement.get();
    }

    private void launchStatusHarness(boolean automatic) {
        startIdleServiceWithReminder();
        boot = InstrumentationRegistry.getInstrumentation().startActivitySync(
                new Intent(context, BootStatusHarnessActivity.class)
                        .putExtra(BootActivity.EXTRA_AUTOMATIC_BOOT, automatic)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertEquals(1, BootStatusHarnessActivity.statusQueries.get());
    }

    private void sendStatus(boolean running, String error) {
        // Exercise the public in-app status contract, not a real projection/LED success claim.
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(WledScreenService.BROADCAST_FILTER)
                .putExtra(WledScreenService.BROADCAST_TAG, running)
                .putExtra(WledScreenService.BROADCAST_ERROR, error));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void awaitReminder() {
        long deadline = SystemClock.elapsedRealtime() + 2_000;
        while (notifications.getActiveNotifications().length != 1 && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20);
        }
        assertEquals(1, notifications.getActiveNotifications().length);
    }
}
