package com.abrenoch.wledgrabber.common.network.wled;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The layout probe is a single-active latch: one edge stays lit until toggled. */
public class ProbeLatchTest {

    @Test
    public void pressingTheSameKeyAgainTurnsItOff() {
        ProbeLatch latch = new ProbeLatch();

        assertEquals(ProbeLatch.Result.STARTED, latch.toggle("probe:TOP"));
        assertTrue(latch.isActive("probe:TOP"));

        assertEquals(ProbeLatch.Result.STOPPED, latch.toggle("probe:TOP"));
        assertFalse(latch.isActive("probe:TOP"));
        assertNull(latch.activeKey());
    }

    @Test
    public void anotherKeyReplacesTheActiveProbe() {
        ProbeLatch latch = new ProbeLatch();
        latch.toggle("probe:TOP");

        assertEquals(ProbeLatch.Result.SWITCHED, latch.toggle("probe:RIGHT"));
        assertFalse(latch.isActive("probe:TOP"));
        assertTrue(latch.isActive("probe:RIGHT"));
    }

    @Test
    public void clearDropsTheActiveProbe() {
        ProbeLatch latch = new ProbeLatch();
        latch.toggle("probe:input");

        latch.clear();

        assertFalse(latch.isActive("probe:input"));
        assertNull(latch.activeKey());
    }
}
