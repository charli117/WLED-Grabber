package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.tv.activities.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

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

    @Test
    public void homeButtonsDescribeTheNextActionAfterServiceUpdates() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.settingsButton)).check(matches(withContentDescription(
                com.abrenoch.wledgrabber.common.R.string.pref_title)));
        onView(withId(R.id.power_toggle)).check(matches(isAssignableFrom(android.widget.ImageButton.class)));
        onView(withId(R.id.power_toggle)).check(matches(withContentDescription(
                com.abrenoch.wledgrabber.common.R.string.capture_action_start)));
        Intent running = new Intent(WledScreenService.BROADCAST_FILTER)
                .putExtra(WledScreenService.BROADCAST_TAG, true);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(running);
        onView(withId(R.id.power_toggle)).check(matches(withContentDescription(
                com.abrenoch.wledgrabber.common.R.string.capture_action_stop)));
        sendReleaseBroadcast(false);
        onView(withId(R.id.power_toggle)).check(matches(withContentDescription(
                com.abrenoch.wledgrabber.common.R.string.capture_action_start)));
    }

    @Test
    public void releaseBroadcastShowsConfirmedOrUnconfirmedStatus() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        sendReleaseBroadcast(true);
        onView(withId(R.id.grabberStartedText)).check(matches(
                withText(com.abrenoch.wledgrabber.common.R.string.tv_status_stopped_confirmed)));
        onView(withId(R.id.grabberStartedText)).check(matches(isDisplayed()));

        sendReleaseBroadcast(false);
        onView(withId(R.id.grabberStartedText)).check(matches(
                withText(com.abrenoch.wledgrabber.common.R.string.tv_status_stopped)));
        onView(withId(R.id.grabberStartedText)).check(matches(isDisplayed()));
    }

    @Test
    public void smallStripScanResultStillOpensMainSurface() {
        WledTestSupport.clearConfig(context);
        com.abrenoch.wledgrabber.common.util.ConfigDraft draft = WledTestSupport.validDraft()
                .set(com.abrenoch.wledgrabber.common.led.ConfigFields.WLED_PHYSICAL_LEDS, "60");
        WledTestSupport.seedDraft(context, draft);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        // The saved physical count is below the default 188-LED layout. The main
        // surface must still open; only the start action is blocked until the
        // layout editor tightens the layout.
        onView(withId(R.id.power_toggle)).check(matches(isDisplayed()));
        onView(withId(R.id.settingsButton)).check(matches(isDisplayed()));
        onView(withId(R.id.grabberStartedText)).check(matches(
                withText(com.abrenoch.wledgrabber.common.R.string.tv_status_layout_required)));
    }

    @Test
    public void invalidLayoutPowerOpensEditorInsteadOfConsent() {
        WledTestSupport.clearConfig(context);
        com.abrenoch.wledgrabber.common.util.ConfigDraft draft = WledTestSupport.validDraft()
                .set(com.abrenoch.wledgrabber.common.led.ConfigFields.WLED_PHYSICAL_LEDS, "60");
        WledTestSupport.seedDraft(context, draft);

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        onView(withId(R.id.power_toggle)).perform(click());
        onView(withId(R.id.layout_editor_title)).check(matches(isDisplayed()));
    }

    @Test
    public void staleObservationBroadcastIsShownWhileRunning() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        Intent status = new Intent(WledScreenService.BROADCAST_FILTER);
        status.putExtra(WledScreenService.BROADCAST_TAG, true);
        status.putExtra(WledScreenService.BROADCAST_OBSERVATION_STALE, true);
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .sendBroadcast(status);

        final String[] text = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.widget.TextView view = activity.findViewById(R.id.grabberStartedText);
            text[0] = view == null ? null : view.getText().toString();
        });
        assertEquals(activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.tv_status_observation_stale),
                text[0]);
    }

    private static void sendReleaseBroadcast(boolean confirmed) {
        Intent status = new Intent(WledScreenService.BROADCAST_FILTER);
        status.putExtra(WledScreenService.BROADCAST_TAG, false);
        status.putExtra(WledScreenService.BROADCAST_RELEASE_KNOWN, true);
        status.putExtra(WledScreenService.BROADCAST_RELEASE_CONFIRMED, confirmed);
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(InstrumentationRegistry.getInstrumentation().getTargetContext())
                .sendBroadcast(status);
    }
}
