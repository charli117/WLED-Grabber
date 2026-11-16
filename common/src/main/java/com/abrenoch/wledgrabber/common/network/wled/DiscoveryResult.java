package com.abrenoch.wledgrabber.common.network.wled;

import java.util.Collections;
import java.util.List;

/** Bounded discovery outcome; partial means timeout/cancel left candidates unobserved. */
public final class DiscoveryResult {
    private final List<WledDevice> devices;
    private final List<ProbeResult> failures;
    private final boolean partial;
    private final boolean cancelled;
    private final int candidatesConsidered;

    public DiscoveryResult(List<WledDevice> devices, List<ProbeResult> failures,
                           boolean partial, boolean cancelled, int candidatesConsidered) {
        this.devices = Collections.unmodifiableList(devices);
        this.failures = Collections.unmodifiableList(failures);
        this.partial = partial;
        this.cancelled = cancelled;
        this.candidatesConsidered = candidatesConsidered;
    }

    public List<WledDevice> devices() {
        return devices;
    }

    public List<ProbeResult> failures() {
        return failures;
    }

    public boolean partial() {
        return partial;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public int candidatesConsidered() {
        return candidatesConsidered;
    }
}
