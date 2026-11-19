package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.ConfigFields;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Android persistence adapter. All configuration values are written through one
 * SharedPreferences editor; the revision is part of the same commit.
 */
public final class PreferencesConfigStorage implements ConfigStorage {
    private static final Map<String, Integer> KEY_RESOURCES;
    private static final Map<String, String> KEY_TYPES;

    static {
        Map<String, Integer> keys = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        add(keys, types, ConfigFields.SCHEMA_VERSION, R.string.pref_key_config_schema_version, "integer");
        add(keys, types, ConfigFields.WLED_HOST, R.string.pref_key_wled_host, "string");
        add(keys, types, ConfigFields.WLED_HTTP_PORT, R.string.pref_key_wled_http_port, "integer");
        add(keys, types, ConfigFields.WLED_UDP_PORT, R.string.pref_key_wled_udp_port, "integer");
        add(keys, types, ConfigFields.WLED_DDP_PORT, R.string.pref_key_wled_ddp_port, "integer");
        add(keys, types, ConfigFields.WLED_PROTOCOL, R.string.pref_key_wled_protocol, "string");
        add(keys, types, ConfigFields.WLED_RGBW_MODE, R.string.pref_key_wled_rgbw_mode, "string");
        add(keys, types, ConfigFields.WLED_RGBW_WHITE_MODE, R.string.pref_key_wled_rgbw_white_mode, "string");
        add(keys, types, ConfigFields.WLED_PHYSICAL_LEDS, R.string.pref_key_wled_physical_leds, "integer");
        add(keys, types, ConfigFields.LED_TOP, R.string.pref_key_led_top, "integer");
        add(keys, types, ConfigFields.LED_BOTTOM, R.string.pref_key_led_bottom, "integer");
        add(keys, types, ConfigFields.LED_LEFT, R.string.pref_key_led_left, "integer");
        add(keys, types, ConfigFields.LED_RIGHT, R.string.pref_key_led_right, "integer");
        add(keys, types, ConfigFields.LED_GAP_LENGTH, R.string.pref_key_led_gap_length, "integer");
        add(keys, types, ConfigFields.LED_GAP_POSITION, R.string.pref_key_led_gap_position, "integer");
        add(keys, types, ConfigFields.LED_INPUT_POSITION, R.string.pref_key_led_input_position, "integer");
        add(keys, types, ConfigFields.LED_REVERSE, R.string.pref_key_led_reverse, "bool");
        add(keys, types, ConfigFields.LED_GAP_MODE, R.string.pref_key_led_gap_mode, "string");
        add(keys, types, ConfigFields.LED_H_DEPTH, R.string.pref_key_led_h_depth, "integer");
        add(keys, types, ConfigFields.LED_V_DEPTH, R.string.pref_key_led_v_depth, "integer");
        add(keys, types, ConfigFields.LED_OVERLAP, R.string.pref_key_led_overlap, "integer");
        add(keys, types, ConfigFields.LED_EDGE_GAP, R.string.pref_key_led_edge_gap, "integer");
        add(keys, types, ConfigFields.LED_CORNER_TL_H, R.string.pref_key_led_corner_tl_h, "integer");
        add(keys, types, ConfigFields.LED_CORNER_TL_V, R.string.pref_key_led_corner_tl_v, "integer");
        add(keys, types, ConfigFields.LED_CORNER_TR_H, R.string.pref_key_led_corner_tr_h, "integer");
        add(keys, types, ConfigFields.LED_CORNER_TR_V, R.string.pref_key_led_corner_tr_v, "integer");
        add(keys, types, ConfigFields.LED_CORNER_BR_H, R.string.pref_key_led_corner_br_h, "integer");
        add(keys, types, ConfigFields.LED_CORNER_BR_V, R.string.pref_key_led_corner_br_v, "integer");
        add(keys, types, ConfigFields.LED_CORNER_BL_H, R.string.pref_key_led_corner_bl_h, "integer");
        add(keys, types, ConfigFields.LED_CORNER_BL_V, R.string.pref_key_led_corner_bl_v, "integer");
        add(keys, types, ConfigFields.CAPTURE_MAX_DIMENSION, R.string.pref_key_capture_max_dimension, "integer");
        add(keys, types, ConfigFields.FRAME_RATE, R.string.pref_key_framerate, "integer");
        add(keys, types, ConfigFields.USE_AVG_COLOR, R.string.pref_key_use_avg_color, "bool");
        add(keys, types, ConfigFields.BOOT, R.string.pref_key_boot, "bool");
        add(keys, types, ConfigFields.QUALITY_CROP_BLACK_BARS, R.string.pref_key_quality_crop, "bool");
        add(keys, types, ConfigFields.QUALITY_LINEAR_AVERAGE, R.string.pref_key_quality_linear, "bool");
        add(keys, types, ConfigFields.QUALITY_SATURATION, R.string.pref_key_quality_saturation, "integer");
        add(keys, types, ConfigFields.QUALITY_SMOOTHING, R.string.pref_key_quality_smoothing, "integer");
        KEY_RESOURCES = Collections.unmodifiableMap(keys);
        KEY_TYPES = Collections.unmodifiableMap(types);
    }

    // Preferences wraps the single default configuration file for this app/process.
    private static final PreferenceCommitGuard COMMITS = new PreferenceCommitGuard();
    private final Preferences preferences;
    private final PreferenceCommitGuard.File file = new PreferenceCommitGuard.File() {
        public StoredConfig readMemory() { return readPreferences(); }
        public boolean commitDelta(Map<String,String> changes, long revision) { return writePreferences(changes, revision); }
    };

    public PreferencesConfigStorage(Preferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("preferences are required");
        }
        this.preferences = preferences;
    }

    @Override
    public StoredConfig load() { return COMMITS.read(file); }

    @Override
    public boolean writeAll(Map<String,String> values, long revision) { return COMMITS.write(file, values, revision); }

    private StoredConfig readPreferences() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : ConfigFields.ALL) {
            String value = preferences.rawStringWithDefault(KEY_RESOURCES.get(key), KEY_TYPES.get(key));
            values.put(key, value == null ? "" : value);
        }
        long revision = 0;
        String rawRevision = preferences.rawStringWithDefault(R.string.pref_key_config_revision, "integer");
        if (rawRevision != null) {
            try {
                revision = Long.parseLong(rawRevision);
            } catch (NumberFormatException ignored) {
                revision = 0;
            }
        }
        return new StoredConfig(values, revision);
    }

    private boolean writePreferences(Map<String, String> values, long revision) {
        Map<String, String> actual = new LinkedHashMap<>();
        for (String key : ConfigFields.ALL) {
            Integer resource = KEY_RESOURCES.get(key);
            if (resource == null || !values.containsKey(key)) {
                continue;
            }
            actual.put(preferences.keyString(resource), values.get(key));
        }
        actual.put(preferences.keyString(R.string.pref_key_config_revision), Long.toString(revision));
        return preferences.commitAllRaw(actual);
    }

    private static void add(Map<String, Integer> keys, Map<String, String> types,
                            String field, int resource, String type) {
        keys.put(field, resource);
        types.put(field, type);
    }
}
