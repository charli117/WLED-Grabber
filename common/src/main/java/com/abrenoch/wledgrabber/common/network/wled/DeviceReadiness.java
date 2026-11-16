package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares user configuration with an observation. READY requires matching
 * identity/count, a supported homogeneous channel hint and every start confirmation.
 */
public final class DeviceReadiness {
    public ValidationResult<ReadyDevice> evaluate(DeviceEndpoint endpoint, WledDevice observed,
                                                  StartConfirmation confirmation) {
        if (endpoint == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.WLED_HOST,
                    FieldError.Code.CONFIG_MISSING, "no device configuration"));
        }
        if (observed == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.WLED_VERSION,
                    FieldError.Code.CAPABILITY_MISSING, "no device observation"));
        }

        List<FieldError> errors = new ArrayList<>();
        validateEndpoint(endpoint, errors);

        Capabilities capabilities = observed.capabilities();
        if (capabilities == null) {
            errors.add(new FieldError(ConfigFields.WLED_VERSION,
                    FieldError.Code.CAPABILITY_MISSING, "capabilities missing"));
        } else {
            validateCapabilities(capabilities, errors);
            validateAddress(endpoint, observed, errors);
            validateCount(endpoint, capabilities, errors);
            validateChannel(endpoint, capabilities, errors);
        }

        validateConfirmation(observed, confirmation, errors);

        Transport transport = Transport.resolve(endpoint.protocol(), endpoint.rgbwMode(),
                capabilities == null ? 0 : capabilities.observedLedCount());
        if (transport == null) {
            errors.add(new FieldError(ConfigFields.WLED_PROTOCOL,
                    FieldError.Code.CROSS_FIELD, "no valid first-version transport"));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }
        return ValidationResult.ok(new ReadyDevice(endpoint, observed, transport,
                capabilities.observedLedCount()));
    }

    private static void validateEndpoint(DeviceEndpoint endpoint, List<FieldError> errors) {
        if (endpoint.host() == null || endpoint.host().trim().isEmpty()) {
            errors.add(new FieldError(ConfigFields.WLED_HOST, FieldError.Code.EMPTY, null));
        }
        if (endpoint.httpPort() < 1 || endpoint.httpPort() > 65535) {
            errors.add(new FieldError(ConfigFields.WLED_HTTP_PORT,
                    FieldError.Code.OUT_OF_RANGE, Integer.toString(endpoint.httpPort())));
        }
        if (endpoint.udpPort() < 1 || endpoint.udpPort() > 65535) {
            errors.add(new FieldError(ConfigFields.WLED_UDP_PORT,
                    FieldError.Code.OUT_OF_RANGE, Integer.toString(endpoint.udpPort())));
        }
        if (endpoint.ddpPort() != 4048) {
            errors.add(new FieldError(ConfigFields.WLED_DDP_PORT,
                    FieldError.Code.OUT_OF_RANGE, "DDP port is fixed at 4048"));
        }
        if (endpoint.protocol() == null) {
            errors.add(new FieldError(ConfigFields.WLED_PROTOCOL,
                    FieldError.Code.INVALID_VALUE, null));
        }
        if (endpoint.rgbwMode() == null || endpoint.rgbwMode() == RgbwMode.UNCONFIRMED) {
            errors.add(new FieldError(ConfigFields.WLED_RGBW_MODE,
                    FieldError.Code.INVALID_VALUE, "RGB/RGBW mode must be confirmed"));
        }
        if (endpoint.physicalLedCount() < 1 || endpoint.physicalLedCount() > 65536) {
            errors.add(new FieldError(ConfigFields.WLED_PHYSICAL_LEDS,
                    FieldError.Code.OUT_OF_RANGE, Integer.toString(endpoint.physicalLedCount())));
        }
    }

    private static void validateCapabilities(Capabilities capabilities, List<FieldError> errors) {
        if (capabilities.firmwareVersion() == null || capabilities.firmwareVersion().trim().isEmpty()) {
            errors.add(new FieldError(ConfigFields.WLED_VERSION,
                    FieldError.Code.EMPTY, "firmware version missing"));
        }
        if (capabilities.deviceId() == null || capabilities.deviceId().trim().isEmpty()) {
            errors.add(new FieldError(ConfigFields.WLED_DEVICE_ID,
                    FieldError.Code.EMPTY, "device identity missing"));
        }
        if (capabilities.observedLedCount() < 1 || capabilities.observedLedCount() > 65536) {
            errors.add(new FieldError(ConfigFields.WLED_PHYSICAL_LEDS,
                    FieldError.Code.OUT_OF_RANGE,
                    Integer.toString(capabilities.observedLedCount())));
        }
        if (capabilities.udpPort() < 1 || capabilities.udpPort() > 65535) {
            errors.add(new FieldError(ConfigFields.WLED_UDP_PORT,
                    FieldError.Code.OUT_OF_RANGE, Integer.toString(capabilities.udpPort())));
        }
        ChannelHint hint = capabilities.channelHint();
        if (hint == null || hint == ChannelHint.UNKNOWN) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.CAPABILITY_MISSING, "channel hint missing"));
        } else if (hint == ChannelHint.MIXED_OR_CCT) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.UNSUPPORTED_HARDWARE, "mixed/CCT bus is not verified"));
        }
        if (hint == ChannelHint.RGB && capabilities.rgbwHint()) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.CROSS_FIELD, "rgbw/rgb channel hints disagree"));
        }
        if (hint == ChannelHint.RGBW && !capabilities.rgbwHint()) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.CROSS_FIELD, "rgbw/rgb channel hints disagree"));
        }
    }

    private static void validateAddress(DeviceEndpoint endpoint, WledDevice observed,
                                        List<FieldError> errors) {
        String host = endpoint.host() == null ? "" : endpoint.host().trim();
        String address = observed.address() == null ? "" : observed.address().trim();
        if (!host.equalsIgnoreCase(address)) {
            errors.add(new FieldError(ConfigFields.WLED_HOST,
                    FieldError.Code.STALE_OBSERVATION,
                    "observation address does not match the configured host"));
        }
    }

    private static void validateCount(DeviceEndpoint endpoint, Capabilities capabilities,
                                      List<FieldError> errors) {
        if (endpoint.physicalLedCount() > 0 && capabilities.observedLedCount() > 0
                && endpoint.physicalLedCount() != capabilities.observedLedCount()) {
            errors.add(new FieldError(ConfigFields.WLED_PHYSICAL_LEDS,
                    FieldError.Code.COUNT_MISMATCH,
                    "manual count " + endpoint.physicalLedCount()
                            + " != observed count " + capabilities.observedLedCount()));
        }
    }

    private static void validateChannel(DeviceEndpoint endpoint, Capabilities capabilities,
                                        List<FieldError> errors) {
        RgbwMode mode = endpoint.rgbwMode();
        ChannelHint hint = capabilities.channelHint();
        if (mode == RgbwMode.RGBW && hint != ChannelHint.RGBW) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.CROSS_FIELD, "user RGBW does not match observation"));
        }
        if (mode == RgbwMode.RGB && hint != ChannelHint.RGB) {
            errors.add(new FieldError(ConfigFields.WLED_CHANNEL_HINT,
                    FieldError.Code.CROSS_FIELD, "user RGB does not match observation"));
        }
    }

    private static void validateConfirmation(WledDevice observed, StartConfirmation confirmation,
                                             List<FieldError> errors) {
        if (confirmation == null) {
            errors.add(new FieldError(ConfigFields.WLED_CONFIRMATION,
                    FieldError.Code.CONFIRMATION_REQUIRED, "start confirmation missing"));
            return;
        }
        String confirmedId = confirmation.confirmedDeviceId();
        String observedId = observed.deviceId();
        if (confirmedId == null || observedId == null || !confirmedId.equals(observedId)) {
            errors.add(new FieldError(ConfigFields.WLED_DEVICE_ID,
                    FieldError.Code.IDENTITY_CHANGED, "device identity was not re-confirmed"));
        }
        boolean rgbw = observed.capabilities() != null
                && observed.capabilities().channelHint() == ChannelHint.RGBW;
        if (rgbw && !confirmation.manualWhiteConfirmed()) {
            confirm(errors, "manual_white");
        }
        if (!confirmation.offsetZeroConfirmed()) {
            confirm(errors, "offset_zero");
        }
        if (!confirmation.mainSegmentOnlyConfirmed()) {
            confirm(errors, "main_segment_only");
        }
        if (!confirmation.dmxStartOneConfirmed()) {
            confirm(errors, "dmx_start_one");
        }
        if (!confirmation.finiteTimeoutConfirmed()) {
            confirm(errors, "finite_timeout");
        }
        if (!confirmation.maxBrightnessOverrideOffConfirmed()) {
            confirm(errors, "max_brightness_override_off");
        }
        if (!confirmation.otherSendersCheckedConfirmed()) {
            confirm(errors, "other_senders_checked");
        }
        if (!confirmation.syncDisabledConfirmed()) {
            confirm(errors, "sync_disabled");
        }
    }

    private static void confirm(List<FieldError> errors, String detail) {
        errors.add(new FieldError(ConfigFields.WLED_CONFIRMATION,
                FieldError.Code.CONFIRMATION_REQUIRED, detail));
    }
}
