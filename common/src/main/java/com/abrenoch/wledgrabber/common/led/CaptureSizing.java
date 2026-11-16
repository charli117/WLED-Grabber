package com.abrenoch.wledgrabber.common.led;

/** Longest-side-640 sizing without upscaling the source frame. */
public final class CaptureSizing {
    public static final int MAX_DIMENSION = 640;

    public static final class Size {
        private final int width;
        private final int height;

        public Size(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    private CaptureSizing() {
    }

    public static Size fit(int sourceWidth, int sourceHeight, int maxDimension) {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("source dimensions must be positive");
        }
        if (maxDimension <= 0 || maxDimension > MAX_DIMENSION) {
            throw new IllegalArgumentException("maxDimension must be 1.." + MAX_DIMENSION);
        }
        if (sourceWidth <= maxDimension && sourceHeight <= maxDimension) {
            return new Size(sourceWidth, sourceHeight);
        }
        double scale = Math.min((double) maxDimension / sourceWidth,
                (double) maxDimension / sourceHeight);
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));
        width = Math.min(width, maxDimension);
        height = Math.min(height, maxDimension);
        return new Size(width, height);
    }
}
