package com.abrenoch.wledgrabber.common.network.wled;

/** Best-effort black frame sender; success is not a device ACK. */
public interface FrameSender {
    boolean sendBlack(int ledCount, boolean rgbw);
}
