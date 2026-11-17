package com.abrenoch.wledgrabber.common.led;

import java.util.List;

/** Samples one ARGB int per logical rect using the shared integral image. */
public final class LedColorSampler {
    private final IntegralImage integral = new IntegralImage();
    private final BlackBarCropper cropper = new BlackBarCropper();
    private final int[] channel = new int[3];
    private int[] logicalColors = new int[0];
    private int[] rowLuma = new int[0];
    private int[] columnLuma = new int[0];
    private byte[] cropRow = new byte[0];
    private int[] previous = new int[0];
    private boolean hasPrevious;

    /** Drops SMP-3 hysteresis/smoothing history on session changes. */
    public void resetQualityState() {
        cropper.reset();
        hasPrevious = false;
    }

    /**
     * Returns false for a malformed/mismatched plane; the caller drops the frame.
     * Hot path does not allocate after the first matching frame.
     */
    public boolean sample(PlaneView plane, CompiledLayout layout, int[] argbOut) {
        if (plane == null || layout == null || argbOut == null
                || argbOut.length < layout.count()) {
            return false;
        }
        if (layout.captureWidth() != plane.cropWidth()
                || layout.captureHeight() != plane.cropHeight()) {
            return false;
        }
        List<PixelRect> rects = layout.pixelRects();
        integral.update(plane, rects);
        for (int i = 0; i < rects.size(); i++) {
            integral.mean(i, channel);
            argbOut[i] = 0xff000000
                    | (channel[0] << 16)
                    | (channel[1] << 8)
                    | channel[2];
        }
        return true;
    }

    /**
     * Fills physical RGB/RGBW bytes using the compiled mapping. Average mode reuses
     * the full-domain query but still writes black for BLACK gaps and physical tails.
     */
    public boolean samplePhysical(PlaneView plane, CompiledLayout layout, boolean averageColor,
                                  RgbwMode mode, WhiteMode whiteMode, byte[] out) {
        return samplePhysical(plane, layout, averageColor, mode, whiteMode, out,
                QualitySettings.OFF);
    }

    /**
     * Same contract as the first version; {@code quality} enables the SMP-3
     * options and is inert when every switch is off.
     */
    public boolean samplePhysical(PlaneView plane, CompiledLayout layout, boolean averageColor,
                                  RgbwMode mode, WhiteMode whiteMode, byte[] out,
                                  QualitySettings quality) {
        if (plane == null || layout == null || out == null || layout.mapping() == null) {
            return false;
        }
        if (mode != RgbwMode.RGB && mode != RgbwMode.RGBW) {
            return false;
        }
        if (mode == RgbwMode.RGBW && whiteMode == null) {
            return false;
        }
        if (layout.captureWidth() != plane.cropWidth()
                || layout.captureHeight() != plane.cropHeight()) {
            return false;
        }
        QualitySettings settings = quality == null ? QualitySettings.OFF : quality;
        int channels = mode == RgbwMode.RGBW ? 4 : 3;
        int physicalCount = layout.mapping().physicalLedCount();
        if (out.length < physicalCount * channels) {
            return false;
        }

        CropWindow window = settings.cropBlackBars() ? detectContentWindow(plane) : null;
        List<PixelRect> rects = layout.pixelRects();
        integral.update(plane, rects, averageColor, settings, window);
        int saturation = settings.saturationPercent();
        if (averageColor) {
            integral.meanFull(channel);
            if (settings.hdrCompensation()) {
                ColorQuality.applyHdrCompensation(channel);
            }
            ColorQuality.applySaturation(channel, saturation);
            for (int physical = 0; physical < physicalCount; physical++) {
                int offset = physical * channels;
                if (isBlack(layout, physical)) {
                    clear(out, offset, channels);
                } else {
                    writeColor(channel[0], channel[1], channel[2], mode, whiteMode, out, offset);
                }
            }
            applySmoothing(settings, out, physicalCount, channels);
            return true;
        }

        int count = layout.count();
        if (logicalColors.length < count) {
            logicalColors = new int[count];
        }
        for (int i = 0; i < count; i++) {
            integral.mean(i, channel);
            if (settings.hdrCompensation()) {
                ColorQuality.applyHdrCompensation(channel);
            }
            ColorQuality.applySaturation(channel, saturation);
            logicalColors[i] = (channel[0] << 16) | (channel[1] << 8) | channel[2];
        }
        for (int physical = 0; physical < physicalCount; physical++) {
            int offset = physical * channels;
            int logical = layout.mapping().physicalToLogical(physical);
            if (isBlack(layout, physical) || logical < 0) {
                clear(out, offset, channels);
                continue;
            }
            int color = logicalColors[logical];
            writeColor((color >>> 16) & 0xff, (color >>> 8) & 0xff, color & 0xff,
                    mode, whiteMode, out, offset);
        }
        applySmoothing(settings, out, physicalCount, channels);
        return true;
    }

    private CropWindow detectContentWindow(PlaneView plane) {
        int width = plane.cropWidth();
        int height = plane.cropHeight();
        if (rowLuma.length < height) {
            rowLuma = new int[height];
        }
        if (columnLuma.length < width) {
            columnLuma = new int[width];
        }
        int span = (width - 1) * plane.pixelStride() + 4;
        if (cropRow.length < span) {
            cropRow = new byte[span];
        }
        java.util.Arrays.fill(rowLuma, 0, height, 0);
        java.util.Arrays.fill(columnLuma, 0, width, 0);
        int stride = plane.pixelStride();
        for (int y = 0; y < height; y++) {
            plane.readRow(y, cropRow);
            int src = 0;
            for (int x = 0; x < width; x++) {
                int luma = ((cropRow[src] & 0xff) * 77
                        + (cropRow[src + 1] & 0xff) * 150
                        + (cropRow[src + 2] & 0xff) * 29) >> 8;
                rowLuma[y] += luma;
                columnLuma[x] += luma;
                src += stride;
            }
            rowLuma[y] /= width;
        }
        for (int x = 0; x < width; x++) {
            columnLuma[x] /= height;
        }
        return cropper.update(width, height, rowLuma, columnLuma);
    }

    private void applySmoothing(QualitySettings settings, byte[] out, int physicalCount,
                                int channels) {
        int percent = settings.temporalSmoothingPercent();
        int length = physicalCount * channels;
        if (percent <= 0) {
            hasPrevious = false;
            return;
        }
        if (previous.length < length) {
            previous = new int[length];
        }
        for (int i = 0; i < length; i++) {
            if (hasPrevious) {
                int current = out[i] & 0xff;
                int blended = previous[i] + (current - previous[i]) * (100 - percent) / 100;
                out[i] = (byte) blended;
                previous[i] = blended;
            } else {
                previous[i] = out[i] & 0xff;
            }
        }
        hasPrevious = true;
    }

    private static boolean isBlack(CompiledLayout layout, int physical) {
        return layout.mapping().isPhysicalBlack(physical)
                || layout.mapping().physicalToLogical(physical) < 0;
    }

    private static void clear(byte[] out, int offset, int channels) {
        for (int i = 0; i < channels; i++) {
            out[offset + i] = 0;
        }
    }

    private static void writeColor(int red, int green, int blue, RgbwMode mode,
                                   WhiteMode whiteMode, byte[] out, int offset) {
        if (mode == RgbwMode.RGB) {
            out[offset] = (byte) red;
            out[offset + 1] = (byte) green;
            out[offset + 2] = (byte) blue;
        } else {
            RgbwConverter.write(red, green, blue, whiteMode, out, offset);
        }
    }

    public int allocationCount() {
        return integral.allocationCount();
    }

    public int integralWidth() {
        return integral.width();
    }

    public int integralHeight() {
        return integral.height();
    }
}
