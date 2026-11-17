package com.abrenoch.wledgrabber.common.network.wled;

/** Pure staleness rule shared by the capture service health loop and tests. */
public final class ObservationMonitor {
    private ObservationMonitor() {
    }

    /** A failed or older-than-10s observation is stale, regardless of the last device. */
    public static boolean isStale(WledSession.Observation observation, long nowMillis) {
        return observation == null
                || observation.error() != null
                || observation.isStale(nowMillis);
    }
}
