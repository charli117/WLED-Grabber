package com.abrenoch.wledgrabber.common.led;

/**
 * SMP-3 color helpers: sRGB/linear conversion tables, saturation and temporal
 * smoothing. Callers skip these entirely when the corresponding switch is off,
 * so the first-version output stays byte-identical.
 *
 * <p>Linear values use a 12-bit scale (0..4095) so gamma-2.2 shadows keep their
 * precision; a 640-wide frame then sums to at most 1.68e9, still inside int.</p>
 */
public final class ColorQuality {
    public static final int LINEAR_MAX = 4095;
    private static final int[] TO_LINEAR = new int[256];
    private static final int[] TO_SRGB = new int[LINEAR_MAX + 1];

    static {
        for (int i = 0; i < 256; i++) {
            TO_LINEAR[i] = (int) Math.round(Math.pow(i / 255.0, 2.2) * LINEAR_MAX);
        }
        for (int i = 0; i <= LINEAR_MAX; i++) {
            TO_SRGB[i] = (int) Math.round(Math.pow(i / (double) LINEAR_MAX, 1.0 / 2.2) * 255.0);
        }
    }

    private ColorQuality() {
    }

    public static int toLinear(int srgb) {
        return TO_LINEAR[srgb & 0xff];
    }

    public static int toSrgb(int linear) {
        int clamped = Math.max(0, Math.min(LINEAR_MAX, linear));
        return TO_SRGB[clamped];
    }

    /** Scales each channel around luma; 100 leaves the color untouched. */
    public static void applySaturation(int[] rgb, int percent) {
        if (percent == QualitySettings.NEUTRAL_SATURATION) {
            return;
        }
        int luma = (rgb[0] * 77 + rgb[1] * 150 + rgb[2] * 29) >> 8;
        for (int i = 0; i < 3; i++) {
            long value = luma + (long) (rgb[i] - luma) * percent / 100;
            rgb[i] = (int) Math.max(0, Math.min(255, value));
        }
    }

    /**
     * EMA towards the previous sample; 0 keeps the current frame, 100 freezes
     * the previous color. The caller resets history on session changes.
     */
    public static void smooth(int[] current, int[] previous, int percent) {
        if (percent <= 0) {
            return;
        }
        int keep = Math.max(0, Math.min(100, percent));
        for (int i = 0; i < 3; i++) {
            current[i] = previous[i] + (current[i] - previous[i]) * (100 - keep) / 100;
        }
    }
}
