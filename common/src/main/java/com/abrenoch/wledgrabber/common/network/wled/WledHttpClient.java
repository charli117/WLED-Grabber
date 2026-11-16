package com.abrenoch.wledgrabber.common.network.wled;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Restricted LAN HTTP client: one GET /json/info per probe, one state read and
 * one off POST per release attempt. No redirects, no bodies above 64KiB.
 */
public final class WledHttpClient implements WledControlClient, AutoCloseable {
    public static final int CONNECT_TIMEOUT_MS = 1000;
    public static final int READ_TIMEOUT_MS = 1000;
    public static final int APP_DEADLINE_MS = 3000;
    public static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_PARALLEL_REQUESTS = 4;
    private static final String OFF_BODY = "{\"on\":false,\"live\":false,\"tt\":0}";
    private static final String ON_BODY = "{\"on\":true}";

    private final boolean allowLoopback;
    private final ExecutorService executor;

    public WledHttpClient() {
        this(false);
    }

    public WledHttpClient(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "wled-http");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(MAX_PARALLEL_REQUESTS, factory);
    }

    @Override
    public ProbeResult probe(String host, int port) {
        long now = System.currentTimeMillis();
        if (!isValidHostInput(host) || port < 1 || port > 65535) {
            return ProbeResult.failure(ProbeError.INVALID_ENDPOINT,
                    "host/port must be explicit, path/userinfo-free LAN input", now);
        }
        Future<ProbeResult> future;
        try {
            future = executor.submit(() -> probeBlocking(host, port));
        } catch (RejectedExecutionException e) {
            return ProbeResult.failure(ProbeError.BUSY, "probe executor is full", now);
        }
        try {
            return future.get(APP_DEADLINE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ProbeResult.failure(ProbeError.TIMEOUT, "probe exceeded app deadline", now);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ProbeResult.failure(ProbeError.TIMEOUT, "probe interrupted", now);
        } catch (ExecutionException e) {
            return ProbeResult.failure(ProbeError.CONNECT_FAILURE,
                    String.valueOf(e.getCause()), now);
        }
    }

    @Override
    public StateReadResult readState(String host, int port) {
        if (!isValidHostInput(host) || port < 1 || port > 65535) {
            return StateReadResult.failure(ProbeError.INVALID_ENDPOINT);
        }
        Future<StateReadResult> future;
        try {
            future = executor.submit(() -> readStateBlocking(host, port));
        } catch (RejectedExecutionException e) {
            return StateReadResult.failure(ProbeError.BUSY);
        }
        try {
            return future.get(APP_DEADLINE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return StateReadResult.failure(ProbeError.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return StateReadResult.failure(ProbeError.TIMEOUT);
        } catch (ExecutionException e) {
            return StateReadResult.failure(ProbeError.CONNECT_FAILURE);
        }
    }

    @Override
    public boolean turnOn(String host, int port) {
        return submitControl(host, port, ON_BODY);
    }

    @Override
    public boolean turnOff(String host, int port) {
        return submitControl(host, port, OFF_BODY);
    }

    private boolean submitControl(String host, int port, String body) {
        if (!isValidHostInput(host) || port < 1 || port > 65535) {
            return false;
        }
        Future<Boolean> future;
        try {
            future = executor.submit(() -> postStateBlocking(host, port, body));
        } catch (RejectedExecutionException e) {
            return false;
        }
        try {
            return future.get(APP_DEADLINE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return false;
        } catch (ExecutionException e) {
            return false;
        }
    }

    private ProbeResult probeBlocking(String host, int port) {
        long now = System.currentTimeMillis();
        HttpResult result = request(host, port, "/json/info", "GET", null);
        if (result.error != null) {
            return ProbeResult.failure(result.error, result.detail, now);
        }
        if (result.status != 200) {
            return ProbeResult.failure(ProbeError.HTTP_ERROR,
                    "HTTP status " + result.status, now);
        }
        return parseInfo(new String(result.body, StandardCharsets.UTF_8), host, now);
    }

    private StateReadResult readStateBlocking(String host, int port) {
        HttpResult result = request(host, port, "/json/state", "GET", null);
        if (result.error != null) {
            return StateReadResult.failure(result.error);
        }
        if (result.status != 200) {
            return StateReadResult.failure(ProbeError.HTTP_ERROR);
        }
        try {
            JSONObject state = new JSONObject(new String(result.body, StandardCharsets.UTF_8));
            if (!state.has("on") || !state.has("live")) {
                return StateReadResult.failure(ProbeError.MISSING_FIELD);
            }
            return StateReadResult.success(state.getBoolean("on"), state.getBoolean("live"));
        } catch (JSONException e) {
            return StateReadResult.failure(ProbeError.MALFORMED_JSON);
        }
    }

    private boolean postStateBlocking(String host, int port, String body) {
        HttpResult result = request(host, port, "/json/state", "POST", body);
        return result.error == null && result.status == 200;
    }

    private HttpResult request(String host, int port, String path, String method, String body) {
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return HttpResult.failure(ProbeError.DNS_FAILURE, "unable to resolve host");
        }
        if (!isLanAddress(address)) {
            return HttpResult.failure(ProbeError.INVALID_ENDPOINT,
                    "target is not a trusted LAN address");
        }

        HttpURLConnection connection = null;
        try {
            String literal = address.getHostAddress();
            String hostPart = literal.contains(":") ? "[" + literal + "]" : literal;
            URL url = new URL("http", hostPart, port, path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                return HttpResult.failure(ProbeError.REDIRECT, "redirect is not allowed");
            }
            if (status != 200) {
                return new HttpResult(status, new byte[0], null, null);
            }
            byte[] response = readLimited(connection.getInputStream(), MAX_BODY_BYTES);
            if (response == null) {
                return HttpResult.failure(ProbeError.BODY_TOO_LARGE, "response exceeds 64KiB");
            }
            return new HttpResult(status, response, null, null);
        } catch (SocketTimeoutException e) {
            return HttpResult.failure(ProbeError.TIMEOUT, "connect/read timeout");
        } catch (MalformedURLException e) {
            return HttpResult.failure(ProbeError.INVALID_ENDPOINT, "invalid endpoint");
        } catch (IOException e) {
            return HttpResult.failure(ProbeError.CONNECT_FAILURE, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ProbeResult parseInfo(String body, String host, long now) {
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            return ProbeResult.failure(ProbeError.MALFORMED_JSON, "invalid JSON", now);
        }
        if (!root.has("leds") && root.has("info")) {
            return ProbeResult.failure(ProbeError.WRONG_SHAPE,
                    "aggregate /json shape is not /json/info", now);
        }
        JSONObject leds = root.optJSONObject("leds");
        if (leds == null) {
            return ProbeResult.failure(ProbeError.WRONG_SHAPE, "root leds object missing", now);
        }
        String version = root.optString("ver", "");
        String mac = root.optString("mac", "");
        int count = leds.optInt("count", -1);
        int udpPort = root.optInt("udpport", -1);
        if (version.isEmpty() || mac.isEmpty() || count < 1 || count > 65536
                || udpPort < 1 || udpPort > 65535) {
            return ProbeResult.failure(ProbeError.MISSING_FIELD,
                    "ver/mac/leds.count/udpport required", now);
        }
        boolean live = root.optBoolean("live", false);
        boolean rgbwPresent = leds.has("rgbw");
        boolean rgbw = leds.optBoolean("rgbw", false);
        String lc = root.optString("lc", "");
        String seglc = root.optString("seglc", "");
        ChannelHint hint;
        if (rgbwPresent) {
            hint = rgbw ? ChannelHint.RGBW : ChannelHint.RGB;
        } else if (containsIgnoreCase(lc, "CCT") || containsIgnoreCase(seglc, "CCT")) {
            hint = ChannelHint.MIXED_OR_CCT;
        } else if (containsIgnoreCase(lc, "RGBW") || containsIgnoreCase(seglc, "RGBW")) {
            hint = ChannelHint.RGBW;
        } else if (containsIgnoreCase(lc, "RGB") || containsIgnoreCase(seglc, "RGB")) {
            hint = ChannelHint.RGB;
        } else {
            hint = ChannelHint.UNKNOWN;
        }
        Capabilities capabilities = new Capabilities(version, mac, count, udpPort,
                hint == ChannelHint.RGBW, hint, lc, seglc, live, now);
        return ProbeResult.success(new WledDevice(host, capabilities));
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        try (InputStream stream = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    return null;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private boolean isLanAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            return allowLoopback;
        }
        if (address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return address.isSiteLocalAddress() || address.isLinkLocalAddress();
        }
        if (bytes.length == 16) {
            if (address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            return (bytes[0] & 0xfe) == 0xfc;
        }
        return false;
    }

    private static boolean isValidHostInput(String host) {
        if (host == null || host.isEmpty() || !host.equals(host.trim())) {
            return false;
        }
        for (char c : host.toCharArray()) {
            if (c == '/' || c == '@' || c == '?' || c == '#' || Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsIgnoreCase(String value, String token) {
        return value != null && value.toUpperCase().contains(token);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class HttpResult {
        private final int status;
        private final byte[] body;
        private final ProbeError error;
        private final String detail;

        private HttpResult(int status, byte[] body, ProbeError error, String detail) {
            this.status = status;
            this.body = body;
            this.error = error;
            this.detail = detail;
        }

        private static HttpResult failure(ProbeError error, String detail) {
            return new HttpResult(0, new byte[0], error, detail);
        }
    }
}
