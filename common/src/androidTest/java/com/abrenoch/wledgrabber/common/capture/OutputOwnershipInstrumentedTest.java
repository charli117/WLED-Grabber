package com.abrenoch.wledgrabber.common.capture;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * CAP-3 device-level ownership: one output owner per process, release requires the
 * matching owner id and epoch, and a released lease can be re-taken (Activity exit
 * then re-entry). Runs on the real Android runtime rather than only on the JVM.
 */
public class OutputOwnershipInstrumentedTest {
    private final OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();

    @Before
    public void requireIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // A previous test may have left a lease behind; release it best-effort.
        if (coordinator.owner() != OutputOwner.NONE) {
            coordinator.release(coordinator.owner(), coordinator.ownerId(),
                    coordinator.epoch());
        }
        assertTrue("output must start idle", coordinator.owner() == OutputOwner.NONE);
    }

    @After
    public void releaseAfterTest() {
        if (coordinator.owner() != OutputOwner.NONE) {
            coordinator.release(coordinator.owner(), coordinator.ownerId(),
                    coordinator.epoch());
        }
    }

    @Test
    public void captureAndTestAreMutuallyExclusive() {
        assertTrue(coordinator.acquire(OutputOwner.TEST, "activity-test"));
        assertFalse("CAPTURE must not start while TEST owns output",
                coordinator.acquire(OutputOwner.CAPTURE, "service-capture"));
        assertEquals(OutputOwner.TEST, coordinator.owner());
        assertEquals("activity-test", coordinator.ownerId());
    }

    @Test
    public void releaseRequiresMatchingOwnerAndEpoch() {
        assertTrue(coordinator.acquire(OutputOwner.TEST, "owner-a"));
        long epoch = coordinator.epoch();
        assertFalse("wrong id must not release",
                coordinator.release(OutputOwner.TEST, "owner-b", epoch));
        assertFalse("wrong owner must not release",
                coordinator.release(OutputOwner.CAPTURE, "owner-a", epoch));
        assertFalse("stale epoch must not release",
                coordinator.release(OutputOwner.TEST, "owner-a", epoch - 1));
        assertTrue(coordinator.release(OutputOwner.TEST, "owner-a", epoch));
        assertEquals(OutputOwner.NONE, coordinator.owner());
    }

    @Test
    public void activityExitReleasesAndReentryCanRetake() {
        assertTrue(coordinator.acquire(OutputOwner.TEST, "preview-activity"));
        long first = coordinator.epoch();
        assertTrue(coordinator.release(OutputOwner.TEST, "preview-activity", first));
        // Destroying the Activity then reopening it must not leave the gate stuck.
        assertTrue(coordinator.acquire(OutputOwner.TEST, "preview-activity"));
        assertTrue("epoch must advance per acquisition", coordinator.epoch() > first);
        assertTrue(coordinator.release(OutputOwner.TEST, "preview-activity",
                coordinator.epoch()));
        assertEquals(OutputOwner.NONE, coordinator.owner());
    }

    @Test
    public void captureCanStartAfterTestStops() {
        assertTrue(coordinator.acquire(OutputOwner.TEST, "activity-test"));
        long epoch = coordinator.epoch();
        assertTrue(coordinator.release(OutputOwner.TEST, "activity-test", epoch));
        assertTrue(coordinator.acquire(OutputOwner.CAPTURE, "service-capture"));
        assertEquals(OutputOwner.CAPTURE, coordinator.owner());
    }
}
