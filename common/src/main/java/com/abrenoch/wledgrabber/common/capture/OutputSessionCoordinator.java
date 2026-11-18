package com.abrenoch.wledgrabber.common.capture;

/** Process-wide CAPTURE/TEST ownership gate; one active owner per process. */
public final class OutputSessionCoordinator {
    private static final OutputSessionCoordinator INSTANCE = new OutputSessionCoordinator();

    private OutputOwner owner = OutputOwner.NONE;
    private long epoch;
    private String ownerId;

    public static OutputSessionCoordinator get() {
        return INSTANCE;
    }

    public synchronized boolean acquire(OutputOwner requested, String id) {
        if (requested == null || requested == OutputOwner.NONE
                || id == null || id.isEmpty()) {
            throw new IllegalArgumentException("owner/id required");
        }
        if (owner != OutputOwner.NONE) {
            return false;
        }
        owner = requested;
        ownerId = id;
        epoch++;
        return true;
    }

    public synchronized boolean release(OutputOwner expected, String id, long expectedEpoch) {
        if (owner != expected || epoch != expectedEpoch
                || ownerId == null || !ownerId.equals(id)) {
            return false;
        }
        owner = OutputOwner.NONE;
        ownerId = null;
        return true;
    }

    /** A stop deadline limits the caller's wait, not the lifetime of the old writer's lease. */
    public LedStreamer.ReleaseAction releaseWhenDone(OutputOwner expected, String id, long expectedEpoch,
                                                       LedStreamer.ReleaseAction finalIo) {
        return reason -> {
            try {
                return finalIo.release(reason);
            } finally {
                release(expected, id, expectedEpoch);
            }
        };
    }

    public synchronized OutputOwner owner() {
        return owner;
    }

    public synchronized long epoch() {
        return epoch;
    }

    public synchronized String ownerId() {
        return ownerId;
    }
}
