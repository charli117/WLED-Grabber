package com.abrenoch.wledgrabber.common.network.wled;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/** UDP socket adapter; the caller owns its lifecycle and must close it once. */
public final class UdpDatagramSink implements DatagramSink, AutoCloseable {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;

    public UdpDatagramSink(String host, int port) throws IOException {
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535) {
            throw new IOException("invalid UDP endpoint");
        }
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(host);
        this.port = port;
    }

    @Override
    public void send(byte[] data, int offset, int length) throws IOException {
        socket.send(new DatagramPacket(data, offset, length, address, port));
    }

    @Override
    public void close() {
        socket.close();
    }
}
