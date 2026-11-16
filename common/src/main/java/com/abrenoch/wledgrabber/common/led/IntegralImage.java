package com.abrenoch.wledgrabber.common.led;

import java.util.Arrays;
import java.util.List;

/**
 * Exact rectangle means in one pass over the frame.
 *
 * <p>For each row the sampler builds the three channel prefix sums in small
 * L1-resident arrays and adds each active rectangle's row span. This produces
 * the same {@code floor(sum/area)} values as a full 2D integral image without
 * writing a width*height accumulator; that write traffic dominated the frame
 * budget on the TV SoC.</p>
 *
 * <p>SMP-3 options: {@code linearAverage} sums 12-bit linearized channel values
 * and converts the mean back to sRGB; a {@link CropWindow} remaps every rect
 * into the detected content domain. Both are inert when off, so the default
 * output is byte-identical to the first version.</p>
 */
public final class IntegralImage {
    private int[] prefixRed = new int[0];
    private int[] prefixGreen = new int[0];
    private int[] prefixBlue = new int[0];
    private int[] sumRed = new int[0];
    private int[] sumGreen = new int[0];
    private int[] sumBlue = new int[0];
    private int[] rectAreas = new int[0];
    private int[] rectX0 = new int[0];
    private int[] rectX1 = new int[0];
    private int[] rectY0 = new int[0];
    private int[] rectY1 = new int[0];
    private int[] workX0 = new int[0];
    private int[] workX1 = new int[0];
    private int[] workY0 = new int[0];
    private int[] workY1 = new int[0];
    private int[] workAreas = new int[0];
    private int[] bX0;
    private int[] bX1;
    private int[] bY0;
    private int[] bY1;
    private int[] bAreas;
    private byte[] rowBytes;

    private int width;
    private int height;
    private int rectCount;
    private long rectAreaSum;
    private long totalRed;
    private long totalGreen;
    private long totalBlue;
    private boolean lastFullMeanValid;
    private boolean linear;

    public void ensureCapacity(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0
                || newWidth > PlaneView.MAX_DIMENSION || newHeight > PlaneView.MAX_DIMENSION) {
            throw new IllegalArgumentException("integral dimensions must be 1..640");
        }
        if (newWidth == width && newHeight == height && prefixRed.length == newWidth + 1) {
            return;
        }
        prefixRed = new int[newWidth + 1];
        prefixGreen = new int[newWidth + 1];
        prefixBlue = new int[newWidth + 1];
        width = newWidth;
        height = newHeight;
    }

    /** Computes every rect mean for this frame; caller passes the layout rects. */
    public void update(PlaneView plane, List<PixelRect> rects) {
        update(plane, rects, false, QualitySettings.OFF, null);
    }

    public void update(PlaneView plane, List<PixelRect> rects, boolean needFullMean) {
        update(plane, rects, needFullMean, QualitySettings.OFF, null);
    }

    /**
     * @param needFullMean also accumulate whole-frame sums for {@link #meanFull}
     *                     (forces the full-frame scan path).
     * @param quality      SMP-3 switches; null is treated as off.
     * @param window       content domain from black-bar detection, or null.
     */
    public void update(PlaneView plane, List<PixelRect> rects, boolean needFullMean,
                       QualitySettings quality, CropWindow window) {
        ensureCapacity(plane.cropWidth(), plane.cropHeight());
        ensureRects(rects);
        linear = quality != null && quality.linearAverage();
        prepareBounds(window);
        Arrays.fill(sumRed, 0, rectCount, 0);
        Arrays.fill(sumGreen, 0, rectCount, 0);
        Arrays.fill(sumBlue, 0, rectCount, 0);
        totalRed = 0;
        totalGreen = 0;
        totalBlue = 0;
        lastFullMeanValid = needFullMean;

        int pixelStride = plane.pixelStride();
        int span = (width - 1) * pixelStride + 4;
        if (rowBytes == null || rowBytes.length < span) {
            rowBytes = new byte[span];
        }
        // Border layouts touch far fewer pixels than the frame; summing the rects
        // directly then beats scanning every row. Dense layouts use the prefix
        // scan instead, which bounds the cost by width*height.
        if (!needFullMean && rectAreaSum <= (long) width * height) {
            updateDirect(plane, pixelStride);
        } else {
            updatePrefix(plane, pixelStride);
        }
    }

    private void updateDirect(PlaneView plane, int pixelStride) {
        for (int y = 0; y < height; y++) {
            plane.readRow(y, rowBytes);
            for (int i = 0; i < rectCount; i++) {
                if (y < bY0[i] || y >= bY1[i]) {
                    continue;
                }
                int red = 0;
                int green = 0;
                int blue = 0;
                int src = bX0[i] * pixelStride;
                int end = bX1[i] * pixelStride;
                if (linear) {
                    for (; src < end; src += pixelStride) {
                        red += ColorQuality.toLinear(rowBytes[src] & 0xff);
                        green += ColorQuality.toLinear(rowBytes[src + 1] & 0xff);
                        blue += ColorQuality.toLinear(rowBytes[src + 2] & 0xff);
                    }
                } else {
                    for (; src < end; src += pixelStride) {
                        red += rowBytes[src] & 0xff;
                        green += rowBytes[src + 1] & 0xff;
                        blue += rowBytes[src + 2] & 0xff;
                    }
                }
                sumRed[i] += red;
                sumGreen[i] += green;
                sumBlue[i] += blue;
            }
        }
    }

    private void updatePrefix(PlaneView plane, int pixelStride) {
        for (int y = 0; y < height; y++) {
            plane.readRow(y, rowBytes);
            int red = 0;
            int green = 0;
            int blue = 0;
            int src = 0;
            if (linear) {
                for (int x = 0; x < width; x++) {
                    red += ColorQuality.toLinear(rowBytes[src] & 0xff);
                    green += ColorQuality.toLinear(rowBytes[src + 1] & 0xff);
                    blue += ColorQuality.toLinear(rowBytes[src + 2] & 0xff);
                    src += pixelStride;
                    prefixRed[x + 1] = red;
                    prefixGreen[x + 1] = green;
                    prefixBlue[x + 1] = blue;
                }
            } else {
                for (int x = 0; x < width; x++) {
                    red += rowBytes[src] & 0xff;
                    green += rowBytes[src + 1] & 0xff;
                    blue += rowBytes[src + 2] & 0xff;
                    src += pixelStride;
                    prefixRed[x + 1] = red;
                    prefixGreen[x + 1] = green;
                    prefixBlue[x + 1] = blue;
                }
            }
            totalRed += red;
            totalGreen += green;
            totalBlue += blue;
            for (int i = 0; i < rectCount; i++) {
                if (y < bY0[i] || y >= bY1[i]) {
                    continue;
                }
                sumRed[i] += prefixRed[bX1[i]] - prefixRed[bX0[i]];
                sumGreen[i] += prefixGreen[bX1[i]] - prefixGreen[bX0[i]];
                sumBlue[i] += prefixBlue[bX1[i]] - prefixBlue[bX0[i]];
            }
        }
    }

    /** Fills out[0..2] with floor(sum/area) for the rect at {@code index}. */
    public void mean(int index, int[] out) {
        int area = bAreas[index];
        if (linear) {
            out[0] = ColorQuality.toSrgb(sumRed[index] / area);
            out[1] = ColorQuality.toSrgb(sumGreen[index] / area);
            out[2] = ColorQuality.toSrgb(sumBlue[index] / area);
        } else {
            out[0] = sumRed[index] / area;
            out[1] = sumGreen[index] / area;
            out[2] = sumBlue[index] / area;
        }
    }

    /** Full-domain mean without allocating a rectangle. */
    public void meanFull(int[] out) {
        if (!lastFullMeanValid) {
            throw new IllegalStateException("meanFull requires a full-frame update");
        }
        int area = width * height;
        if (linear) {
            out[0] = ColorQuality.toSrgb((int) (totalRed / area));
            out[1] = ColorQuality.toSrgb((int) (totalGreen / area));
            out[2] = ColorQuality.toSrgb((int) (totalBlue / area));
        } else {
            out[0] = (int) (totalRed / area);
            out[1] = (int) (totalGreen / area);
            out[2] = (int) (totalBlue / area);
        }
    }

    private void prepareBounds(CropWindow window) {
        if (window == null || window.isFullFrame(width, height)) {
            bX0 = rectX0;
            bX1 = rectX1;
            bY0 = rectY0;
            bY1 = rectY1;
            bAreas = rectAreas;
            return;
        }
        for (int i = 0; i < rectCount; i++) {
            int x0 = window.x0() + Math.round((float) rectX0[i] * window.width() / width);
            int x1 = window.x0() + Math.round((float) rectX1[i] * window.width() / width);
            int y0 = window.y0() + Math.round((float) rectY0[i] * window.height() / height);
            int y1 = window.y0() + Math.round((float) rectY1[i] * window.height() / height);
            x0 = Math.max(window.x0(), Math.min(window.x1() - 1, x0));
            x1 = Math.max(x0 + 1, Math.min(window.x1(), x1));
            y0 = Math.max(window.y0(), Math.min(window.y1() - 1, y0));
            y1 = Math.max(y0 + 1, Math.min(window.y1(), y1));
            workX0[i] = x0;
            workX1[i] = x1;
            workY0[i] = y0;
            workY1[i] = y1;
            workAreas[i] = (x1 - x0) * (y1 - y0);
        }
        bX0 = workX0;
        bX1 = workX1;
        bY0 = workY0;
        bY1 = workY1;
        bAreas = workAreas;
    }

    private void ensureRects(List<PixelRect> rects) {
        int count = rects.size();
        if (rectAreas.length < count) {
            sumRed = new int[count];
            sumGreen = new int[count];
            sumBlue = new int[count];
            rectAreas = new int[count];
            rectX0 = new int[count];
            rectX1 = new int[count];
            rectY0 = new int[count];
            rectY1 = new int[count];
            workX0 = new int[count];
            workX1 = new int[count];
            workY0 = new int[count];
            workY1 = new int[count];
            workAreas = new int[count];
        }
        rectCount = count;
        rectAreaSum = 0;
        for (int i = 0; i < count; i++) {
            PixelRect rect = rects.get(i);
            rectAreas[i] = rect.area();
            rectX0[i] = rect.x0();
            rectX1[i] = rect.x1();
            rectY0[i] = rect.y0();
            rectY1[i] = rect.y1();
            rectAreaSum += rectAreas[i];
        }
    }

    public int allocationCount() {
        // The workspaces are sized once per max rect count / capture size.
        return (prefixRed.length > 0 ? 3 : 0) + (rectX0.length > 0 ? 1 : 0);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Row-prefix workspace bound; the width*height accumulator is gone. */
    public static long requiredBytes(int width, int height) {
        return 3L * (width + 1) * 4L;
    }
}
