package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The probe must not claim a pulse (or take the output lease) without an endpoint. */
public class LayoutProbeSenderTest {

    @After
    public void tearDown() {
        LayoutProbeSender.stop();
    }

    @Test
    public void transportFollowsTheObservedChannelMode() {
        ConfigSnapshot rgb = new ConfigValidator().validateForSave(
                ConfigTestData.validDraft()).value();
        assertEquals(Transport.DNRGB, LayoutProbeSender.transportFor(rgb));

        ConfigSnapshot rgbw = new ConfigValidator().validateForSave(
                ConfigTestData.validDraft()
                        .set(ConfigFields.WLED_RGBW_MODE, "rgbw")).value();
        assertEquals(Transport.DDP, LayoutProbeSender.transportFor(rgbw));
    }

    @Test
    public void emptyHostDoesNotStartAProbeOrTakeTheOutputLease() {
        ConfigSnapshot snapshot = new ConfigValidator().validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.WLED_HOST, "")).value();
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), snapshot.device().physicalLedCount(), 640, 360);
        assertTrue(compiled.errors().toString(), compiled.isValid());

        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        OutputOwner before = coordinator.owner();
        assertEquals(OutputOwner.NONE, before);
        assertEquals(LayoutProbeSender.Result.INVALID,
                LayoutProbeSender.toggle(snapshot, compiled.value(),
                        LayoutProbeSender.keyFor(Side.TOP), new int[]{0}));
        assertEquals(before, coordinator.owner());
    }

    @Test
    public void runningCaptureBlocksTheProbeWithoutStealingTheLease() {
        ConfigSnapshot snapshot = new ConfigValidator().validateForSave(
                ConfigTestData.validDraft()).value();
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), snapshot.device().physicalLedCount(), 640, 360);
        assertTrue(compiled.errors().toString(), compiled.isValid());

        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        assertTrue(coordinator.acquire(OutputOwner.CAPTURE, "test-capture"));
        long epoch = coordinator.epoch();
        try {
            assertEquals(LayoutProbeSender.Result.BUSY,
                    LayoutProbeSender.toggle(snapshot, compiled.value(),
                            LayoutProbeSender.keyFor(Side.TOP), new int[]{0}));
            assertEquals(OutputOwner.CAPTURE, coordinator.owner());
            assertEquals("test-capture", coordinator.ownerId());
        } finally {
            coordinator.release(OutputOwner.CAPTURE, "test-capture", epoch);
        }
    }
}
