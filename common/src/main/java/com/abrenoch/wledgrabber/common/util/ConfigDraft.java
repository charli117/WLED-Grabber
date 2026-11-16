package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.CaptureConfig;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable raw-value draft used by the validator and UI adapters. */
public final class ConfigDraft {
    private final Map<String, String> values = new LinkedHashMap<>();

    public ConfigDraft() {
    }

    public ConfigDraft(Map<String, String> source) {
        if (source != null) {
            values.putAll(source);
        }
    }

    public String get(String key) {
        return values.get(key);
    }

    public ConfigDraft set(String key, String value) {
        values.put(key, value);
        return this;
    }

    public ConfigDraft copy() {
        return new ConfigDraft(values);
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ConfigDraft from(ConfigSnapshot snapshot) {
        ConfigDraft draft = new ConfigDraft();
        DeviceEndpoint device = snapshot.device();
        LedLayoutConfig layout = snapshot.layout();
        CaptureConfig capture = snapshot.capture();

        draft.set(ConfigFields.SCHEMA_VERSION, Integer.toString(snapshot.schemaVersion()));
        draft.set(ConfigFields.WLED_HOST, device.host());
        draft.set(ConfigFields.WLED_HTTP_PORT, Integer.toString(device.httpPort()));
        draft.set(ConfigFields.WLED_UDP_PORT, Integer.toString(device.udpPort()));
        draft.set(ConfigFields.WLED_DDP_PORT, Integer.toString(device.ddpPort()));
        draft.set(ConfigFields.WLED_PROTOCOL, device.protocol().name().toLowerCase());
        draft.set(ConfigFields.WLED_RGBW_MODE, device.rgbwMode().name().toLowerCase());
        draft.set(ConfigFields.WLED_RGBW_WHITE_MODE, device.whiteMode().name().toLowerCase());
        draft.set(ConfigFields.WLED_PHYSICAL_LEDS, Integer.toString(device.physicalLedCount()));

        draft.set(ConfigFields.LED_TOP, Integer.toString(layout.top()));
        draft.set(ConfigFields.LED_BOTTOM, Integer.toString(layout.bottom()));
        draft.set(ConfigFields.LED_LEFT, Integer.toString(layout.left()));
        draft.set(ConfigFields.LED_RIGHT, Integer.toString(layout.right()));
        draft.set(ConfigFields.LED_GAP_LENGTH, Integer.toString(layout.gapLength()));
        draft.set(ConfigFields.LED_GAP_POSITION, Integer.toString(layout.gapPosition()));
        draft.set(ConfigFields.LED_INPUT_POSITION, Integer.toString(layout.inputPosition()));
        draft.set(ConfigFields.LED_REVERSE, Boolean.toString(layout.reverse()));
        draft.set(ConfigFields.LED_GAP_MODE, layout.gapMode().name().toLowerCase());
        draft.set(ConfigFields.LED_H_DEPTH, Integer.toString(layout.horizontalDepth()));
        draft.set(ConfigFields.LED_V_DEPTH, Integer.toString(layout.verticalDepth()));
        draft.set(ConfigFields.LED_OVERLAP, Integer.toString(layout.overlap()));
        draft.set(ConfigFields.LED_EDGE_GAP, Integer.toString(layout.edgeGap()));
        draft.set(ConfigFields.LED_CORNER_TL_H, Integer.toString(layout.cornerTlH()));
        draft.set(ConfigFields.LED_CORNER_TL_V, Integer.toString(layout.cornerTlV()));
        draft.set(ConfigFields.LED_CORNER_TR_H, Integer.toString(layout.cornerTrH()));
        draft.set(ConfigFields.LED_CORNER_TR_V, Integer.toString(layout.cornerTrV()));
        draft.set(ConfigFields.LED_CORNER_BR_H, Integer.toString(layout.cornerBrH()));
        draft.set(ConfigFields.LED_CORNER_BR_V, Integer.toString(layout.cornerBrV()));
        draft.set(ConfigFields.LED_CORNER_BL_H, Integer.toString(layout.cornerBlH()));
        draft.set(ConfigFields.LED_CORNER_BL_V, Integer.toString(layout.cornerBlV()));

        draft.set(ConfigFields.CAPTURE_MAX_DIMENSION, Integer.toString(capture.maxDimension()));
        draft.set(ConfigFields.FRAME_RATE, Integer.toString(capture.frameRate()));
        draft.set(ConfigFields.USE_AVG_COLOR, Boolean.toString(capture.useAverageColor()));
        draft.set(ConfigFields.BOOT, Boolean.toString(capture.bootReminder()));
        return draft;
    }
}
