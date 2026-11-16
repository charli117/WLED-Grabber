package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.CaptureConfig;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DeviceConfigTest {
    private final DeviceReadiness readiness = new DeviceReadiness();

    private static DeviceEndpoint endpoint(int httpPort, int udpPort, int ddpPort,
                                           Protocol protocol, RgbwMode mode, int count) {
        return new DeviceEndpoint("192.168.1.50", httpPort, udpPort, ddpPort, protocol, mode, count);
    }

    private static Capabilities capabilities(int count, boolean rgbwHint, ChannelHint hint) {
        return new Capabilities("0.15.1", "device-1", count, 21324, rgbwHint, hint,
                hint == ChannelHint.RGBW ? "RGBW" : "RGB", "", false, 1000);
    }

    private static WledDevice observed(Capabilities capabilities) {
        return new WledDevice("192.168.1.50", capabilities);
    }

    private static StartConfirmation allConfirmed() {
        return new StartConfirmation("device-1", true, true, true, true, true, true, true, true);
    }

    private static ConfigSnapshot snapshot(long revision, Protocol protocol) {
        LedLayoutConfig layout = new LedLayoutConfig(60, 60, 34, 34, 0, 0, 0, false,
                GapMode.SKIP, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
        CaptureConfig capture = new CaptureConfig(640, 30, false, false);
        return new ConfigSnapshot(1, revision,
                endpoint(80, 21324, 4048, protocol, RgbwMode.RGB, 274), layout, capture);
    }

    @Test
    public void httpUdpAndDdpPortsRemainIndependentAndDdpIsFixed() {
        ValidationResult<ReadyDevice> ready = readiness.evaluate(
                endpoint(8080, 21325, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                allConfirmed());

        assertTrue(ready.errors().toString(), ready.isValid());
        assertEquals(8080, ready.value().endpoint().httpPort());
        assertEquals(21325, ready.value().endpoint().udpPort());
        assertEquals(4048, ready.value().endpoint().ddpPort());

        ValidationResult<ReadyDevice> badDdp = readiness.evaluate(
                endpoint(8080, 21325, 4049, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                allConfirmed());
        assertFalse(badDdp.isValid());
        assertTrue(badDdp.hasError(ConfigFields.WLED_DDP_PORT, FieldError.Code.OUT_OF_RANGE));
    }

    @Test
    public void missingCapabilityFieldsAreVisible() {
        Capabilities missing = new Capabilities("", "", 0, 0, false,
                ChannelHint.UNKNOWN, "", "", false, 0);
        ValidationResult<ReadyDevice> result = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(missing), allConfirmed());

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.WLED_VERSION, FieldError.Code.EMPTY));
        assertTrue(result.hasError(ConfigFields.WLED_DEVICE_ID, FieldError.Code.EMPTY));
        assertTrue(result.hasError(ConfigFields.WLED_PHYSICAL_LEDS, FieldError.Code.OUT_OF_RANGE));
        assertTrue(result.hasError(ConfigFields.WLED_UDP_PORT, FieldError.Code.OUT_OF_RANGE));
        assertTrue(result.hasError(ConfigFields.WLED_CHANNEL_HINT, FieldError.Code.CAPABILITY_MISSING));
    }

    @Test
    public void rgbwObservationAndUserModeMustAgree() {
        ValidationResult<ReadyDevice> userRgbObservedRgbw = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, true, ChannelHint.RGBW)),
                allConfirmed());
        assertFalse(userRgbObservedRgbw.isValid());
        assertTrue(userRgbObservedRgbw.hasError(ConfigFields.WLED_CHANNEL_HINT,
                FieldError.Code.CROSS_FIELD));

        ValidationResult<ReadyDevice> userRgbwObservedRgb = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGBW, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                allConfirmed());
        assertFalse(userRgbwObservedRgb.isValid());
        assertTrue(userRgbwObservedRgb.hasError(ConfigFields.WLED_CHANNEL_HINT,
                FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void identityChangeRequiresExplicitReconfirmation() {
        StartConfirmation stale = new StartConfirmation("old-device-id",
                true, true, true, true, true, true, true, true);
        ValidationResult<ReadyDevice> result = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                stale);

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.WLED_DEVICE_ID, FieldError.Code.IDENTITY_CHANGED));
    }

    @Test
    public void countDecreaseBlocksReadyAndReadyUsesObservedFullCount() {
        ValidationResult<ReadyDevice> decreased = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(200, false, ChannelHint.RGB)),
                allConfirmed());
        assertFalse(decreased.isValid());
        assertTrue(decreased.hasError(ConfigFields.WLED_PHYSICAL_LEDS, FieldError.Code.COUNT_MISMATCH));

        ValidationResult<ReadyDevice> ready = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                allConfirmed());
        assertTrue(ready.isValid());
        assertEquals(274, ready.value().sendLedCount());
    }

    @Test
    public void staleObservationAddressIsRejected() {
        WledDevice stale = new WledDevice("192.168.1.99", capabilities(274, false, ChannelHint.RGB));
        ValidationResult<ReadyDevice> result = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                stale, allConfirmed());

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.WLED_HOST, FieldError.Code.STALE_OBSERVATION));
    }

    @Test
    public void missingStartConfirmationBlocksReady() {
        StartConfirmation missingTimeout = new StartConfirmation("device-1",
                true, true, true, true, false, true, true, true);
        ValidationResult<ReadyDevice> result = readiness.evaluate(
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                observed(capabilities(274, false, ChannelHint.RGB)),
                missingTimeout);

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.WLED_CONFIRMATION,
                FieldError.Code.CONFIRMATION_REQUIRED));
        boolean finiteTimeoutFound = false;
        for (FieldError error : result.errors()) {
            if ("finite_timeout".equals(error.detail())) {
                finiteTimeoutFound = true;
            }
        }
        assertTrue(finiteTimeoutFound);
    }

    @Test
    public void transportSelectionNeverUsesType5() {
        assertEquals(Transport.DRGB, Transport.resolve(Protocol.AUTO, RgbwMode.RGB, 1));
        assertEquals(Transport.DNRGB, Transport.resolve(Protocol.AUTO, RgbwMode.RGB, 2));
        assertEquals(Transport.DDP, Transport.resolve(Protocol.AUTO, RgbwMode.RGBW, 274));
        assertEquals(Transport.DDP, Transport.resolve(Protocol.DDP, RgbwMode.RGB, 274));
        assertEquals(Transport.DNRGB, Transport.resolve(Protocol.DNRGB, RgbwMode.RGB, 274));
        assertNull(Transport.resolve(Protocol.DNRGB, RgbwMode.RGBW, 274));
        assertNull(Transport.resolve(Protocol.AUTO, RgbwMode.UNCONFIRMED, 274));
        assertEquals(3, Transport.values().length);
    }

    @Test
    public void schemaChangeDoesNotEnterReadyStartConfiguration() {
        ConfigSnapshot schemaMismatch = new ConfigSnapshot(2, 1,
                endpoint(80, 21324, 4048, Protocol.AUTO, RgbwMode.RGB, 274),
                snapshot(1, Protocol.AUTO).layout(), snapshot(1, Protocol.AUTO).capture());

        ValidationResult<com.abrenoch.wledgrabber.common.led.ValidatedConfig> result =
                new ConfigValidator().validateForStart(schemaMismatch);
        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.SCHEMA_VERSION,
                FieldError.Code.UNSUPPORTED_SCHEMA));
    }

    @Test
    public void channelChangeDoesNotMutateOldSnapshot() {
        ConfigSnapshot oldSnapshot = snapshot(1, Protocol.DNRGB);
        ConfigSnapshot newSnapshot = snapshot(2, Protocol.DDP);

        assertEquals(Protocol.DNRGB, oldSnapshot.device().protocol());
        assertEquals(Protocol.DDP, newSnapshot.device().protocol());
        assertEquals(1, oldSnapshot.revision());
        assertEquals(2, newSnapshot.revision());
    }

    @Test
    public void configAndObservationTimestampsDoNotOverwriteEachOther() {
        ConfigSnapshot config = snapshot(1, Protocol.AUTO);
        WledDevice observation = observed(capabilities(274, false, ChannelHint.RGB));
        DeviceState state = new DeviceState(config, observation);

        DeviceState withNewConfig = state.withConfig(snapshot(2, Protocol.AUTO));
        assertSame(observation, withNewConfig.observation());
        assertEquals(2, withNewConfig.config().revision());

        WledDevice newerObservation = new WledDevice("192.168.1.50",
                new Capabilities("0.15.1", "device-1", 274, 21324, false,
                        ChannelHint.RGB, "RGB", "", false, 2000));
        DeviceState withNewObservation = withNewConfig.withObservation(newerObservation);
        assertEquals(2, withNewObservation.config().revision());
        assertEquals(2000, withNewObservation.observation().observedAtMillis());
    }
}
