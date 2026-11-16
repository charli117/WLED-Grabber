package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.fixtures.ControlledHttpEndpoint;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WledDiscoveryTest {

    private static List<DiscoveryCandidate> candidates(int count) {
        List<DiscoveryCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            candidates.add(new DiscoveryCandidate("svc-" + i, "192.168.1." + (i % 250 + 1),
                    80, 1000));
        }
        return candidates;
    }

    @Test
    public void cacheDeduplicatesRefreshesAndExpiresNsdIdentities() {
        DiscoveryCandidateCache cache = new DiscoveryCandidateCache(60000);
        cache.add(new DiscoveryCandidate("svc", "192.168.1.5", 80, 1000));
        cache.add(new DiscoveryCandidate("svc", "192.168.1.5", 80, 2000));
        assertEquals(1, cache.size());
        assertEquals(1, cache.candidates(2000).size());
        assertEquals(2000, cache.candidates(2000).get(0).lastSeenMillis());
        assertEquals(0, cache.candidates(62001).size());

        assertFalse(DiscoveryCandidate.isValidHost("fe80::1"));
        assertTrue(DiscoveryCandidate.isValidHost("fe80::1%en0"));
        assertTrue(DiscoveryCandidate.isValidHost("192.168.1.5"));
        assertFalse(DiscoveryCandidate.isValidHost("http://host"));
        assertFalse(new DiscoveryCandidate("svc", "192.168.1.5", 0, 1).isValid());
    }

    @Test
    public void emptyNetworkAndPreCancelledScanAreBounded() {
        WledDiscovery.Probe probe = (host, port) -> ProbeResult.failure(
                ProbeError.HTTP_ERROR, "unused", 0);
        WledDiscovery discovery = new WledDiscovery(probe);

        DiscoveryResult empty = discovery.scan(Collections.emptyList(),
                256, 4, 1000, new AtomicBoolean(false));
        assertFalse(empty.partial());
        assertEquals(0, empty.candidatesConsidered());

        AtomicBoolean cancelled = new AtomicBoolean(true);
        DiscoveryResult result = discovery.scan(candidates(10), 256, 4, 1000, cancelled);
        assertTrue(result.cancelled());
        assertEquals(0, result.candidatesConsidered());
    }

    @Test
    public void candidateCapAndConcurrencyBoundsAreEnforced() {
        AtomicInteger calls = new AtomicInteger();
        WledDiscovery discovery = new WledDiscovery((host, port) -> {
            calls.incrementAndGet();
            return ProbeResult.failure(ProbeError.HTTP_ERROR, "no device", 0);
        });

        DiscoveryResult result = discovery.scan(candidates(300), 9999, 99, 5000,
                new AtomicBoolean(false));
        assertEquals(256, result.candidatesConsidered());
        assertEquals(256, calls.get());
        assertFalse(result.partial());
        assertEquals(256, result.failures().size());
    }

    @Test
    public void successAndFailureResultsRemainDistinct() {
        WledDiscovery discovery = new WledDiscovery((host, port) -> {
            if (host.equals("192.168.1.1")) {
                Capabilities capabilities = new Capabilities("0.15.1", "dev-1", 274,
                        21324, false, ChannelHint.RGB, "RGB", "", false, 0);
                return ProbeResult.success(new WledDevice(host, capabilities));
            }
            return ProbeResult.failure(ProbeError.HTTP_ERROR, "no device", 0);
        });

        DiscoveryResult result = discovery.scan(candidates(2), 256, 4, 5000,
                new AtomicBoolean(false));
        assertFalse(result.partial());
        assertEquals(1, result.devices().size());
        assertEquals(1, result.failures().size());
        assertEquals(2, result.candidatesConsidered());
    }

    @Test
    public void cancellationDoesNotPublishLateResults() throws Exception {
        WledDiscovery discovery = new WledDiscovery((host, port) -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ProbeResult.failure(ProbeError.TIMEOUT, "interrupted", 0);
            }
            Capabilities capabilities = new Capabilities("0.15.1", "dev", 10,
                    21324, false, ChannelHint.RGB, "RGB", "", false, 0);
            return ProbeResult.success(new WledDevice(host, capabilities));
        });
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cancelled.set(true);
        });
        canceller.setDaemon(true);
        canceller.start();

        DiscoveryResult result = discovery.scan(candidates(4), 256, 4, 5000, cancelled);
        assertTrue(result.partial());
        assertTrue(result.cancelled());
        assertTrue(result.devices().isEmpty());
    }

    @Test
    public void discoveryCanUseTheRealLoopbackClient() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.NORMAL);
             WledHttpClient client = new WledHttpClient(true)) {
            WledDiscovery discovery = new WledDiscovery(client::probe);
            DiscoveryCandidate candidate = new DiscoveryCandidate("local",
                    "127.0.0.1", endpoint.port(), 1000);
            DiscoveryResult result = discovery.scan(Collections.singletonList(candidate),
                    256, 4, 5000, new AtomicBoolean(false));

            assertFalse(result.partial());
            assertEquals(1, result.devices().size());
            assertEquals(1, endpoint.requestCount());
        }
    }
}
