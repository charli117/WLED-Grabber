package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.CaptureConfig;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LayoutDraft;
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
        LayoutFields layoutFields = parseLayoutFields(draft, errors);

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

        int effective = validateLayoutCrossFields(layoutFields, errors);

        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }

        int normalizedInput = effective > 0 && layoutFields.inputPosition != null
                ? Math.floorMod(layoutFields.inputPosition, effective)
                : 0;

        DeviceEndpoint device = new DeviceEndpoint(host, httpPort, udpPort, ddpPort,
                protocol, rgbwMode, physicalLeds, whiteMode);
        LedLayoutConfig layout = new LedLayoutConfig(layoutFields.top, layoutFields.bottom,
                layoutFields.left, layoutFields.right,
                layoutFields.gapLength, layoutFields.gapPosition, normalizedInput,
                layoutFields.reverse, layoutFields.gapMode,
                layoutFields.hDepth, layoutFields.vDepth,
                layoutFields.overlap, layoutFields.edgeGap,
                layoutFields.cornerTlH, layoutFields.cornerTlV,
                layoutFields.cornerTrH, layoutFields.cornerTrV,
                layoutFields.cornerBrH, layoutFields.cornerBrV,
                layoutFields.cornerBlH, layoutFields.cornerBlV);
        CaptureConfig capture = new CaptureConfig(maxDimension, frameRate, useAverageColor, boot,
                new QualitySettings(qualityCrop, qualityLinear, qualitySaturation, qualitySmoothing));
        return ValidationResult.ok(new ConfigSnapshot(ConfigSnapshot.CURRENT_SCHEMA_VERSION, 0,
                device, layout, capture));
    }

    /**
     * Layout-scoped validation for the TV editor and live preview. Device endpoint,
     * protocol and capture fields are deliberately not required: geometry can be
     * edited before the WLED host is configured. The rules and ranges are the same
     * ones {@link #validateForSave(ConfigDraft)} applies, not a second copy.
     */
    public ValidationResult<LayoutDraft> validateLayout(ConfigDraft draft) {
        if (draft == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.CONFIG_MISSING, "draft is null"));
        }
        List<FieldError> errors = new ArrayList<>();
        Integer physicalLeds = intValue(draft, ConfigFields.WLED_PHYSICAL_LEDS, 0, MAX_LEDS, errors);
        LayoutFields layoutFields = parseLayoutFields(draft, errors);
        int effective = validateLayoutCrossFields(layoutFields, errors);
        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }
        int normalizedInput = effective > 0
                ? Math.floorMod(layoutFields.inputPosition, effective)
                : 0;
        LedLayoutConfig layout = new LedLayoutConfig(layoutFields.top, layoutFields.bottom,
                layoutFields.left, layoutFields.right,
                layoutFields.gapLength, layoutFields.gapPosition, normalizedInput,
                layoutFields.reverse, layoutFields.gapMode,
                layoutFields.hDepth, layoutFields.vDepth,
                layoutFields.overlap, layoutFields.edgeGap,
                layoutFields.cornerTlH, layoutFields.cornerTlV,
                layoutFields.cornerTrH, layoutFields.cornerTrV,
                layoutFields.cornerBrH, layoutFields.cornerBrV,
                layoutFields.cornerBlH, layoutFields.cornerBlV);
        return ValidationResult.ok(new LayoutDraft(layout, physicalLeds == null ? 0 : physicalLeds));
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

    /** Raw layout fields shared by save validation and layout-only validation. */
    private static final class LayoutFields {
        final Integer top;
        final Integer bottom;
        final Integer left;
        final Integer right;
        final Integer gapLength;
        final Integer gapPosition;
        final Integer inputPosition;
        final Boolean reverse;
        final GapMode gapMode;
        final Integer hDepth;
        final Integer vDepth;
        final Integer overlap;
        final Integer edgeGap;
        final Integer cornerTlH;
        final Integer cornerTlV;
        final Integer cornerTrH;
        final Integer cornerTrV;
        final Integer cornerBrH;
        final Integer cornerBrV;
        final Integer cornerBlH;
        final Integer cornerBlV;

        LayoutFields(Integer top, Integer bottom, Integer left, Integer right,
                     Integer gapLength, Integer gapPosition, Integer inputPosition,
                     Boolean reverse, GapMode gapMode,
                     Integer hDepth, Integer vDepth, Integer overlap, Integer edgeGap,
                     Integer cornerTlH, Integer cornerTlV,
                     Integer cornerTrH, Integer cornerTrV,
                     Integer cornerBrH, Integer cornerBrV,
                     Integer cornerBlH, Integer cornerBlV) {
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
            this.gapLength = gapLength;
            this.gapPosition = gapPosition;
            this.inputPosition = inputPosition;
            this.reverse = reverse;
            this.gapMode = gapMode;
            this.hDepth = hDepth;
            this.vDepth = vDepth;
            this.overlap = overlap;
            this.edgeGap = edgeGap;
            this.cornerTlH = cornerTlH;
            this.cornerTlV = cornerTlV;
            this.cornerTrH = cornerTrH;
            this.cornerTrV = cornerTrV;
            this.cornerBrH = cornerBrH;
            this.cornerBrV = cornerBrV;
            this.cornerBlH = cornerBlH;
            this.cornerBlV = cornerBlV;
        }
    }

    private static LayoutFields parseLayoutFields(ConfigDraft draft, List<FieldError> errors) {
        return new LayoutFields(
                intValue(draft, ConfigFields.LED_TOP, 0, 1000, errors),
                intValue(draft, ConfigFields.LED_BOTTOM, 0, 1000, errors),
                intValue(draft, ConfigFields.LED_LEFT, 0, 1000, errors),
                intValue(draft, ConfigFields.LED_RIGHT, 0, 1000, errors),
                intValue(draft, ConfigFields.LED_GAP_LENGTH, 0, MAX_LEDS, errors),
                intValue(draft, ConfigFields.LED_GAP_POSITION, 0, MAX_LEDS, errors),
                intValue(draft, ConfigFields.LED_INPUT_POSITION,
                        -1_000_000, 1_000_000, errors),
                boolValue(draft, ConfigFields.LED_REVERSE, errors),
                enumValue(draft, ConfigFields.LED_GAP_MODE, GapMode.class, errors),
                intValue(draft, ConfigFields.LED_H_DEPTH, 1, 100, errors),
                intValue(draft, ConfigFields.LED_V_DEPTH, 1, 100, errors),
                intValue(draft, ConfigFields.LED_OVERLAP, 0, 100, errors),
                intValue(draft, ConfigFields.LED_EDGE_GAP, 0, 50, errors),
                intValue(draft, ConfigFields.LED_CORNER_TL_H, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_TL_V, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_TR_H, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_TR_V, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_BR_H, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_BR_V, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_BL_H, 0, 100, errors),
                intValue(draft, ConfigFields.LED_CORNER_BL_V, 0, 100, errors));
    }

    /** @return the effective ring size, or 0 when the total/gap fields are invalid. */
    private static int validateLayoutCrossFields(LayoutFields fields, List<FieldError> errors) {
        int total = 0;
        if (fields.top != null && fields.bottom != null
                && fields.left != null && fields.right != null) {
            total = fields.top + fields.bottom + fields.left + fields.right;
            if (total <= 0) {
                errors.add(new FieldError(ConfigFields.LED_TOP,
                        FieldError.Code.CROSS_FIELD, "total LED count must be positive"));
            }
        }

        int effective = 0;
        if (total > 0 && fields.gapLength != null && fields.gapPosition != null
                && fields.gapMode != null) {
            long end = (long) fields.gapPosition + fields.gapLength;
            if (fields.gapPosition > total || end > total) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "gap must stay inside the LED ring"));
            }
            if (fields.gapMode == GapMode.SKIP && fields.gapLength >= total) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "SKIP gap cannot remove every LED"));
            }
            effective = fields.gapMode == GapMode.SKIP
                    ? total - fields.gapLength : total;
            if (effective <= 0) {
                errors.add(new FieldError(ConfigFields.LED_GAP_LENGTH,
                        FieldError.Code.CROSS_FIELD, "effective LED count must be positive"));
            }
        }
        return effective;
    }
}
