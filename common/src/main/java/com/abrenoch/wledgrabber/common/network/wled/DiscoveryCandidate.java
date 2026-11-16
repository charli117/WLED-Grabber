package com.abrenoch.wledgrabber.common.network.wled;

/** NSD/manual/scan candidate; identity is separate from the cached IP address. */
public final class DiscoveryCandidate {
    private final String identity;
    private final String host;
    private final int httpPort;
    private final long lastSeenMillis;

    public DiscoveryCandidate(String identity, String host, int httpPort, long lastSeenMillis) {
        this.identity = identity;
        this.host = host;
        this.httpPort = httpPort;
        this.lastSeenMillis = lastSeenMillis;
    }

    public String identity() {
        return identity;
    }

    public String host() {
        return host;
    }

    public int httpPort() {
        return httpPort;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public boolean isValid() {
        return identity != null && !identity.isEmpty()
                && isValidHost(host) && httpPort >= 1 && httpPort <= 65535;
    }

    public static boolean isValidHost(String host) {
        if (host == null || host.isEmpty() || !host.equals(host.trim())) {
            return false;
        }
        for (char c : host.toCharArray()) {
            if (c == '/' || c == '@' || c == '?' || c == '#' || Character.isWhitespace(c)) {
                return false;
            }
        }
        String lower = host.toLowerCase();
        return !lower.startsWith("fe80:") || host.contains("%");
    }
}
