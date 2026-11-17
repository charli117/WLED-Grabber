package com.abrenoch.wledgrabber.common.startup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

/** CFG-2 gate: no start without a startable config and explicit confirmations. */
@RunWith(AndroidJUnit4.class)
public class StartCaptureActivityTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clear();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            activity = null;
        }
        clear();
    }

    @Test
    public void incompleteConfigurationDisablesStart() {
        launch();

        onView(withId(R.id.start_capture_button)).check(matches(not(isEnabled())));
        onView(withId(R.id.start_capture_status)).check(matches(
                withText(R.string.wled_start_config_invalid)));
    }

    @Test
    public void uncheckedConfirmationsBlockBeforeAnyProbe() {
        seed();
        launch();

        onView(withId(R.id.start_capture_button)).perform(click());
        onView(withId(R.id.start_capture_status)).check(matches(
                withText(R.string.wled_start_confirm_all)));
    }

    @Test
    public void validConfigShowsEveryRequiredConfirmation() {
        seed();
        launch();

        onView(withId(R.id.start_capture_title)).check(matches(
                withText(R.string.wled_start_title)));
        onView(withId(R.id.start_confirm_offset)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_segment)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_dmx)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_timeout)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_brightness)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_senders)).check(matches(isDisplayed()));
        onView(withId(R.id.start_confirm_sync)).check(matches(isDisplayed()));
    }

    /**
     * Addressing confirmations belong to the first run only. Once a device
     * confirmation is stored, the start page must not present them again.
     */
    @Test
    public void returningUserSkipsAddressingConfirmations() {
        seed();
        new StartConfirmationStore(context).save(new StartConfirmation(
                "dev-1", true, true, true, true, true, true, true, true));

        launch(StartCaptureActivity.EXTRA_AUTO_START);

        onView(withId(R.id.start_capture_title)).check(matches(
                withText(R.string.wled_start_title_auto)));
        onView(withId(R.id.start_capture_description)).check(matches(not(isDisplayed())));
        onView(withId(R.id.start_confirm_offset)).check(matches(not(isDisplayed())));
        onView(withId(R.id.start_confirm_sync)).check(matches(not(isDisplayed())));
    }

    /** Without a stored confirmation the first-run checks must still appear. */
    @Test
    public void firstRunStillShowsAddressingConfirmations() {
        seed();

        launch(StartCaptureActivity.EXTRA_AUTO_START);

        onView(withId(R.id.start_capture_title)).check(matches(
                withText(R.string.wled_start_title)));
        onView(withId(R.id.start_confirm_offset)).check(matches(isDisplayed()));
    }

    private void seed() {
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> saved = repository.save(ConfigTestData.validDraft());
        if (!saved.isValid()) {
            throw new AssertionError("seed failed: " + saved.errors().get(0).code());
        }
    }

    private void launch() {
        launch(null);
    }

    private void launch(String autoStartExtra) {
        Intent intent = new Intent(context, StartCaptureActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (autoStartExtra != null) {
            intent.putExtra(autoStartExtra, true);
        }
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    private void clear() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        context.getSharedPreferences("wled_start_confirmation", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }
}
