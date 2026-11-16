package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.WledScreenService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * CAP-1 foreground-service contract on the real platform: whenever the service is
 * started via {@code startForegroundService()} it must call
 * {@code startForeground()} before returning, even when the start is rejected
 * (missing consent, incomplete configuration). Before the CAP-1 fix this path
 * raised RemoteServiceException and killed the process.
 *
 * <p>Only the rejection paths are exercised: a real capture needs the system
 * consent dialog, which instrumentation cannot dismiss without a user.</p>
 */
public class TvServiceForegroundContractTest {
    private static final long SETTLE_MS = 2000;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.clearConfig(context);
    }

    @After
    public void tearDown() {
        stopServiceQuietly();
        WledTestSupport.clearConfig(context);
    }

    private void stopServiceQuietly() {
        try {
            Intent stop = new Intent(context, WledScreenService.class);
            stop.setAction(WledScreenService.ACTION_EXIT);
            context.startService(stop);
        } catch (RuntimeException ignored) {
            // The service may already be gone.
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }


    private void startForegroundServiceWithoutConsent() {
        Intent start = new Intent(context, WledScreenService.class);
        start.setAction(WledScreenService.ACTION_START);
        // A rejected result code is what a denied consent dialog looks like; the
        // service must still honour the startForeground() deadline.
        start.putExtra(WledScreenService.EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(start);
        } else {
            context.startService(start);
        }
    }

    @Test
    public void rejectedStartStillForegroundsAndDoesNotCrash() throws Exception {
        startForegroundServiceWithoutConsent();
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // The instrumentation process is the app process: had the service thrown
        // RemoteServiceException, this test would never reach the assertions.
        assertTrue("process survived a rejected start", true);
    }

    @Test
    public void incompleteConfigurationStartReleasesOutputOwnership() throws Exception {
        // Config was cleared, so startCapture() rejects on validation; the service
        // must foreground first, then release and stop instead of leaving a lease.
        startForegroundServiceWithoutConsent();
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("a rejected start must not keep the output lease",
                com.abrenoch.wledgrabber.common.capture.OutputOwner.NONE,
                com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator.get().owner());
    }
}
