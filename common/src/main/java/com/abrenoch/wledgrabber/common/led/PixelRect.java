package com.abrenoch.wledgrabber.common.led;

/** Half-open pixel rectangle [x0,x1) x [y0,y1). */
public final class PixelRect {
    private final int x0;
    private final int x1;
    private final int y0;
    private final int y1;

    public PixelRect(int x0, int x1, int y0, int y1) {
        if (x0 < 0 || x1 <= x0 || y0 < 0 || y1 <= y0) {
            throw new IllegalArgumentException("pixel rectangle must be non-empty and non-negative");
        }
        this.x0 = x0;
        this.x1 = x1;
        this.y0 = y0;
        this.y1 = y1;
    }

    public int x0() {
        return x0;
    }

    public int x1() {
        return x1;
    }

    public int y0() {
        return y0;
    }

    public int y1() {
        return y1;
    }

    public int width() {
        return x1 - x0;
    }

    public int height() {
        return y1 - y0;
    }

    public int area() {
        return width() * height();
    }
}
