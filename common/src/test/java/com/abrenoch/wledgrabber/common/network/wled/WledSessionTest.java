package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.fixtures.FakeClock;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WledSessionTest {

    private static ReadyDevice ready() {
        DeviceEndpoint endpoint = new DeviceEndpoint("192.168.1.50", 80, 21324, 4048,
                Protocol.AUTO, RgbwMode.RGB, 274);
        Capabilities capabilities = new Capabilities("0.15.1", "dev-1", 274, 21324,
                false, ChannelHint.RGB, "RGB", "", false, 0);
        WledDevice observed = new WledDevice("192.168.1.50", capabilities);
        return new ReadyDevice(endpoint, observed, Transport.DNRGB, 274);
    }

    private static final class FakeControl implements WledControlClient {
        private ProbeResult probeResult = ProbeResult.success(ready().observed());
        private StateReadResult stateRead = StateReadResult.success(false, false);
        private boolean offAccepted = true;
        private int probes;
        private int offs;
        private int stateReads;
        private Runnable beforeOff;
        private Runnable onReadState;

        @Override
        public ProbeResult probe(String host, int httpPort) {
            probes++;
            return probeResult;
        }

        @Override
        public StateReadResult readState(String host, int httpPort) {
            stateReads++;
            if (onReadState != null) {
                onReadState.run();
            }
            return stateRead;
        }

        @Override
        public boolean turnOff(String host, int httpPort) {
            offs++;
            if (beforeOff != null) {
                beforeOff.run();
            }
            return offAccepted;
        }
    }

    private static final class FakeSender implements FrameSender {
        private int blacks;
        private boolean success = true;

        @Override
        public boolean sendBlack(int ledCount, boolean rgbw) {
            blacks++;
            return success;
        }
    }

    @Test
    public void confirmedReleaseRequiresOffAndLiveReadback() {
        FakeControl control = new FakeControl();
        FakeSender sender = new FakeSender();
        FakeClock clock = new FakeClock(0);
        WledSession session = new WledSession(control, sender, clock::now);
        session.start(ready());

        WledSession.ReleaseResult result = session.release("user");

        assertEquals(WledSession.ReleaseStatus.CONFIRMED, result.status());
        assertEquals(1, sender.blacks);
        assertEquals(1, control.offs);
        assertEquals(1, control.stateReads);
        assertEquals(WledSession.State.RELEASED, session.state());
    }

    @Test
    public void liveOrOnReadbackIsUnconfirmed() {
        for (int mode = 0; mode < 2; mode++) {
            FakeControl control = new FakeControl();
            control.stateRead = mode == 0
                    ? StateReadResult.success(false, true)
                    : StateReadResult.success(true, false);
            WledSession session = new WledSession(control, new FakeSender(), new FakeClock(0)::now);
            session.start(ready());
            assertEquals(WledSession.ReleaseStatus.UNCONFIRMED, session.release("user").status());
        }
    }

    @Test
    public void readbackFailureAndBudgetOverrunAreUnconfirmed() {
        FakeControl control = new FakeControl();
        control.stateRead = StateReadResult.failure(ProbeError.TIMEOUT);
        WledSession session = new WledSession(control, new FakeSender(), new FakeClock(0)::now);
        session.start(ready());
        assertEquals(WledSession.ReleaseStatus.UNCONFIRMED, session.release("user").status());

        FakeControl slowControl = new FakeControl();
        FakeClock clock = new FakeClock(0);
        slowControl.onReadState = () -> clock.advance(4000);
        WledSession slowSession = new WledSession(slowControl, new FakeSender(), clock::now);
        slowSession.start(ready());
        assertEquals(WledSession.ReleaseStatus.UNCONFIRMED, slowSession.release("user").status());
    }

    @Test
    public void blackFrameIsBestEffortButReadbackIsTheConfirmingEvidence() {
        FakeControl control = new FakeControl();
        FakeSender sender = new FakeSender();
        sender.success = false;
        WledSession session = new WledSession(control, sender, new FakeClock(0)::now);
        session.start(ready());

        WledSession.ReleaseResult result = session.release("user");
        assertFalse(result.blackSent());
        assertEquals(WledSession.ReleaseStatus.CONFIRMED, result.status());
    }

    @Test
    public void healthProbeIsLimitedToOncePerFiveSecondsAndStalenessIsTenSeconds() {
        FakeControl control = new FakeControl();
        FakeClock clock = new FakeClock(0);
        WledSession session = new WledSession(control, new FakeSender(), clock::now);
        session.start(ready());
        assertEquals(0, control.probes);

        clock.advance(1000);
        session.observeNow();
        assertEquals(0, control.probes);

        clock.advance(4000);
        WledSession.Observation observation = session.observeNow();
        assertEquals(1, control.probes);
        assertFalse(observation.isStale(clock.now()));

        clock.advance(10001);
        assertTrue(observation.isStale(clock.now()));
        session.observeNow();
        assertEquals(2, control.probes);
    }

    @Test
    public void identityChangeInvalidatesEndpointAndRequiresNewObservation() {
        FakeControl control = new FakeControl();
        FakeClock clock = new FakeClock(0);
        WledSession session = new WledSession(control, new FakeSender(), clock::now);
        session.start(ready());

        Capabilities changedCapabilities = new Capabilities("0.15.1", "dev-2", 274, 21324,
                false, ChannelHint.RGB, "RGB", "", false, 5000);
        control.probeResult = ProbeResult.success(new WledDevice("192.168.1.50", changedCapabilities));
        clock.advance(5000);
        WledSession.Observation observation = session.observeNow();

        assertTrue(observation.identityChanged());
        assertEquals(WledSession.State.IDLE, session.state());
        assertEquals(WledSession.ReleaseStatus.UNCONFIRMED, session.release("user").status());
    }

    @Test
    public void invalidatedDuringReleaseCannotChangeTheNewEpoch() {
        FakeControl control = new FakeControl();
        FakeClock clock = new FakeClock(0);
        WledSession session = new WledSession(control, new FakeSender(), clock::now);
        session.start(ready());
        long firstEpoch = session.epoch();
        control.beforeOff = session::invalidate;

        WledSession.ReleaseResult result = session.release("network change");

        assertEquals(WledSession.ReleaseStatus.UNCONFIRMED, result.status());
        assertEquals(WledSession.State.IDLE, session.state());
        long secondEpoch = session.start(ready());
        assertTrue(secondEpoch > firstEpoch);
        assertEquals(WledSession.State.ACTIVE, session.state());
    }

    @Test
    public void startWhileActiveIsRejectedAndStartDoesNotRelease() {
        FakeControl control = new FakeControl();
        FakeSender sender = new FakeSender();
        WledSession session = new WledSession(control, sender, new FakeClock(0)::now);
        session.start(ready());

        assertEquals(WledSession.State.ACTIVE, session.state());
        assertEquals(0, control.offs);
        assertEquals(0, sender.blacks);
        boolean rejected = false;
        try {
            session.start(ready());
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    @Test
    public void observationStartsFreshAndStaleFlagIsDeterministic() {
        FakeClock clock = new FakeClock(100);
        WledSession session = new WledSession(new FakeControl(), new FakeSender(), clock::now);
        session.start(ready());
        WledSession.Observation observation = session.currentObservation();
        assertNotNull(observation);
        assertEquals(100, observation.observedAtMillis());
        assertFalse(observation.isStale(10100));
        assertTrue(observation.isStale(10101));
    }
}
