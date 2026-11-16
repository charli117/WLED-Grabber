package com.abrenoch.wledgrabber.common.led;

import java.util.List;

/** Samples one ARGB int per logical rect using the shared integral image. */
public final class LedColorSampler {
    private final IntegralImage integral = new IntegralImage();
    private final int[] channel = new int[3];
    private int[] logicalColors = new int[0];

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
        integral.update(plane);
        List<PixelRect> rects = layout.pixelRects();
        for (int i = 0; i < rects.size(); i++) {
            integral.mean(rects.get(i), channel);
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
        int channels = mode == RgbwMode.RGBW ? 4 : 3;
        int physicalCount = layout.mapping().physicalLedCount();
        if (out.length < physicalCount * channels) {
            return false;
        }

        integral.update(plane);
        if (averageColor) {
            integral.meanFull(channel);
            for (int physical = 0; physical < physicalCount; physical++) {
                int offset = physical * channels;
                if (isBlack(layout, physical)) {
                    clear(out, offset, channels);
                } else {
                    writeColor(channel[0], channel[1], channel[2], mode, whiteMode, out, offset);
                }
            }
            return true;
        }

        int count = layout.count();
        if (logicalColors.length < count) {
            logicalColors = new int[count];
        }
        List<PixelRect> rects = layout.pixelRects();
        for (int i = 0; i < count; i++) {
            integral.mean(rects.get(i), channel);
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
        return true;
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
