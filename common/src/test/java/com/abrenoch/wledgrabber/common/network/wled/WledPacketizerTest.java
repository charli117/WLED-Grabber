package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.fixtures.RecordingDatagramSink;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WledPacketizerTest {

    private static byte[] frame(int ledCount, int channels) {
        byte[] frame = new byte[ledCount * channels];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (byte) ((i * 7 + 3) & 0xff);
        }
        return frame;
    }

    private static void assertNoType5(List<byte[]> packets) {
        for (byte[] packet : packets) {
            assertTrue(packet[0] != 5);
        }
    }

    private static byte[] reassembleDnrgb(List<byte[]> packets, int ledCount) {
        byte[] rebuilt = new byte[ledCount * 3];
        boolean[] seen = new boolean[ledCount];
        for (byte[] packet : packets) {
            assertEquals(4, packet[0]);
            assertEquals(2, packet[1]);
            int start = ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
            int dataBytes = packet.length - 4;
            assertEquals(0, dataBytes % 3);
            int leds = dataBytes / 3;
            assertTrue(leds >= 2);
            assertTrue(leds <= WledPacketizer.DNRGB_MAX_LEDS);
            for (int i = 0; i < leds; i++) {
                int logical = start + i;
                assertTrue(logical < ledCount);
                assertFalse("duplicate LED " + logical, seen[logical]);
                seen[logical] = true;
                System.arraycopy(packet, 4 + i * 3, rebuilt, logical * 3, 3);
            }
        }
        for (boolean value : seen) {
            assertTrue(value);
        }
        return rebuilt;
    }

    private static byte[] reassembleDdp(List<byte[]> packets, int ledCount, int channels) {
        byte[] rebuilt = new byte[ledCount * channels];
        int expectedOffset = 0;
        for (int i = 0; i < packets.size(); i++) {
            byte[] packet = packets.get(i);
            int offset = ((packet[4] & 0xff) << 24) | ((packet[5] & 0xff) << 16)
                    | ((packet[6] & 0xff) << 8) | (packet[7] & 0xff);
            int length = ((packet[8] & 0xff) << 8) | (packet[9] & 0xff);
            assertEquals(expectedOffset, offset);
            assertEquals(packet.length - 10, length);
            boolean last = i == packets.size() - 1;
            assertEquals(last ? 0x41 : 0x40, packet[0] & 0xff);
            assertEquals(0, packet[1]);
            assertEquals(1, packet[3]);
            System.arraycopy(packet, 10, rebuilt, offset, length);
            expectedOffset += length;
        }
        assertEquals(rebuilt.length, expectedOffset);
        return rebuilt;
    }

    @Test
    public void rgbSingleUsesDrgbType2() {
        byte[] frame = new byte[]{0x11, 0x22, 0x33};
        List<byte[]> packets = WledPacketizer.packetizeRgb(frame, 1);
        assertEquals(1, packets.size());
        assertArrayEquals(new byte[]{2, 2, 0x11, 0x22, 0x33}, packets.get(0));
        assertNoType5(packets);
    }

    @Test
    public void dnrgbCountsRebuildWithoutGapsAndAvoidOneLedTails() {
        for (int count : new int[]{2, 274, 288, 489, 490, 978, 979}) {
            byte[] frame = frame(count, 3);
            List<byte[]> packets = WledPacketizer.packetizeRgb(frame, count);
            assertArrayEquals("count " + count, frame, reassembleDnrgb(packets, count));
            assertNoType5(packets);
        }

        List<byte[]> count490 = WledPacketizer.packetizeRgb(frame(490, 3), 490);
        assertEquals(2, count490.size());
        assertEquals(488, (count490.get(0).length - 4) / 3);
        assertEquals(2, (count490.get(1).length - 4) / 3);

        List<byte[]> count979 = WledPacketizer.packetizeRgb(frame(979, 3), 979);
        assertEquals(3, count979.size());
        assertEquals(489, (count979.get(0).length - 4) / 3);
        assertEquals(488, (count979.get(1).length - 4) / 3);
        assertEquals(2, (count979.get(2).length - 4) / 3);
    }

    @Test
    public void rgb274DnrgbIsExactly826Bytes() {
        List<byte[]> packets = WledPacketizer.packetizeRgb(frame(274, 3), 274);
        int total = 0;
        for (byte[] packet : packets) {
            total += packet.length;
        }
        assertEquals(826, total);
    }

    @Test
    public void rgbwDdpCountsFlagsOffsetsAndLengthsAreExact() {
        for (int count : new int[]{1, 274, 360, 361, 720, 721}) {
            byte[] frame = frame(count, 4);
            List<byte[]> packets = WledPacketizer.packetizeRgbw(frame, count);
            assertArrayEquals("count " + count, frame, reassembleDdp(packets, count, 4));
            for (byte[] packet : packets) {
                assertEquals(WledPacketizer.DDP_DATATYPE_RGBW, packet[2] & 0xff);
            }
            assertNoType5(packets);
        }

        List<byte[]> count1 = WledPacketizer.packetizeRgbw(frame(1, 4), 1);
        assertEquals(1, count1.size());
        assertEquals(14, count1.get(0).length);
        assertEquals(0x41, count1.get(0)[0] & 0xff);

        List<byte[]> count274 = WledPacketizer.packetizeRgbw(frame(274, 4), 274);
        assertEquals(1, count274.size());
        assertEquals(1106, count274.get(0).length);
        assertEquals(0x41, count274.get(0)[0] & 0xff);
        assertEquals(1096, ((count274.get(0)[8] & 0xff) << 8) | (count274.get(0)[9] & 0xff));

        List<byte[]> count361 = WledPacketizer.packetizeRgbw(frame(361, 4), 361);
        assertEquals(2, count361.size());
        assertEquals(1440, count361.get(0).length - 10);
        assertEquals(4, count361.get(1).length - 10);
        assertEquals(0x40, count361.get(0)[0] & 0xff);
        assertEquals(0x41, count361.get(1)[0] & 0xff);

        List<byte[]> count721 = WledPacketizer.packetizeRgbw(frame(721, 4), 721);
        assertEquals(3, count721.size());
        assertEquals(0x40, count721.get(0)[0] & 0xff);
        assertEquals(0x40, count721.get(1)[0] & 0xff);
        assertEquals(0x41, count721.get(2)[0] & 0xff);
        assertEquals(2880, ((count721.get(2)[4] & 0xff) << 24)
                | ((count721.get(2)[5] & 0xff) << 16)
                | ((count721.get(2)[6] & 0xff) << 8)
                | (count721.get(2)[7] & 0xff));
    }

    @Test
    public void ddpRgbCounts480And481ArePackedTo1440ByteData() {
        List<byte[]> count480 = WledPacketizer.packetizeDdpRgb(frame(480, 3), 480);
        assertEquals(1, count480.size());
        assertEquals(1440, count480.get(0).length - 10);
        assertEquals(0x41, count480.get(0)[0] & 0xff);
        assertEquals(WledPacketizer.DDP_DATATYPE_RGB, count480.get(0)[2] & 0xff);
        assertArrayEquals(frame(480, 3), reassembleDdp(count480, 480, 3));

        List<byte[]> count481 = WledPacketizer.packetizeDdpRgb(frame(481, 3), 481);
        assertEquals(2, count481.size());
        assertEquals(1440, count481.get(0).length - 10);
        assertEquals(3, count481.get(1).length - 10);
        assertArrayEquals(frame(481, 3), reassembleDdp(count481, 481, 3));
    }

    @Test
    public void invalidCountsChannelsAndFramesAreRejected() {
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeRgb(new byte[3], 0)));
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeRgb(new byte[2], 1)));
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeRgb(new byte[3], 65537)));
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeDnrgb(new byte[3], 1)));
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeRgbw(new byte[3], 1)));
        assertTrue(throwsIllegal(() -> WledPacketizer.packetizeRgb(null, 1)));
    }

    @Test
    public void transportStopsAfterFirstLocalWriteFailureWithoutRetry() {
        FailingSink sink = new FailingSink();
        WledUdpTransport transport = new WledUdpTransport(sink);
        assertFalse(transport.sendRgb(frame(490, 3), 490));
        assertEquals(1, sink.count);
    }

    @Test
    public void transportWritesThroughRecordingSink() {
        RecordingDatagramSink sink = new RecordingDatagramSink();
        WledUdpTransport transport = new WledUdpTransport(sink);
        assertTrue(transport.sendRgbw(frame(274, 4), 274));
        assertEquals(1, sink.count());
        assertEquals(1106, sink.last().length);
        assertEquals(0x41, sink.last()[0] & 0xff);
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static boolean throwsIllegal(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static final class FailingSink implements DatagramSink {
        private int count;

        @Override
        public void send(byte[] data, int offset, int length) throws IOException {
            count++;
            throw new IOException("local failure");
        }
    }
}
