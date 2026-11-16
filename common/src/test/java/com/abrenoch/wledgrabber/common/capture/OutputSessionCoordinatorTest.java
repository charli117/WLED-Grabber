package com.abrenoch.wledgrabber.common.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OutputSessionCoordinatorTest {

    @Test
    public void captureAndTestAreMutuallyExclusive() {
        OutputSessionCoordinator coordinator = new OutputSessionCoordinator();
        assertTrue(coordinator.acquire(OutputOwner.CAPTURE, "capture"));
        long captureEpoch = coordinator.epoch();
        assertFalse(coordinator.acquire(OutputOwner.TEST, "test"));
        assertEquals(OutputOwner.CAPTURE, coordinator.owner());
        assertTrue(coordinator.release(OutputOwner.CAPTURE, "capture", captureEpoch));
        assertEquals(OutputOwner.NONE, coordinator.owner());
        assertTrue(coordinator.acquire(OutputOwner.TEST, "test"));
    }

    @Test
    public void staleOrWrongReleaseCannotStealTheNewOwner() {
        OutputSessionCoordinator coordinator = new OutputSessionCoordinator();
        assertTrue(coordinator.acquire(OutputOwner.CAPTURE, "capture"));
        long oldEpoch = coordinator.epoch();
        assertTrue(coordinator.release(OutputOwner.CAPTURE, "capture", oldEpoch));
        assertTrue(coordinator.acquire(OutputOwner.TEST, "test"));
        long testEpoch = coordinator.epoch();

        assertFalse(coordinator.release(OutputOwner.CAPTURE, "capture", oldEpoch));
        assertFalse(coordinator.release(OutputOwner.TEST, "capture", testEpoch));
        assertEquals(OutputOwner.TEST, coordinator.owner());
        assertEquals("test", coordinator.ownerId());
    }

    @Test
    public void duplicateAcquireIsRejectedUntilRelease() {
        OutputSessionCoordinator coordinator = new OutputSessionCoordinator();
        assertTrue(coordinator.acquire(OutputOwner.TEST, "test"));
        assertFalse(coordinator.acquire(OutputOwner.TEST, "test"));
        long epoch = coordinator.epoch();
        assertTrue(coordinator.release(OutputOwner.TEST, "test", epoch));
        assertTrue(coordinator.acquire(OutputOwner.TEST, "test"));
    }
}
