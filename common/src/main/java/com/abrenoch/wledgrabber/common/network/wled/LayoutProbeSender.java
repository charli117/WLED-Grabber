package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

/**
 * One-shot layout probe: pulses the selected physical LEDs in white for about
 * two seconds so the user can check an edge or the input position on the strip.
 *
 * <p>Takes the shared TEST output lease, so it never fights a running capture;
 * the caller reports that the output is busy instead.</p>
 */
public final class LayoutProbeSender {
    private static final String OWNER_ID = "layout-probe";
    private static final int REPEATS = 8;
    private static final long INTERVAL_MS = 250L;

    private LayoutProbeSender() {
    }

    /** @return true when the pulse started; false when another session owns output. */
    public static boolean pulse(ConfigSnapshot snapshot, CompiledLayout compiled, int[] logical) {
        if (snapshot == null || compiled == null || logical == null || logical.length == 0) {
            return false;
        }
        String host = snapshot.device().host();
        if (host == null || host.trim().isEmpty()) {
            // Do not report a started pulse when there is no endpoint to send to.
            return false;
        }
        Transport transport = transportFor(snapshot);
        if (transport == null) {
            return false;
        }
        RgbwMode rgbwMode = snapshot.device().rgbwMode();
        int channels = rgbwMode == RgbwMode.RGBW ? 4 : 3;
        WhiteMode whiteMode = snapshot.device().whiteMode();
        if (channels == 4 && whiteMode == null) {
            return false;
        }
        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        if (!coordinator.acquire(OutputOwner.TEST, OWNER_ID)) {
            return false;
        }
        long epoch = coordinator.epoch();
        final byte[] frame = LayoutProbe.buildFrame(compiled, logical, channels, whiteMode,
                255, 255, 255);
        final byte[] black = new byte[compiled.mapping().physicalLedCount() * channels];
        final int ledCount = compiled.mapping().physicalLedCount();
        final int httpPort = snapshot.device().httpPort();
        final int port = transport == Transport.DDP
                ? snapshot.device().ddpPort() : snapshot.device().udpPort();
        Thread worker = new Thread(() -> {
            try (UdpDatagramSink sink = new UdpDatagramSink(host, port);
                 WledHttpClient control = new WledHttpClient()) {
                WledUdpTransport udp = new WledUdpTransport(sink);
                try {
                    // Realtime UDP alone does not turn the master power on.
                    control.turnOn(host, httpPort);
                } catch (RuntimeException ignored) {
                    // The pulse remains visible only if the strip is already on.
                }
                for (int i = 0; i < REPEATS; i++) {
                    send(udp, transport, channels, frame, ledCount);
                    try {
                        Thread.sleep(INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                send(udp, transport, channels, black, ledCount);
            } catch (Exception ignored) {
                // Best effort: the UI only reports that the pulse started.
            } finally {
                coordinator.release(OutputOwner.TEST, OWNER_ID, epoch);
            }
        }, "layout-probe");
        worker.setDaemon(true);
        worker.start();
        return true;
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
}
