package com.abrenoch.wledgrabber.common.network.wled;

/** A validated device observation; sendLedCount is the confirmed full device count. */
public final class ReadyDevice {
    private final DeviceEndpoint endpoint;
    private final WledDevice observed;
    private final Transport transport;
    private final int sendLedCount;

    public ReadyDevice(DeviceEndpoint endpoint, WledDevice observed,
                       Transport transport, int sendLedCount) {
        this.endpoint = endpoint;
        this.observed = observed;
        this.transport = transport;
        this.sendLedCount = sendLedCount;
    }

    public DeviceEndpoint endpoint() {
        return endpoint;
    }

    public WledDevice observed() {
        return observed;
    }

    public Transport transport() {
        return transport;
    }

    public int sendLedCount() {
        return sendLedCount;
    }
}
