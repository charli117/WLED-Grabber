package com.abrenoch.wledgrabber.common.network.wled;

/** Control-plane boundary shared by probe, state readback and off. */
public interface WledControlClient {
    ProbeResult probe(String host, int httpPort);

    StateReadResult readState(String host, int httpPort);

    boolean turnOn(String host, int httpPort);

    boolean turnOff(String host, int httpPort);
}
