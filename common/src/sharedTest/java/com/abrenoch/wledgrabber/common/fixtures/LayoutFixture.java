package com.abrenoch.wledgrabber.common.fixtures;

/**
 * Pinned Classic-layout inputs and logical mappings for the first unit
 * (274 LEDs, reverse=true) and the reference example (288 LEDs).
 */
public final class LayoutFixture {
    public static final int COUNT_274 = 274;
    public static final int TOP_274 = 86;
    public static final int RIGHT_274 = 49;
    public static final int BOTTOM_274 = 90;
    public static final int LEFT_274 = 49;
    public static final int INPUT_274 = 161;
    public static final boolean REVERSE_274 = true;

    public static final int COUNT_288 = 288;
    public static final int TOP_288 = 116;
    public static final int RIGHT_288 = 50;
    public static final int BOTTOM_288 = 72;
    public static final int LEFT_288 = 50;
    public static final int INPUT_288 = 206;
    public static final boolean REVERSE_288 = false;

    private LayoutFixture() {
    }

    /** First-unit physical index to logical slot: floorMod(160 - p, 274). */
    public static int slot274(int physical) {
        return Math.floorMod(160 - physical, COUNT_274);
    }

    /** Reference-example physical index to logical slot: (p + 206) mod 288. */
    public static int slot288(int physical) {
        return Math.floorMod(physical + INPUT_288, COUNT_288);
    }

    public static int[] sides274() {
        return new int[]{TOP_274, RIGHT_274, BOTTOM_274, LEFT_274};
    }

    public static int[] sides288() {
        return new int[]{TOP_288, RIGHT_288, BOTTOM_288, LEFT_288};
    }
}
