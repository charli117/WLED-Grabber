package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.ValidatedConfig;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigValidatorTest {
    private final ConfigValidator validator = new ConfigValidator();

    @Test
    public void validDraftProducesSnapshotAndStartableConfig() {
        ValidationResult<ConfigSnapshot> saved = validator.validateForSave(ConfigTestData.validDraft());

        assertTrue(saved.errors().toString(), saved.isValid());
        ConfigSnapshot snapshot = saved.value();
        assertEquals(ConfigSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion());
        assertEquals(0, snapshot.revision());
        assertEquals(274, snapshot.device().physicalLedCount());
        assertEquals(274, snapshot.layout().totalLedCount());
        assertEquals(274, snapshot.layout().effectiveLedCount());
        assertEquals(161, snapshot.layout().inputPosition());

        ValidationResult<ValidatedConfig> started = validator.validateForStart(snapshot);
        assertTrue(started.errors().toString(), started.isValid());
        assertNotNull(started.value());
    }

    @Test
    public void defaultDraftIsSaveableButNotStartable() {
        ValidationResult<ConfigSnapshot> saved = validator.validateForSave(ConfigTestData.defaultDraft());
        assertTrue(saved.errors().toString(), saved.isValid());

        ConfigSnapshot snapshot = saved.value();
        assertEquals(60, snapshot.layout().top());
        assertEquals(60, snapshot.layout().bottom());
        assertEquals(34, snapshot.layout().left());
        assertEquals(34, snapshot.layout().right());
        assertEquals(8, snapshot.layout().horizontalDepth());
        assertEquals(5, snapshot.layout().verticalDepth());
        assertEquals(640, snapshot.capture().maxDimension());
        assertEquals(30, snapshot.capture().frameRate());

        ValidationResult<ValidatedConfig> started = validator.validateForStart(snapshot);
        assertFalse(started.isValid());
        assertNotNull(started.errorFor(ConfigFields.WLED_HOST));
        assertNotNull(started.errorFor(ConfigFields.WLED_PHYSICAL_LEDS));
        assertNotNull(started.errorFor(ConfigFields.WLED_RGBW_MODE));
    }

    @Test
    public void emptyNumericValueReturnsStructuredError() {
        ValidationResult<ConfigSnapshot> result = validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.LED_TOP, ""));

        assertFalse(result.isValid());
        assertEquals(ConfigFields.LED_TOP, result.errorFor(ConfigFields.LED_TOP).field());
        assertEquals(com.abrenoch.wledgrabber.common.util.FieldError.Code.EMPTY,
                result.errorFor(ConfigFields.LED_TOP).code());
    }

    @Test
    public void nonNumericAndHugeValuesDoNotThrow() {
        ValidationResult<ConfigSnapshot> nonNumeric = validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.FRAME_RATE, "abc"));
        assertFalse(nonNumeric.isValid());
        assertEquals(com.abrenoch.wledgrabber.common.util.FieldError.Code.NOT_A_NUMBER,
                nonNumeric.errorFor(ConfigFields.FRAME_RATE).code());

        ValidationResult<ConfigSnapshot> huge = validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.LED_TOP, "999999999999999999999"));
        assertFalse(huge.isValid());
        assertNotNull(huge.errorFor(ConfigFields.LED_TOP));
    }

    @Test
    public void outOfRangeValuesAreRejected() {
        assertFalse(validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.FRAME_RATE, "0")).isValid());
        assertFalse(validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.FRAME_RATE, "61")).isValid());
        assertFalse(validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.LED_TOP, "-1")).isValid());
        assertFalse(validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.CAPTURE_MAX_DIMENSION, "641")).isValid());
        assertFalse(validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.LED_H_DEPTH, "0")).isValid());
    }

    @Test
    public void skipGapThatRemovesEveryLedIsRejected() {
        ValidationResult<ConfigSnapshot> result = validator.validateForSave(
                ConfigTestData.validDraft()
                        .set(ConfigFields.LED_GAP_MODE, "skip")
                        .set(ConfigFields.LED_GAP_LENGTH, "274"));

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.LED_GAP_LENGTH,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void insufficientPhysicalCountIsSaveableButNotStartable() {
        ValidationResult<ConfigSnapshot> saved = validator.validateForSave(
                ConfigTestData.validDraftWithPhysicalLeds(100));
        assertTrue(saved.errors().toString(), saved.isValid());

        ValidationResult<ValidatedConfig> started = validator.validateForStart(saved.value());
        assertFalse(started.isValid());
        assertTrue(started.hasError(ConfigFields.WLED_PHYSICAL_LEDS,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void rgbwWithDnrgbIsRejected() {
        ValidationResult<ConfigSnapshot> result = validator.validateForSave(
                ConfigTestData.validDraft()
                        .set(ConfigFields.WLED_RGBW_MODE, "rgbw")
                        .set(ConfigFields.WLED_PROTOCOL, "dnrgb"));

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.WLED_PROTOCOL,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void schemaMismatchIsRejected() {
        ValidationResult<ConfigSnapshot> result = validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.SCHEMA_VERSION, "2"));

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.SCHEMA_VERSION,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.UNSUPPORTED_SCHEMA));
    }

    @Test
    public void whiteModeDefaultsAndInvalidValueAreStructured() {
        ConfigSnapshot defaults = validator.validateForSave(ConfigTestData.validDraft()).value();
        assertEquals(com.abrenoch.wledgrabber.common.led.WhiteMode.MIN_SUBTRACT,
                defaults.device().whiteMode());

        ConfigSnapshot preserve = validator.validateForSave(ConfigTestData.validDraft()
                .set(ConfigFields.WLED_RGBW_WHITE_MODE, "preserve")).value();
        assertEquals(com.abrenoch.wledgrabber.common.led.WhiteMode.PRESERVE,
                preserve.device().whiteMode());

        ValidationResult<ConfigSnapshot> invalid = validator.validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.WLED_RGBW_WHITE_MODE, "invalid"));
        assertFalse(invalid.isValid());
        assertTrue(invalid.hasError(ConfigFields.WLED_RGBW_WHITE_MODE,
                com.abrenoch.wledgrabber.common.util.FieldError.Code.INVALID_VALUE));
    }

    @Test
    public void inputPositionIsNormalizedWithFloorMod() {
        ConfigDraft draft = ConfigTestData.validDraft().set(ConfigFields.LED_INPUT_POSITION, "274");
        ConfigSnapshot snapshot = validator.validateForSave(draft).value();
        assertEquals(0, snapshot.layout().inputPosition());
    }
}
