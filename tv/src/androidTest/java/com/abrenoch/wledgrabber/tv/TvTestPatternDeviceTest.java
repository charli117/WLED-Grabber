package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.views.TestPatternActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * QA-1 end-to-end Android TV path: seeded 274 config -> shared TEST host ->
 * DRGB/DNRGB UDP frames -> real WLED -> black frame + off POST + state readback.
 * The test skips with an explicit assumption when the lab device is unreachable.
 */
@RunWith(AndroidJUnit4.class)
public class TvTestPatternDeviceTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.probeOrSkip();
        WledTestSupport.clearConfig(context);
        WledTestSupport.seedConfig(context);
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            activity = null;
        }
        WledTestSupport.turnOffBestEffort();
        WledTestSupport.clearConfig(context);
    }

    @Test
    public void tenSecondPatternConfirmsRelease() throws Exception {
        Intent intent = new Intent(context, TestPatternActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        String status = WledTestSupport.awaitTerminalStatus(activity, 25000);
        assertTrue("TEST did not reach a terminal state: " + status,
                status.contains("TEST finished"));
        assertTrue("release was not CONFIRMED: " + status,
                status.contains("release CONFIRMED"));

        try (WledHttpClient client = new WledHttpClient()) {
            StateReadResult state = client.readState(WledTestSupport.host(),
                    WledTestSupport.httpPort());
            assertTrue("post-TEST state read failed: " + state.error(), state.isSuccess());
            assertFalse("device is still on after TEST", state.on());
            assertFalse("device is still live after TEST", state.live());
        }
    }
}
