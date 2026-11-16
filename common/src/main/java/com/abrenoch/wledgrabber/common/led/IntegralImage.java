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
 * <p>Workspace arrays are reallocated only when the capture size or the rect
 * count grows, so the steady-state hot path allocates nothing.</p>
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
    private byte[] rowBytes;

    private int width;
    private int height;
    private int rectCount;
    private long rectAreaSum;
    private long totalRed;
    private long totalGreen;
    private long totalBlue;
    private boolean lastFullMeanValid;
    private int allocationCount;

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
        allocationCount += 3;
        width = newWidth;
        height = newHeight;
    }

    /** Computes every rect mean for this frame; caller passes the layout rects. */
    public void update(PlaneView plane, List<PixelRect> rects) {
        update(plane, rects, false);
    }

    /**
     * @param needFullMean also accumulate whole-frame sums for {@link #meanFull}
     *                     (forces the full-frame scan path).
     */
    public void update(PlaneView plane, List<PixelRect> rects, boolean needFullMean) {
        ensureCapacity(plane.cropWidth(), plane.cropHeight());
        ensureRects(rects);
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
                if (y < rectY0[i] || y >= rectY1[i]) {
                    continue;
                }
                int red = 0;
                int green = 0;
                int blue = 0;
                int src = rectX0[i] * pixelStride;
                int end = rectX1[i] * pixelStride;
                for (; src < end; src += pixelStride) {
                    red += rowBytes[src] & 0xff;
                    green += rowBytes[src + 1] & 0xff;
                    blue += rowBytes[src + 2] & 0xff;
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
            for (int x = 0; x < width; x++) {
                red += rowBytes[src] & 0xff;
                green += rowBytes[src + 1] & 0xff;
                blue += rowBytes[src + 2] & 0xff;
                src += pixelStride;
                prefixRed[x + 1] = red;
                prefixGreen[x + 1] = green;
                prefixBlue[x + 1] = blue;
            }
            totalRed += red;
            totalGreen += green;
            totalBlue += blue;
            for (int i = 0; i < rectCount; i++) {
                if (y < rectY0[i] || y >= rectY1[i]) {
                    continue;
                }
                sumRed[i] += prefixRed[rectX1[i]] - prefixRed[rectX0[i]];
                sumGreen[i] += prefixGreen[rectX1[i]] - prefixGreen[rectX0[i]];
                sumBlue[i] += prefixBlue[rectX1[i]] - prefixBlue[rectX0[i]];
            }
        }
    }

    /** Fills out[0..2] with floor(sum/area) for the rect at {@code index}. */
    public void mean(int index, int[] out) {
        int area = rectAreas[index];
        out[0] = sumRed[index] / area;
        out[1] = sumGreen[index] / area;
        out[2] = sumBlue[index] / area;
    }

    /** Full-domain mean without allocating a rectangle. */
    public void meanFull(int[] out) {
        if (!lastFullMeanValid) {
            throw new IllegalStateException("meanFull requires a full-frame update");
        }
        int area = width * height;
        out[0] = (int) (totalRed / area);
        out[1] = (int) (totalGreen / area);
        out[2] = (int) (totalBlue / area);
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
            allocationCount += 4;
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
        return allocationCount;
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
