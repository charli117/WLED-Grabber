package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.CaptureConfig;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.QualitySettings;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.ValidatedConfig;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

import java.util.ArrayList;
import java.util.List;

/** Pure configuration validation; no Android Resources and no unchecked parsing. */
public final class ConfigValidator {
    public static final int MAX_LEDS = 65536;
    public static final int FIXED_DDP_PORT = 4048;

    public ValidationResult<ConfigSnapshot> validateForSave(ConfigDraft draft) {
        List<FieldError> errors = new ArrayList<>();
        if (draft == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.CONFIG_MISSING, "draft is null"));
        }

        Integer schema = intValue(draft, ConfigFields.SCHEMA_VERSION, 0, Integer.MAX_VALUE, errors);
        if (schema != null && schema != ConfigSnapshot.CURRENT_SCHEMA_VERSION) {
            errors.add(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.UNSUPPORTED_SCHEMA, Integer.toString(schema)));
        }

        String host = draft.get(ConfigFields.WLED_HOST);
        host = host == null ? "" : host.trim();
        Integer httpPort = intValue(draft, ConfigFields.WLED_HTTP_PORT, 1, 65535, errors);
        Integer udpPort = intValue(draft, ConfigFields.WLED_UDP_PORT, 1, 65535, errors);
        Integer ddpPort = intValue(draft, ConfigFields.WLED_DDP_PORT, 1, 65535, errors);
        if (ddpPort != null && ddpPort != FIXED_DDP_PORT) {
            errors.add(new FieldError(ConfigFields.WLED_DDP_PORT,
                    FieldError.Code.OUT_OF_RANGE, "DDP port is fixed at " + FIXED_DDP_PORT));
        }
        Protocol protocol = enumValue(draft, ConfigFields.WLED_PROTOCOL, Protocol.class, errors);
        RgbwMode rgbwMode = enumValue(draft, ConfigFields.WLED_RGBW_MODE, RgbwMode.class, errors);
        WhiteMode whiteMode = enumValue(draft, ConfigFields.WLED_RGBW_WHITE_MODE,
                WhiteMode.class, errors);
        Integer physicalLeds = intValue(draft, ConfigFields.WLED_PHYSICAL_LEDS, 0, MAX_LEDS, errors);

        Integer top = intValue(draft, ConfigFields.LED_TOP, 0, 1000, errors);
        Integer bottom = intValue(draft, ConfigFields.LED_BOTTOM, 0, 1000, errors);
        Integer left = intValue(draft, ConfigFields.LED_LEFT, 0, 1000, errors);
        Integer right = intValue(draft, ConfigFields.LED_RIGHT, 0, 1000, errors);
        Integer gapLength = intValue(draft, ConfigFields.LED_GAP_LENGTH, 0, MAX_LEDS, errors);
        Integer gapPosition = intValue(draft, ConfigFields.LED_GAP_POSITION, 0, MAX_LEDS, errors);
        Integer inputPosition = intValue(draft, ConfigFields.LED_INPUT_POSITION,
                -1_000_000, 1_000_000, errors);
        Boolean reverse = boolValue(draft, ConfigFields.LED_REVERSE, errors);
        GapMode gapMode = enumValue(draft, ConfigFields.LED_GAP_MODE, GapMode.class, errors);
        Integer hDepth = intValue(draft, ConfigFields.LED_H_DEPTH, 1, 100, errors);
        Integer vDepth = intValue(draft, ConfigFields.LED_V_DEPTH, 1, 100, errors);
        Integer overlap = intValue(draft, ConfigFields.LED_OVERLAP, 0, 100, errors);
        Integer edgeGap = intValue(draft, ConfigFields.LED_EDGE_GAP, 0, 50, errors);
        Integer cornerTlH = intValue(draft, ConfigFields.LED_CORNER_TL_H, 0, 100, errors);
        Integer cornerTlV = intValue(draft, ConfigFields.LED_CORNER_TL_V, 0, 100, errors);
        Integer cornerTrH = intValue(draft, ConfigFields.LED_CORNER_TR_H, 0, 100, errors);
        Integer cornerTrV = intValue(draft, ConfigFields.LED_CORNER_TR_V, 0, 100, errors);
        Integer cornerBrH = intValue(draft, ConfigFields.LED_CORNER_BR_H, 0, 100, errors);
        Integer cornerBrV = intValue(draft, ConfigFields.LED_CORNER_BR_V, 0, 100, errors);
        Integer cornerBlH = intValue(draft, ConfigFields.LED_CORNER_BL_H, 0, 100, errors);
        Integer cornerBlV = intValue(draft, ConfigFields.LED_CORNER_BL_V, 0, 100, errors);

        Integer maxDimension = intValue(draft, ConfigFields.CAPTURE_MAX_DIMENSION, 1, 640, errors);
        Integer frameRate = intValue(draft, ConfigFields.FRAME_RATE, 1, 60, errors);
        Boolean useAverageColor = boolValue(draft, ConfigFields.USE_AVG_COLOR, errors);
        Boolean boot = boolValue(draft, ConfigFields.BOOT, errors);
        Boolean qualityCrop = boolValue(draft, ConfigFields.QUALITY_CROP_BLACK_BARS, errors);
        Boolean qualityLinear = boolValue(draft, ConfigFields.QUALITY_LINEAR_AVERAGE, errors);
        Integer qualitySaturation = intValue(draft, ConfigFields.QUALITY_SATURATION, 0, 200, errors);
        Integer qualitySmoothing = intValue(draft, ConfigFields.QUALITY_SMOOTHING, 0, 100, errors);

        if (rgbwMode == RgbwMode.RGBW && protocol == Protocol.DNRGB) {
            errors.add(new FieldError(ConfigFields.WLED_PROTOCOL,
                    FieldError.Code.CROSS_FIELD, "RGBW requires DDP; DNRGB is not allowed"));
        }

        int total = 0;
        if (top != null && bottom != null && left != null && right != null) {
            total = top + bottom + left + right;
            if (total <= 0) {
                errors.add(new FieldError(ConfigFields.LED_TOP,
                        FieldError.Code.CROSS_FIELD, "total LED count must be positive"));
            }
        }

        int effective = 0;
        if (total > 0 && gapLength != null && gapPosition != null && gapMode != null) {
            long end = (long) gapPosition + gapLength;
            if (gapPosition > total || end > total) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "gap must stay inside the LED ring"));
            }
            if (gapMode == GapMode.SKIP && gapLength >= total) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "SKIP gap cannot remove every LED"));
            }
            effective = gapMode == GapMode.SKIP ? total - gapLength : total;
            if (effective <= 0) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "effective LED count must be positive"));
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }

        int normalizedInput = effective > 0 && inputPosition != null
                ? Math.floorMod(inputPosition, effective)
                : 0;

        DeviceEndpoint device = new DeviceEndpoint(host, httpPort, udpPort, ddpPort,
                protocol, rgbwMode, physicalLeds, whiteMode);
        LedLayoutConfig layout = new LedLayoutConfig(top, bottom, left, right,
                gapLength, gapPosition, normalizedInput, reverse, gapMode,
                hDepth, vDepth, overlap, edgeGap,
                cornerTlH, cornerTlV, cornerTrH, cornerTrV,
                cornerBrH, cornerBrV, cornerBlH, cornerBlV);
        CaptureConfig capture = new CaptureConfig(maxDimension, frameRate, useAverageColor, boot,
                new QualitySettings(qualityCrop, qualityLinear, qualitySaturation, qualitySmoothing));
        return ValidationResult.ok(new ConfigSnapshot(ConfigSnapshot.CURRENT_SCHEMA_VERSION, 0,
                device, layout, capture));
    }

    public ValidationResult<ValidatedConfig> validateForStart(ConfigSnapshot snapshot) {
        if (snapshot == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.CONFIG_MISSING, "no committed configuration"));
        }
        List<FieldError> errors = new ArrayList<>();
        if (snapshot.schemaVersion() != ConfigSnapshot.CURRENT_SCHEMA_VERSION) {
            errors.add(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.UNSUPPORTED_SCHEMA, Integer.toString(snapshot.schemaVersion())));
        }
        DeviceEndpoint device = snapshot.device();
        if (device.host() == null || device.host().trim().isEmpty()) {
            errors.add(new FieldError(ConfigFields.WLED_HOST,
                    FieldError.Code.EMPTY, "host is required before start"));
        }
        if (device.physicalLedCount() < 1 || device.physicalLedCount() > MAX_LEDS) {
            errors.add(new FieldError(ConfigFields.WLED_PHYSICAL_LEDS,
                    FieldError.Code.OUT_OF_RANGE, Integer.toString(device.physicalLedCount())));
        } else if (device.physicalLedCount() < snapshot.layout().effectiveLedCount()) {
            errors.add(new FieldError(ConfigFields.WLED_PHYSICAL_LEDS,
                    FieldError.Code.CROSS_FIELD,
                    "physical LED count is below the effective layout count"));
        }
        if (device.rgbwMode() == RgbwMode.UNCONFIRMED) {
            errors.add(new FieldError(ConfigFields.WLED_RGBW_MODE,
                    FieldError.Code.INVALID_VALUE, "RGB/RGBW mode must be confirmed"));
        }
        if (device.rgbwMode() == RgbwMode.RGBW && device.protocol() == Protocol.DNRGB) {
            errors.add(new FieldError(ConfigFields.WLED_PROTOCOL,
                    FieldError.Code.CROSS_FIELD, "RGBW requires DDP"));
        }
        if (snapshot.layout().effectiveLedCount() <= 0) {
            errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                    FieldError.Code.CROSS_FIELD, "effective LED count must be positive"));
        }
        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }
        return ValidationResult.ok(ValidatedConfig.from(snapshot));
    }

    private static Integer intValue(ConfigDraft draft, String field, int min, int max,
                                    List<FieldError> errors) {
        String raw = draft.get(field);
        if (raw == null || raw.trim().isEmpty()) {
            errors.add(new FieldError(field, FieldError.Code.EMPTY, null));
            return null;
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            errors.add(new FieldError(field, FieldError.Code.NOT_A_NUMBER, raw));
            return null;
        }
        if (value < min || value > max) {
            errors.add(new FieldError(field, FieldError.Code.OUT_OF_RANGE, raw));
            return null;
        }
        return (int) value;
    }

    private static Boolean boolValue(ConfigDraft draft, String field, List<FieldError> errors) {
        String raw = draft.get(field);
        if (raw == null || raw.trim().isEmpty()) {
            errors.add(new FieldError(field, FieldError.Code.EMPTY, null));
            return null;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        errors.add(new FieldError(field, FieldError.Code.INVALID_VALUE, raw));
        return null;
    }

    private static <E extends Enum<E>> E enumValue(ConfigDraft draft, String field,
                                                   Class<E> type, List<FieldError> errors) {
        String raw = draft.get(field);
        if (raw == null || raw.trim().isEmpty()) {
            errors.add(new FieldError(field, FieldError.Code.EMPTY, null));
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(new FieldError(field, FieldError.Code.INVALID_VALUE, raw));
            return null;
        }
    }
}
