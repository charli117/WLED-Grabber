package com.abrenoch.wledgrabber.common.fixtures;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeviceJsonFixtureTest {

    @Test
    public void infoUsesRootShapeAndPinnedDeviceValues() throws Exception {
        JSONObject info = new JSONObject(DeviceJsonFixture.infoJson());

        assertEquals("0.15.1", info.getString("ver"));
        assertEquals(21324, info.getInt("udpport"));
        assertFalse(info.getBoolean("live"));
        assertFalse(info.has("info"));

        JSONObject leds = info.getJSONObject("leds");
        assertEquals(274, leds.getInt("count"));
        assertFalse(leds.getBoolean("rgbw"));
    }

    @Test
    public void stateExposesOnAndLiveBooleans() throws Exception {
        JSONObject state = new JSONObject(DeviceJsonFixture.stateJson());

        assertFalse(state.getBoolean("on"));
        assertFalse(state.getBoolean("live"));
        assertTrue(state.has("on"));
    }
}
