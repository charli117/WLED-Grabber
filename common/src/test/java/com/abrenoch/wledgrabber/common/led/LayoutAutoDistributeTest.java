package com.abrenoch.wledgrabber.common.led;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** Pure aspect-ratio distribution used by the TV "auto layout" action. */
public class LayoutAutoDistributeTest {

    @Test
    public void distributesFirstUnit274At16By9() {
        int[] sides = LayoutAutoDistribute.distribute(274, 16, 9);

        assertEquals(274, sum(sides));
        assertArrayEquals(new int[]{88, 88, 49, 49}, sides);
        assertEquals(sides[0], sides[1]);
        assertEquals(sides[2], sides[3]);
    }

    @Test
    public void distributesReference288At16By9() {
        int[] sides = LayoutAutoDistribute.distribute(288, 16, 9);

        assertEquals(288, sum(sides));
        assertArrayEquals(new int[]{92, 92, 52, 52}, sides);
    }

    @Test
    public void keepsEverySideForSmallCounts() {
        int[] sides = LayoutAutoDistribute.distribute(4, 16, 9);

        assertArrayEquals(new int[]{1, 1, 1, 1}, sides);
        assertEquals(4, sum(sides));
    }

    @Test
    public void followsNonSixteenByNineAspect() {
        int[] sides = LayoutAutoDistribute.distribute(100, 10, 1);

        assertArrayEquals(new int[]{45, 45, 5, 5}, sides);
        assertEquals(100, sum(sides));
    }

    @Test
    public void tieBreaksInSideOrder() {
        int[] sides = LayoutAutoDistribute.distribute(5, 1, 1);

        assertArrayEquals(new int[]{2, 1, 1, 1}, sides);
        assertEquals(5, sum(sides));
    }

    @Test
    public void nonPositiveCountsYieldZeroSides() {
        assertArrayEquals(new int[]{0, 0, 0, 0}, LayoutAutoDistribute.distribute(0, 16, 9));
        assertArrayEquals(new int[]{0, 0, 0, 0}, LayoutAutoDistribute.distribute(-10, 16, 9));
    }

    @Test
    public void invalidDimensionsFallBackToSixteenByNine() {
        assertArrayEquals(new int[]{88, 88, 49, 49}, LayoutAutoDistribute.distribute(274, 0, 0));
        assertArrayEquals(new int[]{88, 88, 49, 49}, LayoutAutoDistribute.distribute(274, -1, 9));
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }
}
