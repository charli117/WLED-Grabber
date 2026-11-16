package com.abrenoch.wledgrabber.common.capture;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

/**
 * Drives a TestPatternSession into the shared three-lease streamer. The caller
 * ticks this on one capture/UI timer; no network call happens on the UI thread
 * when the streamer's sink is asynchronous.
 */
public final class TestPatternRunner {
    private final TestPatternSession session;
    private final LedTestPattern pattern;
    private final LedStreamer streamer;
    private final FramePool pool;
    private final long epoch;
    private final int ledCount;
    private final int channels;
    private final long layoutGeneration;
    private final byte[] scratch;
    private LedStreamer.StopResult lastStop;

    public TestPatternRunner(CompiledLayout layout, int channels, WhiteMode whiteMode,
                             LedStreamer streamer, FramePool pool, long epoch,
                             long durationMs) {
        this.pattern = new LedTestPattern(layout, channels, whiteMode);
        this.session = new TestPatternSession(pattern, durationMs);
        this.streamer = streamer;
        this.pool = pool;
        this.epoch = epoch;
        this.ledCount = pattern.physicalCount();
        this.channels = channels;
        this.layoutGeneration = layout.generation();
        this.scratch = new byte[ledCount * channels];
    }

    public boolean start(LedTestPattern.Type type, int solidRgb,
                         boolean captureActive, long nowMillis) {
        if (!session.start(captureActive, type, solidRgb, nowMillis)) {
            return false;
        }
        streamer.start(epoch, OutputOwner.TEST, ledCount, channels);
        return true;
    }

    public TestPatternSession.State tick(long nowMillis) {
        TestPatternSession.State state = session.render(scratch, nowMillis);
        if (state == TestPatternSession.State.RUNNING) {
            FrameLease lease = pool.acquire(epoch, layoutGeneration, nowMillis,
                    channels, ledCount);
            if (lease != null) {
                System.arraycopy(scratch, 0, lease.data(), 0, scratch.length);
                streamer.offer(lease);
            }
        } else if (state == TestPatternSession.State.FINISHED
                || state == TestPatternSession.State.CANCELLED
                || state == TestPatternSession.State.ERROR) {
            lastStop = streamer.stop("test " + state.name().toLowerCase());
        }
        return state;
    }

    public void cancel() {
        session.cancel();
        lastStop = streamer.stop("test cancelled");
    }

    public LedStreamer.StopResult lastStop() {
        return lastStop;
    }

    public long remainingMillis(long nowMillis) {
        return session.remainingMillis(nowMillis);
    }

    public int walkingIndex() {
        return session.walkingIndex();
    }

    public TestPatternSession.State state() {
        return session.state();
    }

    public int ledCount() {
        return ledCount;
    }

    public int channels() {
        return channels;
    }
}
