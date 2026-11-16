package com.abrenoch.wledgrabber.common.fixtures;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ControlledHttpEndpointTest {

    private static final class Result {
        private int status;
        private String body;
    }

    private static Result get(ControlledHttpEndpoint endpoint, String path, int readTimeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.url(path)).openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(readTimeoutMs);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        Result result = new Result();
        result.status = connection.getResponseCode();
        InputStream stream = result.status >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        result.body = readAll(stream);
        connection.disconnect();
        return result;
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    @Test
    public void normalEndpointReturnsDeviceJson() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.NORMAL)) {
            Result result = get(endpoint, "/json/info", 2000);
            assertEquals(200, result.status);
            assertTrue(result.body.contains("\"ver\":\"0.15.1\""));
            assertEquals(1, endpoint.requestCount());
        }
    }

    @Test
    public void errorRedirectOversizedAndMalformedModesAreDeterministic() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.ERROR)) {
            assertEquals(500, get(endpoint, "/json/info", 2000).status);
        }

        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.REDIRECT)) {
            assertEquals(302, get(endpoint, "/json/info", 2000).status);
        }

        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.OVERSIZED)) {
            assertTrue(get(endpoint, "/json/info", 2000).body.length() > 65536);
        }

        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.MALFORMED)) {
            assertEquals("not-json", get(endpoint, "/json/info", 2000).body);
        }
    }

    @Test
    public void slowEndpointCanBeExercisedForTimeoutHandling() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(ControlledHttpEndpoint.Mode.SLOW)) {
            try {
                get(endpoint, "/json/info", 300);
                fail("expected the client to time out");
            } catch (SocketTimeoutException expected) {
                assertEquals(0, endpoint.requestCount() - 1);
            }
        }
    }
}
