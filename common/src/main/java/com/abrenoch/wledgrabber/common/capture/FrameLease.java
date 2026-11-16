package com.abrenoch.wledgrabber.common.capture;

/** Preallocated frame buffer; ownership is transferred on offer. */
public final class FrameLease {
    public enum State {
        FREE,
        PRODUCING,
        PENDING,
        IN_FLIGHT
    }

    private final int index;
    private final byte[] data;
    private volatile State state = State.FREE;
    private long sessionEpoch;
    private long layoutGeneration;
    private long timestampNanos;
    private int channels;
    private int ledCount;

    FrameLease(int index, int bytes) {
        this.index = index;
        this.data = new byte[bytes];
    }

    void occupy(long sessionEpoch, long layoutGeneration, long timestampNanos,
                int channels, int ledCount) {
        this.sessionEpoch = sessionEpoch;
        this.layoutGeneration = layoutGeneration;
        this.timestampNanos = timestampNanos;
        this.channels = channels;
        this.ledCount = ledCount;
        this.state = State.PRODUCING;
    }

    void markPending() {
        this.state = State.PENDING;
    }

    void markInFlight() {
        this.state = State.IN_FLIGHT;
    }

    void markFree() {
        this.state = State.FREE;
    }

    public int index() {
        return index;
    }

    public byte[] data() {
        return data;
    }

    public State state() {
        return state;
    }

    public long sessionEpoch() {
        return sessionEpoch;
    }

    public long layoutGeneration() {
        return layoutGeneration;
    }

    public long timestampNanos() {
        return timestampNanos;
    }

    public int channels() {
        return channels;
    }

    public int ledCount() {
        return ledCount;
    }
}
