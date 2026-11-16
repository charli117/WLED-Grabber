package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.Capabilities;
import com.abrenoch.wledgrabber.common.network.wled.ChannelHint;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;

/** Shared manual/probe connection setup used by TV and mobile hosts. */
public final class WledSetupController {
    private final SettingsDraftController settings;

    public WledSetupController(SettingsDraftController settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings controller is required");
        }
        this.settings = settings;
    }

    public ValidationResult<ConfigSnapshot> applyManual(String host, int httpPort,
                                                        int udpPort, int ddpPort) {
        settings.set(ConfigFields.WLED_HOST, host == null ? "" : host.trim());
        settings.set(ConfigFields.WLED_HTTP_PORT, Integer.toString(httpPort));
        settings.set(ConfigFields.WLED_UDP_PORT, Integer.toString(udpPort));
        settings.set(ConfigFields.WLED_DDP_PORT, Integer.toString(ddpPort));
        return settings.validate();
    }

    /**
     * Applies observed device facts without overwriting the user's RGB/RGBW
     * confirmation. The host comes from the address that was actually probed.
     */
    public ValidationResult<ConfigSnapshot> applyProbe(ProbeResult probe) {
        if (probe == null || !probe.isSuccess() || probe.device() == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.WLED_HOST,
                    FieldError.Code.CAPABILITY_MISSING,
                    probe == null ? "no probe result"
                            : String.valueOf(probe.errorType())));
        }
        Capabilities capabilities = probe.device().capabilities();
        settings.set(ConfigFields.WLED_HOST, probe.device().address());
        if (capabilities != null) {
            settings.set(ConfigFields.WLED_UDP_PORT,
                    Integer.toString(capabilities.udpPort()));
            settings.set(ConfigFields.WLED_PHYSICAL_LEDS,
                    Integer.toString(capabilities.observedLedCount()));
            settings.set(ConfigFields.WLED_PROTOCOL, "auto");
            if (capabilities.channelHint() == ChannelHint.RGBW) {
                // Hint only; user confirmation is still required before start.
                settings.set(ConfigFields.WLED_RGBW_MODE, "unconfirmed");
            } else if (capabilities.channelHint() == ChannelHint.RGB) {
                settings.set(ConfigFields.WLED_RGBW_MODE, "rgb");
            }
        }
        return settings.validate();
    }

    public SettingsDraftController settings() {
        return settings;
    }
}
