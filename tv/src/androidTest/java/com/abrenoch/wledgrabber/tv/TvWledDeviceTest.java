package com.abrenoch.wledgrabber.tv;

import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.network.wled.Capabilities;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * QA-1 device-level smoke test for the Android TV runtime: the restricted
 * {@link WledHttpClient} must reach the configured LAN WLED over cleartext HTTP
 * and parse its root /json/info and /json/state shapes.
 */
@RunWith(AndroidJUnit4.class)
public class TvWledDeviceTest {

    @Test
    public void deviceReportsWledCapabilities() {
        ProbeResult result = WledTestSupport.probeOrSkip();
        WledDevice device = result.device();
        Capabilities capabilities = device.capabilities();

        assertNotNull(capabilities);
        assertFalse("firmware version must be present",
                capabilities.firmwareVersion().isEmpty());
        assertFalse("device identity must be present", capabilities.deviceId().isEmpty());
        assertTrue("observed LED count: " + capabilities.observedLedCount(),
                capabilities.observedLedCount() >= 1);
        assertTrue("udp port: " + capabilities.udpPort(),
                capabilities.udpPort() >= 1 && capabilities.udpPort() <= 65535);
        assertEquals(WledTestSupport.host(), device.address());
    }

    @Test
    public void stateEndpointIsReadableThroughRestrictedClient() {
        WledTestSupport.probeOrSkip();
        try (WledHttpClient client = new WledHttpClient()) {
            StateReadResult state = client.readState(WledTestSupport.host(),
                    WledTestSupport.httpPort());
            assertTrue("state read failed: " + state.error(), state.isSuccess());
        }
    }
}
