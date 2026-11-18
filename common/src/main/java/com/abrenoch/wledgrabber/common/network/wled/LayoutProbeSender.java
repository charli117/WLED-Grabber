package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

/**
 * Latching layout probe: keeps the selected edge or input marker white until the
 * same action is pressed again. WLED realtime frames expire, so the worker keeps
 * resending while the probe is latched.
 *
 * <p>Takes the shared TEST output lease, so it never fights a running capture;
 * the caller reports that the output is busy instead. Only one probe is active
 * at a time, and switching edges reuses the same lease and worker.</p>
 */
public final class LayoutProbeSender {
    public static final String KEY_INPUT = "probe:input";

    public enum Result {
        STARTED,
        STOPPED,
        BUSY,
        INVALID
    }

    private static final String OWNER_ID = "layout-probe";
    private static final long INTERVAL_MS = 250L;
    private static final LayoutProbeSender INSTANCE = new LayoutProbeSender(WledHttpClient::new);
    private final java.util.concurrent.Callable<WledHttpClient> openClient;

    private final OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
    private final ProbeLatch latch = new ProbeLatch();
    private final Object lock = new Object();

    private Thread worker;
    private Endpoint currentEndpoint;
    private volatile boolean running;
    private volatile byte[] frame;

    LayoutProbeSender(java.util.concurrent.Callable<WledHttpClient> openClient) {
        this.openClient = openClient;
    }

    public static LayoutProbeSender get() {
        return INSTANCE;
    }

    /** Stable latch key for one edge; {@code null} is the input marker. */
    public static String keyFor(Side side) {
        return side == null ? KEY_INPUT : "probe:" + side.name();
    }

    /** Toggles one probe; every production caller shares the process-wide instance. */
    public Result toggle(ConfigSnapshot snapshot, CompiledLayout compiled, String key, int[] logical) {
        if (snapshot == null || compiled == null || key == null || key.isEmpty()
                || logical == null || logical.length == 0) {
            return Result.INVALID;
        }
        Endpoint endpoint = Endpoint.from(snapshot, compiled);
        if (endpoint == null) {
            return Result.INVALID;
        }
        byte[] next = LayoutProbe.buildFrame(compiled, logical, endpoint.channels,
                endpoint.whiteMode, 255, 255, 255);

        synchronized (lock) {
            if (worker != null && running) {
                if (latch.isActive(key)) {
                    latch.clear();
                    running = false;
                    lock.notifyAll();
                    return Result.STOPPED;
                }
                if (!endpoint.sameTarget(currentEndpoint) || next.length != frame.length) {
                    // The draft changed under a latched probe. Stop it rather than
                    // sending a frame with a different shape; the next press
                    // starts a fresh session.
                    latch.clear();
                    running = false;
                    lock.notifyAll();
                    return Result.STOPPED;
                }
                frame = next;
                latch.toggle(key);
                return Result.STARTED;
            }
            if (worker != null) {
                return Result.BUSY;
            }
            if (coordinator.owner() != OutputOwner.NONE
                    || !coordinator.acquire(OutputOwner.TEST, OWNER_ID)) {
                return Result.BUSY;
            }
            long epoch = coordinator.epoch();
            byte[] black = new byte[endpoint.ledCount * endpoint.channels];
            currentEndpoint = endpoint;
            frame = next;
            running = true;
            latch.toggle(key);
            worker = new Thread(() -> run(endpoint, black, epoch), "layout-probe");
            worker.setDaemon(true);
            worker.start();
            return Result.STARTED;
        }
    }

    public void stop() {
        synchronized (lock) {
            latch.clear();
            if (worker != null && running) {
                running = false;
                lock.notifyAll();
            }
        }
    }

    public boolean isActive(String key) {
        synchronized (lock) {
            return latch.isActive(key);
        }
    }

    private void run(Endpoint endpoint, byte[] black, long epoch) {
        try (UdpDatagramSink sink = new UdpDatagramSink(endpoint.host, endpoint.port);
             WledHttpClient control = openClient.call()) {
            WledUdpTransport udp = new WledUdpTransport(sink);
            if (!running) {
                return;
            }
            try {
                control.turnOn(endpoint.host, endpoint.httpPort);
                while (running) {
                    send(udp, endpoint.transport, endpoint.channels, frame, endpoint.ledCount);
                    synchronized (lock) {
                        if (running) lock.wait(INTERVAL_MS);
                    }
                }
            } finally {
                try {
                    send(udp, endpoint.transport, endpoint.channels, black, endpoint.ledCount);
                } finally {
                    if (control.turnOff(endpoint.host, endpoint.httpPort)) {
                        control.readState(endpoint.host, endpoint.httpPort);
                    }
                }
            }
        } catch (Exception ignored) {
            // Best effort: the UI only reports the latch state.
        } finally {
            coordinator.release(OutputOwner.TEST, OWNER_ID, epoch);
            synchronized (lock) {
                worker = null;
                currentEndpoint = null;
                frame = null;
                latch.clear();
            }
        }
    }

    /** Shared transport resolution; RGBW must never fall back to DNRGB/type5. */
    static Transport transportFor(ConfigSnapshot snapshot) {
        if (snapshot == null || snapshot.device() == null) {
            return null;
        }
        return Transport.resolve(snapshot.device().protocol(), snapshot.device().rgbwMode(),
                snapshot.device().physicalLedCount());
    }

    private static boolean send(WledUdpTransport udp, Transport transport, int channels,
                                byte[] frame, int ledCount) {
        if (channels == 4) {
            return udp.sendRgbw(frame, ledCount);
        }
        return transport == Transport.DDP
                ? udp.sendRgbDdp(frame, ledCount)
                : udp.sendRgb(frame, ledCount);
    }

    private static final class Endpoint {
        final String host;
        final int httpPort;
        final int port;
        final Transport transport;
        final int channels;
        final int ledCount;
        final WhiteMode whiteMode;

        Endpoint(String host, int httpPort, int port, Transport transport,
                 int channels, int ledCount, WhiteMode whiteMode) {
            this.host = host;
            this.httpPort = httpPort;
            this.port = port;
            this.transport = transport;
            this.channels = channels;
            this.ledCount = ledCount;
            this.whiteMode = whiteMode;
        }

        boolean sameTarget(Endpoint other) {
            return other != null
                    && host.equals(other.host)
                    && httpPort == other.httpPort
                    && port == other.port
                    && transport == other.transport
                    && channels == other.channels
                    && ledCount == other.ledCount;
        }

        static Endpoint from(ConfigSnapshot snapshot, CompiledLayout compiled) {
            if (snapshot == null || snapshot.device() == null || compiled == null
                    || compiled.mapping() == null) {
                return null;
            }
            String host = snapshot.device().host();
            if (host == null || host.trim().isEmpty()) {
                // Do not report a started probe when there is no endpoint.
                return null;
            }
            Transport transport = transportFor(snapshot);
            if (transport == null) {
                return null;
            }
            int channels = snapshot.device().rgbwMode() == RgbwMode.RGBW ? 4 : 3;
            WhiteMode whiteMode = snapshot.device().whiteMode();
            int ledCount = compiled.mapping().physicalLedCount();
            if (ledCount <= 0) {
                return null;
            }
            int port = transport == Transport.DDP
                    ? snapshot.device().ddpPort() : snapshot.device().udpPort();
            return new Endpoint(host.trim(), snapshot.device().httpPort(), port,
                    transport, channels, ledCount, whiteMode);
        }
    }
}
