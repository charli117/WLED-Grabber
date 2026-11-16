package com.abrenoch.wledgrabber.common.network.wled;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-triggered, bounded WLED HTTP scan over the current private IPv4 /24. */
public final class WledScannerTask {
    public interface Listener {
        void onScanStarted();

        void onScanFinished(@Nullable WledDevice device);

        void onScanError(String error);
    }

    private static final int MAX_CANDIDATES = 256;
    private static final int CONCURRENCY = 4;
    private static final long TIMEOUT_MS = 15000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wled-scan");
        thread.setDaemon(true);
        return thread;
    });

    public void start(Listener listener) {
        if (listener == null) {
            return;
        }
        listener.onScanStarted();
        executor.execute(() -> {
            List<DiscoveryCandidate> candidates;
            try {
                candidates = localCandidates();
            } catch (Exception e) {
                main.post(() -> listener.onScanError(e.getMessage()));
                return;
            }
            try (WledHttpClient client = new WledHttpClient()) {
                WledDiscovery discovery = new WledDiscovery(client::probe);
                DiscoveryResult result = discovery.scan(candidates, MAX_CANDIDATES,
                        CONCURRENCY, TIMEOUT_MS, cancelled);
                WledDevice device = result.devices().isEmpty() ? null : result.devices().get(0);
                main.post(() -> listener.onScanFinished(device));
            }
        });
    }

    public void cancel() {
        cancelled.set(true);
    }

    private static List<DiscoveryCandidate> localCandidates() throws Exception {
        List<DiscoveryCandidate> candidates = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback()) {
                continue;
            }
            for (java.net.InterfaceAddress address : network.getInterfaceAddresses()) {
                InetAddress ip = address.getAddress();
                if (!(ip instanceof Inet4Address) || !ip.isSiteLocalAddress()) {
                    continue;
                }
                byte[] bytes = ip.getAddress();
                String prefix = (bytes[0] & 0xff) + "." + (bytes[1] & 0xff)
                        + "." + (bytes[2] & 0xff) + ".";
                for (int host = 1; host <= 254 && candidates.size() < MAX_CANDIDATES; host++) {
                    String hostAddress = prefix + host;
                    candidates.add(new DiscoveryCandidate(hostAddress, hostAddress, 80, now));
                }
            }
        }
        return candidates;
    }
}
