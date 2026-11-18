package com.abrenoch.wledgrabber.common.fixtures;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal localhost HTTP endpoint with deterministic success, error, delay,
 * oversized-body, redirect and malformed-body modes.
 *
 * <p>Implemented with ServerSocket so it also compiles on the Android unit-test
 * classpath, which does not expose java.net.http or com.sun.net.httpserver.</p>
 */
public final class ControlledHttpEndpoint implements AutoCloseable {
    public enum Mode {
        NORMAL,
        ERROR,
        SLOW,
        OVERSIZED,
        REDIRECT,
        MALFORMED,
        WRONG_SHAPE,
        MISSING_FIELDS,
        NON_WLED,
        STATE_OFF,
        STATE_LIVE,
        STATE_WITHOUT_LIVE
    }

    private static final int SLOW_DELAY_MS = 1500;
    private static final int OVERSIZED_BYTES = 70000;

    private final ServerSocket server;
    private final Thread thread;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean running = true;
    private volatile String lastMethod = "";
    private volatile String lastPath = "";
    private volatile String lastPostBody;
    private volatile String lastBody = "";

    public ControlledHttpEndpoint(Mode mode) throws IOException {
        server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        thread = new Thread(() -> serve(mode), "controlled-http-endpoint");
        thread.setDaemon(true);
        thread.start();
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getLocalPort() + path;
    }

    public int port() {
        return server.getLocalPort();
    }

    public int requestCount() {
        return requests.get();
    }

    public String lastMethod() {
        return lastMethod;
    }

    public String lastPath() {
        return lastPath;
    }

    public String lastPostBody() {
        return lastPostBody;
    }

    public String lastBody() {
        return lastBody;
    }

    private void serve(Mode mode) {
        while (running) {
            try (Socket socket = server.accept()) {
                requests.incrementAndGet();
                readRequest(socket);
                if (mode == Mode.SLOW) {
                    Thread.sleep(SLOW_DELAY_MS);
                }
                Response response = responseFor(mode, lastPath);
                writeResponse(socket, response);
            } catch (IOException e) {
                // A timeout test closes the client socket while the fixture is still serving;
                // keep the accept loop alive until close() is called.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void readRequest(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine != null) {
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) {
                lastMethod = parts[0];
                lastPath = parts[1];
            }
        }
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    contentLength = 0;
                }
            }
        }
        StringBuilder body = new StringBuilder();
        int remaining = contentLength;
        char[] buffer = new char[512];
        while (remaining > 0) {
            int read = reader.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            body.append(buffer, 0, read);
            remaining -= read;
        }
        lastBody = body.toString();
        if ("POST".equals(lastMethod)) lastPostBody = lastBody;
    }

    private static Response responseFor(Mode mode, String path) {
        switch (mode) {
            case ERROR:
                return new Response(500, "Internal Server Error", "error", null);
            case REDIRECT:
                return new Response(302, "Found", "", "/json/state");
            case OVERSIZED:
                char[] oversized = new char[OVERSIZED_BYTES];
                java.util.Arrays.fill(oversized, 'x');
                return new Response(200, "OK", new String(oversized), null);
            case MALFORMED:
                return new Response(200, "OK", "not-json", null);
            case WRONG_SHAPE:
                return new Response(200, "OK",
                        "{\"info\":{\"ver\":\"0.15.1\",\"leds\":{\"count\":274}}}", null);
            case MISSING_FIELDS:
                return new Response(200, "OK",
                        "{\"ver\":\"0.15.1\",\"mac\":\"example-device-id\","
                                + "\"udpport\":21324,\"leds\":{\"rgbw\":false}}", null);
            case NON_WLED:
                return new Response(200, "OK", "{\"name\":\"not-wled\"}", null);
            case STATE_OFF:
                return new Response(200, "OK", "{\"on\":false,\"live\":false}", null);
            case STATE_LIVE:
                return "/json/info".equals(path)
                        ? new Response(200, "OK", "{\"live\":true}", null)
                        : new Response(200, "OK", "{\"on\":false,\"live\":false}", null);
            case STATE_WITHOUT_LIVE:
                // WLED 16.x omits "live" from /json/state; only /json/info has it.
                return path != null && path.startsWith("/json/info")
                        ? new Response(200, "OK", "{\"live\":false}", null)
                        : new Response(200, "OK", "{\"on\":false}", null);
            case NORMAL:
            case SLOW:
            default:
                return new Response(200, "OK",
                        "{\"ver\":\"0.15.1\",\"mac\":\"example-device-id\","
                                + "\"udpport\":21324,\"live\":false,"
                                + "\"leds\":{\"count\":274,\"rgbw\":false}}", null);
        }
    }

    private static void writeResponse(Socket socket, Response response) throws IOException {
        byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(response.status).append(' ')
                .append(response.reason).append("\r\n");
        headers.append("Content-Type: application/json\r\n");
        headers.append("Content-Length: ").append(body.length).append("\r\n");
        headers.append("Connection: close\r\n");
        if (response.location != null) {
            headers.append("Location: ").append(response.location).append("\r\n");
        }
        headers.append("\r\n");
        OutputStream output = socket.getOutputStream();
        output.write(headers.toString().getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    @Override
    public void close() {
        running = false;
        try {
            server.close();
        } catch (IOException ignored) {
            // Best-effort cleanup in tests.
        }
    }

    private static final class Response {
        private final int status;
        private final String reason;
        private final String body;
        private final String location;

        private Response(int status, String reason, String body, String location) {
            this.status = status;
            this.reason = reason;
            this.body = body;
            this.location = location;
        }
    }
}
