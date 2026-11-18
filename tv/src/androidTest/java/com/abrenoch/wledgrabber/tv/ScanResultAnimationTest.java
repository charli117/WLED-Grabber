package com.abrenoch.wledgrabber.tv;

import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.tv.activities.ScanResultActivity;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import nl.dionsegijn.konfetti.KonfettiView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScanResultAnimationTest {
    @Test public void resultIsReadableAndIdleUntilTheNextCelebration() {
        Intent result = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                ScanResultActivity.class).putExtra(ScanResultActivity.EXTRA_RESULT_HOST_NAME, "192.0.2.1");
        try (ActivityScenario<ScanResultActivity> scenario = ActivityScenario.launch(result)) {
            scenario.onActivity(activity -> assertEquals(1f,
                    activity.findViewById(R.id.scanResultHostName).getAlpha(), 0f));
            AtomicBoolean active = new AtomicBoolean(true);
            long deadline = SystemClock.uptimeMillis() + 6000;
            do {
                SystemClock.sleep(100);
                scenario.onActivity(activity -> active.set(
                        ((KonfettiView) activity.findViewById(R.id.konfetti)).isActive()));
            } while (active.get() && SystemClock.uptimeMillis() < deadline);
            assertFalse("Idle result page should stop emitting particles", active.get());
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.changeColorButton).performClick();
                assertTrue(((KonfettiView) activity.findViewById(R.id.konfetti)).isActive());
            });
        }
    }
}
