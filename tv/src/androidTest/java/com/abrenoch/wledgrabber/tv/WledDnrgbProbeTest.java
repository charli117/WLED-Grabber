package com.abrenoch.wledgrabber.tv;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * One-off live-path probe: send the same DNRGB frames the app sends, but with a
 * solid colour, so the user can tell whether the realtime path itself matches.
 */
@RunWith(AndroidJUnit4.class)
public class WledDnrgbProbeTest {
    private static final int LEDS = 274;

    @Test
    public void cycleSolidColorsOverDnrgb() throws Exception {
        String host = InstrumentationRegistry.getArguments().getString("wledHost", "10.245.0.99");
        int port = Integer.parseInt(
                InstrumentationRegistry.getArguments().getString("wledUdpPort", "21324"));
        InetAddress address = InetAddress.getByName(host);
        DatagramSocket socket = new DatagramSocket();
        byte[] packet = new byte[4 + LEDS * 3];
        packet[0] = 4;
        packet[1] = 2;
        packet[2] = 0;
        packet[3] = 0;
        try {
            send(socket, address, port, packet, 255, 0, 0, 15000);
            send(socket, address, port, packet, 0, 255, 0, 15000);
            send(socket, address, port, packet, 0, 0, 255, 15000);
        } finally {
            socket.close();
            post(host, "/json/state", "{\"live\":false,\"on\":false}");
        }
    }

    private static void send(DatagramSocket socket, InetAddress address, int port,
                             byte[] packet, int r, int g, int b, long durationMs)
            throws Exception {
        Log.i("WledDnrgbProbe", "color " + r + "," + g + "," + b);
        for (int i = 4; i < packet.length; i += 3) {
            packet[i] = (byte) r;
            packet[i + 1] = (byte) g;
            packet[i + 2] = (byte) b;
        }
        long end = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < end) {
            socket.send(new DatagramPacket(packet, packet.length, address, port));
            Thread.sleep(33);
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
        Log.i("WledDnrgbProbe", "restore " + connection.getResponseCode());
        connection.disconnect();
    }
}
