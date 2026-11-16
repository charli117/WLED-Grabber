package com.abrenoch.wledgrabber.common.network.wled;

/** Device identity/observation result; address is the address that was actually probed. */
public final class WledDevice {
    private final String address;
    private final Capabilities capabilities;

    public WledDevice(String address, Capabilities capabilities) {
        this.address = address;
        this.capabilities = capabilities;
    }

    public String address() {
        return address;
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    public String firmwareVersion() {
        return capabilities == null ? null : capabilities.firmwareVersion();
    }

    public String deviceId() {
        return capabilities == null ? null : capabilities.deviceId();
    }

    public int observedLedCount() {
        return capabilities == null ? 0 : capabilities.observedLedCount();
    }

    public int udpPort() {
        return capabilities == null ? 0 : capabilities.udpPort();
    }

    public long observedAtMillis() {
        return capabilities == null ? 0 : capabilities.observedAtMillis();
    }
}
