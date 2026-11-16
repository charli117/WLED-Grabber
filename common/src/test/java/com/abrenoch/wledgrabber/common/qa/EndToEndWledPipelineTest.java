package com.abrenoch.wledgrabber.common.qa;

import com.abrenoch.wledgrabber.common.fixtures.ControlledHttpEndpoint;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedColorSampler;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.PlaneView;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.WhiteMode;
import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.UdpDatagramSink;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledPacketizer;
import com.abrenoch.wledgrabber.common.network.wled.WledUdpTransport;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * QA-1 synthetic screen -> sample -> packetize -> localhost UDP capture, plus
 * localhost HTTP release readback. Decoders below are independent of production
 * encoders and assert fixed offsets/datatypes.
 */
public class EndToEndWledPipelineTest {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 36;

    private static CompiledLayout layout274() {
        LedLayoutConfig config = new LedLayoutConfig(86, 90, 49, 49, 0, 0, 161,
                true, GapMode.SKIP, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result = new ClassicLedLayout().compile(
                config, 274, WIDTH, HEIGHT);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static PlaneView solidPlane(int r, int g, int b) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = (y * WIDTH + x) * 4;
                buffer.put(index, (byte) r);
                buffer.put(index + 1, (byte) g);
                buffer.put(index + 2, (byte) b);
                buffer.put(index + 3, (byte) 255);
            }
        }
        buffer.position(0);
        buffer.limit(WIDTH * HEIGHT * 4);
        ValidationResult<PlaneView> view = PlaneView.create(buffer, WIDTH, HEIGHT,
                WIDTH * 4, 4, 0, 0, WIDTH, HEIGHT);
        assertTrue(view.errors().toString(), view.isValid());
        return view.value();
    }

    private static List<byte[]> receive(DatagramSocket socket, int expectedBytes,
                                        int headerBytes) throws Exception {
        List<byte[]> packets = new ArrayList<>();
        int assembled = 0;
        long deadline = System.currentTimeMillis() + 2000;
        byte[] buffer = new byte[2048];
        while (assembled < expectedBytes && System.currentTimeMillis() < deadline) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(500);
            socket.receive(packet);
            byte[] copy = Arrays.copyOf(packet.getData(), packet.getLength());
            packets.add(copy);
            assembled += copy.length - headerBytes;
        }
        return packets;
    }

    @Test
    public void synthetic274SamplesToDnrgbAndLocalhostUdpCapture() throws Exception {
        CompiledLayout layout = layout274();
        byte[] frame = new byte[274 * 3];
        assertTrue(new LedColorSampler().samplePhysical(solidPlane(10, 20, 30), layout,
                false, RgbwMode.RGB, null, frame));
        for (int i = 0; i < frame.length; i += 3) {
            assertEquals(10, frame[i] & 0xff);
            assertEquals(20, frame[i + 1] & 0xff);
            assertEquals(30, frame[i + 2] & 0xff);
        }

        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             UdpDatagramSink sink = new UdpDatagramSink("127.0.0.1", receiver.getLocalPort())) {
            assertTrue(new WledUdpTransport(sink).sendRgb(frame, 274));
            List<byte[]> packets = receive(receiver, 274 * 3, 4);
            assertEquals(1, packets.size());
            byte[] packet = packets.get(0);
            assertEquals(4, packet[0]);
            assertEquals(2, packet[1]);
            assertEquals(0, packet[2]);
            assertEquals(0, packet[3]);
            assertArrayEquals(frame, Arrays.copyOfRange(packet, 4, packet.length));
        }
    }

    @Test
    public void syntheticRgbwDdpCarriesRgbAndDdpDatatype() throws Exception {
        CompiledLayout layout = layout274();
        byte[] frame = new byte[274 * 4];
        assertTrue(new LedColorSampler().samplePhysical(solidPlane(10, 20, 30), layout,
                false, RgbwMode.RGBW, WhiteMode.PRESERVE, frame));
        for (int i = 0; i < frame.length; i += 4) {
            assertEquals(10, frame[i] & 0xff);
            assertEquals(20, frame[i + 1] & 0xff);
            assertEquals(30, frame[i + 2] & 0xff);
        }

        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             UdpDatagramSink sink = new UdpDatagramSink("127.0.0.1", receiver.getLocalPort())) {
            assertTrue(new WledUdpTransport(sink).sendRgbw(frame, 274));
            List<byte[]> packets = receive(receiver, 274 * 4, 10);
            assertEquals(1, packets.size());
            byte[] packet = packets.get(0);
            assertEquals(0x41, packet[0] & 0xff); // PUSH on the final packet
            assertEquals(0, packet[1] & 0xff);
            assertEquals(WledPacketizer.DDP_DATATYPE_RGBW, packet[2] & 0xff);
            assertEquals(1, packet[3] & 0xff);
            assertEquals(0, packet[4] & 0xff);
            assertEquals(0, packet[5] & 0xff);
            assertEquals(0, packet[6] & 0xff);
            assertEquals(0, packet[7] & 0xff);
            int length = ((packet[8] & 0xff) << 8) | (packet[9] & 0xff);
            assertEquals(274 * 4, length);
            assertArrayEquals(frame, Arrays.copyOfRange(packet, 10, packet.length));
        }
    }

    @Test
    public void ddpMultiPacketOffsetsAndPushAreFixed() throws Exception {
        int leds = 500; // 1500 RGB bytes -> 1440 + 60
        byte[] frame = new byte[leds * 3];
        for (int i = 0; i < frame.length; i++) {
            frame[i] = (byte) (i & 0xff);
        }
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
             UdpDatagramSink sink = new UdpDatagramSink("127.0.0.1", receiver.getLocalPort())) {
            assertTrue(new WledUdpTransport(sink).sendRgbDdp(frame, leds));
            List<byte[]> packets = receive(receiver, frame.length, 10);
            assertEquals(2, packets.size());
            assertEquals(0x40, packets.get(0)[0] & 0xff);
            assertEquals(0x41, packets.get(1)[0] & 0xff);
            assertEquals(1440, ((packets.get(0)[8] & 0xff) << 8) | (packets.get(0)[9] & 0xff));
            assertEquals(60, ((packets.get(1)[8] & 0xff) << 8) | (packets.get(1)[9] & 0xff));
            byte[] assembled = new byte[frame.length];
            for (byte[] packet : packets) {
                int offset = ((packet[4] & 0xff) << 24) | ((packet[5] & 0xff) << 16)
                        | ((packet[6] & 0xff) << 8) | (packet[7] & 0xff);
                int length = ((packet[8] & 0xff) << 8) | (packet[9] & 0xff);
                System.arraycopy(packet, 10, assembled, offset, length);
            }
            assertArrayEquals(frame, assembled);
        }
    }

    @Test
    public void localhostOffAndReadbackUseTheSamePolicyClient() throws Exception {
        try (ControlledHttpEndpoint endpoint = new ControlledHttpEndpoint(
                ControlledHttpEndpoint.Mode.STATE_OFF);
             WledHttpClient client = new WledHttpClient(true)) {
            assertTrue(client.turnOff("127.0.0.1", endpoint.port()));
            StateReadResult state = client.readState("127.0.0.1", endpoint.port());
            assertTrue(String.valueOf(state.error()), state.isSuccess());
            assertEquals(false, state.on());
            assertEquals(false, state.live());
            assertEquals(2, endpoint.requestCount());
        }
    }
}
