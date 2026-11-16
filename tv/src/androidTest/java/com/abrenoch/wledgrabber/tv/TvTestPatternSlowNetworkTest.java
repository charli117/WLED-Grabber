package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.views.TestPatternActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * PRE-2 slow/unreachable network: the 10 s pattern host must still reach a
 * terminal state within its stop budget when the WLED endpoint never answers,
 * and it must report the release as UNCONFIRMED rather than pretending success.
 */
public class TvTestPatternSlowNetworkTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.clearConfig(context);
        // TEST-NET-1 (RFC 5737): guaranteed not routable on a real LAN.
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)), new ConfigValidator());
        com.abrenoch.wledgrabber.common.util.ConfigDraft draft = repository.newDraft();
        draft.set(ConfigFields.SCHEMA_VERSION, "1");
        draft.set(ConfigFields.WLED_HOST, "192.0.2.1");
        draft.set(ConfigFields.WLED_HTTP_PORT, "80");
        draft.set(ConfigFields.WLED_UDP_PORT, "21324");
        draft.set(ConfigFields.WLED_DDP_PORT, "4048");
        draft.set(ConfigFields.WLED_PROTOCOL, "auto");
        draft.set(ConfigFields.WLED_RGBW_MODE, "rgb");
        draft.set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract");
        draft.set(ConfigFields.WLED_PHYSICAL_LEDS, "274");
        draft.set(ConfigFields.LED_TOP, "86");
        draft.set(ConfigFields.LED_BOTTOM, "90");
        draft.set(ConfigFields.LED_LEFT, "49");
        draft.set(ConfigFields.LED_RIGHT, "49");
        draft.set(ConfigFields.LED_GAP_LENGTH, "0");
        draft.set(ConfigFields.LED_GAP_POSITION, "0");
        draft.set(ConfigFields.LED_INPUT_POSITION, "161");
        draft.set(ConfigFields.LED_REVERSE, "true");
        draft.set(ConfigFields.LED_GAP_MODE, "skip");
        draft.set(ConfigFields.LED_H_DEPTH, "8");
        draft.set(ConfigFields.LED_V_DEPTH, "5");
        draft.set(ConfigFields.LED_OVERLAP, "0");
        draft.set(ConfigFields.LED_EDGE_GAP, "0");
        draft.set(ConfigFields.LED_CORNER_TL_H, "0");
        draft.set(ConfigFields.LED_CORNER_TL_V, "0");
        draft.set(ConfigFields.LED_CORNER_TR_H, "100");
        draft.set(ConfigFields.LED_CORNER_TR_V, "0");
        draft.set(ConfigFields.LED_CORNER_BR_H, "100");
        draft.set(ConfigFields.LED_CORNER_BR_V, "100");
        draft.set(ConfigFields.LED_CORNER_BL_H, "0");
        draft.set(ConfigFields.LED_CORNER_BL_V, "100");
        draft.set(ConfigFields.CAPTURE_MAX_DIMENSION, "640");
        draft.set(ConfigFields.FRAME_RATE, "30");
        draft.set(ConfigFields.USE_AVG_COLOR, "false");
        draft.set(ConfigFields.BOOT, "false");
        draft.set(ConfigFields.QUALITY_CROP_BLACK_BARS, "false");
        draft.set(ConfigFields.QUALITY_LINEAR_AVERAGE, "false");
        draft.set(ConfigFields.QUALITY_SATURATION, "100");
        draft.set(ConfigFields.QUALITY_SMOOTHING, "0");
        assertTrue(repository.save(draft).errors().toString(), repository.save(draft).isValid());
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            activity = null;
        }
        WledTestSupport.clearConfig(context);
    }

    @Test
    public void unreachableDeviceStillReachesTerminalUnconfirmed() throws Exception {
        Intent intent = new Intent(context, TestPatternActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        long start = System.currentTimeMillis();
        String status = WledTestSupport.awaitTerminalStatus(activity, 20000);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("pattern must reach a terminal state, got: " + status,
                status.contains("finished") || status.contains("cancelled")
                        || status.contains("error"));
        assertTrue("unreachable device must not be reported as confirmed: " + status,
                !status.contains("release CONFIRMED"));
        assertTrue("terminal state must arrive within the stop budget, took " + elapsed + "ms",
                elapsed < 15000);
    }
}
