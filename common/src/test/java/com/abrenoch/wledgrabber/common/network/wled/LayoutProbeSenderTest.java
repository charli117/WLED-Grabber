package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.config.ConfigTestData;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The probe must not claim a pulse (or take the output lease) without an endpoint. */
public class LayoutProbeSenderTest {

    @Test
    public void emptyHostDoesNotStartAPulseOrTakeTheOutputLease() {
        ConfigSnapshot snapshot = new ConfigValidator().validateForSave(
                ConfigTestData.validDraft().set(ConfigFields.WLED_HOST, "")).value();
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), snapshot.device().physicalLedCount(), 640, 360);
        assertTrue(compiled.errors().toString(), compiled.isValid());

        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        OutputOwner before = coordinator.owner();
        assertFalse(LayoutProbeSender.pulse(snapshot, compiled.value(), new int[]{0}));
        assertEquals(before, coordinator.owner());
    }
}
