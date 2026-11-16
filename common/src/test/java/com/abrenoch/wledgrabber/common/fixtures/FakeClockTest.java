package com.abrenoch.wledgrabber.common.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FakeClockTest {

    @Test
    public void advancesOnlyForwardByExplicitDuration() {
        FakeClock clock = new FakeClock(1000L);
        assertEquals(1000L, clock.now());
        clock.advance(250L);
        assertEquals(1250L, clock.now());
        clock.advance(0L);
        assertEquals(1250L, clock.now());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAdvance() {
        new FakeClock(0L).advance(-1L);
    }
}
