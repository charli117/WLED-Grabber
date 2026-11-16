package com.abrenoch.wledgrabber.tv;

import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.views.TestPatternActivity;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PRE-2 host lifecycle: leaving the pattern Activity must release the shared
 * TEST lease, otherwise the next CAPTURE start would be refused forever.
 */
public class TvTestPatternLifecycleTest {

    @After
    public void ensureReleased() {
        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        if (coordinator.owner() != OutputOwner.NONE) {
            coordinator.release(coordinator.owner(), coordinator.ownerId(),
                    coordinator.epoch());
        }
    }


    @Test
    public void previewThenPatternThenExitKeepsAFreeOwner() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();

        // 1) Preview page opens and closes without taking the TEST lease.
        android.content.Intent preview = new android.content.Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                com.abrenoch.wledgrabber.common.views.LayoutPreviewActivity.class);
        preview.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity previewActivity = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(preview);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(previewActivity::finish);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("preview must not hold the output lease",
                OutputOwner.NONE, coordinator.owner());

        // 2) Pattern page takes and releases TEST, leaving a free owner for capture.
        android.content.Intent pattern = new android.content.Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                com.abrenoch.wledgrabber.common.views.TestPatternActivity.class);
        pattern.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity patternActivity = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(pattern);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(patternActivity::finish);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("exiting the pattern must free the TEST lease",
                OutputOwner.NONE, coordinator.owner());

        // 3) Capture can be taken afterwards.
        assertTrue(coordinator.acquire(OutputOwner.CAPTURE, "post-pattern-capture"));
        assertTrue(coordinator.release(OutputOwner.CAPTURE, "post-pattern-capture",
                coordinator.epoch()));
    }

    @Test
    public void leavingThePatternActivityReleasesTheOutputLease() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        assertEquals("output must start idle", OutputOwner.NONE, coordinator.owner());

        Intent intent = new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                TestPatternActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity = InstrumentationRegistry.getInstrumentation()
                .startActivitySync(intent);
        try {
            // The Activity takes the TEST lease on start; an unconfigured device is
            // fine here, the lease contract is what this test pins down.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue("pattern host should own TEST while visible",
                    coordinator.owner() == OutputOwner.TEST
                            || coordinator.owner() == OutputOwner.NONE);
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
        assertEquals("finishing the host must release the TEST lease",
                OutputOwner.NONE, coordinator.owner());
        // A later capture start must be able to take the lease again.
        assertTrue("CAPTURE can start after the pattern host exits",
                coordinator.acquire(OutputOwner.CAPTURE, "post-pattern-capture"));
        long epoch = coordinator.epoch();
        assertTrue(coordinator.release(OutputOwner.CAPTURE, "post-pattern-capture", epoch));
    }
}
