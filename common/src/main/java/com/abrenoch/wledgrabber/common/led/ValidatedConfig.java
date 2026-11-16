package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;

/** Startable configuration: host non-empty, RGB/RGBW confirmed, M >= effective LED count. */
public final class ValidatedConfig extends ConfigSnapshot {
    public ValidatedConfig(int schemaVersion, long revision, DeviceEndpoint device,
                           LedLayoutConfig layout, CaptureConfig capture) {
        super(schemaVersion, revision, device, layout, capture);
    }

    public static ValidatedConfig from(ConfigSnapshot snapshot) {
        return new ValidatedConfig(snapshot.schemaVersion(), snapshot.revision(),
                snapshot.device(), snapshot.layout(), snapshot.capture());
    }
}
