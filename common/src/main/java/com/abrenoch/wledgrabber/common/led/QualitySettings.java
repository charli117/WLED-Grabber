package com.abrenoch.wledgrabber.common.led;

/** Optional SMP-3 enhancements. Every switch defaults to its off/neutral value. */
public final class QualitySettings {
    public static final int NEUTRAL_SATURATION = 100;
    public static final int NO_SMOOTHING = 0;
    public static final QualitySettings OFF = new QualitySettings(false, false,
            NEUTRAL_SATURATION, NO_SMOOTHING);

    private final boolean cropBlackBars;
    private final boolean linearAverage;
    private final int saturationPercent;
    private final int temporalSmoothingPercent;
    private final boolean hdrCompensation;

    public QualitySettings(boolean cropBlackBars, boolean linearAverage,
                           int saturationPercent, int temporalSmoothingPercent) {
        this(cropBlackBars, linearAverage, saturationPercent, temporalSmoothingPercent, false);
    }

    public QualitySettings(boolean cropBlackBars, boolean linearAverage,
                           int saturationPercent, int temporalSmoothingPercent,
                           boolean hdrCompensation) {
        this.cropBlackBars = cropBlackBars;
        this.linearAverage = linearAverage;
        this.saturationPercent = clamp(saturationPercent, 0, 200);
        this.temporalSmoothingPercent = clamp(temporalSmoothingPercent, 0, 100);
        this.hdrCompensation = hdrCompensation;
    }

    public boolean cropBlackBars() {
        return cropBlackBars;
    }

    public boolean linearAverage() {
        return linearAverage;
    }

    public int saturationPercent() {
        return saturationPercent;
    }

    public int temporalSmoothingPercent() {
        return temporalSmoothingPercent;
    }

    /** True when the active display is HDR/Dolby Vision and capture is SDR. */
    public boolean hdrCompensation() {
        return hdrCompensation;
    }

    /** Returns a copy with HDR compensation toggled; the receiver stays immutable. */
    public QualitySettings withHdrCompensation(boolean enabled) {
        return new QualitySettings(cropBlackBars, linearAverage, saturationPercent,
                temporalSmoothingPercent, enabled);
    }

    /** True when no enhancement can change the first-version output. */
    public boolean isOff() {
        return !cropBlackBars && !linearAverage
                && saturationPercent == NEUTRAL_SATURATION
                && temporalSmoothingPercent == NO_SMOOTHING
                && !hdrCompensation;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
