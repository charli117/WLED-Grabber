package com.abrenoch.wledgrabber.common.led;

/**
 * SMP-3 black-bar detection with hysteresis. Row/column mean luma are supplied
 * by the sampler; a candidate window replaces the committed one only after it
 * stays identical for {@code stableFrames} frames. An all-black frame keeps the
 * previous window (or the full frame), so the content domain never collapses.
 */
public final class BlackBarCropper {
    public static final int DEFAULT_THRESHOLD = 8;
    public static final int DEFAULT_STABLE_FRAMES = 3;

    private final int threshold;
    private final int stableFrames;
    private CropWindow committed;
    private CropWindow candidate;
    private int candidateFrames;

    public BlackBarCropper() {
        this(DEFAULT_THRESHOLD, DEFAULT_STABLE_FRAMES);
    }

    public BlackBarCropper(int threshold, int stableFrames) {
        this.threshold = threshold;
        this.stableFrames = Math.max(1, stableFrames);
    }

    public CropWindow update(int width, int height, int[] rowLuma, int[] columnLuma) {
        CropWindow detected = detect(width, height, rowLuma, columnLuma);
        if (detected == null) {
            return committed != null ? committed : new CropWindow(0, 0, width, height);
        }
        if (committed == null) {
            if (++candidateFrames >= stableFrames && detected.equals(candidate)) {
                committed = detected;
                candidate = null;
                candidateFrames = 0;
            } else if (!detected.equals(candidate)) {
                candidate = detected;
                candidateFrames = 1;
            }
            return committed != null ? committed : detected;
        }
        if (detected.equals(committed)) {
            candidate = null;
            candidateFrames = 0;
            return committed;
        }
        if (detected.equals(candidate)) {
            candidateFrames++;
        } else {
            candidate = detected;
            candidateFrames = 1;
        }
        if (candidateFrames >= stableFrames) {
            committed = detected;
            candidate = null;
            candidateFrames = 0;
        }
        return committed;
    }

    public CropWindow committed() {
        return committed;
    }

    public void reset() {
        committed = null;
        candidate = null;
        candidateFrames = 0;
    }

    private CropWindow detect(int width, int height, int[] rowLuma, int[] columnLuma) {
        int top = firstAbove(rowLuma, height);
        int bottom = lastAbove(rowLuma, height);
        int left = firstAbove(columnLuma, width);
        int right = lastAbove(columnLuma, width);
        if (top < 0 || left < 0) {
            return null;
        }
        // Keep at least a 2x2 domain so a stray bright pixel can never collapse it.
        if (right - left < 1 || bottom - top < 1) {
            return null;
        }
        return new CropWindow(left, top, right + 1, bottom + 1);
    }

    private int firstAbove(int[] values, int length) {
        for (int i = 0; i < length; i++) {
            if (values[i] > threshold) {
                return i;
            }
        }
        return -1;
    }

    private int lastAbove(int[] values, int length) {
        for (int i = length - 1; i >= 0; i--) {
            if (values[i] > threshold) {
                return i;
            }
        }
        return -1;
    }
}
