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

import com.abrenoch.wledgrabber.common.fixtures.ControlledHttpEndpoint;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The probe must not claim a pulse (or take the output lease) without an endpoint. */
public class LayoutProbeSenderTest {

    @After
    public void tearDown() {
        LayoutProbeSender.get().stop();
    }

    @Test
    public void stoppingVisibleProbePowersOffBeforeReturningLease() throws Exception {
        LayoutProbeSender probe = new LayoutProbeSender(() -> new WledHttpClient(true));
        try (ControlledHttpEndpoint http = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.STATE_OFF);
             DatagramSocket udp = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            ConfigSnapshot config = new ConfigValidator().validateForSave(ConfigTestData.validDraft()
                    .set(ConfigFields.WLED_HOST, "127.0.0.1")
                    .set(ConfigFields.WLED_HTTP_PORT, String.valueOf(http.port()))
                    .set(ConfigFields.WLED_UDP_PORT, String.valueOf(udp.getLocalPort()))).value();
            CompiledLayout layout = new ClassicLedLayout().compile(config.layout(),
                    config.device().physicalLedCount(), 640, 360).value();
            assertEquals(LayoutProbeSender.Result.STARTED, probe.toggle(config, layout, "top", new int[]{0}));
            try {
                udp.setSoTimeout(2500);
                udp.receive(new DatagramPacket(new byte[1500], 1500));
            } finally {
                probe.stop();
            }
            OutputSessionCoordinator gate = OutputSessionCoordinator.get();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
            while (gate.owner() != OutputOwner.NONE && System.nanoTime() < deadline) Thread.yield();
            assertEquals(OutputOwner.NONE, gate.owner());
            assertEquals("{\"on\":false,\"live\":false,\"tt\":0}", http.lastPostBody());
        }
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
                LayoutProbeSender.get().toggle(snapshot, compiled.value(),
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
                    LayoutProbeSender.get().toggle(snapshot, compiled.value(),
                            LayoutProbeSender.keyFor(Side.TOP), new int[]{0}));
            assertEquals(OutputOwner.CAPTURE, coordinator.owner());
            assertEquals("test-capture", coordinator.ownerId());
        } finally {
            coordinator.release(OutputOwner.CAPTURE, "test-capture", epoch);
        }
    }
}
