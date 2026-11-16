package com.abrenoch.wledgrabber.common.network.wled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded discovery: at most 256 candidates, 4 workers and 15 seconds. Results
 * completed after cancellation/timeout are not published.
 */
public final class WledDiscovery {
    public static final int MAX_CANDIDATES = 256;
    public static final int MAX_CONCURRENCY = 4;
    public static final long MAX_TIMEOUT_MS = 15000;

    public interface Probe {
        ProbeResult probe(String host, int httpPort);
    }

    private final Probe probe;

    public WledDiscovery(Probe probe) {
        if (probe == null) {
            throw new IllegalArgumentException("probe is required");
        }
        this.probe = probe;
    }

    public DiscoveryResult scan(List<DiscoveryCandidate> candidates, int maxCandidates,
                                int concurrency, long timeoutMillis,
                                AtomicBoolean cancelled) {
        int cap = Math.min(MAX_CANDIDATES, Math.max(0, maxCandidates));
        int workers = Math.min(MAX_CONCURRENCY, Math.max(1, concurrency));
        long budget = Math.min(MAX_TIMEOUT_MS, Math.max(0, timeoutMillis));

        List<DiscoveryCandidate> unique = dedupe(candidates, cap);
        if (unique.isEmpty() || cancelled != null && cancelled.get()) {
            return new DiscoveryResult(Collections.emptyList(), Collections.emptyList(),
                    cancelled != null && cancelled.get(), cancelled != null && cancelled.get(), 0);
        }

        ExecutorService pool = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "wled-discovery");
            thread.setDaemon(true);
            return thread;
        });
        CompletionService<ProbeResult> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        for (DiscoveryCandidate candidate : unique) {
            if (cancelled != null && cancelled.get()) {
                break;
            }
            completion.submit(() -> {
                try {
                    return probe.probe(candidate.host(), candidate.httpPort());
                } catch (RuntimeException e) {
                    return ProbeResult.failure(ProbeError.CONNECT_FAILURE,
                            String.valueOf(e), System.currentTimeMillis());
                }
            });
            submitted++;
        }

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budget);
        List<WledDevice> devices = new ArrayList<>();
        List<ProbeResult> failures = new ArrayList<>();
        boolean partial = submitted < unique.size();
        int completed = 0;
        for (int i = 0; i < submitted; i++) {
            if (cancelled != null && cancelled.get()) {
                partial = true;
                break;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                partial = true;
                break;
            }
            try {
                Future<ProbeResult> future = completion.poll(remaining, TimeUnit.NANOSECONDS);
                if (future == null) {
                    partial = true;
                    break;
                }
                ProbeResult result = future.get();
                if (cancelled != null && cancelled.get()) {
                    partial = true;
                    break;
                }
                completed++;
                if (result.isSuccess()) {
                    devices.add(result.device());
                } else {
                    failures.add(result);
                }
            } catch (Exception e) {
                partial = true;
                break;
            }
        }
        if (completed < submitted) {
            partial = true;
        }
        pool.shutdownNow();
        return new DiscoveryResult(devices, failures, partial,
                cancelled != null && cancelled.get(), submitted);
    }

    private static List<DiscoveryCandidate> dedupe(List<DiscoveryCandidate> candidates, int cap) {
        Map<String, DiscoveryCandidate> unique = new LinkedHashMap<>();
        if (candidates == null) {
            return Collections.emptyList();
        }
        for (DiscoveryCandidate candidate : candidates) {
            if (unique.size() >= cap) {
                break;
            }
            if (candidate == null || !candidate.isValid()) {
                continue;
            }
            String key = candidate.identity() + "|" + candidate.host() + "|" + candidate.httpPort();
            if (!unique.containsKey(key)) {
                unique.put(key, candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
