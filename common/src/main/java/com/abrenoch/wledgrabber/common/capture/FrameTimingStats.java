package com.abrenoch.wledgrabber.common.capture;

import java.util.Arrays;

/**
 * Bounded recorder for per-frame timings (microseconds). Records into a ring
 * buffer; percentile queries copy at most {@code capacity} values and sort, so
 * the capture hot path never allocates.
 */
public final class FrameTimingStats {
    private final long[] samples;
    private int count;
    private int next;
    private long total;

    public FrameTimingStats(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        samples = new long[capacity];
    }

    public void recordMicros(long micros) {
        samples[next] = Math.max(0, micros);
        next = (next + 1) % samples.length;
        total += Math.max(0, micros);
        if (count < samples.length) {
            count++;
        }
    }

    public int size() {
        return count;
    }

    public long maxMicros() {
        long max = 0;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }

    public double meanMicros() {
        return count == 0 ? 0 : (double) total / count;
    }

    public long percentileMicros(double percentile) {
        if (count == 0) {
            return 0;
        }
        long[] copy = Arrays.copyOf(samples, count);
        Arrays.sort(copy);
        int index = (int) Math.ceil(percentile / 100.0 * count) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= count) {
            index = count - 1;
        }
        return copy[index];
    }

    /** Keeps the capacity but drops every sample. */
    public void reset() {
        count = 0;
        next = 0;
        total = 0;
        Arrays.fill(samples, 0);
    }
}
