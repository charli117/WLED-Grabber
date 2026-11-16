package com.abrenoch.wledgrabber.common.led;

public enum RgbwMode {
    UNCONFIRMED,
    RGB,
    RGBW;

    public static RgbwMode fromString(String value) {
        if (value == null) {
            return null;
        }
        for (RgbwMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
