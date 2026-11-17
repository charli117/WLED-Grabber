package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CaptureResizePlannerTest {

    private static LedLayoutConfig config() {
        return new ConfigValidator().validateForSave(ConfigTestData.validDraft())
                .value().layout();
    }

    @Test
    public void unchangedSizeDoesNotRecompileOrAdvanceGeneration() {
        LedLayoutConfig config = config();
        ValidationResult<CaptureResizePlanner.Plan> result = CaptureResizePlanner.plan(
                config, 274, 1280, 720, 640, 640, 360, 7);

        assertTrue(result.errors().toString(), result.isValid());
        assertFalse(result.value().changed());
        assertEquals(640, result.value().width());
        assertEquals(360, result.value().height());
        assertEquals(7, result.value().generation());
    }

    @Test
    public void rotationRecompilesTheLayoutAtTheNewSize() {
        LedLayoutConfig config = config();
        ValidationResult<CaptureResizePlanner.Plan> result = CaptureResizePlanner.plan(
                config, 274, 720, 1280, 640, 640, 360, 3);

        assertTrue(result.errors().toString(), result.isValid());
        CaptureResizePlanner.Plan plan = result.value();
        assertTrue(plan.changed());
        assertEquals(360, plan.width());
        assertEquals(640, plan.height());
        assertEquals(4, plan.generation());
        assertNotNull(plan.layout());
        assertEquals(360, plan.layout().captureWidth());
        assertEquals(640, plan.layout().captureHeight());
        assertEquals(4, plan.layout().generation());
        assertEquals(274, plan.layout().mapping().physicalLedCount());
    }

    @Test
    public void longEdgeBudgetIsAppliedToTheNewSourceSize() {
        ValidationResult<CaptureResizePlanner.Plan> result = CaptureResizePlanner.plan(
                config(), 274, 1920, 1080, 640, 640, 360, 0);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(640, result.value().width());
        assertEquals(360, result.value().height());
        assertFalse(result.value().changed());
    }
}
