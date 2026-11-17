package com.abrenoch.wledgrabber.common.startup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.WledGrabberApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Public Activity/persistence seam; run only in an isolated instrumentation app. */
@RunWith(AndroidJUnit4.class)
public class StartupSetupActivityTest {
    private Context context;
    private Activity activity;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearSetup();
    }

    @After
    public void tearDown() {
        if (activity != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
        clearSetup();
    }

    @Test
    public void savedBootOptOutCanCompleteWithoutAutomaticCaptureAccess() {
        StartupAccess access = new StartupAccess(context);
        assertTrue(access.saveBootEnabled(false));
        launch();

        onView(withId(R.id.startup_boot_enabled)).check(matches(isNotChecked()));
        onView(withId(R.id.startup_continue)).perform(scrollTo(), click());

        assertFalse(new StartupAccess(context).bootEnabled());
        assertTrue(new StartupAccess(context).hasCompletedSetup());
    }

    @Test
    public void firstOpenOffersBootCaptureButDoesNotMarkSetupComplete() {
        launch();

        onView(withId(R.id.startup_boot_enabled)).check(matches(isChecked()));
        assertFalse(new StartupAccess(context).hasCompletedSetup());
    }

    @Test
    public void manualUseCompletesWithBootDisabled() {
        assertTrue(new StartupAccess(context).saveBootEnabled(true));
        launch();

        onView(withId(R.id.startup_manual_only)).perform(scrollTo(), click());

        assertFalse(new StartupAccess(context).bootEnabled());
        assertTrue(new StartupAccess(context).hasCompletedSetup());
    }

    @Test
    public void missingAccessDisablesAutomaticCompletion() {
        StartupAccess access = new StartupAccess(context);
        assumeFalse("Requires a device without all automatic-capture grants",
                access.status().automaticCaptureAvailable());
        assertTrue(access.saveBootEnabled(true));
        launch();

        onView(withId(R.id.startup_continue)).perform(scrollTo()).check(matches(not(isEnabled())));
        assertFalse(new StartupAccess(context).hasCompletedSetup());
    }

    @Test
    public void dpadCanChangeDraftAndBackCancelsWithoutPersistingIt() {
        assertTrue(new StartupAccess(context).saveBootEnabled(false));
        launch();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                activity.findViewById(R.id.startup_boot_enabled).requestFocus());
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        onView(withId(R.id.startup_boot_enabled)).check(matches(isChecked()));

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertFalse(new StartupAccess(context).bootEnabled());
        assertFalse(new StartupAccess(context).hasCompletedSetup());
    }

    @Test
    public void unsupportedPlatformDoesNotOfferOverlayOrAdbGrants() {
        assumeFalse(new StartupAccess(context).status().platformSupported());
        launch();

        onView(withId(R.id.startup_overlay_settings)).perform(scrollTo())
                .check(matches(not(isEnabled())));
        onView(withId(R.id.startup_copy_commands)).perform(scrollTo())
                .check(matches(not(isEnabled())));
        onView(withId(R.id.startup_commands)).check(matches(withText("")));
    }

    @Test
    public void pageHonorsSavedChineseLanguage() {
        WledGrabberApp.setLanguage(context, WledGrabberApp.LANGUAGE_ZH);
        launch();

        onView(withText("首次启动设置")).check(matches(isDisplayed()));
        onView(withId(R.id.startup_boot_enabled)).check(matches(withText("设备开机后自动启动采集")));
    }

    private void launch() {
        Intent intent = new Intent(context, StartupSetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    private void clearSetup() {
        // Never clear a real mobile/TV app, even if someone misconfigures the test runner.
        assertEquals("com.abrenoch.wledgrabber.common.test", context.getPackageName());
        // Test-only storage reset; production code writes solely through StartupAccess.
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit());
        assertTrue(context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE)
                .edit().clear().commit());
    }
}
