package com.abrenoch.wledgrabber.common.capture;

/** Exactly three preallocated frame leases. */
public final class FramePool {
    public static final int POOL_SIZE = 3;

    private final FrameLease[] leases;

    public FramePool(int frameBytes) {
        if (frameBytes <= 0) {
            throw new IllegalArgumentException("frameBytes must be positive");
        }
        leases = new FrameLease[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            leases[i] = new FrameLease(i, frameBytes);
        }
    }

    public synchronized FrameLease acquire(long sessionEpoch, long layoutGeneration,
                                           long timestampNanos, int channels, int ledCount) {
        if (channels < 3 || channels > 4 || ledCount < 1) {
            throw new IllegalArgumentException("invalid channels/ledCount");
        }
        if ((long) channels * ledCount > leases[0].data().length) {
            throw new IllegalArgumentException("frame does not fit the pool buffer");
        }
        for (FrameLease lease : leases) {
            if (lease.state() == FrameLease.State.FREE) {
                lease.occupy(sessionEpoch, layoutGeneration, timestampNanos, channels, ledCount);
                return lease;
            }
        }
        return null;
    }

    public synchronized void release(FrameLease lease) {
        if (lease == null) {
            return;
        }
        if (lease.state() == FrameLease.State.FREE) {
            throw new IllegalStateException("frame lease released twice");
        }
        lease.markFree();
    }

    public synchronized int inUse() {
        int count = 0;
        for (FrameLease lease : leases) {
            if (lease.state() != FrameLease.State.FREE) {
                count++;
            }
        }
        return count;
    }

    public FrameLease lease(int index) {
        return leases[index];
    }
}
