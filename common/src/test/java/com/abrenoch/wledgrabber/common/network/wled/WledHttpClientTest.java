package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.fixtures.ControlledHttpEndpoint;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WledHttpClientTest {
    private final WledHttpClient loopbackClient = new WledHttpClient(true);

    @After
    public void tearDown() throws Exception {
        loopbackClient.close();
    }

    private static ProbeResult probe(WledHttpClient client, ControlledHttpEndpoint endpoint) {
        return client.probe("127.0.0.1", endpoint.port());
    }

    @Test
    public void rootInfoIsParsedOncePerProbe() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.NORMAL)) {
            ProbeResult result = probe(loopbackClient, endpoint);
            assertTrue(String.valueOf(result.error()), result.isSuccess());
            assertEquals("0.15.1", result.device().firmwareVersion());
            assertEquals("example-device-id", result.device().deviceId());
            assertEquals(274, result.device().observedLedCount());
            assertEquals(21324, result.device().udpPort());
            assertEquals(ChannelHint.RGB, result.device().capabilities().channelHint());
            assertEquals(1, endpoint.requestCount());

            ProbeResult second = probe(loopbackClient, endpoint);
            assertTrue(second.isSuccess());
            assertEquals(2, endpoint.requestCount());
        }
    }

    @Test
    public void wrongShapesMissingFieldsBadJsonAndNonWledAreExplicit() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.WRONG_SHAPE)) {
            assertEquals(ProbeError.WRONG_SHAPE, probe(loopbackClient, endpoint).errorType());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.MISSING_FIELDS)) {
            assertEquals(ProbeError.MISSING_FIELD, probe(loopbackClient, endpoint).errorType());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.NON_WLED)) {
            assertEquals(ProbeError.WRONG_SHAPE, probe(loopbackClient, endpoint).errorType());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.MALFORMED)) {
            assertEquals(ProbeError.MALFORMED_JSON, probe(loopbackClient, endpoint).errorType());
        }
    }

    @Test
    public void redirectOversizedAndSlowResponsesAreRejected() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.REDIRECT)) {
            ProbeResult result = probe(loopbackClient, endpoint);
            assertEquals(ProbeError.REDIRECT, result.errorType());
            assertEquals(1, endpoint.requestCount());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.OVERSIZED)) {
            assertEquals(ProbeError.BODY_TOO_LARGE, probe(loopbackClient, endpoint).errorType());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.SLOW)) {
            assertEquals(ProbeError.TIMEOUT, probe(loopbackClient, endpoint).errorType());
        }
    }

    @Test
    public void publicLoopbackAndPathInputsAreRejectedWithoutRequests() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.NORMAL)) {
            WledHttpClient strict = new WledHttpClient(false);
            try {
                assertEquals(ProbeError.INVALID_ENDPOINT,
                        strict.probe("127.0.0.1", endpoint.port()).errorType());
                assertEquals(0, endpoint.requestCount());
            } finally {
                strict.close();
            }
        }
        assertEquals(ProbeError.INVALID_ENDPOINT,
                loopbackClient.probe("8.8.8.8", 80).errorType());
        assertEquals(ProbeError.INVALID_ENDPOINT,
                loopbackClient.probe("http://127.0.0.1", 80).errorType());
        assertEquals(ProbeError.INVALID_ENDPOINT,
                loopbackClient.probe("127.0.0.1", 0).errorType());
    }

    @Test
    public void stateReadbackAndOffUseTheRestrictedClient() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(
                ControlledHttpEndpoint.Mode.STATE_OFF)) {
            StateReadResult state = loopbackClient.readState("127.0.0.1", endpoint.port());
            assertTrue(String.valueOf(state.error()), state.isSuccess());
            assertFalse(state.on());
            assertFalse(state.live());
            assertTrue(loopbackClient.turnOff("127.0.0.1", endpoint.port()));
            assertEquals(2, endpoint.requestCount());
        }
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(
                ControlledHttpEndpoint.Mode.STATE_LIVE)) {
            StateReadResult state = loopbackClient.readState("127.0.0.1", endpoint.port());
            assertTrue(state.isSuccess());
            assertTrue(state.live());
        }
    }

    @Test
    public void turnOnPostsPowerOnJson() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(
                ControlledHttpEndpoint.Mode.STATE_OFF)) {
            assertTrue(loopbackClient.turnOn("127.0.0.1", endpoint.port()));
            assertEquals("POST", endpoint.lastMethod());
            assertEquals("/json/state", endpoint.lastPath());
            assertTrue(endpoint.lastBody(), endpoint.lastBody().contains("\"on\":true"));
        }
    }

    @Test
    public void errorResultsNeverPassAsDiscoverySuccess() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.ERROR)) {
            ProbeResult result = probe(loopbackClient, endpoint);
            assertFalse(result.isSuccess());
            assertEquals(ProbeError.HTTP_ERROR, result.errorType());
        }
    }
}
