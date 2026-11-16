package com.abrenoch.wledgrabber.common.fixtures;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Compiles the shared fixtures and JSON assets into the Android instrumentation
 * APK. The same files are consumed by the JVM tests; no fixture is duplicated.
 */
public class FixtureAvailabilityTest {

    @Test
    public void sharedFixtureClassesAndAssetsAreAvailable() throws Exception {
        assertEquals(274, LayoutFixture.COUNT_274);
        assertEquals(160, LayoutFixture.slot274(0));

        RgbaPlaneFixture plane = RgbaPlaneFixture.tightlyPacked(2, 2);
        assertEquals(0x112b61ff, plane.pixelAt(1, 0));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (InputStream input = context.getAssets().open("fixtures/wled-info.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            assertTrue(output.toString("UTF-8").contains("\"ver\":\"0.15.1\""));
        }
    }
}
