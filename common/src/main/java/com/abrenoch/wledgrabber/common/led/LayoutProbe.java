package com.abrenoch.wledgrabber.common.led;

/**
 * Computes which physical LEDs belong to a layout edge or to the configured
 * input position, and builds a one-shot RGB frame that lights only those LEDs.
 * Used by the TV layout page to verify a configuration on the real strip.
 */
public final class LayoutProbe {
    private LayoutProbe() {
    }

    /** Logical ring indices covered by one edge, in ring order. */
    public static int[] logicalForSide(LedLayoutConfig layout, Side side) {
        int top = layout.top();
        int right = layout.right();
        int bottom = layout.bottom();
        int left = layout.left();
        int from;
        int count;
        switch (side) {
            case TOP:
                from = 0;
                count = top;
                break;
            case RIGHT:
                from = top;
                count = right;
                break;
            case BOTTOM:
                from = top + right;
                count = bottom;
                break;
            case LEFT:
                from = top + right + bottom;
                count = left;
                break;
            default:
                from = 0;
                count = 0;
                break;
        }
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = from + i;
        }
        return out;
    }

    /**
     * Logical index of the strip's first physical LED (physical 0). The input
     * position is the logical slot at that LED; with Reverse on, the raw input
     * index maps to the last physical LED instead, so the probe must map back.
     *
     * @return -1 when the layout has no physical LEDs.
     */
    public static int firstPhysicalLogical(CompiledLayout compiled) {
        if (compiled == null || compiled.mapping() == null
                || compiled.mapping().physicalLedCount() <= 0) {
            return -1;
        }
        return compiled.mapping().physicalToLogical(0);
    }

    /**
     * Builds a physical RGB frame with the given logical LEDs lit and everything
     * else black. Gap-black and unmapped LEDs stay black.
     */
    public static byte[] buildFrame(CompiledLayout compiled, int[] logicals,
                                    int red, int green, int blue) {
        return buildFrame(compiled, logicals, 3, null, red, green, blue);
    }

    /**
     * Same as {@link #buildFrame(CompiledLayout, int[], int, int, int)} but packs
     * RGB or RGBW bytes. RGBW uses the same white synthesis as the capture path,
     * so the pulse matches the configured strip format.
     */
    public static byte[] buildFrame(CompiledLayout compiled, int[] logicals, int channels,
                                    WhiteMode whiteMode, int red, int green, int blue) {
        if (compiled == null || logicals == null || (channels != 3 && channels != 4)
                || (channels == 4 && whiteMode == null)) {
            throw new IllegalArgumentException("compiled/logicals/channels required");
        }
        LedMapping mapping = compiled.mapping();
        int physicalCount = mapping.physicalLedCount();
        byte[] frame = new byte[physicalCount * channels];
        int logicalCount = mapping.logicalToPhysical().length;
        for (int logical : logicals) {
            if (logical < 0 || logical >= logicalCount || mapping.isLogicalBlack(logical)) {
                continue;
            }
            int physical = mapping.logicalToPhysical(logical);
            if (physical < 0 || physical >= physicalCount) {
                continue;
            }
            int offset = physical * channels;
            if (channels == 3) {
                frame[offset] = (byte) red;
                frame[offset + 1] = (byte) green;
                frame[offset + 2] = (byte) blue;
            } else {
                RgbwConverter.write(red, green, blue, whiteMode, frame, offset);
            }
        }
        return frame;
    }
}
