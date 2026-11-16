package com.abrenoch.wledgrabber.common.capture;

/** Default 10-second TEST session; refuses to start while capture owns the output. */
public final class TestPatternSession {
    public enum State {
        IDLE,
        RUNNING,
        FINISHED,
        CANCELLED,
        ERROR
    }

    public static final long DEFAULT_DURATION_MS = 10000;

    private final LedTestPattern pattern;
    private final long durationMs;
    private State state = State.IDLE;
    private LedTestPattern.Type type;
    private int solidRgb;
    private long startedAt;
    private int walking;
    private String error;

    public TestPatternSession(LedTestPattern pattern) {
        this(pattern, DEFAULT_DURATION_MS);
    }

    public TestPatternSession(LedTestPattern pattern, long durationMs) {
        if (pattern == null || durationMs <= 0) {
            throw new IllegalArgumentException("pattern/duration required");
        }
        this.pattern = pattern;
        this.durationMs = durationMs;
    }

    public boolean start(boolean captureActive, LedTestPattern.Type type, int solidRgb, long nowMillis) {
        if (captureActive) {
            state = State.ERROR;
            error = "stop capture before TEST";
            return false;
        }
        if (state == State.RUNNING || state == State.FINISHED
                || state == State.CANCELLED || type == null) {
            return false;
        }
        this.type = type;
        this.solidRgb = solidRgb;
        this.startedAt = nowMillis;
        this.walking = 0;
        this.error = null;
        this.state = State.RUNNING;
        return true;
    }

    public State render(byte[] out, long nowMillis) {
        if (state != State.RUNNING) {
            return state;
        }
        if (nowMillis - startedAt >= durationMs) {
            state = State.FINISHED;
            return state;
        }
        if (!pattern.render(type, solidRgb, walking, out)) {
            state = State.ERROR;
            error = "pattern render failed";
            return state;
        }
        walking = (walking + 1) % pattern.physicalCount();
        return state;
    }

    public void cancel() {
        if (state == State.RUNNING) {
            state = State.CANCELLED;
        }
    }

    public State state() {
        return state;
    }

    public long remainingMillis(long nowMillis) {
        return Math.max(0, durationMs - (nowMillis - startedAt));
    }

    public int walkingIndex() {
        return walking;
    }

    public String error() {
        return error;
    }
}
