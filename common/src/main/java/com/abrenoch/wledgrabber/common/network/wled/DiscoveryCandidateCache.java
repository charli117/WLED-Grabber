package com.abrenoch.wledgrabber.common.network.wled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deduplicates NSD identities, refreshes last-seen and expires stale candidates. */
public final class DiscoveryCandidateCache {
    private final long ttlMillis;
    private final Map<String, DiscoveryCandidate> byIdentity = new LinkedHashMap<>();

    public DiscoveryCandidateCache(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttlMillis = ttlMillis;
    }

    public void add(DiscoveryCandidate candidate) {
        if (candidate == null || !candidate.isValid()) {
            return;
        }
        DiscoveryCandidate previous = byIdentity.get(candidate.identity());
        if (previous == null || candidate.lastSeenMillis() >= previous.lastSeenMillis()) {
            byIdentity.put(candidate.identity(), candidate);
        }
    }

    public List<DiscoveryCandidate> candidates(long nowMillis) {
        List<DiscoveryCandidate> result = new ArrayList<>();
        for (DiscoveryCandidate candidate : byIdentity.values()) {
            if (nowMillis - candidate.lastSeenMillis() <= ttlMillis) {
                result.add(candidate);
            }
        }
        Collections.sort(result, new Comparator<DiscoveryCandidate>() {
            @Override
            public int compare(DiscoveryCandidate left, DiscoveryCandidate right) {
                return Long.compare(right.lastSeenMillis(), left.lastSeenMillis());
            }
        });
        return result;
    }

    public int size() {
        return byIdentity.size();
    }
}
