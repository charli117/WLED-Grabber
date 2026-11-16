package com.abrenoch.wledgrabber.common.views;

import android.content.Context;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PRE-1 device-level view checks on the real Android runtime: D-pad moves the
 * physical selection, both ends are reachable, the resize path keeps the
 * selection identity, and an error state blocks input.
 */
public class LedLayoutPreviewInstrumentedTest {

    private static CompiledLayout layout274() {
        LedLayoutConfig config = new LedLayoutConfig(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result =
                new ClassicLedLayout().compile(config, 274, 640, 360);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static LedLayoutPreviewView newView() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final AtomicReference<LedLayoutPreviewView> view = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> view.set(new LedLayoutPreviewView(context)));
        return view.get();
    }

    private static void onMain(Runnable body) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(body);
    }

    private static void press(LedLayoutPreviewView view, int keyCode) {
        view.onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
    }

    @Test
    public void dpadWalksToTheLastLedAndBack() {
        LedLayoutPreviewView view = newView();
        onMain(() -> {
            view.setLayout(layout274(), 0);
            assertEquals(0, view.selectedPhysical());
            for (int i = 0; i < 300; i++) {
                press(view, KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            assertEquals("selection clamps at the last physical LED",
                    273, view.selectedPhysical());
            for (int i = 0; i < 300; i++) {
                press(view, KeyEvent.KEYCODE_DPAD_LEFT);
            }
            assertEquals("selection clamps at physical 0", 0, view.selectedPhysical());
        });
    }

    @Test
    public void selectionSurvivesResize() {
        LedLayoutPreviewView view = newView();
        onMain(() -> {
            view.setLayout(layout274(), 161);
            assertEquals(161, view.selectedPhysical());
            // Re-applying a compiled layout is the resize path; the user's
            // selection must survive instead of snapping back to physical 0.
            view.setLayout(layout274(), view.selectedPhysical());
            assertEquals(161, view.selectedPhysical());
        });
    }

    @Test
    public void errorStateBlocksDpadSelection() {
        LedLayoutPreviewView view = newView();
        onMain(() -> {
            view.setError("invalid layout");
            press(view, KeyEvent.KEYCODE_DPAD_RIGHT);
            assertEquals("error state must not select anything",
                    -1, view.selectedPhysical());
        });
    }
}
