package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.tv.activities.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/** TV-1 main surface with a committed 274 configuration: ready state, no onboarding. */
@RunWith(AndroidJUnit4.class)
public class TvMainActivityTest {
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
    public void mainShowsReadyStatusAndControls() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        // The idle TV surface hides the rainbow/status text and only exposes the
        // power and settings controls; the ready text is still the status value.
        onView(withId(R.id.grabberStartedText)).check(matches(
                withText(com.abrenoch.wledgrabber.common.R.string.tv_status_ready)));
        onView(withId(R.id.power_toggle)).check(matches(isDisplayed()));
        onView(withId(R.id.settingsButton)).check(matches(isDisplayed()));
    }
}
