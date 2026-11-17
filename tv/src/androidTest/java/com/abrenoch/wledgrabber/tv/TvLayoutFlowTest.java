package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.leanback.widget.GuidedAction;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.tv.activities.LayoutEditorActivity;
import com.abrenoch.wledgrabber.tv.activities.LayoutSettingsActivity;
import com.abrenoch.wledgrabber.tv.fragments.settings.WledLayoutStepFragment;
import com.abrenoch.wledgrabber.tv.views.LayoutValueRow;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TV-2 host split: the live editor owns geometry, preview and strip verification;
 * the guided step keeps the advanced capture/RGBW options.
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
    public void editorShowsLivePreviewShapeRowsAndVerifyActions() {
        final String[] expected = new String[5];
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                assertNotNull("live preview missing",
                        activity.findViewById(com.abrenoch.wledgrabber.tv.R.id
                                .layout_editor_preview));
                List<String> titles = rowTitles(activity);
                expected[0] = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_led_top);
                expected[1] = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_led_corner_br_v);
                expected[2] = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.wled_layout_preset_274);
                expected[3] = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.wled_layout_test_pattern);
                expected[4] = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_led_input_position);
                assertTrue(titles.toString(), titles.contains(expected[0]));
                assertTrue(titles.toString(), titles.contains(expected[1]));
                assertTrue(titles.toString(), titles.contains(expected[2]));
                assertTrue(titles.toString(), titles.contains(expected[3]));
                assertTrue(titles.toString(), titles.contains(expected[4]));
            });
            onView(withText(expected[0])).check(matches(isDisplayed()));
        }
    }

    @Test
    public void advancedStepKeepsCaptureAndQualityButNotGeometry() {
        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)), new ConfigValidator());
        assertTrue(repository.save(WledTestSupport.validDraft()
                .set(ConfigFields.WLED_RGBW_MODE, "rgbw")).isValid());

        try (ActivityScenario<LayoutSettingsActivity> scenario =
                     ActivityScenario.launch(LayoutSettingsActivity.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = activity.getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                assertTrue("advanced step fragment missing",
                        fragment instanceof WledLayoutStepFragment);
                List<String> titles = new ArrayList<>();
                for (GuidedAction action : ((WledLayoutStepFragment) fragment).getActions()) {
                    titles.add(String.valueOf(action.getTitle()));
                }
                String whiteMode = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.wled_layout_white_mode);
                String captureSize = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_capture_max_dimension);
                String qualityCrop = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_quality_crop);
                String top = activity.getString(
                        com.abrenoch.wledgrabber.common.R.string.field_led_top);
                assertTrue(titles.toString(), titles.contains(whiteMode));
                assertTrue(titles.toString(), titles.contains(captureSize));
                assertTrue(titles.toString(), titles.contains(qualityCrop));
                assertFalse("geometry moved to the live editor: " + titles,
                        titles.contains(top));
            });
        }
    }

    private static List<String> rowTitles(Activity activity) {
        ViewGroup container = activity.findViewById(
                com.abrenoch.wledgrabber.tv.R.id.layout_editor_rows);
        List<String> titles = new ArrayList<>();
        collect(container, titles);
        return titles;
    }

    private static void collect(View view, List<String> titles) {
        if (view instanceof LayoutValueRow) {
            titles.add(String.valueOf(((LayoutValueRow) view).title()));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), titles);
            }
        }
    }
}
