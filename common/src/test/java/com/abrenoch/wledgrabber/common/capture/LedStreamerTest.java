package com.abrenoch.wledgrabber.common.capture;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedStreamerTest {
    private static final int CHANNELS = 4;
    private static final int LEDS = 4;
    private static final int FRAME_BYTES = CHANNELS * LEDS;

    private static void writeId(FrameLease lease, long id) {
        byte[] data = lease.data();
        for (int i = 0; i < 8; i++) {
            data[i] = (byte) (id >>> (8 * i));
        }
        for (int i = 8; i < data.length; i++) {
            data[i] = (byte) id;
        }
    }

    private static long readId(byte[] data) {
        long id = 0;
        for (int i = 0; i < 8; i++) {
            id |= (long) (data[i] & 0xff) << (8 * i);
        }
        return id;
    }

    private static class RecordingSink implements LedStreamer.FrameSink {
        private long lastId = -1;
        private int sent;
        private int tears;
        private int outOfOrder;
        int blacks;
        long blackAfterLastColor = -1;

        @Override
        public boolean send(FrameLease lease) {
            long id = readId(lease.data());
            for (int i = 8; i < lease.data().length; i++) {
                if (lease.data()[i] != (byte) id) {
                    tears++;
                    return false;
                }
            }
            if (id <= lastId) {
                outOfOrder++;
                return false;
            }
            lastId = id;
            sent++;
            return true;
        }

        @Override
        public boolean sendBlack(int ledCount, int channels) {
            blacks++;
            blackAfterLastColor = lastId;
            return true;
        }
    }

    private static final class BlockingSink extends RecordingSink {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public boolean send(FrameLease lease) {
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.send(lease);
        }

        private void unblock() {
            release.countDown();
        }

        private boolean awaitEntered() throws InterruptedException {
            return entered.await(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void stopWhilePowerOnIsPendingDropsColorBeforeBlackAndOff() throws Exception {
        CountDownLatch poweringOn = new CountDownLatch(1);
        CountDownLatch allowOn = new CountDownLatch(1);
        CountDownLatch off = new CountDownLatch(1);
        StringBuffer events = new StringBuffer();
        FramePool pool = new FramePool(FRAME_BYTES);
        LedStreamer.FrameSink sink = new LedStreamer.FrameSink() {
            @Override
            public void beforeFirstColor() {
                events.append("on;");
                poweringOn.countDown();
                try {
                    if (!allowOn.await(4, TimeUnit.SECONDS)) throw new AssertionError("power-on stalled");
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
            @Override
            public boolean send(FrameLease lease) { events.append("color;"); return true; }
            @Override
            public boolean sendBlack(int leds, int channels) { events.append("black;"); return true; }
        };
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> {
            events.append("off;"); off.countDown(); return true;
        }, 50);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);
        try {
            assertEquals("", events.toString());
            assertTrue(streamer.offer(pool.acquire(1, 1, 0, CHANNELS, LEDS)));
            assertTrue("first frame must order power-on on the writer", poweringOn.await(1, TimeUnit.SECONDS));
            assertTrue("stop must not hang behind HTTP", streamer.stop("sleep").timedOut());
        } finally {
            allowOn.countDown();
        }
        assertTrue(off.await(1, TimeUnit.SECONDS));
        assertEquals("on;black;off;", events.toString());
        assertEquals(0, pool.inUse());
    }

    @Test
    public void timedOutReleaseRetainsLeaseUntilTheWriterFinishes() throws Exception {
        OutputSessionCoordinator gate = new OutputSessionCoordinator();
        assertTrue(gate.acquire(OutputOwner.CAPTURE, "capture"));
        CountDownLatch finish = new CountDownLatch(1);
        LedStreamer streamer = new LedStreamer(new FramePool(FRAME_BYTES), new RecordingSink(),
                gate.releaseWhenDone(OutputOwner.CAPTURE, "capture", gate.epoch(), reason -> {
                    try {
                        return finish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }), 50);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);
        try {
            assertTrue(streamer.stop("sleep").timedOut());
            assertFalse(gate.acquire(OutputOwner.TEST, "new owner"));
        } finally {
            finish.countDown();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (gate.owner() != OutputOwner.NONE && System.nanoTime() < deadline) Thread.yield();
        assertTrue(gate.acquire(OutputOwner.TEST, "new owner"));
    }

    @Test
    public void hundredThousandFramesHaveNoTearingOrLeaks() {
        FramePool pool = new FramePool(FRAME_BYTES);
        RecordingSink sink = new RecordingSink();
        AtomicBoolean released = new AtomicBoolean(false);
        LedStreamer streamer = new LedStreamer(pool, sink,
                reason -> {
                    released.set(true);
                    return true;
                }, 2000);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);

        int acquired = 0;
        for (long id = 0; id < 100000; id++) {
            FrameLease lease = pool.acquire(1, 1, id, CHANNELS, LEDS);
            if (lease == null) {
                continue;
            }
            acquired++;
            writeId(lease, id);
            streamer.offer(lease);
        }

        LedStreamer.StopResult stop = streamer.stop("end");
        assertFalse(stop.timedOut());
        assertTrue(stop.blackSent());
        assertTrue(stop.releaseCalled());
        assertTrue(released.get());
        assertTrue(sink.sent > 0);
        assertTrue(sink.sent <= acquired);
        assertEquals(0, sink.tears);
        assertEquals(0, sink.outOfOrder);
        assertEquals(0, pool.inUse());
        LedStreamer.Metrics metrics = streamer.metrics();
        assertEquals(acquired, metrics.offered());
        assertFalse(metrics.pending());
        assertFalse(metrics.inFlight());
        assertEquals(0, metrics.poolInUse());
    }

    @Test
    public void sparseOffersEventuallyDrainWithoutAnotherOfferOrStopStress() {
        FramePool pool = new FramePool(FRAME_BYTES);
        RecordingSink sink = new RecordingSink();
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> true, 2000);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);

        try {
            // Exercise the drain-to-idle race through public behavior. Scheduling is
            // probabilistic: neither the sink nor metrics can pause the idle transition.
            int frames = 10000;
            for (int id = 0; id < frames; id++) {
                FrameLease lease = pool.acquire(1, 1, id, CHANNELS, LEDS);
                assertTrue("a drained frame must return its lease", lease != null);
                writeId(lease, id);
                assertTrue(streamer.offer(lease));

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                LedStreamer.Metrics metrics = streamer.metrics();
                while (metrics.sent() < id + 1 && System.nanoTime() < deadline) {
                    Thread.yield();
                    metrics = streamer.metrics();
                }
                assertEquals("accepted frame must drain without another offer or stop; "
                                + "pending=" + metrics.pending()
                                + ", inFlight=" + metrics.inFlight()
                                + ", poolInUse=" + metrics.poolInUse(),
                        id + 1, metrics.sent());
                assertFalse(metrics.pending());
                assertFalse(metrics.inFlight());
                assertEquals(0, metrics.poolInUse());
            }
            assertEquals(frames - 1, sink.lastId);
            assertEquals(frames, sink.sent);
            assertEquals(0, sink.tears);
            assertEquals(0, sink.outOfOrder);
            assertEquals(0, pool.inUse());
            assertEquals(OutputOwner.CAPTURE, streamer.owner());
        } finally {
            // Cleanup must not rescue a stranded frame before the liveness assertions.
            streamer.stop("end");
        }
    }

    @Test
    public void stopSendsBlackAfterLastColourAndThenReleases() throws Exception {
        FramePool pool = new FramePool(FRAME_BYTES);
        BlockingSink sink = new BlockingSink();
        AtomicInteger releaseCalls = new AtomicInteger();
        LedStreamer streamer = new LedStreamer(pool, sink,
                reason -> {
                    releaseCalls.incrementAndGet();
                    return true;
                }, 2000);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);

        FrameLease lease = pool.acquire(1, 1, 0, CHANNELS, LEDS);
        writeId(lease, 7);
        assertTrue(streamer.offer(lease));
        assertTrue(sink.awaitEntered());

        AtomicReference<LedStreamer.StopResult> result = new AtomicReference<>();
        Thread stopper = new Thread(() -> result.set(streamer.stop("user")));
        stopper.start();
        Thread.sleep(50);
        sink.unblock();
        stopper.join(2000);

        assertTrue(result.get() != null);
        assertFalse(result.get().timedOut());
        assertEquals(1, sink.blacks);
        assertEquals(7, sink.blackAfterLastColor);
        assertEquals(1, releaseCalls.get());
        assertEquals(0, pool.inUse());
    }

    @Test
    public void stopTimeoutDoesNotClaimBlackOrRelease() throws Exception {
        FramePool pool = new FramePool(FRAME_BYTES);
        BlockingSink sink = new BlockingSink();
        CountDownLatch releaseLatch = new CountDownLatch(1);
        LedStreamer streamer = new LedStreamer(pool, sink,
                reason -> {
                    releaseLatch.countDown();
                    return true;
                }, 100);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);
        FrameLease lease = pool.acquire(1, 1, 0, CHANNELS, LEDS);
        writeId(lease, 1);
        streamer.offer(lease);
        assertTrue(sink.awaitEntered());

        LedStreamer.StopResult result = streamer.stop("timeout");
        assertTrue(result.timedOut());
        assertFalse(result.releaseOk());
        assertFalse(result.releaseCalled());

        sink.unblock();
        assertTrue(releaseLatch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void staleEpochAndPostStopOffersReturnTheLease() {
        FramePool pool = new FramePool(FRAME_BYTES);
        RecordingSink sink = new RecordingSink();
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> true, 500);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);

        FrameLease stale = pool.acquire(2, 1, 0, CHANNELS, LEDS);
        assertFalse(streamer.offer(stale));
        assertEquals(0, pool.inUse());

        streamer.stop("end");
        FrameLease afterStop = pool.acquire(1, 1, 1, CHANNELS, LEDS);
        assertFalse(streamer.offer(afterStop));
        assertEquals(0, pool.inUse());
    }

    @Test
    public void captureAndTestOwnershipAreMutuallyExclusive() {
        FramePool pool = new FramePool(FRAME_BYTES);
        LedStreamer streamer = new LedStreamer(pool, new RecordingSink(), reason -> true, 500);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);
        boolean rejected = false;
        try {
            streamer.start(2, OutputOwner.TEST, LEDS, CHANNELS);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected);
        assertEquals(OutputOwner.CAPTURE, streamer.owner());
        streamer.stop("end");
        streamer.start(2, OutputOwner.TEST, LEDS, CHANNELS);
        assertEquals(OutputOwner.TEST, streamer.owner());
        streamer.stop("end");
    }

    @Test
    public void pendingIsLatestWinsWhileInFlightIsNeverOverwritten() throws Exception {
        FramePool pool = new FramePool(FRAME_BYTES);
        BlockingSink sink = new BlockingSink();
        LedStreamer streamer = new LedStreamer(pool, sink, reason -> true, 2000);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);

        FrameLease first = pool.acquire(1, 1, 0, CHANNELS, LEDS);
        writeId(first, 0);
        assertTrue(streamer.offer(first));
        assertTrue(sink.awaitEntered());

        for (long id = 1; id <= 20; id++) {
            FrameLease next = pool.acquire(1, 1, id, CHANNELS, LEDS);
            if (next != null) {
                writeId(next, id);
                streamer.offer(next);
            }
        }
        LedStreamer.Metrics during = streamer.metrics();
        assertTrue(during.inFlight());
        assertTrue(during.pending());
        assertTrue(during.replaced() > 0);

        sink.unblock();
        LedStreamer.StopResult stop = streamer.stop("end");
        assertFalse(stop.timedOut());
        assertEquals(0, pool.inUse());
    }

    @Test
    public void sinkFailureReturnsLeaseAndKeepsWriterAlive() throws Exception {
        FramePool pool = new FramePool(FRAME_BYTES);
        CountDownLatch sendObserved = new CountDownLatch(1);
        LedStreamer.FrameSink failing = new LedStreamer.FrameSink() {
            @Override
            public boolean send(FrameLease lease) {
                sendObserved.countDown();
                return false;
            }

            @Override
            public boolean sendBlack(int ledCount, int channels) {
                return true;
            }
        };
        LedStreamer streamer = new LedStreamer(pool, failing, reason -> true, 500);
        streamer.start(1, OutputOwner.CAPTURE, LEDS, CHANNELS);
        for (long id = 0; id < 20; id++) {
            FrameLease lease = pool.acquire(1, 1, id, CHANNELS, LEDS);
            if (lease != null) {
                writeId(lease, id);
                streamer.offer(lease);
            }
        }
        assertTrue(sendObserved.await(1, TimeUnit.SECONDS));
        LedStreamer.StopResult stop = streamer.stop("end");
        assertFalse(stop.timedOut());
        assertEquals(0, pool.inUse());
        assertTrue(streamer.metrics().failures() > 0);
    }
}
