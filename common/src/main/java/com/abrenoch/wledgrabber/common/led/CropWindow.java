package com.abrenoch.wledgrabber.common.led;

/** Content window produced by black-bar detection, in capture pixels. */
public final class CropWindow {
    public static final CropWindow FULL = new CropWindow(0, 0, 0, 0);

    private final int x0;
    private final int y0;
    private final int x1;
    private final int y1;

    public CropWindow(int x0, int y0, int x1, int y1) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
    }

    public int x0() {
        return x0;
    }

    public int y0() {
        return y0;
    }

    public int x1() {
        return x1;
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

    public boolean isFullFrame(int width, int height) {
        return x0 == 0 && y0 == 0 && x1 == width && y1 == height;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CropWindow)) {
            return false;
        }
        CropWindow that = (CropWindow) other;
        return x0 == that.x0 && y0 == that.y0 && x1 == that.x1 && y1 == that.y1;
    }

    @Override
    public int hashCode() {
        return ((x0 * 31 + y0) * 31 + x1) * 31 + y1;
    }
}
