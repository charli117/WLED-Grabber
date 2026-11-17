package com.abrenoch.wledgrabber.common.network.wled;

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

        long startedAt = System.nanoTime();
        WledPowerOn.async(control, "192.168.1.50", 80, "test-power-on");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue("power-on waited on the caller thread", elapsedMillis < 500);
        assertTrue("power-on was not dispatched", control.called.await(2, TimeUnit.SECONDS));
        assertTrue(control.on.get());
        assertNotEquals("power-on must not run on the caller", caller, control.executing.get());
        assertTrue(control.executing.get().getName().contains("test-power-on"));
    }
}
