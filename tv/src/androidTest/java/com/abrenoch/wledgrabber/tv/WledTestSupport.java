package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.FirstUnitPreset;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Assume;

/**
 * Shared Android TV test target and deterministic config seeding.
 *
 * <p>The WLED endpoint defaults to the lab device and can be overridden with
 * instrumentation arguments, e.g.
 * {@code -Pandroid.testInstrumentationRunnerArguments.wledHost=10.245.0.49}.
 * Tests that need the physical device call {@link #probeOrSkip()} so an
 * unreachable device is reported as an explicit assumption failure rather than
 * a false pass or an unexplained crash.</p>
 */
final class WledTestSupport {
    static final String DEFAULT_HOST = "10.245.0.49";
    static final int DEFAULT_HTTP_PORT = 80;
    static final int DEFAULT_UDP_PORT = 21324;
    static final int DEFAULT_DDP_PORT = 4048;
    static final int PHYSICAL_LEDS_274 = FirstUnitPreset.PHYSICAL_LED_COUNT;

    private WledTestSupport() {
    }

    static String host() {
        return stringArgument("wledHost", DEFAULT_HOST);
    }

    static int httpPort() {
        return intArgument("wledHttpPort", DEFAULT_HTTP_PORT);
    }

    static int udpPort() {
        return intArgument("wledUdpPort", DEFAULT_UDP_PORT);
    }

    static int ddpPort() {
        return intArgument("wledDdpPort", DEFAULT_DDP_PORT);
    }

    static ProbeResult probeOrSkip() {
        ProbeResult result;
        try (WledHttpClient client = new WledHttpClient()) {
            result = client.probe(host(), httpPort());
        }
        Assume.assumeTrue("WLED test device " + host() + ":" + httpPort()
                + " is unreachable (" + result.errorType() + ": " + result.error() + ")",
                result.isSuccess());
        return result;
    }

    /**
     * First-unit 274 WS2815 configuration with the lab WLED endpoint. Mirrors the
     * pinned LAY-1/CONFIG values so the TV TEST host can run unchanged.
     */
    static ConfigDraft validDraft() {
        ConfigDraft draft = new ConfigDraft()
                .set(ConfigFields.SCHEMA_VERSION, "1")
                .set(ConfigFields.WLED_HOST, host())
                .set(ConfigFields.WLED_HTTP_PORT, Integer.toString(httpPort()))
                .set(ConfigFields.WLED_UDP_PORT, Integer.toString(udpPort()))
                .set(ConfigFields.WLED_DDP_PORT, Integer.toString(ddpPort()))
                .set(ConfigFields.WLED_PROTOCOL, "auto")
                .set(ConfigFields.WLED_RGBW_MODE, "rgb")
                .set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract")
                .set(ConfigFields.WLED_PHYSICAL_LEDS, Integer.toString(PHYSICAL_LEDS_274));
        for (java.util.Map.Entry<String, String> entry
                : FirstUnitPreset.layoutValues().entrySet()) {
            draft.set(entry.getKey(), entry.getValue());
        }
        draft.set(ConfigFields.CAPTURE_MAX_DIMENSION, "640")
                .set(ConfigFields.FRAME_RATE, "30")
                .set(ConfigFields.USE_AVG_COLOR, "false")
                .set(ConfigFields.BOOT, "false")
                .set(ConfigFields.QUALITY_CROP_BLACK_BARS, "false")
                .set(ConfigFields.QUALITY_LINEAR_AVERAGE, "false")
                .set(ConfigFields.QUALITY_SATURATION, "100")
                .set(ConfigFields.QUALITY_SMOOTHING, "0");
        return draft;
    }

    static ConfigSnapshot seedConfig(Context context) {
        return seedDraft(context, validDraft());
    }

    static ConfigSnapshot seedDraft(Context context, ConfigDraft draft) {
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> result = repository.save(draft);
        markStartupCompleted(context);
        if (!result.isValid()) {
            throw new AssertionError("seeded config is invalid: "
                    + result.errors().get(0).field() + ":"
                    + result.errors().get(0).code());
        }
        return result.value();
    }

    static void clearConfig(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().clear().commit();
        context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    /** First-run onboarding must not cover the screen under test. */
    static void markStartupCompleted(Context context) {
        context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().putBoolean("completed_v1", true).commit();
    }

    static void turnOffBestEffort() {
        try (WledHttpClient client = new WledHttpClient()) {
            client.turnOff(host(), httpPort());
        }
    }

    /** Polls the TEST host status line until it reports a terminal state. */
    static String awaitTerminalStatus(Activity activity, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String last = "";
        while (System.currentTimeMillis() < deadline) {
            final String[] value = new String[1];
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                TextView status = testHostStatus(activity);
                value[0] = status == null ? "" : status.getText().toString();
            });
            last = value[0];
            if (last.contains("finished") || last.contains("cancelled") || last.contains("error")) {
                return last;
            }
            Thread.sleep(200);
        }
        return last;
    }

    private static TextView testHostStatus(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup frame = (ViewGroup) content;
            if (frame.getChildCount() > 0) {
                View root = frame.getChildAt(0);
                if (root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0) {
                    View first = ((ViewGroup) root).getChildAt(0);
                    if (first instanceof TextView) {
                        return (TextView) first;
                    }
                }
            }
        }
        return firstTextView(activity.getWindow().getDecorView());
    }

    private static TextView firstTextView(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = firstTextView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String stringArgument(String key, String fallback) {
        String value = InstrumentationRegistry.getArguments().getString(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int intArgument(String key, int fallback) {
        String value = InstrumentationRegistry.getArguments().getString(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
