package com.abrenoch.wledgrabber.common.led;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * First-unit 274 layout from the technical design (§4.5). It is a layout preset
 * only: the physical count and device endpoint remain explicit user confirmations.
 *
 * <p>Keeping the values here prevents the TV editor, tests and documentation from
 * drifting apart.</p>
 */
public final class FirstUnitPreset {
    public static final int PHYSICAL_LED_COUNT = 274;

    private FirstUnitPreset() {
    }

    /** @return the layout fields in {@link ConfigFields} keys, unmodifiable. */
    public static Map<String, String> layoutValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ConfigFields.LED_TOP, "86");
        values.put(ConfigFields.LED_BOTTOM, "90");
        values.put(ConfigFields.LED_LEFT, "49");
        values.put(ConfigFields.LED_RIGHT, "49");
        values.put(ConfigFields.LED_GAP_LENGTH, "0");
        values.put(ConfigFields.LED_GAP_POSITION, "0");
        values.put(ConfigFields.LED_INPUT_POSITION, "161");
        values.put(ConfigFields.LED_REVERSE, "true");
        values.put(ConfigFields.LED_GAP_MODE, "skip");
        values.put(ConfigFields.LED_H_DEPTH, "8");
        values.put(ConfigFields.LED_V_DEPTH, "5");
        values.put(ConfigFields.LED_OVERLAP, "0");
        values.put(ConfigFields.LED_EDGE_GAP, "0");
        values.put(ConfigFields.LED_CORNER_TL_H, "0");
        values.put(ConfigFields.LED_CORNER_TL_V, "0");
        values.put(ConfigFields.LED_CORNER_TR_H, "100");
        values.put(ConfigFields.LED_CORNER_TR_V, "0");
        values.put(ConfigFields.LED_CORNER_BR_H, "100");
        values.put(ConfigFields.LED_CORNER_BR_V, "100");
        values.put(ConfigFields.LED_CORNER_BL_H, "0");
        values.put(ConfigFields.LED_CORNER_BL_V, "100");
        return Collections.unmodifiableMap(values);
    }
}
