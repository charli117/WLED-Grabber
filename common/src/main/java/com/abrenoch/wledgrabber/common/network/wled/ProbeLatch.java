package com.abrenoch.wledgrabber.common.network.wled;

/**
 * Single-active layout-probe selection. Pressing the active key turns it off;
 * pressing another key replaces it. Pure state so toggling is testable without
 * the network or Android views.
 */
final class ProbeLatch {
    enum Result {
        STARTED,
        SWITCHED,
        STOPPED
    }

    private String activeKey;

    Result toggle(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("probe key required");
        }
        if (key.equals(activeKey)) {
            activeKey = null;
            return Result.STOPPED;
        }
        boolean replaced = activeKey != null;
        activeKey = key;
        return replaced ? Result.SWITCHED : Result.STARTED;
    }

    boolean isActive(String key) {
        return key != null && key.equals(activeKey);
    }

    String activeKey() {
        return activeKey;
    }

    void clear() {
        activeKey = null;
    }
}
