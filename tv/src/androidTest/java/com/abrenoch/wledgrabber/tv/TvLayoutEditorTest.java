package com.abrenoch.wledgrabber.tv;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.LayoutAutoDistribute;
import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.network.wled.LayoutProbeSender;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.tv.activities.LayoutEditorActivity;
import com.abrenoch.wledgrabber.tv.views.LayoutValueRow;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TV-2 live editor: D-pad steps update the draft and preview without touching the
 * committed config, auto-distribution uses the confirmed count, and Save keeps the
 * shared repository as the only writer.
 */
@RunWith(AndroidJUnit4.class)
public class TvLayoutEditorTest {

    @Before
    public void setUp() {
        WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
        WledTestSupport.seedConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
    }

    @After
    public void tearDown() {
        LayoutProbeSender.get().stop();
        long deadline = System.currentTimeMillis() + 5000;
        while (OutputSessionCoordinator.get().owner() != OutputOwner.NONE
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        WledTestSupport.clearConfig(InstrumentationRegistry.getInstrumentation()
                .getTargetContext());
    }

    @Test public void openingCalibrationAndBackingOutNeverTakesALeaseOrEditsTheLayout() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario = ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, LayoutEditorActivity.ACTION_CALIBRATION);
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            androidx.test.espresso.Espresso.pressBack();
            scenario.onActivity(activity -> assertTrue(findRow(activity, LayoutEditorActivity.ACTION_CALIBRATION).hasFocus()));
            assertEquals(OutputOwner.NONE, OutputSessionCoordinator.get().owner());
        }
    }

    @Test
    public void dpadStepsTheFocusedFieldWithoutTouchingTheCommittedConfig() {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutValueRow top = findRow(activity, ConfigFields.LED_TOP);
                LayoutValueRow bottom = findRow(activity, ConfigFields.LED_BOTTOM);
                assertNotNull("top row missing", top);
                assertNotNull("bottom row missing", bottom);
                assertEquals("86", String.valueOf(top.displayValue()));
                assertEquals("90", String.valueOf(bottom.displayValue()));

                top.requestFocus();
                assertTrue(top.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)));
                assertEquals("87", String.valueOf(top.displayValue()));
                assertTrue(top.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,
                        keyDown(KeyEvent.KEYCODE_DPAD_LEFT)));
                assertEquals("86", String.valueOf(top.displayValue()));
            });
        }
        ConfigRepository repository = repository();
        assertTrue(repository.load().isValid());
        assertEquals(86, repository.committedSnapshot().layout().top());
    }

    @Test
    public void autoDistributeWritesAllFourSidesForTheConfirmedCount() {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                android.util.DisplayMetrics metrics =
                        activity.getResources().getDisplayMetrics();
                int[] expected = LayoutAutoDistribute.distribute(
                        WledTestSupport.PHYSICAL_LEDS_274,
                        metrics.widthPixels, metrics.heightPixels);

                LayoutValueRow auto = findRow(activity, LayoutEditorActivity.ACTION_AUTO);
                assertNotNull("auto action missing", auto);
                auto.requestFocus();
                assertTrue(auto.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        keyDown(KeyEvent.KEYCODE_DPAD_CENTER)));

                assertEquals(String.valueOf(expected[LayoutAutoDistribute.TOP]),
                        String.valueOf(findRow(activity, ConfigFields.LED_TOP).displayValue()));
                assertEquals(String.valueOf(expected[LayoutAutoDistribute.BOTTOM]),
                        String.valueOf(findRow(activity, ConfigFields.LED_BOTTOM).displayValue()));
                assertEquals(String.valueOf(expected[LayoutAutoDistribute.LEFT]),
                        String.valueOf(findRow(activity, ConfigFields.LED_LEFT).displayValue()));
                assertEquals(String.valueOf(expected[LayoutAutoDistribute.RIGHT]),
                        String.valueOf(findRow(activity, ConfigFields.LED_RIGHT).displayValue()));
            });
        }
    }

    @Test
    public void presetActionRestoresTheFirstUnitLayout() throws Exception {
        ConfigRepository repository = repository();
        assertTrue(repository.save(repository.newDraft()
                .set(ConfigFields.LED_TOP, "60")
                .set(ConfigFields.LED_BOTTOM, "60")
                .set(ConfigFields.LED_LEFT, "34")
                .set(ConfigFields.LED_RIGHT, "34")
                .set(ConfigFields.LED_INPUT_POSITION, "0")
                .set(ConfigFields.LED_REVERSE, "false")).isValid());

        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, LayoutEditorActivity.ACTION_PRESET_274);
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals("86", String.valueOf(
                        findRow(activity, ConfigFields.LED_TOP).displayValue()));
                assertEquals("90", String.valueOf(
                        findRow(activity, ConfigFields.LED_BOTTOM).displayValue()));
                assertEquals("49", String.valueOf(
                        findRow(activity, ConfigFields.LED_LEFT).displayValue()));
                assertEquals("49", String.valueOf(
                        findRow(activity, ConfigFields.LED_RIGHT).displayValue()));
                assertEquals("161", String.valueOf(
                        findRow(activity, ConfigFields.LED_INPUT_POSITION).displayValue()));
                assertEquals(activity.getString(
                                com.abrenoch.wledgrabber.tv.R.string.layout_editor_on),
                        String.valueOf(findRow(activity, ConfigFields.LED_REVERSE)
                                .displayValue()));
            });
        }
    }

    @Test
    public void savePersistsTheAdjustedLayout() {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutValueRow top = findRow(activity, ConfigFields.LED_TOP);
                LayoutValueRow bottom = findRow(activity, ConfigFields.LED_BOTTOM);
                top.requestFocus();
                assertTrue(top.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)));
                assertTrue(bottom.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,
                        keyDown(KeyEvent.KEYCODE_DPAD_LEFT)));

                activity.findViewById(com.abrenoch.wledgrabber.tv.R.id.layout_editor_save)
                        .performClick();
            });
        }
        ConfigRepository repository = repository();
        assertTrue(repository.load().isValid());
        assertEquals(87, repository.committedSnapshot().layout().top());
        assertEquals(89, repository.committedSnapshot().layout().bottom());
    }

    @Test
    public void exactEntryReplacesTheValue() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            scenario.onActivity(activity -> {
                LayoutValueRow top = findRow(activity, ConfigFields.LED_TOP);
                top.requestFocus();
                assertTrue(top.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER,
                        keyDown(KeyEvent.KEYCODE_DPAD_CENTER)));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            androidx.test.espresso.Espresso.onView(
                            androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(
                                    android.widget.EditText.class))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.replaceText("99"),
                            androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            androidx.test.espresso.Espresso.onView(
                            androidx.test.espresso.matcher.ViewMatchers.withText(
                                    android.R.string.ok))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.click());
            scenario.onActivity(activity -> assertEquals("99",
                    String.valueOf(findRow(activity, ConfigFields.LED_TOP).displayValue())));
        }
    }

    @Test
    public void physicalCountStartsFromTheEffectiveRingWhenUnconfirmed() {
        ConfigRepository repository = repository();
        assertTrue(repository.save(repository.newDraft()
                .set(ConfigFields.WLED_PHYSICAL_LEDS, "0")).isValid());

        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutValueRow physical = findRow(activity, ConfigFields.WLED_PHYSICAL_LEDS);
                assertEquals("0", String.valueOf(physical.displayValue()));
                physical.requestFocus();
                assertTrue(physical.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT)));
                assertEquals("274", String.valueOf(physical.displayValue()));
            });
        }
    }

    @Test
    public void focusReturnsFromSaveToTheLastRow() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, ConfigFields.LED_CORNER_BL_V);
            // Reach Save the way a remote user does, then walk back up.
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue("save button should take focus",
                    activity.findViewById(com.abrenoch.wledgrabber.tv.R.id.layout_editor_save)
                            .isFocused()));
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue("last row should regain focus",
                    findRow(activity, ConfigFields.LED_CORNER_BL_V).isFocused()));
        }
    }

    @Test
    public void verifySectionDownReachesTheCornerSection() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, LayoutEditorActivity.ACTION_TEST);
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                android.view.View focus = activity.getCurrentFocus();
                assertTrue("Down from Test must enter the corner section, focus=" + focus,
                        findRow(activity, ConfigFields.LED_CORNER_TL_H).isFocused());
            });
        }
    }

    @Test
    public void holdingConfirmTogglesOnlyOncePerPress() {
        try (ActivityScenario<LayoutEditorActivity> scenario = ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutValueRow reverse = findRow(activity, ConfigFields.LED_REVERSE);
                assertTrue(reverse.requestFocus());
                String initial = reverse.displayValue().toString();
                int key = KeyEvent.KEYCODE_DPAD_CENTER;
                long down = android.os.SystemClock.uptimeMillis();
                assertTrue(reverse.dispatchKeyEvent(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, key, 0)));
                String toggled = reverse.displayValue().toString();
                assertFalse(initial.equals(toggled));
                assertTrue(reverse.dispatchKeyEvent(new KeyEvent(down, down + 250, KeyEvent.ACTION_DOWN, key, 1)));
                assertEquals(toggled, reverse.displayValue().toString());
                assertTrue(reverse.dispatchKeyEvent(new KeyEvent(down, down + 260, KeyEvent.ACTION_UP, key, 0)));
                assertTrue(reverse.dispatchKeyEvent(keyDown(key)));
                assertEquals(initial, reverse.displayValue().toString());
            });
        }
    }

    @Test
    public void holdingAToggleDoesNotFlipOnKeyRepeat() {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                LayoutValueRow reverse = findRow(activity, ConfigFields.LED_REVERSE);
                String on = activity.getString(
                        com.abrenoch.wledgrabber.tv.R.string.layout_editor_on);
                String off = activity.getString(
                        com.abrenoch.wledgrabber.tv.R.string.layout_editor_off);
                assertEquals(on, String.valueOf(reverse.displayValue()));

                long now = android.os.SystemClock.uptimeMillis();
                KeyEvent repeat = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT, 3);
                reverse.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat);
                assertEquals("repeat must not flip the toggle",
                        on, String.valueOf(reverse.displayValue()));

                reverse.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,
                        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT));
                assertEquals(off, String.valueOf(reverse.displayValue()));
            });
        }
    }

    @Test
    public void saveBlocksPhysicalCountBelowTheLayout() {
        ConfigRepository repository = repository();
        assertTrue(repository.save(repository.newDraft()
                .set(ConfigFields.WLED_PHYSICAL_LEDS, "100")).isValid());

        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(com.abrenoch.wledgrabber.tv.R.id.layout_editor_save)
                        .performClick();
                assertFalse("save must not accept physical < effective",
                        activity.isFinishing());
            });
        }
        assertEquals(100, repository().load().value().device().physicalLedCount());
    }

    @Test
    public void saveRejectsACompileInvalidLayout() {
        ConfigRepository repository = repository();
        assertTrue(repository.save(repository.newDraft()
                .set(ConfigFields.LED_CORNER_TR_H, "0")).isValid());
        long revision = repository.committedSnapshot().revision();

        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(com.abrenoch.wledgrabber.tv.R.id.layout_editor_save)
                        .performClick();
                assertFalse("save must stay on the editor for invalid geometry",
                        activity.isFinishing());
            });
        }
        assertEquals("invalid geometry must not write a new revision",
                revision, repository().load().value().revision());
    }

    @Test
    public void probeRowsToggleAndSwitchWithoutLeavingAStaleLatch()
            throws InterruptedException {
        // Loopback keeps the UI test off the physical lab strip; the probe only
        // needs a valid endpoint, not a reachable WLED for this assertion.
        ConfigRepository repo = repository();
        assertTrue(repo.save(repo.newDraft()
                .set(ConfigFields.WLED_HOST, "127.0.0.1")).isValid());
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            scenario.onActivity(activity -> {
                String on = activity.getString(
                        com.abrenoch.wledgrabber.tv.R.string.layout_editor_on);
                String off = activity.getString(
                        com.abrenoch.wledgrabber.tv.R.string.layout_editor_off);
                LayoutValueRow top = findRow(activity, LayoutEditorActivity.ACTION_PROBE_TOP);
                LayoutValueRow right = findRow(activity, LayoutEditorActivity.ACTION_PROBE_RIGHT);
                assertNotNull("top probe missing", top);
                assertNotNull("right probe missing", right);
                assertEquals(off, String.valueOf(top.displayValue()));

                top.performClick();
                assertEquals(on, String.valueOf(top.displayValue()));
                assertTrue("probe state must reach accessibility",
                        String.valueOf(top.getContentDescription()).contains(on));
                assertEquals(off, String.valueOf(right.displayValue()));

                right.performClick();
                assertEquals("switching must turn the previous edge off",
                        off, String.valueOf(top.displayValue()));
                assertEquals(on, String.valueOf(right.displayValue()));

                right.performClick();
                assertEquals(off, String.valueOf(right.displayValue()));
            });
        }
        long deadline = System.currentTimeMillis() + 5000;
        while (OutputSessionCoordinator.get().owner() != OutputOwner.NONE
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals("closing the editor must release the TEST lease",
                OutputOwner.NONE, OutputSessionCoordinator.get().owner());
    }

    @Test
    public void dpadDownMovesFocusToTheNextRow() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, ConfigFields.WLED_PHYSICAL_LEDS);
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue("auto action should take focus",
                    findRow(activity, LayoutEditorActivity.ACTION_AUTO).isFocused()));
        }
    }

    @Test
    public void focusReachesSaveAfterTheLastRow() throws Exception {
        try (ActivityScenario<LayoutEditorActivity> scenario =
                     ActivityScenario.launch(LayoutEditorActivity.class)) {
            awaitWindowFocus(scenario);
            focusRow(scenario, ConfigFields.LED_CORNER_BL_V);
            // Inject from the test thread so ViewRootImpl performs real focus
            // navigation instead of dispatching straight to the focused view.
            InstrumentationRegistry.getInstrumentation()
                    .sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> assertTrue("save button should receive focus",
                    activity.findViewById(com.abrenoch.wledgrabber.tv.R.id.layout_editor_save)
                            .isFocused()));
        }
    }

    /**
     * ActivityScenario reports RESUMED before the window owns input focus; key
     * injection before that lands outside the activity and makes focus tests flaky.
     */
    private static void awaitWindowFocus(ActivityScenario<LayoutEditorActivity> scenario)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] focused = new boolean[1];
            scenario.onActivity(activity -> focused[0] = activity.hasWindowFocus());
            if (focused[0]) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private static KeyEvent keyDown(int keyCode) {
        return new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
    }

    /**
     * A newly launched ActivityScenario can report RESUMED before the window and
     * its children can accept focus; retry the focus request instead of racing it.
     */
    private static void focusRow(ActivityScenario<LayoutEditorActivity> scenario, String key)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] focused = new boolean[1];
            scenario.onActivity(activity -> {
                LayoutValueRow row = findRow(activity, key);
                focused[0] = row != null && row.requestFocus();
            });
            if (focused[0]) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("row did not take focus: " + key);
    }

    private static ConfigRepository repository() {
        return new ConfigRepository(new PreferencesConfigStorage(new Preferences(
                InstrumentationRegistry.getInstrumentation().getTargetContext())),
                new ConfigValidator());
    }

    private static LayoutValueRow findRow(Activity activity, String fieldKey) {
        ViewGroup container = activity.findViewById(
                com.abrenoch.wledgrabber.tv.R.id.layout_editor_rows);
        return findRow(container, fieldKey);
    }

    private static LayoutValueRow findRow(View view, String fieldKey) {
        if (view instanceof LayoutValueRow
                && fieldKey.equals(((LayoutValueRow) view).fieldKey())) {
            return (LayoutValueRow) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LayoutValueRow found = findRow(group.getChildAt(i), fieldKey);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
