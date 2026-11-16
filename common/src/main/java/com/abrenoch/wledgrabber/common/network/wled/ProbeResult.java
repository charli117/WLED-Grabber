package com.abrenoch.wledgrabber.common.network.wled;

/** One parsed probe result; error/errorType are non-null only when the observation failed. */
public final class ProbeResult {
    private final WledDevice device;
    private final long observedAtMillis;
    private final ProbeError errorType;
    private final String error;

    private ProbeResult(WledDevice device, long observedAtMillis,
                        ProbeError errorType, String error) {
        this.device = device;
        this.observedAtMillis = observedAtMillis;
        this.errorType = errorType;
        this.error = error;
    }

    public static ProbeResult success(WledDevice device) {
        return new ProbeResult(device, device == null ? 0 : device.observedAtMillis(), null, null);
    }

    public static ProbeResult failure(ProbeError errorType, String error, long observedAtMillis) {
        return new ProbeResult(null, observedAtMillis, errorType, error);
    }

    public static ProbeResult failure(String error, long observedAtMillis) {
        return failure(ProbeError.CONNECT_FAILURE, error, observedAtMillis);
    }

    public boolean isSuccess() {
        return errorType == null && device != null;
    }

    public WledDevice device() {
        return device;
    }

    public long observedAtMillis() {
        return observedAtMillis;
    }

    public ProbeError errorType() {
        return errorType;
    }

    public String error() {
        return error;
    }
}
