package com.abrenoch.wledgrabber.common.led;

public enum Protocol {
    AUTO,
    DNRGB,
    DDP;

    public static Protocol fromString(String value) {
        if (value == null) {
            return null;
        }
        for (Protocol protocol : values()) {
            if (protocol.name().equalsIgnoreCase(value.trim())) {
                return protocol;
            }
        }
        return null;
    }
}
