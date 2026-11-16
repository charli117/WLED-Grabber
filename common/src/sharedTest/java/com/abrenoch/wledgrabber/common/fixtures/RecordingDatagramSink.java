package com.abrenoch.wledgrabber.common.fixtures;

import com.abrenoch.wledgrabber.common.network.wled.DatagramSink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Records copies of outgoing datagrams for transport tests. */
public final class RecordingDatagramSink implements DatagramSink {
    private final List<byte[]> packets = new ArrayList<>();

    public void send(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException("invalid datagram range");
        }
        packets.add(Arrays.copyOfRange(data, offset, offset + length));
    }

    public int count() {
        return packets.size();
    }

    public byte[] last() {
        if (packets.isEmpty()) {
            return null;
        }
        return packets.get(packets.size() - 1).clone();
    }

    public List<byte[]> packets() {
        List<byte[]> copy = new ArrayList<>(packets.size());
        for (byte[] packet : packets) {
            copy.add(packet.clone());
        }
        return copy;
    }

    public void clear() {
        packets.clear();
    }
}
