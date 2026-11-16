package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.activities.ManualSetupActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TV-1/TV-2 setup surface: the Leanback connection step renders the seeded lab
 * endpoint and a Save action persists the whole snapshot. No network is needed
 * for these assertions; the probe path is covered by the device tests.
 */
@RunWith(AndroidJUnit4.class)
public class TvSetupUiTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.clearConfig(context);
        WledTestSupport.seedConfig(context);
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
    public void connectionStepShowsSeededDeviceAndActions() {
        activity = launchManualSetup();

        // Resolve labels from the activity so assertions follow the active locale.
        String hostLabel = activity.getString(
                com.abrenoch.wledgrabber.common.R.string.wled_setup_host);
        String saveLabel = activity.getString(R.string.guidedstep_save);
        onView(withText(hostLabel)).check(matches(isDisplayed()));
        onView(withText(WledTestSupport.host())).check(matches(isDisplayed()));
        onView(withText(saveLabel)).check(matches(isDisplayed()));
    }

    @Test
    public void savePersistsWholeSeededSnapshot() {
        activity = launchManualSetup();

        onView(withText(activity.getString(R.string.guidedstep_save))).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)),
                new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        assertTrue("saved config must remain valid: " + loaded.errors(), loaded.isValid());
        assertEquals(WledTestSupport.host(), loaded.value().device().host());
        assertEquals(WledTestSupport.httpPort(), loaded.value().device().httpPort());
        assertEquals(WledTestSupport.udpPort(), loaded.value().device().udpPort());
        assertEquals(WledTestSupport.ddpPort(), loaded.value().device().ddpPort());
        assertEquals(WledTestSupport.PHYSICAL_LEDS_274,
                loaded.value().device().physicalLedCount());
    }

    private Activity launchManualSetup() {
        Intent intent = new Intent(context, ManualSetupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }
}
