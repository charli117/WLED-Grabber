package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.network.wled.DeviceEndpoint;

/** Immutable committed configuration snapshot; may be a non-startable draft. */
public class ConfigSnapshot {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final long revision;
    private final DeviceEndpoint device;
    private final LedLayoutConfig layout;
    private final CaptureConfig capture;

    public ConfigSnapshot(int schemaVersion, long revision, DeviceEndpoint device,
                          LedLayoutConfig layout, CaptureConfig capture) {
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.device = device;
        this.layout = layout;
        this.capture = capture;
    }

    public ConfigSnapshot withRevision(long newRevision) {
        return new ConfigSnapshot(schemaVersion, newRevision, device, layout, capture);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public long revision() {
        return revision;
    }

    public DeviceEndpoint device() {
        return device;
    }

    public LedLayoutConfig layout() {
        return layout;
    }

    public CaptureConfig capture() {
        return capture;
    }
}
