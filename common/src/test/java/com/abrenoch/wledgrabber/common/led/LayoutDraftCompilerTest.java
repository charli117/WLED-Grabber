package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Draft-to-preview compilation shared by the TV editor and probe actions. */
public class LayoutDraftCompilerTest {

    @Test
    public void compilesTheCommittedShapeFromADraft() {
        ValidationResult<CompiledLayout> result = LayoutDraftCompiler.compileForPreview(
                ConfigTestData.validDraft(), 640, 360);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(274, result.value().count());
        assertEquals(274, result.value().mapping().physicalLedCount());
        assertEquals(160, result.value().mapping().physicalToLogical(0));
    }

    @Test
    public void unknownPhysicalCountFallsBackToTheEffectiveRing() {
        ValidationResult<CompiledLayout> result = LayoutDraftCompiler.compileForPreview(
                ConfigTestData.defaultDraft(), 640, 360);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(188, result.value().count());
        assertEquals(188, result.value().mapping().physicalLedCount());
    }

    @Test
    public void previewKeepsGeometryWhenPhysicalCountIsBelowTheLayout() {
        ValidationResult<CompiledLayout> result = LayoutDraftCompiler.compileForPreview(
                ConfigTestData.validDraftWithPhysicalLeds(100), 640, 360);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(274, result.value().count());
        assertEquals(274, result.value().mapping().physicalLedCount());
    }

    @Test
    public void layoutErrorsSurfaceBeforeCompiling() {
        ValidationResult<CompiledLayout> result = LayoutDraftCompiler.compileForPreview(
                ConfigTestData.validDraft().set(ConfigFields.LED_TOP, ""), 640, 360);

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.LED_TOP,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.EMPTY));
    }
}
