package com.abrenoch.wledgrabber.common.network.wled;

/**
 * Best-effort WLED power-on off the caller thread. UDP realtime frames alone do
 * not switch the master power on, so every output owner issues this first.
 */
public final class WledPowerOn {
    private WledPowerOn() {
    }

    /** Dispatches {@link WledControlClient#turnOn} on a daemon worker. */
    public static void async(WledControlClient client, String host, int httpPort,
                             String threadName) {
        if (client == null || host == null || host.trim().isEmpty()
                || httpPort < 1 || httpPort > 65535) {
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                client.turnOn(host, httpPort);
            } catch (RuntimeException ignored) {
                // A failed power-on leaves the send path honestly unconfirmed.
            }
        }, threadName == null ? "wled-power-on" : threadName);
        worker.setDaemon(true);
        worker.start();
    }
}
