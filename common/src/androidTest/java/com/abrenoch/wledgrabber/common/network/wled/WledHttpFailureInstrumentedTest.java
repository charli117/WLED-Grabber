package com.abrenoch.wledgrabber.common.network.wled;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * CAP-3 device-level network failure: an endpoint that accepts the socket but
 * never answers must surface as a typed TIMEOUT, not as a success or a hang, so
 * the UI never reports a release it did not observe.
 */
public class WledHttpFailureInstrumentedTest {

    @Test
    public void silentServerSurfacesAsTimeout() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try (ServerSocket silent = new ServerSocket(0, 4,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread acceptor = new Thread(() -> {
                while (!silent.isClosed()) {
                    try (Socket socket = silent.accept()) {
                        // Accept and hold: never write a response.
                        Thread.sleep(4000);
                    } catch (IOException | InterruptedException ignored) {
                        return;
                    }
                }
            }, "silent-http");
            acceptor.setDaemon(true);
            acceptor.start();

            try (WledHttpClient client = new WledHttpClient(true)) {
                long start = System.currentTimeMillis();
                ProbeResult probe = client.probe("127.0.0.1", silent.getLocalPort());
                long elapsed = System.currentTimeMillis() - start;
                org.junit.Assert.assertFalse("silent server must not probe as success",
                        probe.isSuccess());
                org.junit.Assert.assertEquals(ProbeError.TIMEOUT, probe.errorType());
                org.junit.Assert.assertTrue("probe must respect its deadline, took " + elapsed,
                        elapsed < 5000);

                StateReadResult state = client.readState("127.0.0.1", silent.getLocalPort());
                org.junit.Assert.assertFalse("silent server must not read as success",
                        state.isSuccess());
                org.junit.Assert.assertEquals(ProbeError.TIMEOUT, state.error());

                boolean off = client.turnOff("127.0.0.1", silent.getLocalPort());
                org.junit.Assert.assertFalse("must not report off without a readback", off);
            }
        }
    }

    @Test
    public void refusedPortSurfacesAsConnectFailure() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            closedPort = probe.getLocalPort();
        }
        try (WledHttpClient client = new WledHttpClient(true)) {
            ProbeResult result = client.probe("127.0.0.1", closedPort);
            org.junit.Assert.assertFalse(result.isSuccess());
            org.junit.Assert.assertEquals(ProbeError.CONNECT_FAILURE, result.errorType());
        }
    }
}
