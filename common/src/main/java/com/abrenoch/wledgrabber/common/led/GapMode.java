package com.abrenoch.wledgrabber.common.led;

public enum GapMode {
    SKIP,
    BLACK;

    public static GapMode fromString(String value) {
        if (value == null) {
            return null;
        }
        for (GapMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
