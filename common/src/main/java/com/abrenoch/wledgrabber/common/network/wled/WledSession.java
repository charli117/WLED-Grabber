package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.RgbwMode;


/**
 * Observation and unified release owner. Release is CONFIRMED only when a
 * readback returns on=false and live=false; late local results never mutate a
 * newer epoch.
 */
public final class WledSession {
    public static final long HEALTH_INTERVAL_MS = 5000;
    public static final long STALE_AFTER_MS = 10000;
    public static final long RELEASE_BUDGET_MS = 3000;

    public enum State {
        IDLE,
        ACTIVE,
        STOPPING,
        RELEASED
    }

    public enum ReleaseStatus {
        CONFIRMED,
        UNCONFIRMED
    }

    public static final class Observation {
        private final WledDevice device;
        private final long observedAtMillis;
        private final ProbeError error;
        private final boolean identityChanged;

        private Observation(WledDevice device, long observedAtMillis,
                            ProbeError error, boolean identityChanged) {
            this.device = device;
            this.observedAtMillis = observedAtMillis;
            this.error = error;
            this.identityChanged = identityChanged;
        }

        public WledDevice device() {
            return device;
        }

        public long observedAtMillis() {
            return observedAtMillis;
        }

        public ProbeError error() {
            return error;
        }

        public boolean identityChanged() {
            return identityChanged;
        }

        public boolean isStale(long nowMillis) {
            return nowMillis - observedAtMillis > STALE_AFTER_MS;
        }
    }

    public static final class ReleaseResult {
        private final ReleaseStatus status;
        private final long epoch;
        private final String detail;
        private final boolean blackSent;
        private final boolean offAccepted;
        private final StateReadResult stateRead;

        private ReleaseResult(ReleaseStatus status, long epoch, String detail,
                              boolean blackSent, boolean offAccepted, StateReadResult stateRead) {
            this.status = status;
            this.epoch = epoch;
            this.detail = detail;
            this.blackSent = blackSent;
            this.offAccepted = offAccepted;
            this.stateRead = stateRead;
        }

        public ReleaseStatus status() {
            return status;
        }

        public long epoch() {
            return epoch;
        }

        public String detail() {
            return detail;
        }

        public boolean blackSent() {
            return blackSent;
        }

        public boolean offAccepted() {
            return offAccepted;
        }

        public StateReadResult stateRead() {
            return stateRead;
        }
    }

    public interface Clock {
        long now();
    }

    private final WledControlClient control;
    private final FrameSender sender;
    private final Clock clock;
    private final Object lock = new Object();

    private State state = State.IDLE;
    private long epoch;
    private ReadyDevice ready;
    private Observation observation;
    private long lastProbeAt = -1;

    public WledSession(WledControlClient control, FrameSender sender, Clock clock) {
        this.control = control;
        this.sender = sender;
        this.clock = clock;
    }

    public long start(ReadyDevice readyDevice) {
        if (readyDevice == null) {
            throw new IllegalArgumentException("ready device is required");
        }
        synchronized (lock) {
            if (state == State.ACTIVE || state == State.STOPPING) {
                throw new IllegalStateException("session already active/stopping");
            }
            epoch++;
            state = State.ACTIVE;
            ready = readyDevice;
            long now = clock.now();
            observation = new Observation(readyDevice.observed(), now, null, false);
            lastProbeAt = now;
            return epoch;
        }
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public long epoch() {
        synchronized (lock) {
            return epoch;
        }
    }

    public Observation currentObservation() {
        synchronized (lock) {
            return observation;
        }
    }

    /** Probes at most once per 5 seconds; a changed identity invalidates the endpoint. */
    public Observation observeNow() {
        long probeEpoch;
        ReadyDevice active;
        long now = clock.now();
        synchronized (lock) {
            if (state != State.ACTIVE || ready == null) {
                return observation;
            }
            if (lastProbeAt >= 0 && now - lastProbeAt < HEALTH_INTERVAL_MS) {
                return observation;
            }
            lastProbeAt = now;
            probeEpoch = epoch;
            active = ready;
        }

        ProbeResult result = control.probe(active.endpoint().host(),
                active.endpoint().httpPort());
        synchronized (lock) {
            if (epoch != probeEpoch || state != State.ACTIVE || ready != active) {
                return observation;
            }
            if (!result.isSuccess()) {
                observation = new Observation(observation == null ? null : observation.device(),
                        now, result.errorType(), false);
                return observation;
            }
            WledDevice device = result.device();
            boolean changed = !device.deviceId().equals(active.observed().deviceId())
                    || !device.address().equalsIgnoreCase(active.endpoint().host());
            observation = new Observation(device, now, null, changed);
            if (changed) {
                epoch++;
                state = State.IDLE;
                ready = null;
            }
            return observation;
        }
    }

    /** Network change/identity change: discard the endpoint and require a fresh observation. */
    public void invalidate() {
        synchronized (lock) {
            if (state == State.ACTIVE || state == State.STOPPING) {
                epoch++;
                state = State.IDLE;
                ready = null;
            }
        }
    }

    public ReleaseResult release(String reason) {
        long releaseEpoch;
        ReadyDevice active;
        synchronized (lock) {
            if (state != State.ACTIVE || ready == null) {
                return new ReleaseResult(ReleaseStatus.UNCONFIRMED, epoch,
                        "no active session", false, false, null);
            }
            releaseEpoch = epoch;
            active = ready;
            state = State.STOPPING;
        }

        long startedAt = clock.now();
        boolean blackSent = false;
        try {
            blackSent = sender.sendBlack(active.sendLedCount(),
                    active.endpoint().rgbwMode() == RgbwMode.RGBW);
        } catch (RuntimeException ignored) {
            blackSent = false;
        }

        boolean offAccepted = false;
        try {
            offAccepted = control.turnOff(active.endpoint().host(),
                    active.endpoint().httpPort());
        } catch (RuntimeException ignored) {
            offAccepted = false;
        }

        StateReadResult readback;
        try {
            readback = control.readState(active.endpoint().host(),
                    active.endpoint().httpPort());
        } catch (RuntimeException e) {
            readback = StateReadResult.failure(ProbeError.TIMEOUT);
        }
        long elapsed = clock.now() - startedAt;

        synchronized (lock) {
            if (epoch != releaseEpoch) {
                return new ReleaseResult(ReleaseStatus.UNCONFIRMED, releaseEpoch,
                        "epoch changed during release", blackSent, offAccepted, readback);
            }
            state = State.RELEASED;
            ready = null;
            boolean confirmed = elapsed <= RELEASE_BUDGET_MS
                    && offAccepted
                    && readback != null
                    && readback.isSuccess()
                    && !readback.on()
                    && !readback.live();
            return new ReleaseResult(confirmed ? ReleaseStatus.CONFIRMED
                    : ReleaseStatus.UNCONFIRMED, releaseEpoch,
                    confirmed ? "off/live readback false" : "release not confirmed",
                    blackSent, offAccepted, readback);
        }
    }
}
