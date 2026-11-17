package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.LayoutDraft;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Layout-scoped validation lets the TV editor preview and adjust geometry before
 * the WLED endpoint is configured, without duplicating field ranges.
 */
public class LayoutDraftValidationTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    public void layoutOnlyDraftValidatesWithoutDeviceFields() {
        ValidationResult<LayoutDraft> result =
                validator.validateLayout(ConfigTestData.defaultDraft());

        assertTrue(result.errors().toString(), result.isValid());
        LayoutDraft draft = result.value();
        assertEquals(60, draft.layout().top());
        assertEquals(34, draft.layout().right());
        assertEquals(0, draft.physicalLedCount());
        assertFalse(draft.physicalConfirmed());
    }

    @Test
    public void physicalCountIsPreservedAsAnObservationDraft() {
        ValidationResult<LayoutDraft> result =
                validator.validateLayout(ConfigTestData.validDraftWithPhysicalLeds(274));

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(274, result.value().physicalLedCount());
        assertTrue(result.value().physicalConfirmed());
    }

    @Test
    public void layoutFieldErrorsDoNotReportDeviceFields() {
        ConfigDraft draft = ConfigTestData.defaultDraft().set(ConfigFields.LED_TOP, "");
        ValidationResult<LayoutDraft> result = validator.validateLayout(draft);

        assertFalse(result.isValid());
        assertEquals(FieldError.Code.EMPTY, result.errorFor(ConfigFields.LED_TOP).code());
        assertNull(result.errorFor(ConfigFields.WLED_HOST));
        assertNull(result.errorFor(ConfigFields.WLED_RGBW_MODE));
    }

    @Test
    public void crossFieldGapRulesStillApply() {
        ConfigDraft draft = ConfigTestData.validDraft()
                .set(ConfigFields.LED_GAP_MODE, "skip")
                .set(ConfigFields.LED_GAP_LENGTH, "274");
        ValidationResult<LayoutDraft> result = validator.validateLayout(draft);

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.LED_GAP_LENGTH, FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void inputPositionIsNormalizedWithFloorMod() {
        ConfigDraft draft = ConfigTestData.validDraft()
                .set(ConfigFields.LED_INPUT_POSITION, "274");
        ValidationResult<LayoutDraft> result = validator.validateLayout(draft);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(0, result.value().layout().inputPosition());
    }
}
