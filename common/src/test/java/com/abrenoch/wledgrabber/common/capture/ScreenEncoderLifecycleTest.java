package com.abrenoch.wledgrabber.common.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenEncoderLifecycleTest {

    private static final class FakeImage implements ImageLifecycle.ImageHandle {
        private int closes;

        @Override
        public void close() {
            closes++;
        }
    }

    private static final class FakeSource implements ImageLifecycle.ImageSource {
        private FakeImage next;
        private RuntimeException failure;
        private int acquisitions;

        @Override
        public ImageLifecycle.ImageHandle acquire() {
            acquisitions++;
            if (failure != null) {
                throw failure;
            }
            return next;
        }
    }

    @Test
    public void samplingExceptionStillClosesImage() {
        FakeSource source = new FakeSource();
        FakeImage image = new FakeImage();
        source.next = image;
        GenerationGate gate = new GenerationGate();
        long generation = gate.next();
        ImageLifecycle lifecycle = new ImageLifecycle(source, gate);

        boolean accepted = lifecycle.consume(generation, ignored -> {
            throw new IllegalStateException("sampling failure");
        });

        assertFalse(accepted);
        assertEquals(1, image.closes);
        assertEquals(1, lifecycle.acquired());
        assertEquals(1, lifecycle.closed());
        assertEquals(0, lifecycle.open());
    }

    @Test
    public void nullAcquisitionDoesNotCloseOrLeak() {
        FakeSource source = new FakeSource();
        GenerationGate gate = new GenerationGate();
        ImageLifecycle lifecycle = new ImageLifecycle(source, gate);
        assertFalse(lifecycle.consume(gate.next(), ignored -> {
        }));
        assertEquals(1, source.acquisitions);
        assertEquals(0, lifecycle.acquired());
        assertEquals(0, lifecycle.open());
    }

    @Test
    public void acquireFailureIsContained() {
        FakeSource source = new FakeSource();
        source.failure = new IllegalStateException("reader failed");
        GenerationGate gate = new GenerationGate();
        ImageLifecycle lifecycle = new ImageLifecycle(source, gate);
        assertFalse(lifecycle.consume(gate.next(), ignored -> {
        }));
        assertEquals(0, lifecycle.acquired());
        assertEquals(0, lifecycle.open());
    }

    @Test
    public void staleGenerationIsRejectedBeforeAcquire() {
        FakeSource source = new FakeSource();
        source.next = new FakeImage();
        GenerationGate gate = new GenerationGate();
        long oldGeneration = gate.next();
        gate.next();
        ImageLifecycle lifecycle = new ImageLifecycle(source, gate);

        assertFalse(lifecycle.consume(oldGeneration, ignored -> {
        }));
        assertEquals(0, source.acquisitions);
        assertEquals(1, lifecycle.rejected());
    }

    @Test
    public void hundredRotationResizeLockRevokeCyclesLeaveNoOpenImages() {
        FakeSource source = new FakeSource();
        GenerationGate gate = new GenerationGate();
        ImageLifecycle lifecycle = new ImageLifecycle(source, gate);

        for (int i = 0; i < 100; i++) {
            FakeImage image = new FakeImage();
            source.next = image;
            long generation = gate.next();
            boolean throwDuringSample = i % 3 == 0;
            lifecycle.consume(generation, ignored -> {
                if (throwDuringSample) {
                    throw new IllegalStateException("frame dropped");
                }
            });
            assertEquals(1, image.closes);
            // Late callback from the previous generation must be rejected.
            if (i > 0) {
                lifecycle.consume(generation - 1, ignored -> {
                });
            }
        }

        assertEquals(100, lifecycle.acquired());
        assertEquals(100, lifecycle.closed());
        assertEquals(0, lifecycle.open());
        assertTrue(lifecycle.rejected() >= 99);
    }
}
