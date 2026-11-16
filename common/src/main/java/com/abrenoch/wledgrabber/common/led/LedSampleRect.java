package com.abrenoch.wledgrabber.common.led;

/** Normalized sample rectangle plus its side and logical slot. */
public final class LedSampleRect {
    private final double hmin;
    private final double hmax;
    private final double vmin;
    private final double vmax;
    private final Side side;
    private final int logicalIndex;

    public LedSampleRect(double hmin, double hmax, double vmin, double vmax,
                         Side side, int logicalIndex) {
        if (!Double.isFinite(hmin) || !Double.isFinite(hmax)
                || !Double.isFinite(vmin) || !Double.isFinite(vmax)) {
            throw new IllegalArgumentException("rect coordinates must be finite");
        }
        this.hmin = hmin;
        this.hmax = hmax;
        this.vmin = vmin;
        this.vmax = vmax;
        this.side = side;
        this.logicalIndex = logicalIndex;
    }

    public PixelRect toPixelRect(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("capture dimensions must be positive");
        }
        int x0 = clamp(Math.round(hmin * width), 0, width - 1);
        int x1 = clamp(Math.round(hmax * width), x0 + 1, width);
        int y0 = clamp(Math.round(vmin * height), 0, height - 1);
        int y1 = clamp(Math.round(vmax * height), y0 + 1, height);
        return new PixelRect(x0, x1, y0, y1);
    }

    private static int clamp(long value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return (int) value;
    }

    public double hmin() {
        return hmin;
    }

    public double hmax() {
        return hmax;
    }

    public double vmin() {
        return vmin;
    }

    public double vmax() {
        return vmax;
    }

    public Side side() {
        return side;
    }

    public int logicalIndex() {
        return logicalIndex;
    }

    public boolean isValidNormalized() {
        return hmin >= 0.0 && hmax <= 1.0 && vmin >= 0.0 && vmax <= 1.0
                && hmin < hmax && vmin < vmax;
    }
}
