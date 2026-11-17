package com.abrenoch.wledgrabber.mobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

/** MOB-1: a damaged preference shows a field error and is never half-written. */
@RunWith(AndroidJUnit4.class)
public class MobileSettingsCorruptPreferenceTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        SettingsDraftController controller = new SettingsDraftController(
                new ConfigRepository(new PreferencesConfigStorage(new Preferences(context)),
                        new ConfigValidator()));
        controller.load();
        controller.set(ConfigFields.WLED_HOST, "192.0.2.10");
        controller.set(ConfigFields.WLED_RGBW_MODE, "rgb");
        controller.set(ConfigFields.WLED_PHYSICAL_LEDS, "274");
        controller.set(ConfigFields.FRAME_RATE, "30");
        ValidationResult<ConfigSnapshot> saved = controller.save();
        if (!saved.isValid()) {
            throw new AssertionError("seed failed: " + saved.errors().get(0).code());
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putString(ConfigFields.LED_TOP, "not-a-number").commit();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            activity = null;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
    }

    @Test
    public void corruptValueShowsFieldErrorAndKeepsStorageValue() {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        onView(withText(containsString(ConfigFields.LED_TOP)))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(isDisplayed()));

        String stored = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(ConfigFields.LED_TOP, null);
        org.junit.Assert.assertEquals("not-a-number", stored);
    }
}
