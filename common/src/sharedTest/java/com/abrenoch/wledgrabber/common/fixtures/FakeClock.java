package com.abrenoch.wledgrabber.common.fixtures;

/** Deterministic clock shared by time-dependent tests. */
public final class FakeClock {
    private long now;

    public FakeClock(long startMillis) {
        this.now = startMillis;
    }

    public long now() {
        return now;
    }

    public void advance(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("time cannot move backwards");
        }
        now += millis;
    }
}
