package com.abrenoch.wledgrabber.common.capture;

import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPatternRunnerTest {

    private static CompiledLayout layout() {
        LedLayoutConfig config = new LedLayoutConfig(4, 0, 0, 0, 0, 0, 0,
                false, GapMode.SKIP, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result = new ClassicLedLayout().compile(
                config, 4, 100, 100);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static final class RecordingSink implements LedStreamer.FrameSink {
        private final AtomicInteger frames = new AtomicInteger();
        private final AtomicInteger blacks = new AtomicInteger();
        private final AtomicBoolean release = new AtomicBoolean();
        private byte[] last;
        private long blackAfterFrame = -1;

        @Override
        public boolean send(FrameLease lease) {
            last = Arrays.copyOf(lease.data(), lease.data().length);
            frames.incrementAndGet();
            return true;
        }

        @Override
        public boolean sendBlack(int ledCount, int channels) {
            blackAfterFrame = frames.get();
            blacks.incrementAndGet();
            return true;
        }
    }

    private static void await(AtomicInteger counter, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (counter.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue("expected " + expected + " but got " + counter.get(),
                counter.get() >= expected);
    }

    @Test
    public void sidesPatternFlowsThroughStreamerAndStopsWithBlack() throws Exception {
        CompiledLayout layout = layout();
        RecordingSink sink = new RecordingSink();
        FramePool pool = new FramePool(4 * 3);
        LedStreamer streamer = new LedStreamer(pool, sink,
                reason -> {
                    sink.release.set(true);
                    return true;
                }, 500);
        TestPatternRunner runner = new TestPatternRunner(layout, 3, null,
                streamer, pool, 1, 1000);

        assertTrue(runner.start(LedTestPattern.Type.SIDES, 0, false, 0));
        assertTrue(runner.tick(0) == TestPatternSession.State.RUNNING);
        await(sink.frames, 1);
        assertEquals(1, runner.walkingIndex());

        assertEquals(TestPatternSession.State.FINISHED, runner.tick(1000));
        await(sink.blacks, 1);
        assertEquals(1, sink.blacks.get());
        assertEquals(1, sink.blackAfterFrame);
        assertTrue(sink.release.get());
        assertEquals(0, pool.inUse());
        assertFalse(runner.start(LedTestPattern.Type.SIDES, 0, false, 1001));
    }

    @Test
    public void walkingPatternSendsExactlyOneLitLed() throws Exception {
        CompiledLayout layout = layout();
        RecordingSink sink = new RecordingSink();
        FramePool pool = new FramePool(4 * 3);
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> true, 500);
        TestPatternRunner runner = new TestPatternRunner(layout, 3, null,
                streamer, pool, 1, 10000);
        assertTrue(runner.start(LedTestPattern.Type.WALKING, 0xffffff, false, 0));
        runner.tick(0);
        await(sink.frames, 1);

        int lit = 0;
        for (int i = 0; i < sink.last.length; i += 3) {
            if (sink.last[i] != 0 || sink.last[i + 1] != 0 || sink.last[i + 2] != 0) {
                lit++;
            }
        }
        assertEquals(1, lit);
        runner.cancel();
        await(sink.blacks, 1);
    }

    @Test
    public void captureActiveOrCancelStopsOnce() throws Exception {
        CompiledLayout layout = layout();
        RecordingSink sink = new RecordingSink();
        FramePool pool = new FramePool(4 * 3);
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> true, 500);
        TestPatternRunner runner = new TestPatternRunner(layout, 3, null,
                streamer, pool, 1, 10000);

        assertFalse(runner.start(LedTestPattern.Type.SIDES, 0, true, 0));
        assertTrue(runner.start(LedTestPattern.Type.SIDES, 0, false, 0));
        runner.cancel();
        runner.cancel();
        await(sink.blacks, 1);
        assertEquals(1, sink.blacks.get());
        assertEquals(0, pool.inUse());
    }
}
