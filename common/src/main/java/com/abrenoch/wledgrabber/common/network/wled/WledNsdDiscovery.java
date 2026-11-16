package com.abrenoch.wledgrabber.common.network.wled;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.util.HashMap;
import java.util.Map;

/** NSD browse adapter for _wled._tcp.; resolution is cached by service identity. */
public final class WledNsdDiscovery implements AutoCloseable {
    public interface Listener {
        void onCandidate(DiscoveryCandidate candidate);
    }

    private static final String SERVICE_TYPE = "_wled._tcp.";

    private final Map<String, NsdManager.ResolveListener> resolving = new HashMap<>();
    private NsdManager manager;
    private NsdManager.DiscoveryListener discoveryListener;

    public void start(Context context, Listener listener) {
        if (context == null || listener == null || manager != null) {
            return;
        }
        manager = (NsdManager) context.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
        if (manager == null) {
            return;
        }
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                resolve(serviceInfo, listener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }
        };
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void resolve(NsdServiceInfo serviceInfo, Listener listener) {
        String name = serviceInfo.getServiceName();
        if (name == null || resolving.containsKey(name)) {
            return;
        }
        NsdManager.ResolveListener resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                resolving.remove(name);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                resolving.remove(name);
                if (info.getHost() == null) {
                    return;
                }
                String host = info.getHost().getHostAddress();
                DiscoveryCandidate candidate = new DiscoveryCandidate(name, host,
                        info.getPort(), System.currentTimeMillis());
                if (candidate.isValid()) {
                    listener.onCandidate(candidate);
                }
            }
        };
        resolving.put(name, resolveListener);
        manager.resolveService(serviceInfo, resolveListener);
    }

    @Override
    public void close() {
        if (manager != null && discoveryListener != null) {
            try {
                manager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException ignored) {
                // Already stopped.
            }
        }
        resolving.clear();
        discoveryListener = null;
        manager = null;
    }
}
