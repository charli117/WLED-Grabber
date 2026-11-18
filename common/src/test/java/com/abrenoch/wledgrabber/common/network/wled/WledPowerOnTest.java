package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.FramePool;
import com.abrenoch.wledgrabber.common.capture.LedStreamer;
import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Power-on must never block the caller (main-thread network rule). */
public class WledPowerOnTest {

    private static final class FakeControl implements WledControlClient {
        private final AtomicBoolean on = new AtomicBoolean();
        private final AtomicReference<Thread> executing = new AtomicReference<>();
        private final CountDownLatch called = new CountDownLatch(1);

        @Override
        public ProbeResult probe(String host, int httpPort) {
            return ProbeResult.failure(ProbeError.CONNECT_FAILURE, "unused", 0);
        }

        @Override
        public StateReadResult readState(String host, int httpPort) {
            return StateReadResult.failure(ProbeError.CONNECT_FAILURE);
        }

        @Override
        public boolean turnOn(String host, int httpPort) {
            executing.set(Thread.currentThread());
            on.set(true);
            called.countDown();
            return true;
        }

        @Override
        public boolean turnOff(String host, int httpPort) {
            return false;
        }
    }

    @Test
    public void powerOnRunsOnAWorkerWithoutBlockingTheCaller() throws InterruptedException {
        FakeControl control = new FakeControl();
        Thread caller = Thread.currentThread();

        FramePool pool = new FramePool(6);
        DeviceEndpoint device = new DeviceEndpoint("192.168.1.50", 80, 21324, 4048,
                Protocol.DDP, RgbwMode.RGB, 2);
        LedStreamer streamer = new LedStreamer(pool,
                new WledFrameSink(device, control, (bytes, offset, length) -> {}), reason -> true, 1000);
        streamer.start(1, OutputOwner.CAPTURE, 2, 3);
        try {
            assertTrue(streamer.offer(pool.acquire(1, 1, 0, 3, 2)));
            assertTrue("power-on was not dispatched", control.called.await(2, TimeUnit.SECONDS));
            assertTrue(control.on.get());
            assertNotEquals("power-on must not run on the caller", caller, control.executing.get());
        } finally {
            streamer.stop("test complete");
        }
    }
}
