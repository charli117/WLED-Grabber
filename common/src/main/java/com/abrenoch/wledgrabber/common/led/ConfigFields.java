package com.abrenoch.wledgrabber.common.led;

/** Stable logical keys shared by the pure model, validator, repository and UI adapters. */
public final class ConfigFields {
    public static final String SCHEMA_VERSION = "pref_key_config_schema_version";
    public static final String WLED_HOST = "pref_key_wled_host";
    public static final String WLED_HTTP_PORT = "pref_key_wled_http_port";
    public static final String WLED_UDP_PORT = "pref_key_wled_udp_port";
    public static final String WLED_DDP_PORT = "pref_key_wled_ddp_port";
    public static final String WLED_PROTOCOL = "pref_key_wled_protocol";
    public static final String WLED_RGBW_MODE = "pref_key_wled_rgbw_mode";
    public static final String WLED_RGBW_WHITE_MODE = "pref_key_wled_rgbw_white_mode";
    public static final String WLED_PHYSICAL_LEDS = "pref_key_wled_physical_leds";

    public static final String LED_TOP = "pref_key_led_top";
    public static final String LED_BOTTOM = "pref_key_led_bottom";
    public static final String LED_LEFT = "pref_key_led_left";
    public static final String LED_RIGHT = "pref_key_led_right";
    public static final String LED_GAP_LENGTH = "pref_key_led_gap_length";
    public static final String LED_GAP_POSITION = "pref_key_led_gap_position";
    public static final String LED_INPUT_POSITION = "pref_key_led_input_position";
    public static final String LED_REVERSE = "pref_key_led_reverse";
    public static final String LED_GAP_MODE = "pref_key_led_gap_mode";
    public static final String LED_H_DEPTH = "pref_key_led_h_depth";
    public static final String LED_V_DEPTH = "pref_key_led_v_depth";
    public static final String LED_OVERLAP = "pref_key_led_overlap";
    public static final String LED_EDGE_GAP = "pref_key_led_edge_gap";
    public static final String LED_CORNER_TL_H = "pref_key_led_corner_tl_h";
    public static final String LED_CORNER_TL_V = "pref_key_led_corner_tl_v";
    public static final String LED_CORNER_TR_H = "pref_key_led_corner_tr_h";
    public static final String LED_CORNER_TR_V = "pref_key_led_corner_tr_v";
    public static final String LED_CORNER_BR_H = "pref_key_led_corner_br_h";
    public static final String LED_CORNER_BR_V = "pref_key_led_corner_br_v";
    public static final String LED_CORNER_BL_H = "pref_key_led_corner_bl_h";
    public static final String LED_CORNER_BL_V = "pref_key_led_corner_bl_v";

    public static final String CAPTURE_MAX_DIMENSION = "pref_key_capture_max_dimension";
    public static final String FRAME_RATE = "pref_key_framerate";
    public static final String USE_AVG_COLOR = "pref_key_use_avg_color";
    public static final String BOOT = "pref_key_start_on_boot";

    /** Repository-only error field for storage failures. */
    public static final String STORAGE = "config_storage";

    /** Observation-only fields (not persisted with user configuration). */
    public static final String WLED_DEVICE_ID = "wled_device_id";
    public static final String WLED_VERSION = "wled_version";
    public static final String WLED_CHANNEL_HINT = "wled_channel_hint";
    public static final String WLED_CONFIRMATION = "wled_confirmation";
    public static final String WLED_OBSERVED_AT = "wled_observed_at";

    /** LAY-only geometry error field. */
    public static final String LAYOUT_GEOMETRY = "led_geometry";

    /** SMP-only plane validation field. */
    public static final String PLANE = "capture_plane";

    public static final String[] ALL = {
            SCHEMA_VERSION,
            WLED_HOST,
            WLED_HTTP_PORT,
            WLED_UDP_PORT,
            WLED_DDP_PORT,
            WLED_PROTOCOL,
            WLED_RGBW_MODE,
            WLED_RGBW_WHITE_MODE,
            WLED_PHYSICAL_LEDS,
            LED_TOP,
            LED_BOTTOM,
            LED_LEFT,
            LED_RIGHT,
            LED_GAP_LENGTH,
            LED_GAP_POSITION,
            LED_INPUT_POSITION,
            LED_REVERSE,
            LED_GAP_MODE,
            LED_H_DEPTH,
            LED_V_DEPTH,
            LED_OVERLAP,
            LED_EDGE_GAP,
            LED_CORNER_TL_H,
            LED_CORNER_TL_V,
            LED_CORNER_TR_H,
            LED_CORNER_TR_V,
            LED_CORNER_BR_H,
            LED_CORNER_BR_V,
            LED_CORNER_BL_H,
            LED_CORNER_BL_V,
            CAPTURE_MAX_DIMENSION,
            FRAME_RATE,
            USE_AVG_COLOR,
            BOOT
    };

    private ConfigFields() {
    }
}
