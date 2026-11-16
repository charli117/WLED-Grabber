package com.abrenoch.wledgrabber.common.network.wled;

/**
 * Observed WLED capability snapshot. rgbw/lc/seglc are hints, not proof of a
 * homogeneous physical bus; callers must not derive a stride from them.
 */
public final class Capabilities {
    private final String firmwareVersion;
    private final String deviceId;
    private final int observedLedCount;
    private final int udpPort;
    private final boolean rgbwHint;
    private final ChannelHint channelHint;
    private final String lc;
    private final String seglc;
    private final boolean live;
    private final long observedAtMillis;

    public Capabilities(String firmwareVersion, String deviceId, int observedLedCount,
                        int udpPort, boolean rgbwHint, ChannelHint channelHint,
                        String lc, String seglc, boolean live, long observedAtMillis) {
        this.firmwareVersion = firmwareVersion;
        this.deviceId = deviceId;
        this.observedLedCount = observedLedCount;
        this.udpPort = udpPort;
        this.rgbwHint = rgbwHint;
        this.channelHint = channelHint;
        this.lc = lc;
        this.seglc = seglc;
        this.live = live;
        this.observedAtMillis = observedAtMillis;
    }

    public String firmwareVersion() {
        return firmwareVersion;
    }

    public String deviceId() {
        return deviceId;
    }

    public int observedLedCount() {
        return observedLedCount;
    }

    public int udpPort() {
        return udpPort;
    }

    public boolean rgbwHint() {
        return rgbwHint;
    }

    public ChannelHint channelHint() {
        return channelHint;
    }

    public String lc() {
        return lc;
    }

    public String seglc() {
        return seglc;
    }

    public boolean live() {
        return live;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }
}
