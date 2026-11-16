package com.abrenoch.wledgrabber.common.network.wled;

import java.io.IOException;

/** Local UDP write boundary injected into the transport. */
public interface DatagramSink {
    void send(byte[] data, int offset, int length) throws IOException;
}
