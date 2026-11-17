package com.abrenoch.wledgrabber.common.startup;

import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.WledBootReceiver;
import com.abrenoch.wledgrabber.common.util.Preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real receiver/notification boundary, not a synthetic OS reboot or projection grant. */
@SdkSuppress(minSdkVersion = 23)
public class StartupBootReceiverInstrumentedTest {
    private Context isolated;
    private NotificationManager notifications;
    private final Set<SharedPreferences> stores = new HashSet<>();
    private final List<Intent> launches = new ArrayList<>();

    @Before public void isolateTestApplication() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.abrenoch.wledgrabber.common.test", target.getPackageName());
        String prefix = "boot-receiver-test-" + UUID.randomUUID() + "-";
        isolated = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                SharedPreferences prefs = super.getSharedPreferences(prefix + name, mode);
                stores.add(prefs);
                return prefs;
            }
            @Override public void startActivity(Intent intent) {
                launches.add(intent);
            }
        };
        notifications = (NotificationManager) target.getSystemService(Context.NOTIFICATION_SERVICE);
        assertNotNull(notifications);
        notifications.cancelAll();
        awaitNotificationCount(0);
    }

    @After public void clearOnlyTestApplicationState() {
        if (notifications != null) notifications.cancelAll();
        for (SharedPreferences store : stores) assertTrue(store.edit().clear().commit());
    }

    @Test public void unrelatedOrMissingBroadcastHasNoStartupSideEffects() {
        WledBootReceiver receiver = new WledBootReceiver();
        receiver.onReceive(isolated, null);
        receiver.onReceive(isolated, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
        assertTrue(launches.isEmpty());
        awaitNotificationCount(0);
    }

    @Test public void savedOptOutSuppressesBootReminderAndActivity() {
        assertTrue(new StartupAccess(isolated).saveBootEnabled(false));
        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(launches.isEmpty());
        awaitNotificationCount(0);
    }

    @Test public void enabledBootWithInvalidFrameRateRetainsReminderWithoutStartingCapture() {
        assertTrue(new StartupAccess(isolated).completeSetup(false));
        Preferences preferences = new Preferences(isolated);
        preferences.putString(R.string.pref_key_boot, "true");
        preferences.putInt(R.string.pref_key_framerate, 0);
        assertTrue(new StartupAccess(isolated).hasCompletedSetup());
        assertFalse(new StartupAccess(isolated).configurationReady());

        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertTrue(launches.isEmpty());
        assertFalse(new StartupAccess(isolated).captureServiceRunning());
        assertEquals("true", preferences.getString(R.string.pref_key_boot, null));
        assertEquals("0", preferences.getString(R.string.pref_key_framerate, null));
        StatusBarNotification[] posted = awaitNotificationCount(1);
        assertNotNull(posted[0].getNotification().contentIntent);
    }

    @Test public void disabledBootWithInvalidFrameRateRemainsSilentWithoutRepairingFields() {
        assertTrue(new StartupAccess(isolated).completeSetup(false));
        Preferences preferences = new Preferences(isolated);
        preferences.putInt(R.string.pref_key_framerate, 0);
        assertFalse(new StartupAccess(isolated).configurationReady());

        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertTrue(launches.isEmpty());
        awaitNotificationCount(0);
        assertEquals("false", preferences.getString(R.string.pref_key_boot, null));
        assertEquals("0", preferences.getString(R.string.pref_key_framerate, null));
    }

    @Test public void malformedBootChoiceRetainsReminderWithoutStartingCapture() {
        assertTrue(new StartupAccess(isolated).completeSetup(false));
        Preferences preferences = new Preferences(isolated);
        preferences.putString(R.string.pref_key_boot, "invalid");
        assertFalse(new StartupAccess(isolated).configurationReady());

        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertTrue(launches.isEmpty());
        assertEquals("invalid", preferences.getString(R.string.pref_key_boot, null));
        assertNotNull(awaitNotificationCount(1)[0].getNotification().contentIntent);
    }

    @Test public void booleanTypedBootChoiceRetainsReminderWithoutStartingCapture() {
        assertTrue(new StartupAccess(isolated).completeSetup(false));
        Preferences preferences = new Preferences(isolated);
        preferences.putBoolean(R.string.pref_key_boot, false);
        assertFalse(new StartupAccess(isolated).configurationReady());

        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertTrue(launches.isEmpty());
        assertFalse(preferences.getBoolean(R.string.pref_key_boot));
        assertNotNull(awaitNotificationCount(1)[0].getNotification().contentIntent);
    }

    @Test public void unfinishedFirstOpenFallsBackToUserActionInsteadOfCapture() {
        StartupAccess access = new StartupAccess(isolated);
        assertTrue(access.bootEnabled());
        assertFalse(access.hasCompletedSetup());
        new WledBootReceiver().onReceive(isolated, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(launches.isEmpty());
        StatusBarNotification[] posted = awaitNotificationCount(1);
        assertNotNull(posted[0].getNotification().contentIntent);
        assertFalse(new StartupAccess(isolated).hasCompletedSetup());
    }

    @Test public void automaticAttemptRemainsClaimedAfterAdapterReconstructionInSameBoot() {
        // Read the real boot identity; never write Settings.Global or fake a device restart.
        assumeTrue(Settings.Global.getInt(isolated.getContentResolver(), "boot_count", -1) >= 0);
        assertTrue(new StartupAccess(isolated).claimBootAttempt());
        assertFalse(new StartupAccess(isolated).claimBootAttempt());
        assertFalse(new StartupAccess(isolated).hasCompletedSetup());
        assertTrue(launches.isEmpty());
    }

    private StatusBarNotification[] awaitNotificationCount(int expected) {
        long deadline = SystemClock.elapsedRealtime() + 2_000;
        StatusBarNotification[] current;
        do {
            current = notifications.getActiveNotifications();
            if (current.length == expected) return current;
            SystemClock.sleep(20);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertEquals("Notifications belonging to the isolated test app", expected, current.length);
        return current;
    }
}
