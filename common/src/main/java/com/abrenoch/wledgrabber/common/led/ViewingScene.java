package com.abrenoch.wledgrabber.common.led;

/** Presentation only: presets never change the capture or device configuration. */
public enum ViewingScene {
    CUSTOM(100, 0, 100), CINEMA(95, 40, 85), GAME(100, 0, 100), NIGHT(85, 55, 25);

    private final int saturation, smoothing, gain;
    ViewingScene(int saturation, int smoothing, int gain) {
        this.saturation = saturation;
        this.smoothing = smoothing;
        this.gain = gain;
    }
    int saturation(QualitySettings base) { return this == CUSTOM ? base.saturationPercent() : saturation; }
    int smoothing(QualitySettings base) { return this == CUSTOM ? base.temporalSmoothingPercent() : smoothing; }
    void applyGain(byte[] bytes, int length) {
        if (gain == 100) return;
        for (int i = 0; i < length; i++) bytes[i] = (byte)(((bytes[i] & 255) * gain + 50) / 100);
    }
}
