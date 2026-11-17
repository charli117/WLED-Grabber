package com.abrenoch.wledgrabber.common.led;

/**
 * Suggests per-side LED counts from a confirmed physical count and the display
 * aspect ratio. Top/bottom share the width, left/right share the height; the
 * remainders go to the largest fractional parts (top, bottom, left, right order
 * on ties), so the result always sums to exactly {@code physicalLeds}.
 */
public final class LayoutAutoDistribute {
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    private LayoutAutoDistribute() {
    }

    /**
     * @return counts in {@code {top, bottom, left, right}} order; all zeros when
     *         {@code physicalLeds <= 0}. Non-positive dimensions fall back to 16:9.
     */
    public static int[] distribute(int physicalLeds, int width, int height) {
        int[] sides = new int[4];
        if (physicalLeds <= 0) {
            return sides;
        }
        long w = width > 0 ? width : 16L;
        long h = height > 0 ? height : 9L;
        long totalWeight = 2L * (w + h);
        long[] weights = {w, w, h, h};
        long[] remainders = new long[4];
        long assigned = 0;
        for (int i = 0; i < sides.length; i++) {
            long scaled = (long) physicalLeds * weights[i];
            sides[i] = (int) (scaled / totalWeight);
            remainders[i] = scaled % totalWeight;
            assigned += sides[i];
        }
        int remaining = (int) (physicalLeds - assigned);
        while (remaining > 0) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            sides[best]++;
            remainders[best] = -1L;
            remaining--;
        }
        return sides;
    }
}
