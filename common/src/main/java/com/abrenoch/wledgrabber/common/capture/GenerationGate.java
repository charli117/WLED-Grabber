package com.abrenoch.wledgrabber.common.capture;

/** Monotonic generation gate; callbacks from older generations are rejected. */
public final class GenerationGate {
    private volatile long current;

    public synchronized long next() {
        return ++current;
    }

    public long current() {
        return current;
    }

    public boolean isCurrent(long generation) {
        return generation == current;
    }
}
