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

    /** The input marker's logical index, normalised into the effective ring. */
    public static int inputLogical(LedLayoutConfig layout) {
        int effective = layout.effectiveLedCount();
        if (effective <= 0) {
            return 0;
        }
        return Math.floorMod(layout.inputPosition(), effective);
    }

    /**
     * Builds a physical RGB frame with the given logical LEDs lit and everything
     * else black. Gap-black and unmapped LEDs stay black.
     */
    public static byte[] buildFrame(CompiledLayout compiled, int[] logicals,
                                    int red, int green, int blue) {
        LedMapping mapping = compiled.mapping();
        int physicalCount = mapping.physicalLedCount();
        byte[] frame = new byte[physicalCount * 3];
        int logicalCount = mapping.logicalToPhysical().length;
        for (int logical : logicals) {
            if (logical < 0 || logical >= logicalCount || mapping.isLogicalBlack(logical)) {
                continue;
            }
            int physical = mapping.logicalToPhysical(logical);
            if (physical < 0 || physical >= physicalCount) {
                continue;
            }
            frame[physical * 3] = (byte) red;
            frame[physical * 3 + 1] = (byte) green;
            frame[physical * 3 + 2] = (byte) blue;
        }
        return frame;
    }
}
