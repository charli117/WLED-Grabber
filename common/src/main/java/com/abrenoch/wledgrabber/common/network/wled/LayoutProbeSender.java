package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.Protocol;

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
        OutputSessionCoordinator coordinator = OutputSessionCoordinator.get();
        if (!coordinator.acquire(OutputOwner.TEST, OWNER_ID)) {
            return false;
        }
        long epoch = coordinator.epoch();
        final byte[] frame = LayoutProbe.buildFrame(compiled, logical, 255, 255, 255);
        final int ledCount = compiled.mapping().physicalLedCount();
        final Protocol protocol = snapshot.device().protocol();
        final int port = protocol == Protocol.DDP
                ? snapshot.device().ddpPort() : snapshot.device().udpPort();
        Thread worker = new Thread(() -> {
            boolean ddp = protocol == Protocol.DDP;
            try (UdpDatagramSink sink = new UdpDatagramSink(host, port)) {
                WledUdpTransport transport = new WledUdpTransport(sink);
                for (int i = 0; i < REPEATS; i++) {
                    if (ddp) {
                        transport.sendRgbDdp(frame, ledCount);
                    } else {
                        transport.sendRgb(frame, ledCount);
                    }
                    try {
                        Thread.sleep(INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
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
}
