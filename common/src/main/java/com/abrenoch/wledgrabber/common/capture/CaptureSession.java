package com.abrenoch.wledgrabber.common.capture;

/**
 * Pure CAP-1 session state machine. Android code must follow the order:
 * validation -> consent -> foreground -> one projection request -> one display.
 */
public final class CaptureSession {
    public enum State {
        IDLE,
        VALIDATING,
        AWAITING_CONSENT,
        STARTING,
        CAPTURING,
        STOPPING,
        RELEASED,
        NEEDS_CONSENT
    }

    private State state = State.IDLE;
    private long epoch;
    private boolean consentConsumed;
    private boolean foregrounded;
    private boolean projectionRequested;
    private boolean projectionCreated;
    private boolean displayCreated;

    private int startRequests;
    private int consentRequests;
    private int projectionRequests;
    private int displayCreations;
    private int stopRequests;

    public synchronized boolean beginStart() {
        if (state == State.VALIDATING || state == State.AWAITING_CONSENT
                || state == State.STARTING || state == State.CAPTURING
                || state == State.STOPPING) {
            return false;
        }
        startRequests++;
        epoch++;
        state = State.VALIDATING;
        resetRuntimeFlags();
        return true;
    }

    public synchronized boolean validationPassed() {
        if (state != State.VALIDATING) {
            return false;
        }
        consentRequests++;
        state = State.AWAITING_CONSENT;
        return true;
    }

    /** A consent result is accepted exactly once per session. */
    public synchronized boolean onConsentResult(boolean granted) {
        if (state != State.AWAITING_CONSENT || consentConsumed) {
            return false;
        }
        consentConsumed = true;
        if (!granted) {
            state = State.IDLE;
            return false;
        }
        state = State.STARTING;
        return true;
    }

    public synchronized boolean onServiceForegrounded() {
        if (state != State.STARTING || foregrounded) {
            return false;
        }
        foregrounded = true;
        return true;
    }

    public synchronized boolean requestProjection() {
        if (state != State.STARTING || !foregrounded || projectionRequested) {
            return false;
        }
        projectionRequested = true;
        projectionRequests++;
        return true;
    }

    public synchronized boolean onProjectionCreated(boolean success) {
        if (state != State.STARTING || !projectionRequested || projectionCreated) {
            return false;
        }
        projectionCreated = success;
        if (!success) {
            state = State.IDLE;
        }
        return success;
    }

    public synchronized boolean createDisplay(boolean success) {
        if (state != State.STARTING || !projectionCreated || displayCreated) {
            return false;
        }
        if (!success) {
            state = State.IDLE;
            return false;
        }
        displayCreated = true;
        displayCreations++;
        state = State.CAPTURING;
        return true;
    }

    public synchronized boolean onProjectionRevoked() {
        if (state != State.CAPTURING && state != State.STARTING) {
            return false;
        }
        state = State.NEEDS_CONSENT;
        epoch++;
        resetRuntimeFlags();
        return true;
    }

    public synchronized boolean beginStop() {
        if (state == State.IDLE || state == State.RELEASED
                || state == State.NEEDS_CONSENT || state == State.STOPPING) {
            return false;
        }
        stopRequests++;
        epoch++;
        state = State.STOPPING;
        return true;
    }

    public synchronized boolean stopCompleted() {
        if (state != State.STOPPING) {
            return false;
        }
        state = State.RELEASED;
        resetRuntimeFlags();
        return true;
    }

    /** Service recreation never resumes capture; a new visible consent flow is required. */
    public synchronized boolean serviceRebuilt() {
        if (state == State.CAPTURING || state == State.STARTING || state == State.STOPPING) {
            state = State.NEEDS_CONSENT;
            epoch++;
            resetRuntimeFlags();
            return true;
        }
        return false;
    }

    /**
     * Aborts an in-flight start. Only allowed once the service has entered the
     * foreground, so a rejected start can never skip startForeground().
     */
    public synchronized boolean rejectStart() {
        if (state != State.STARTING || !foregrounded) {
            return false;
        }
        state = State.IDLE;
        resetRuntimeFlags();
        return true;
    }

    public synchronized void resetToIdle() {
        state = State.IDLE;
        resetRuntimeFlags();
    }

    private void resetRuntimeFlags() {
        consentConsumed = false;
        foregrounded = false;
        projectionRequested = false;
        projectionCreated = false;
        displayCreated = false;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized long epoch() {
        return epoch;
    }

    public synchronized int startRequests() {
        return startRequests;
    }

    public synchronized int consentRequests() {
        return consentRequests;
    }

    public synchronized int projectionRequests() {
        return projectionRequests;
    }

    public synchronized int displayCreations() {
        return displayCreations;
    }

    public synchronized int stopRequests() {
        return stopRequests;
    }
}
