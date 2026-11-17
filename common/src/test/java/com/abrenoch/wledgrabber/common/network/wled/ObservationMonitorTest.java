package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.fixtures.FakeClock;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObservationMonitorTest {

    private static final class FakeControl implements WledControlClient {
        private ProbeResult probeResult = ProbeResult.success(device());

        @Override
        public ProbeResult probe(String host, int httpPort) {
            return probeResult;
        }

        @Override
        public StateReadResult readState(String host, int httpPort) {
            return StateReadResult.failure(ProbeError.CONNECT_FAILURE);
        }

        @Override
        public boolean turnOn(String host, int httpPort) {
            return false;
        }

        @Override
        public boolean turnOff(String host, int httpPort) {
            return false;
        }
    }

    private static WledDevice device() {
        Capabilities capabilities = new Capabilities("0.15.1", "device-1", 274, 21324,
                false, ChannelHint.RGB, "RGB", "", false, 0);
        return new WledDevice("192.168.1.50", capabilities);
    }

    private static ReadyDevice ready() {
        DeviceEndpoint endpoint = new DeviceEndpoint("192.168.1.50", 80, 21324, 4048,
                Protocol.AUTO, RgbwMode.RGB, 274);
        return new ReadyDevice(endpoint, device(), Transport.DNRGB, 274);
    }

    private static WledSession sessionAt(long now) {
        WledSession session = new WledSession(new FakeControl(),
                (count, rgbw) -> true, new FakeClock(now)::now);
        session.start(ready());
        return session;
    }

    @Test
    public void freshObservationIsNotStale() {
        WledSession session = sessionAt(1_000);
        WledSession.Observation observation = session.currentObservation();

        assertFalse(ObservationMonitor.isStale(observation, 1_000));
        assertFalse(ObservationMonitor.isStale(observation, 10_000));
    }

    @Test
    public void observationOlderThanTenSecondsIsStale() {
        WledSession session = sessionAt(1_000);

        assertTrue(ObservationMonitor.isStale(session.currentObservation(), 11_001));
    }

    @Test
    public void failedProbeMarksTheObservationStale() {
        WledSession session = sessionAt(0);
        WledSession.Observation failed = new WledSession.Observation(
                device(), 0, ProbeError.TIMEOUT, false);

        assertTrue(ObservationMonitor.isStale(failed, 0));
        assertTrue(ObservationMonitor.isStale(null, 0));
    }
}
