package com.abrenoch.wledgrabber.common.network.wled;

/** Readback of /json/state; only on=false and live=false can confirm release. */
public final class StateReadResult {
    private final boolean on;
    private final boolean live;
    private final ProbeError error;

    private StateReadResult(boolean on, boolean live, ProbeError error) {
        this.on = on;
        this.live = live;
        this.error = error;
    }

    public static StateReadResult success(boolean on, boolean live) {
        return new StateReadResult(on, live, null);
    }

    public static StateReadResult failure(ProbeError error) {
        return new StateReadResult(false, false, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public boolean on() {
        return on;
    }

    public boolean live() {
        return live;
    }

    public ProbeError error() {
        return error;
    }
}
