package com.abrenoch.wledgrabber.tv;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * One-off output probe: cycle solid red, green and blue for a few seconds each,
 * then restore the previous WLED state. The user watches the strip.
 */
@RunWith(AndroidJUnit4.class)
public class WledOutputProbeTest {
    @Test
    public void cycleSolidColors() throws Exception {
        String host = InstrumentationRegistry.getArguments().getString("wledHost", "10.245.0.99");
        String original = get(host, "/json/state");
        try {
            show(host, "red", 255, 0, 0);
            Thread.sleep(12000);
            show(host, "green", 0, 255, 0);
            Thread.sleep(12000);
            show(host, "blue", 0, 0, 255);
            Thread.sleep(12000);
        } finally {
            post(host, "/json/state", original);
        }
    }

    private static void show(String host, String name, int r, int g, int b) throws Exception {
        android.util.Log.i("WledOutputProbe", "show " + name);
        post(host, "/json/state", "{\"on\":true,\"live\":false,\"bri\":128,"
                + "\"seg\":[{\"id\":0,\"fx\":0,\"col\":[[" + r + "," + g + "," + b
                + "],[0,0,0],[0,0,0]]}]}");
    }

    private static String get(String host, String path) throws Exception {
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
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static void post(String host, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://" + host + path).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        android.util.Log.i("WledOutputProbe", "POST " + status);
        connection.disconnect();
    }
}
