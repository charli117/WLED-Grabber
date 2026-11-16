package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

import java.util.Objects;

/** Immutable saved configuration. physicalLedCount may be 0 while the draft is incomplete. */
public final class DeviceEndpoint {
    private final String host;
    private final int httpPort;
    private final int udpPort;
    private final int ddpPort;
    private final Protocol protocol;
    private final RgbwMode rgbwMode;
    private final int physicalLedCount;
    private final WhiteMode whiteMode;

    public DeviceEndpoint(String host, int httpPort, int udpPort, int ddpPort,
                          Protocol protocol, RgbwMode rgbwMode, int physicalLedCount) {
        this(host, httpPort, udpPort, ddpPort, protocol, rgbwMode, physicalLedCount,
                WhiteMode.MIN_SUBTRACT);
    }

    public DeviceEndpoint(String host, int httpPort, int udpPort, int ddpPort,
                          Protocol protocol, RgbwMode rgbwMode, int physicalLedCount,
                          WhiteMode whiteMode) {
        this.host = host;
        this.httpPort = httpPort;
        this.udpPort = udpPort;
        this.ddpPort = ddpPort;
        this.protocol = protocol;
        this.rgbwMode = rgbwMode;
        this.physicalLedCount = physicalLedCount;
        this.whiteMode = whiteMode == null ? WhiteMode.MIN_SUBTRACT : whiteMode;
    }

    public String host() {
        return host;
    }

    public int httpPort() {
        return httpPort;
    }

    public int udpPort() {
        return udpPort;
    }

    public int ddpPort() {
        return ddpPort;
    }

    public Protocol protocol() {
        return protocol;
    }

    public RgbwMode rgbwMode() {
        return rgbwMode;
    }

    public int physicalLedCount() {
        return physicalLedCount;
    }

    public WhiteMode whiteMode() {
        return whiteMode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeviceEndpoint)) {
            return false;
        }
        DeviceEndpoint that = (DeviceEndpoint) o;
        return httpPort == that.httpPort
                && udpPort == that.udpPort
                && ddpPort == that.ddpPort
                && physicalLedCount == that.physicalLedCount
                && Objects.equals(host, that.host)
                && protocol == that.protocol
                && rgbwMode == that.rgbwMode
                && whiteMode == that.whiteMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, httpPort, udpPort, ddpPort, protocol, rgbwMode, physicalLedCount, whiteMode);
    }
}
