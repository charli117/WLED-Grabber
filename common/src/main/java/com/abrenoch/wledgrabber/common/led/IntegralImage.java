package com.abrenoch.wledgrabber.common.led;

/**
 * Reusable three-channel integral image. Arrays are reallocated only when the
 * capture dimensions change; update() and mean() allocate nothing.
 */
public final class IntegralImage {
    private int[] red;
    private int[] green;
    private int[] blue;
    private int width;
    private int height;
    private int allocationCount;

    public void ensureCapacity(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0
                || newWidth > PlaneView.MAX_DIMENSION || newHeight > PlaneView.MAX_DIMENSION) {
            throw new IllegalArgumentException("integral dimensions must be 1..640");
        }
        if (newWidth == width && newHeight == height && red != null) {
            return;
        }
        long maxChannelSum = 255L * newWidth * newHeight;
        if (maxChannelSum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("integral accumulation exceeds int range");
        }
        int size = (newWidth + 1) * (newHeight + 1);
        red = new int[size];
        green = new int[size];
        blue = new int[size];
        allocationCount += 3;
        width = newWidth;
        height = newHeight;
    }

    public void update(PlaneView plane) {
        ensureCapacity(plane.cropWidth(), plane.cropHeight());
        int stride = width + 1;
        for (int y = 0; y < height; y++) {
            int rowR = 0;
            int rowG = 0;
            int rowB = 0;
            int previous = y * stride;
            int current = (y + 1) * stride;
            for (int x = 0; x < width; x++) {
                rowR += plane.read(x, y, 0);
                rowG += plane.read(x, y, 1);
                rowB += plane.read(x, y, 2);
                red[current + x + 1] = red[previous + x + 1] + rowR;
                green[current + x + 1] = green[previous + x + 1] + rowG;
                blue[current + x + 1] = blue[previous + x + 1] + rowB;
            }
        }
    }

    /** Fills out[0..2] with floor(sum/area) for each channel. */
    public void mean(PixelRect rect, int[] out) {
        int stride = width + 1;
        int area = rect.area();
        out[0] = sum(red, stride, rect) / area;
        out[1] = sum(green, stride, rect) / area;
        out[2] = sum(blue, stride, rect) / area;
    }

    private static int sum(int[] image, int stride, PixelRect rect) {
        int x0 = rect.x0();
        int x1 = rect.x1();
        int y0 = rect.y0();
        int y1 = rect.y1();
        return image[y1 * stride + x1]
                - image[y0 * stride + x1]
                - image[y1 * stride + x0]
                + image[y0 * stride + x0];
    }

    /** Full-domain mean without allocating a rectangle. */
    public void meanFull(int[] out) {
        int stride = width + 1;
        int area = width * height;
        int index = height * stride + width;
        out[0] = red[index] / area;
        out[1] = green[index] / area;
        out[2] = blue[index] / area;
    }

    public static long requiredBytes(int width, int height) {
        return 3L * (width + 1) * (height + 1) * 4L;
    }

    public int allocationCount() {
        return allocationCount;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
