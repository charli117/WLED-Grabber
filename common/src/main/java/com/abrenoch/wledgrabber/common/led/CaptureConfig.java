package com.abrenoch.wledgrabber.common.led;

/** Immutable capture configuration. */
public final class CaptureConfig {
    private final int maxDimension;
    private final int frameRate;
    private final boolean useAverageColor;
    private final boolean bootReminder;

    public CaptureConfig(int maxDimension, int frameRate, boolean useAverageColor, boolean bootReminder) {
        this.maxDimension = maxDimension;
        this.frameRate = frameRate;
        this.useAverageColor = useAverageColor;
        this.bootReminder = bootReminder;
    }

    public int maxDimension() {
        return maxDimension;
    }

    public int frameRate() {
        return frameRate;
    }

    public boolean useAverageColor() {
        return useAverageColor;
    }

    public boolean bootReminder() {
        return bootReminder;
    }
}
