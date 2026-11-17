package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pins the 274 first-unit preset shared by the TV editor and the device tests. */
public class FirstUnitPresetTest {

    @Test
    public void matchesTheFirstUnitLayoutFromTheDesign() {
        Map<String, String> values = FirstUnitPreset.layoutValues();

        assertEquals("86", values.get(ConfigFields.LED_TOP));
        assertEquals("90", values.get(ConfigFields.LED_BOTTOM));
        assertEquals("49", values.get(ConfigFields.LED_LEFT));
        assertEquals("49", values.get(ConfigFields.LED_RIGHT));
        assertEquals("161", values.get(ConfigFields.LED_INPUT_POSITION));
        assertEquals("true", values.get(ConfigFields.LED_REVERSE));
        assertEquals("skip", values.get(ConfigFields.LED_GAP_MODE));
        assertEquals(274, FirstUnitPreset.PHYSICAL_LED_COUNT);
    }

    @Test
    public void presetProducesAValidLayoutDraft() {
        ConfigDraft draft = ConfigTestData.validDraft()
                .set(ConfigFields.WLED_PHYSICAL_LEDS, "0");
        for (Map.Entry<String, String> entry : FirstUnitPreset.layoutValues().entrySet()) {
            draft.set(entry.getKey(), entry.getValue());
        }

        ValidationResult<LayoutDraft> result = new ConfigValidator().validateLayout(draft);

        assertTrue(result.errors().toString(), result.isValid());
        assertEquals(274, result.value().layout().totalLedCount());
        assertEquals(161, result.value().layout().inputPosition());
    }
}
