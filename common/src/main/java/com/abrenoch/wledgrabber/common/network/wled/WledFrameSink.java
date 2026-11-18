package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.capture.FrameLease;
import com.abrenoch.wledgrabber.common.capture.LedStreamer;
import com.abrenoch.wledgrabber.common.led.Protocol;

/** CAPTURE and TEST issue power and datagrams on their session's single writer. */
public final class WledFrameSink implements LedStreamer.FrameSink {
    private final DeviceEndpoint device;
    private final WledControlClient control;
    private final WledUdpTransport udp;

    public WledFrameSink(DeviceEndpoint device, WledControlClient control, DatagramSink sink) {
        this.device = device;
        this.control = control;
        this.udp = new WledUdpTransport(sink);
    }

    @Override
    public void beforeFirstColor() {
        control.turnOn(device.host(), device.httpPort());
    }

    @Override
    public boolean send(FrameLease lease) {
        return send(lease.data(), lease.ledCount(), lease.channels());
    }

    @Override
    public boolean sendBlack(int count, int channels) {
        return send(new byte[count * channels], count, channels);
    }

    private boolean send(byte[] data, int count, int channels) {
        if (channels == 4) {
            return udp.sendRgbw(data, count);
        }
        return device.protocol() == Protocol.DDP ? udp.sendRgbDdp(data, count) : udp.sendRgb(data, count);
    }
}
