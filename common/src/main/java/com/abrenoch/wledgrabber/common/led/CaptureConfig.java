package com.abrenoch.wledgrabber.common.led;

/** Immutable capture configuration. */
public final class CaptureConfig {
    private final int maxDimension;
    private final int frameRate;
    private final boolean useAverageColor;
    private final boolean bootReminder;
    private final QualitySettings quality;

    public CaptureConfig(int maxDimension, int frameRate, boolean useAverageColor, boolean bootReminder) {
        this(maxDimension, frameRate, useAverageColor, bootReminder, QualitySettings.OFF);
    }

    public CaptureConfig(int maxDimension, int frameRate, boolean useAverageColor,
                         boolean bootReminder, QualitySettings quality) {
        this.maxDimension = maxDimension;
        this.frameRate = frameRate;
        this.useAverageColor = useAverageColor;
        this.bootReminder = bootReminder;
        this.quality = quality == null ? QualitySettings.OFF : quality;
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

    /** SMP-3 switches; {@link QualitySettings#OFF} when no enhancement is enabled. */
    public QualitySettings quality() {
        return quality;
    }
}
