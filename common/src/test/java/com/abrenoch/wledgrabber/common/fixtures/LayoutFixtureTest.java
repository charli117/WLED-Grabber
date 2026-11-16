package com.abrenoch.wledgrabber.common.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LayoutFixtureTest {

    @Test
    public void firstUnit274MappingMatchesPinnedAnchors() {
        assertEquals(274, LayoutFixture.COUNT_274);
        assertEquals(86 + 49 + 90 + 49, LayoutFixture.COUNT_274);
        assertEquals(160, LayoutFixture.slot274(0));
        assertEquals(135, LayoutFixture.slot274(25));
        assertEquals(134, LayoutFixture.slot274(26));
        assertEquals(0, LayoutFixture.slot274(160));
        assertEquals(273, LayoutFixture.slot274(161));
        assertEquals(161, LayoutFixture.slot274(273));
    }

    @Test
    public void firstUnit274MappingIsAFullPermutation() {
        boolean[] seen = new boolean[LayoutFixture.COUNT_274];
        for (int physical = 0; physical < LayoutFixture.COUNT_274; physical++) {
            int logical = LayoutFixture.slot274(physical);
            assertTrue("logical index out of range: " + logical, logical >= 0 && logical < seen.length);
            assertTrue("duplicate logical index: " + logical, !seen[logical]);
            seen[logical] = true;
        }
        for (boolean value : seen) {
            assertTrue(value);
        }
    }

    @Test
    public void reference288MappingMatchesPinnedAnchors() {
        assertEquals(288, LayoutFixture.COUNT_288);
        assertEquals(116 + 50 + 72 + 50, LayoutFixture.COUNT_288);
        assertEquals(206, LayoutFixture.slot288(0));
        assertEquals(237, LayoutFixture.slot288(31));
        assertEquals(238, LayoutFixture.slot288(32));
        assertEquals(287, LayoutFixture.slot288(81));
        assertEquals(0, LayoutFixture.slot288(82));
        assertEquals(115, LayoutFixture.slot288(197));
        assertEquals(116, LayoutFixture.slot288(198));
        assertEquals(165, LayoutFixture.slot288(247));
        assertEquals(166, LayoutFixture.slot288(248));
        assertEquals(205, LayoutFixture.slot288(287));
    }

    @Test
    public void reference288MappingIsAFullPermutation() {
        boolean[] seen = new boolean[LayoutFixture.COUNT_288];
        for (int physical = 0; physical < LayoutFixture.COUNT_288; physical++) {
            int logical = LayoutFixture.slot288(physical);
            assertTrue(logical >= 0 && logical < seen.length);
            assertTrue(!seen[logical]);
            seen[logical] = true;
        }
        for (boolean value : seen) {
            assertTrue(value);
        }
    }

    @Test
    public void sideCountsMatchTaskCard() {
        assertArrayEquals(new int[]{86, 49, 90, 49}, LayoutFixture.sides274());
        assertArrayEquals(new int[]{116, 50, 72, 50}, LayoutFixture.sides288());
    }
}
