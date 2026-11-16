package com.abrenoch.wledgrabber.common.network.wled;

import java.util.ArrayList;
import java.util.List;

/** Fixed-firmware packet encoders; no type-5 branch and no UDP socket lifecycle. */
public final class WledPacketizer {
    public static final int DNRGB_MAX_LEDS = 489;
    public static final int DDP_MAX_DATA_BYTES = 1440;
    public static final int DDP_DATATYPE_RGB = 0x0B;
    public static final int DDP_DATATYPE_RGBW = 0x1B;

    private WledPacketizer() {
    }

    public static List<byte[]> packetizeRgb(byte[] rgb, int ledCount) {
        validate(rgb, ledCount, 3);
        if (ledCount == 1) {
            List<byte[]> packets = new ArrayList<>(1);
            packets.add(new byte[]{2, 2, rgb[0], rgb[1], rgb[2]});
            return packets;
        }
        return packetizeDnrgb(rgb, ledCount);
    }

    public static List<byte[]> packetizeDnrgb(byte[] rgb, int ledCount) {
        validate(rgb, ledCount, 3);
        if (ledCount == 1) {
            throw new IllegalArgumentException("DNRGB requires at least 2 LEDs");
        }
        List<byte[]> packets = new ArrayList<>();
        int start = 0;
        int remaining = ledCount;
        while (remaining > 0) {
            int take = Math.min(DNRGB_MAX_LEDS, remaining);
            if (remaining - take == 1) {
                take--;
            }
            byte[] packet = new byte[4 + take * 3];
            packet[0] = 4;
            packet[1] = 2;
            packet[2] = (byte) ((start >>> 8) & 0xff);
            packet[3] = (byte) (start & 0xff);
            System.arraycopy(rgb, start * 3, packet, 4, take * 3);
            packets.add(packet);
            start += take;
            remaining -= take;
        }
        return packets;
    }

    public static List<byte[]> packetizeRgbw(byte[] rgbw, int ledCount) {
        validate(rgbw, ledCount, 4);
        return packetizeDdp(rgbw, ledCount, 4, DDP_DATATYPE_RGBW);
    }

    public static List<byte[]> packetizeDdpRgb(byte[] rgb, int ledCount) {
        validate(rgb, ledCount, 3);
        return packetizeDdp(rgb, ledCount, 3, DDP_DATATYPE_RGB);
    }

    private static List<byte[]> packetizeDdp(byte[] frame, int ledCount,
                                             int channels, int datatype) {
        int totalBytes = ledCount * channels;
        List<byte[]> packets = new ArrayList<>();
        int offset = 0;
        while (offset < totalBytes) {
            int length = Math.min(DDP_MAX_DATA_BYTES, totalBytes - offset);
            boolean last = offset + length == totalBytes;
            byte[] packet = new byte[10 + length];
            packet[0] = (byte) (last ? 0x41 : 0x40); // PUSH on final packet only
            packet[1] = 0;                            // sequence fixed at 0
            packet[2] = (byte) datatype;
            packet[3] = 1;                            // destination
            packet[4] = (byte) ((offset >>> 24) & 0xff);
            packet[5] = (byte) ((offset >>> 16) & 0xff);
            packet[6] = (byte) ((offset >>> 8) & 0xff);
            packet[7] = (byte) (offset & 0xff);
            packet[8] = (byte) ((length >>> 8) & 0xff);
            packet[9] = (byte) (length & 0xff);
            System.arraycopy(frame, offset, packet, 10, length);
            packets.add(packet);
            offset += length;
        }
        return packets;
    }

    private static void validate(byte[] frame, int ledCount, int channels) {
        if (frame == null) {
            throw new IllegalArgumentException("frame is null");
        }
        if (ledCount < 1 || ledCount > 65536) {
            throw new IllegalArgumentException("LED count must be 1..65536");
        }
        long expected = (long) ledCount * channels;
        if (frame.length != expected) {
            throw new IllegalArgumentException("frame length does not match count*channels");
        }
    }
}
