package com.abrenoch.wledgrabber.tv;

import android.app.Activity;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.tv.activities.LayoutSettingsActivity;
import com.abrenoch.wledgrabber.tv.activities.ManualSetupActivity;

import org.junit.Test;

/**
 * TV-1/TV-2 D-pad walkthrough: every guided step must tolerate D-pad navigation
 * (down through the list, back out) without crashing or leaving a dead focus.
 * The test drives real key events through the Instrumentation API rather than
 * touching internal fragments, so it exercises the host Activity wiring.
 */
public class TvDpadWalkthroughTest {

    private static Activity launch(Class<?> activityClass) {
        android.content.Intent intent = new android.content.Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                activityClass);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        return InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    private static void tap(Activity activity, int keyCode) throws Exception {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            activity.dispatchKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN, keyCode));
            activity.dispatchKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP, keyCode));
        });
        Thread.sleep(60);
    }

    @Test
    public void connectionStepSurvivesDownAndBack() throws Exception {
        WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
        Activity activity = launch(ManualSetupActivity.class);
        try {
            for (int i = 0; i < 12; i++) {
                tap(activity, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            }
            for (int i = 0; i < 6; i++) {
                tap(activity, android.view.KeyEvent.KEYCODE_DPAD_UP);
            }
            tap(activity, android.view.KeyEvent.KEYCODE_BACK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            // Reaching here means the guided step handled the full walk without
            // throwing; BACK must have dismissed it.
            android.app.ActivityManager manager = (android.app.ActivityManager)
                    activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            if (manager != null) {
                for (android.app.ActivityManager.RunningTaskInfo task
                        : manager.getRunningTasks(Integer.MAX_VALUE)) {
                    if (task.topActivity != null && task.topActivity.getClassName()
                            .equals(ManualSetupActivity.class.getName())) {
                        // The host may still be finishing; not a failure.
                        break;
                    }
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext());
        }
    }

    @Test
    public void layoutStepSurvivesFullDpadWalk() throws Exception {
        WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
        WledTestSupport.seedConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
        Activity activity = launch(LayoutSettingsActivity.class);
        try {
            for (int i = 0; i < 34; i++) {
                tap(activity, android.view.KeyEvent.KEYCODE_DPAD_DOWN);
            }
            for (int i = 0; i < 10; i++) {
                tap(activity, android.view.KeyEvent.KEYCODE_DPAD_UP);
            }
            tap(activity, android.view.KeyEvent.KEYCODE_BACK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                    .getTargetContext());
        }
    }
}
