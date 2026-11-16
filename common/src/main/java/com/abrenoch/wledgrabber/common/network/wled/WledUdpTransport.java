package com.abrenoch.wledgrabber.common.network.wled;

import java.io.IOException;
import java.util.List;

/**
 * Encodes a complete frame and writes each packet once. A local write failure
 * abandons the rest of that frame; stale fragments are never retried.
 */
public final class WledUdpTransport {
    private final DatagramSink sink;

    public WledUdpTransport(DatagramSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink is required");
        }
        this.sink = sink;
    }

    public boolean sendRgb(byte[] rgb, int ledCount) {
        try {
            return send(WledPacketizer.packetizeRgb(rgb, ledCount));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean sendRgbDdp(byte[] rgb, int ledCount) {
        try {
            return send(WledPacketizer.packetizeDdpRgb(rgb, ledCount));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean sendRgbw(byte[] rgbw, int ledCount) {
        try {
            return send(WledPacketizer.packetizeRgbw(rgbw, ledCount));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean send(List<byte[]> packets) {
        try {
            for (byte[] packet : packets) {
                sink.send(packet, 0, packet.length);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
