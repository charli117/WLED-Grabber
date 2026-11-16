package com.abrenoch.wledgrabber.tv;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.GuidedAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.tv.activities.LayoutSettingsActivity;
import com.abrenoch.wledgrabber.tv.fragments.settings.WledLayoutStepFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

/**
 * TV-2 layout flow smoke using the shared 274 fixture.
 *
 * <p>GuidedStep only binds the visible rows, so the full action model is
 * asserted through the fragment's public {@link GuidedAction} list while the
 * first field is also checked in the rendered hierarchy.</p>
 */
@RunWith(AndroidJUnit4.class)
public class TvLayoutFlowTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WledTestSupport.clearConfig(context);
        WledTestSupport.seedConfig(context);
    }

    @After
    public void tearDown() {
        WledTestSupport.clearConfig(context);
    }

    @Test
    public void layoutStepShowsFieldsPresetAndTestEntry() {
        try (ActivityScenario<LayoutSettingsActivity> scenario =
                     ActivityScenario.launch(LayoutSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                assertTrue("layout step fragment missing",
                        fragment instanceof WledLayoutStepFragment);
                List<String> titles = new ArrayList<>();
                for (GuidedAction action : ((WledLayoutStepFragment) fragment).getActions()) {
                    titles.add(String.valueOf(action.getTitle()));
                }
                assertTrue(titles.toString(), titles.contains("Led top"));
                assertTrue(titles.toString(), titles.contains("RGBW white mode"));
                assertTrue(titles.toString(), titles.contains("Load 274 first-unit preset"));
                assertTrue(titles.toString(), titles.contains("Run 10-second test patterns"));
            });
            onView(withText("Led top")).check(matches(isDisplayed()));
        }
    }
}
