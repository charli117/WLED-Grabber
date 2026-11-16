package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;

/** Keeps user configuration and the latest observation independent of each other. */
public final class DeviceState {
    private final ConfigSnapshot config;
    private final WledDevice observation;

    public DeviceState(ConfigSnapshot config, WledDevice observation) {
        this.config = config;
        this.observation = observation;
    }

    public ConfigSnapshot config() {
        return config;
    }

    public WledDevice observation() {
        return observation;
    }

    public DeviceState withConfig(ConfigSnapshot newConfig) {
        return new DeviceState(newConfig, observation);
    }

    public DeviceState withObservation(WledDevice newObservation) {
        return new DeviceState(config, newObservation);
    }
}
