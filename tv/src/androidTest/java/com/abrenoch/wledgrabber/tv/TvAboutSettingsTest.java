package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.view.KeyEvent;

import androidx.core.content.pm.PackageInfoCompat;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.tv.activities.ManualSetupActivity;
import com.abrenoch.wledgrabber.tv.fragments.settings.WledConnectionStepFragment;

import org.junit.After;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isFocused;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;

public class TvAboutSettingsTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;

    private void openSettings(boolean editConnection) {
        Intent intent = new Intent(instrumentation.getTargetContext(), ManualSetupActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WledConnectionStepFragment.EXTRA_EDIT_CONNECTION, editConnection);
        activity = instrumentation.startActivitySync(intent);
    }

    @After public void closeSettings() {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void remoteOpensAboutAndReturnsToTheSameRow() throws Exception {
        openSettings(false);
        String title = activity.getString(R.string.tv_about_title);
        var focusedRow = allOf(isFocused(), anyOf(withText(title), hasDescendant(withText(title))));
        // Follow actual focus rather than activating a Fragment action by ID.
        for (int i = 0; i < 6; i++) {
            try {
                onView(focusedRow).check(matches(isDisplayed()));
                break;
            } catch (NoMatchingViewException notThereYet) {
                onView(isRoot()).perform(pressKey(KeyEvent.KEYCODE_DPAD_DOWN));
            }
        }
        onView(focusedRow).perform(pressKey(KeyEvent.KEYCODE_DPAD_CENTER));
        PackageInfo installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        String message = activity.getString(R.string.tv_about_details,
                activity.getString(R.string.tv_about_summary), "Charli117", installed.versionName,
                PackageInfoCompat.getLongVersionCode(installed));
        onView(withText(message)).inRoot(isDialog()).check(matches(isDisplayed()));
        onView(isRoot()).inRoot(isDialog()).perform(pressKey(KeyEvent.KEYCODE_BACK));
        onView(focusedRow).check(matches(isDisplayed()));
    }

    @Test public void connectionEditorDoesNotRepeatTheRootAboutEntry() {
        openSettings(true);
        onView(withText(activity.getString(R.string.tv_about_title))).check(doesNotExist());
    }
}
