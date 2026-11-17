package com.abrenoch.wledgrabber.mobile;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

/** MOB-2 entry smoke: MainActivity exposes the single capture entry after config seeding. */
@RunWith(AndroidJUnit4.class)
public class MobileEntryTest {

    @Test
    public void mainEntryIsDisplayedAndClickable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        seed(context);
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.power_toggle)).check(matches(isDisplayed()));
            onView(withId(R.id.power_toggle)).check(matches(isClickable()));
        }
    }

    @Test
    public void emptyConfigRoutesPowerToSettingsInsteadOfConsent() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        seed(context);
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .edit().clear().commit();
        context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().putBoolean("completed_v1", true).commit();

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.power_toggle)).perform(click());
        }
        onView(withText(com.abrenoch.wledgrabber.common.R.string.settings_title))
                .check(matches(isDisplayed()));
    }

    private static void seed(Context context) {
        SettingsDraftController controller = new SettingsDraftController(
                new ConfigRepository(new PreferencesConfigStorage(new Preferences(context)),
                        new ConfigValidator()));
        context.getSharedPreferences("startup_setup",
                Context.MODE_PRIVATE).edit().putBoolean("completed_v1", true).commit();
        controller.load();
        controller.set(ConfigFields.WLED_HOST, "192.0.2.10");
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
        ValidationResult<ConfigSnapshot> result = controller.save();
        assertTrue(result.errors().toString(), result.isValid());
    }
}
