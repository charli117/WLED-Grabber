package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.LayoutDraft;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigStorage;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.StoredConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The D-pad stepper seam: candidate values go through the shared validator. */
public class SettingsDraftControllerLayoutTest {

    private static final class FakeStorage implements ConfigStorage {
        private Map<String, String> values;

        FakeStorage(Map<String, String> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public StoredConfig load() {
            return new StoredConfig(values, 0);
        }

        @Override
        public boolean writeAll(Map<String, String> values, long revision) {
            this.values = new LinkedHashMap<>(values);
            return true;
        }
    }

    private static SettingsDraftController controller(Map<String, String> values) {
        SettingsDraftController controller = new SettingsDraftController(
                new ConfigRepository(new FakeStorage(values), new ConfigValidator()));
        assertTrue(controller.load().isValid());
        return controller;
    }

    @Test
    public void adjustIncrementsAndKeepsTheDraftValid() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());

        assertTrue(controller.adjustLayoutField(ConfigFields.LED_TOP, 1));

        assertEquals("87", controller.draft().get(ConfigFields.LED_TOP));
        ValidationResult<LayoutDraft> layout = controller.validateLayout();
        assertTrue(layout.errors().toString(), layout.isValid());
        assertEquals(87, layout.value().layout().top());
    }

    @Test
    public void adjustRejectsOutOfRangeAndRestoresThePreviousValue() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());
        controller.set(ConfigFields.LED_H_DEPTH, "1");

        assertFalse(controller.adjustLayoutField(ConfigFields.LED_H_DEPTH, -1));

        assertEquals("1", controller.draft().get(ConfigFields.LED_H_DEPTH));
        assertTrue(controller.validateLayout().isValid());
    }

    @Test
    public void adjustWorksBeforeTheDeviceEndpointIsConfigured() {
        SettingsDraftController controller = controller(ConfigTestData.defaultDraft().asMap());

        assertTrue(controller.adjustLayoutField(ConfigFields.LED_TOP, 1));

        assertEquals("61", controller.draft().get(ConfigFields.LED_TOP));
    }

    @Test
    public void setLayoutFieldRejectsInvalidInputAndRestoresThePreviousValue() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());

        ValidationResult<LayoutDraft> result =
                controller.setLayoutField(ConfigFields.LED_TOP, "1001");

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.LED_TOP, FieldError.Code.OUT_OF_RANGE));
        assertEquals("86", controller.draft().get(ConfigFields.LED_TOP));
    }

    @Test
    public void setLayoutFieldAcceptsAValidExactValue() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());

        ValidationResult<LayoutDraft> result =
                controller.setLayoutField(ConfigFields.LED_TOP, "87");

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals("87", controller.draft().get(ConfigFields.LED_TOP));
    }

    @Test
    public void adjustmentThatRepairsACrossFieldErrorIsAccepted() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());
        controller.set(ConfigFields.LED_GAP_MODE, "skip");
        controller.set(ConfigFields.LED_GAP_LENGTH, "274");
        assertFalse(controller.validateLayout().isValid());

        assertTrue(controller.adjustLayoutField(ConfigFields.LED_GAP_LENGTH, -1));

        assertEquals("273", controller.draft().get(ConfigFields.LED_GAP_LENGTH));
        assertTrue(controller.validateLayout().isValid());
    }

    @Test
    public void groupUpdateIsAllOrNothing() {
        SettingsDraftController controller = controller(ConfigTestData.validDraft().asMap());
        java.util.Map<String, String> invalid = new java.util.LinkedHashMap<>();
        invalid.put(ConfigFields.LED_TOP, "100");
        invalid.put(ConfigFields.LED_GAP_LENGTH, "300");

        ValidationResult<LayoutDraft> rejected = controller.setLayoutFields(invalid);

        assertFalse(rejected.isValid());
        assertEquals("86", controller.draft().get(ConfigFields.LED_TOP));
        assertEquals("0", controller.draft().get(ConfigFields.LED_GAP_LENGTH));

        java.util.Map<String, String> valid = new java.util.LinkedHashMap<>();
        valid.put(ConfigFields.LED_TOP, "100");
        valid.put(ConfigFields.LED_BOTTOM, "100");
        ValidationResult<LayoutDraft> accepted = controller.setLayoutFields(valid);

        assertTrue(accepted.errors().toString(), accepted.isValid());
        assertEquals("100", controller.draft().get(ConfigFields.LED_TOP));
        assertEquals("100", controller.draft().get(ConfigFields.LED_BOTTOM));
    }
}
