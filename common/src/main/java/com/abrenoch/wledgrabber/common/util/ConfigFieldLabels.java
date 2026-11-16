package com.abrenoch.wledgrabber.common.util;

import android.content.Context;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.ConfigFields;

/**
 * Single mapping from configuration field keys to localized labels. Both the
 * mobile form and the TV guided steps resolve labels here instead of deriving
 * English text from the preference keys.
 */
public final class ConfigFieldLabels {
    private ConfigFieldLabels() {
    }

    /** @return the localized label resource, or 0 when the field has none. */
    public static int labelRes(String field) {
        if (field == null) {
            return 0;
        }
        switch (field) {
            case ConfigFields.WLED_HOST: return R.string.field_wled_host;
            case ConfigFields.WLED_HTTP_PORT: return R.string.field_wled_http_port;
            case ConfigFields.WLED_UDP_PORT: return R.string.field_wled_udp_port;
            case ConfigFields.WLED_DDP_PORT: return R.string.field_wled_ddp_port;
            case ConfigFields.WLED_PROTOCOL: return R.string.field_wled_protocol;
            case ConfigFields.WLED_RGBW_MODE: return R.string.field_wled_rgbw_mode;
            case ConfigFields.WLED_RGBW_WHITE_MODE: return R.string.field_wled_rgbw_white_mode;
            case ConfigFields.WLED_PHYSICAL_LEDS: return R.string.field_wled_physical_leds;
            case ConfigFields.LED_TOP: return R.string.field_led_top;
            case ConfigFields.LED_BOTTOM: return R.string.field_led_bottom;
            case ConfigFields.LED_LEFT: return R.string.field_led_left;
            case ConfigFields.LED_RIGHT: return R.string.field_led_right;
            case ConfigFields.LED_GAP_LENGTH: return R.string.field_led_gap_length;
            case ConfigFields.LED_GAP_POSITION: return R.string.field_led_gap_position;
            case ConfigFields.LED_INPUT_POSITION: return R.string.field_led_input_position;
            case ConfigFields.LED_REVERSE: return R.string.field_led_reverse;
            case ConfigFields.LED_GAP_MODE: return R.string.field_led_gap_mode;
            case ConfigFields.LED_H_DEPTH: return R.string.field_led_h_depth;
            case ConfigFields.LED_V_DEPTH: return R.string.field_led_v_depth;
            case ConfigFields.LED_OVERLAP: return R.string.field_led_overlap;
            case ConfigFields.LED_EDGE_GAP: return R.string.field_led_edge_gap;
            case ConfigFields.LED_CORNER_TL_H: return R.string.field_led_corner_tl_h;
            case ConfigFields.LED_CORNER_TL_V: return R.string.field_led_corner_tl_v;
            case ConfigFields.LED_CORNER_TR_H: return R.string.field_led_corner_tr_h;
            case ConfigFields.LED_CORNER_TR_V: return R.string.field_led_corner_tr_v;
            case ConfigFields.LED_CORNER_BR_H: return R.string.field_led_corner_br_h;
            case ConfigFields.LED_CORNER_BR_V: return R.string.field_led_corner_br_v;
            case ConfigFields.LED_CORNER_BL_H: return R.string.field_led_corner_bl_h;
            case ConfigFields.LED_CORNER_BL_V: return R.string.field_led_corner_bl_v;
            case ConfigFields.CAPTURE_MAX_DIMENSION: return R.string.field_capture_max_dimension;
            case ConfigFields.FRAME_RATE: return R.string.field_framerate;
            case ConfigFields.USE_AVG_COLOR: return R.string.field_use_avg_color;
            case ConfigFields.BOOT: return R.string.field_start_on_boot;
            default: return 0;
        }
    }

    public static String label(Context context, String field) {
        int res = labelRes(field);
        if (res != 0) {
            return context.getString(res);
        }
        return deriveLabel(field);
    }

    private static String deriveLabel(String field) {
        if (field == null) {
            return "";
        }
        String label = field.replace("pref_key_", "").replace('_', ' ');
        if (label.isEmpty()) {
            return field;
        }
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
