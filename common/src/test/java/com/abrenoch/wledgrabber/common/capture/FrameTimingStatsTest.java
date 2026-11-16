package com.abrenoch.wledgrabber.common.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FrameTimingStatsTest {
    @Test
    public void percentilesAreComputedOverRecordedSamples() {
        FrameTimingStats stats = new FrameTimingStats(100);
        for (int i = 1; i <= 100; i++) {
            stats.recordMicros(i);
        }
        assertEquals(100, stats.size());
        assertEquals(50, stats.percentileMicros(50.0));
        assertEquals(95, stats.percentileMicros(95.0));
        assertEquals(99, stats.percentileMicros(99.0));
        assertEquals(100, stats.maxMicros());
    }

    @Test
    public void boundedCapacityKeepsTheNewestSamples() {
        FrameTimingStats stats = new FrameTimingStats(3);
        for (int i = 1; i <= 5; i++) {
            stats.recordMicros(i);
        }
        assertEquals(3, stats.size());
        assertEquals(5, stats.maxMicros());
        assertEquals(3, stats.percentileMicros(0.0));
    }

    @Test
    public void emptyStatsReportZero() {
        FrameTimingStats stats = new FrameTimingStats(4);
        assertEquals(0, stats.size());
        assertEquals(0, stats.maxMicros());
        assertEquals(0, stats.percentileMicros(95.0));
    }

    @Test
    public void resetDropsSamples() {
        FrameTimingStats stats = new FrameTimingStats(4);
        stats.recordMicros(7);
        stats.reset();
        assertEquals(0, stats.size());
        assertEquals(0, stats.maxMicros());
    }
}
