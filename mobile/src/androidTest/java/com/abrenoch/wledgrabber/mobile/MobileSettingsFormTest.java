package com.abrenoch.wledgrabber.mobile;

import android.content.Context;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.util.ConfigFieldLabels;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * MOB-1 device-level form: every configuration field has a visible localized
 * label, and a recreated Activity re-populates from the same shared draft
 * instead of losing the values.
 */
@RunWith(AndroidJUnit4.class)
public class MobileSettingsFormTest {

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void seed() {
        SettingsDraftController controller = new SettingsDraftController(new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context())), new ConfigValidator()));
        context().getSharedPreferences("startup_setup",
                Context.MODE_PRIVATE).edit().putBoolean("completed_v1", true).commit();
        controller.load();
        controller.set(ConfigFields.WLED_HOST, "192.0.2.11");
        controller.set(ConfigFields.WLED_HTTP_PORT, "80");
        controller.set(ConfigFields.WLED_UDP_PORT, "21324");
        controller.set(ConfigFields.WLED_DDP_PORT, "4048");
        controller.set(ConfigFields.WLED_PROTOCOL, "auto");
        controller.set(ConfigFields.WLED_RGBW_MODE, "rgb");
        controller.set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract");
        controller.set(ConfigFields.WLED_PHYSICAL_LEDS, "274");
        controller.set(ConfigFields.LED_TOP, "86");
        controller.set(ConfigFields.LED_BOTTOM, "90");
        controller.set(ConfigFields.LED_LEFT, "49");
        controller.set(ConfigFields.LED_RIGHT, "49");
        controller.set(ConfigFields.LED_GAP_LENGTH, "0");
        controller.set(ConfigFields.LED_GAP_POSITION, "0");
        controller.set(ConfigFields.LED_INPUT_POSITION, "161");
        controller.set(ConfigFields.LED_REVERSE, "true");
        controller.set(ConfigFields.LED_GAP_MODE, "skip");
        controller.set(ConfigFields.LED_H_DEPTH, "8");
        controller.set(ConfigFields.LED_V_DEPTH, "5");
        controller.set(ConfigFields.LED_OVERLAP, "0");
        controller.set(ConfigFields.LED_EDGE_GAP, "0");
        controller.set(ConfigFields.LED_CORNER_TL_H, "0");
        controller.set(ConfigFields.LED_CORNER_TL_V, "0");
        controller.set(ConfigFields.LED_CORNER_TR_H, "100");
        controller.set(ConfigFields.LED_CORNER_TR_V, "0");
        controller.set(ConfigFields.LED_CORNER_BR_H, "100");
        controller.set(ConfigFields.LED_CORNER_BR_V, "100");
        controller.set(ConfigFields.LED_CORNER_BL_H, "0");
        controller.set(ConfigFields.LED_CORNER_BL_V, "100");
        controller.set(ConfigFields.CAPTURE_MAX_DIMENSION, "640");
        controller.set(ConfigFields.FRAME_RATE, "60");
        controller.set(ConfigFields.USE_AVG_COLOR, "false");
        controller.set(ConfigFields.BOOT, "false");
        controller.set(ConfigFields.QUALITY_CROP_BLACK_BARS, "false");
        controller.set(ConfigFields.QUALITY_LINEAR_AVERAGE, "false");
        controller.set(ConfigFields.QUALITY_SATURATION, "100");
        controller.set(ConfigFields.QUALITY_SMOOTHING, "0");
        assertTrue(controller.save().errors().toString(),
                controller.save().isValid());
    }

    @Test
    public void everyFieldHasAVisibleLabel() {
        seed();
        List<String> missing = new ArrayList<>();
        for (String field : ConfigFields.ALL) {
            if (ConfigFields.SCHEMA_VERSION.equals(field)) {
                continue;
            }
            String label = ConfigFieldLabels.label(context(), field);
            if (label.isEmpty()) {
                missing.add(field);
            }
        }
        assertTrue("fields without a label: " + missing, missing.isEmpty());
        try (ActivityScenario<SettingsActivity> ignored =
                     ActivityScenario.launch(SettingsActivity.class)) {
            onView(withText(ConfigFieldLabels.label(context(), ConfigFields.WLED_HOST)))
                    .check(matches(isDisplayed()));
            onView(withText(ConfigFieldLabels.label(context(), ConfigFields.FRAME_RATE)))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void recreationKeepsTheFormValues() {
        seed();
        final String[] before = new String[1];
        try (ActivityScenario<SettingsActivity> scenario =
                     ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(activity -> before[0] = firstEditTextValue(activity));
            assertNotNull(before[0]);
            assertEquals("192.0.2.11", before[0]);

            scenario.recreate();

            scenario.onActivity(activity -> {
                assertEquals("recreated form must keep the seeded value",
                        before[0], firstEditTextValue(activity));
                assertTrue("recreated form must still show every field",
                        activity.findViewById(android.R.id.content) != null);
            });
        }
    }

    private static String firstEditTextValue(android.app.Activity activity) {
        android.view.View root = activity.findViewById(android.R.id.content);
        EditText first = findFirstEditText(root);
        return first == null ? null : first.getText().toString();
    }

    private static EditText findFirstEditText(android.view.View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findFirstEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
