package com.abrenoch.wledgrabber.tv;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One-off diagnostic: print the WLED configuration that affects colour output. */
@RunWith(AndroidJUnit4.class)
public class WledColorDiagnosticsTest {
    private static final String TAG = "WledColorDiag";

    @Test
    public void dumpColorRelevantDeviceSettings() throws Exception {
        String host = InstrumentationRegistry.getArguments().getString("wledHost", "10.245.0.99");
        JSONObject info = get(host, "/json/info");
        JSONObject state = get(host, "/json/state");
        JSONObject cfg = get(host, "/json/cfg");
        JSONObject leds = info.optJSONObject("leds");
        JSONObject wifi = info.optJSONObject("wifi");
        Log.i(TAG, "info live=" + info.opt("live")
                + " pwr=" + (leds == null ? "?" : leds.opt("pwr"))
                + " maxpwr=" + (leds == null ? "?" : leds.opt("maxpwr"))
                + " fps=" + (leds == null ? "?" : leds.opt("fps"))
                + " rssi=" + (wifi == null ? "?" : wifi.opt("rssi"))
                + " signal=" + (wifi == null ? "?" : wifi.opt("signal")));
        Log.i(TAG, "state=" + state.toString());
        if (cfg.optJSONObject("hw") != null) {
            Log.i(TAG, "hw.led=" + cfg.optJSONObject("hw").optJSONObject("led"));
        }
        Log.i(TAG, "light=" + cfg.opt("light"));
        Log.i(TAG, "if=" + cfg.opt("if"));
    }

    private static JSONObject get(String host, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://" + host + path).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }
}
