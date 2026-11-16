package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;

/** Shared valid/default drafts for CFG-1 tests. */
public final class ConfigTestData {
    private ConfigTestData() {
    }

    public static ConfigDraft validDraft() {
        return validDraftWithPhysicalLeds(274);
    }

    public static ConfigDraft validDraftWithPhysicalLeds(int physicalLeds) {
        return new ConfigDraft()
                .set(ConfigFields.SCHEMA_VERSION, "1")
                .set(ConfigFields.WLED_HOST, "192.168.1.50")
                .set(ConfigFields.WLED_HTTP_PORT, "80")
                .set(ConfigFields.WLED_UDP_PORT, "21324")
                .set(ConfigFields.WLED_DDP_PORT, "4048")
                .set(ConfigFields.WLED_PROTOCOL, "auto")
                .set(ConfigFields.WLED_RGBW_MODE, "rgb")
                .set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract")
                .set(ConfigFields.WLED_PHYSICAL_LEDS, Integer.toString(physicalLeds))
                .set(ConfigFields.LED_TOP, "86")
                .set(ConfigFields.LED_BOTTOM, "90")
                .set(ConfigFields.LED_LEFT, "49")
                .set(ConfigFields.LED_RIGHT, "49")
                .set(ConfigFields.LED_GAP_LENGTH, "0")
                .set(ConfigFields.LED_GAP_POSITION, "0")
                .set(ConfigFields.LED_INPUT_POSITION, "161")
                .set(ConfigFields.LED_REVERSE, "true")
                .set(ConfigFields.LED_GAP_MODE, "skip")
                .set(ConfigFields.LED_H_DEPTH, "8")
                .set(ConfigFields.LED_V_DEPTH, "5")
                .set(ConfigFields.LED_OVERLAP, "0")
                .set(ConfigFields.LED_EDGE_GAP, "0")
                .set(ConfigFields.LED_CORNER_TL_H, "0")
                .set(ConfigFields.LED_CORNER_TL_V, "0")
                .set(ConfigFields.LED_CORNER_TR_H, "100")
                .set(ConfigFields.LED_CORNER_TR_V, "0")
                .set(ConfigFields.LED_CORNER_BR_H, "100")
                .set(ConfigFields.LED_CORNER_BR_V, "100")
                .set(ConfigFields.LED_CORNER_BL_H, "0")
                .set(ConfigFields.LED_CORNER_BL_V, "100")
                .set(ConfigFields.CAPTURE_MAX_DIMENSION, "640")
                .set(ConfigFields.FRAME_RATE, "30")
                .set(ConfigFields.USE_AVG_COLOR, "false")
                .set(ConfigFields.BOOT, "false")
                .set(ConfigFields.QUALITY_CROP_BLACK_BARS, "false")
                .set(ConfigFields.QUALITY_LINEAR_AVERAGE, "false")
                .set(ConfigFields.QUALITY_SATURATION, "100")
                .set(ConfigFields.QUALITY_SMOOTHING, "0");
    }

    /** Design defaults: a saveable draft that is not yet startable. */
    public static ConfigDraft defaultDraft() {
        return new ConfigDraft()
                .set(ConfigFields.SCHEMA_VERSION, "1")
                .set(ConfigFields.WLED_HOST, "")
                .set(ConfigFields.WLED_HTTP_PORT, "80")
                .set(ConfigFields.WLED_UDP_PORT, "21324")
                .set(ConfigFields.WLED_DDP_PORT, "4048")
                .set(ConfigFields.WLED_PROTOCOL, "auto")
                .set(ConfigFields.WLED_RGBW_MODE, "unconfirmed")
                .set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract")
                .set(ConfigFields.WLED_PHYSICAL_LEDS, "0")
                .set(ConfigFields.LED_TOP, "60")
                .set(ConfigFields.LED_BOTTOM, "60")
                .set(ConfigFields.LED_LEFT, "34")
                .set(ConfigFields.LED_RIGHT, "34")
                .set(ConfigFields.LED_GAP_LENGTH, "0")
                .set(ConfigFields.LED_GAP_POSITION, "0")
                .set(ConfigFields.LED_INPUT_POSITION, "0")
                .set(ConfigFields.LED_REVERSE, "false")
                .set(ConfigFields.LED_GAP_MODE, "skip")
                .set(ConfigFields.LED_H_DEPTH, "8")
                .set(ConfigFields.LED_V_DEPTH, "5")
                .set(ConfigFields.LED_OVERLAP, "0")
                .set(ConfigFields.LED_EDGE_GAP, "0")
                .set(ConfigFields.LED_CORNER_TL_H, "0")
                .set(ConfigFields.LED_CORNER_TL_V, "0")
                .set(ConfigFields.LED_CORNER_TR_H, "100")
                .set(ConfigFields.LED_CORNER_TR_V, "0")
                .set(ConfigFields.LED_CORNER_BR_H, "100")
                .set(ConfigFields.LED_CORNER_BR_V, "100")
                .set(ConfigFields.LED_CORNER_BL_H, "0")
                .set(ConfigFields.LED_CORNER_BL_V, "100")
                .set(ConfigFields.CAPTURE_MAX_DIMENSION, "640")
                .set(ConfigFields.FRAME_RATE, "30")
                .set(ConfigFields.USE_AVG_COLOR, "false")
                .set(ConfigFields.BOOT, "false")
                .set(ConfigFields.QUALITY_CROP_BLACK_BARS, "false")
                .set(ConfigFields.QUALITY_LINEAR_AVERAGE, "false")
                .set(ConfigFields.QUALITY_SATURATION, "100")
                .set(ConfigFields.QUALITY_SMOOTHING, "0");
    }
}
