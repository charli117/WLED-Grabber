package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.BootActivity;
import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * CFG-2 gate in the production service: a start without a fresh observation or
 * without stored confirmations must be rejected before any capture resource is
 * acquired, even when the consent result code looks valid.
 */
public class TvServiceReadinessContractTest {
    private static final long SETTLE_MS = 2000;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.clearConfig(context);
        WledTestSupport.seedConfig(context);
    }

    @After
    public void tearDown() {
        stopServiceQuietly();
        WledTestSupport.clearConfig(context);
    }

    @Test
    public void startWithoutObservationIsRejectedBeforeCapture() throws Exception {
        startForegroundService(startIntent());
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("an unverified start must not keep the output lease",
                OutputOwner.NONE, OutputSessionCoordinator.get().owner());
    }

    @Test
    public void startWithMismatchedObservationIsRejectedBeforeCapture() throws Exception {
        Intent intent = startIntent();
        // A fresh-looking observation whose device identity/count do not match the
        // saved confirmation (none is stored) must still block the start.
        intent.putExtra(BootActivity.EXTRA_OBSERVED_ID, "someone-else");
        intent.putExtra(BootActivity.EXTRA_OBSERVED_HOST, "192.0.2.77");
        intent.putExtra(BootActivity.EXTRA_OBSERVED_COUNT, 274);
        intent.putExtra(BootActivity.EXTRA_OBSERVED_UDP_PORT, 21324);
        intent.putExtra(BootActivity.EXTRA_OBSERVED_CHANNEL, "RGB");
        intent.putExtra(BootActivity.EXTRA_OBSERVED_AT, System.currentTimeMillis());
        startForegroundService(intent);
        Thread.sleep(SETTLE_MS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals(OutputOwner.NONE, OutputSessionCoordinator.get().owner());
    }

    private static Intent startIntent() {
        Intent start = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                WledScreenService.class);
        start.setAction(WledScreenService.ACTION_START);
        start.putExtra(WledScreenService.EXTRA_RESULT_CODE, Activity.RESULT_OK);
        return start;
    }

    private void startForegroundService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void stopServiceQuietly() {
        try {
            Intent stop = new Intent(context, WledScreenService.class);
            stop.setAction(WledScreenService.ACTION_EXIT);
            context.startService(stop);
        } catch (RuntimeException ignored) {
            // Already gone.
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
