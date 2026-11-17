package com.abrenoch.wledgrabber.common.capture;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single UDP writer with a three-lease pool, latest-wins pending frame and a
 * stop barrier. Black is sent by the same writer after the last colour frame,
 * then the release callback runs.
 */
public final class LedStreamer {
    public interface FrameSink {
        boolean send(FrameLease lease);

        boolean sendBlack(int ledCount, int channels);
    }

    public interface ReleaseAction {
        boolean release(String reason);
    }

    public static final class Metrics {
        private final long offered;
        private final long dropped;
        private final long replaced;
        private final long sent;
        private final long failures;
        private final int poolInUse;
        private final boolean pending;
        private final boolean inFlight;

        private Metrics(long offered, long dropped, long replaced, long sent, long failures,
                        int poolInUse, boolean pending, boolean inFlight) {
            this.offered = offered;
            this.dropped = dropped;
            this.replaced = replaced;
            this.sent = sent;
            this.failures = failures;
            this.poolInUse = poolInUse;
            this.pending = pending;
            this.inFlight = inFlight;
        }

        public long offered() {
            return offered;
        }

        public long dropped() {
            return dropped;
        }

        public long replaced() {
            return replaced;
        }

        public long sent() {
            return sent;
        }

        public long failures() {
            return failures;
        }

        public int poolInUse() {
            return poolInUse;
        }

        public boolean pending() {
            return pending;
        }

        public boolean inFlight() {
            return inFlight;
        }
    }

    public static final class StopResult {
        private final boolean timedOut;
        private final boolean blackSent;
        private final boolean releaseCalled;
        private final boolean releaseOk;
        private final long dropped;

        private StopResult(boolean timedOut, boolean blackSent, boolean releaseCalled,
                           boolean releaseOk, long dropped) {
            this.timedOut = timedOut;
            this.blackSent = blackSent;
            this.releaseCalled = releaseCalled;
            this.releaseOk = releaseOk;
            this.dropped = dropped;
        }

        public boolean timedOut() {
            return timedOut;
        }

        public boolean blackSent() {
            return blackSent;
        }

        public boolean releaseCalled() {
            return releaseCalled;
        }

        public boolean releaseOk() {
            return releaseOk;
        }

        public long dropped() {
            return dropped;
        }
    }

    private final FramePool pool;
    private final FrameSink sink;
    private final ReleaseAction releaseAction;
    private final long stopBudgetMs;
    private final Object lock = new Object();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wled-frame-writer");
        thread.setDaemon(true);
        return thread;
    });
    private CountDownLatch stopLatch = new CountDownLatch(1);

    private long epoch;
    private OutputOwner owner = OutputOwner.NONE;
    private boolean started;
    private boolean stopping;
    private boolean writerRunning;
    private FrameLease pending;
    private FrameLease inFlight;
    private int blackLedCount;
    private int blackChannels;
    private String stopReason;

    private long offered;
    private long dropped;
    private long replaced;
    private long sent;
    private long failures;
    private boolean blackSent;
    private boolean releaseCalled;
    private boolean releaseOk;

    public LedStreamer(FramePool pool, FrameSink sink, ReleaseAction releaseAction,
                       long stopBudgetMs) {
        if (pool == null || sink == null || releaseAction == null || stopBudgetMs <= 0) {
            throw new IllegalArgumentException("pool/sink/release/budget are required");
        }
        this.pool = pool;
        this.sink = sink;
        this.releaseAction = releaseAction;
        this.stopBudgetMs = stopBudgetMs;
    }

    public void start(long sessionEpoch, OutputOwner owner, int ledCount, int channels) {
        synchronized (lock) {
            if (started || stopping) {
                throw new IllegalStateException("output already owned");
            }
            if (owner == OutputOwner.NONE) {
                throw new IllegalArgumentException("owner must be CAPTURE or TEST");
            }
            this.epoch = sessionEpoch;
            this.owner = owner;
            this.blackLedCount = ledCount;
            this.blackChannels = channels;
            this.started = true;
            this.blackSent = false;
            this.releaseCalled = false;
            this.releaseOk = false;
            this.stopLatch = new CountDownLatch(1);
        }
    }

    /** Ownership is taken even when the lease is rejected. */
    public boolean offer(FrameLease lease) {
        if (lease == null) {
            return false;
        }
        synchronized (lock) {
            offered++;
            if (!started || stopping || lease.sessionEpoch() != epoch) {
                pool.release(lease);
                dropped++;
                return false;
            }
            lease.markPending();
            if (pending != null) {
                pool.release(pending);
                replaced++;
            }
            pending = lease;
            if (!writerRunning) {
                writerRunning = true;
                writer.execute(this::drain);
            }
            return true;
        }
    }

    private void drain() {
        while (true) {
            FrameLease lease;
            synchronized (lock) {
                if (pending != null) {
                    lease = pending;
                    pending = null;
                    inFlight = lease;
                    lease.markInFlight();
                } else {
                    lease = null;
                }
            }
            if (lease != null) {
                boolean ok;
                try {
                    ok = sink.send(lease);
                } catch (RuntimeException e) {
                    ok = false;
                }
                synchronized (lock) {
                    inFlight = null;
                    pool.release(lease);
                    if (ok) {
                        sent++;
                    } else {
                        failures++;
                    }
                }
                continue;
            }
            boolean shouldStop;
            synchronized (lock) {
                // An offer may have arrived since the first pending check. Keep
                // the final empty check and idle transition atomic with offer().
                if (pending != null) {
                    continue;
                }
                shouldStop = stopping;
                if (!shouldStop) {
                    writerRunning = false;
                    return;
                }
            }
            if (shouldStop) {
                finishStop();
                return;
            }
        }
    }

    private void finishStop() {
        boolean black = false;
        try {
            black = sink.sendBlack(blackLedCount, blackChannels);
        } catch (RuntimeException ignored) {
            black = false;
        }
        boolean release = false;
        try {
            release = releaseAction.release(stopReason);
        } catch (RuntimeException ignored) {
            release = false;
        }
        synchronized (lock) {
            blackSent = black;
            releaseCalled = true;
            releaseOk = release;
            writerRunning = false;
            stopping = false;
            started = false;
            owner = OutputOwner.NONE;
            stopLatch.countDown();
        }
    }

    public StopResult stop(String reason) {
        synchronized (lock) {
            if (!started && !stopping) {
                return new StopResult(false, blackSent, releaseCalled, releaseOk, dropped);
            }
            if (!stopping) {
                stopping = true;
                stopReason = reason;
                if (pending != null) {
                    pool.release(pending);
                    pending = null;
                    dropped++;
                }
                if (!writerRunning) {
                    writerRunning = true;
                    writer.execute(this::drain);
                }
            }
        }
        boolean completed;
        try {
            completed = stopLatch.await(stopBudgetMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            completed = false;
        }
        synchronized (lock) {
            return new StopResult(!completed, blackSent, releaseCalled, releaseOk, dropped);
        }
    }

    public Metrics metrics() {
        synchronized (lock) {
            return new Metrics(offered, dropped, replaced, sent, failures,
                    pool.inUse(), pending != null, inFlight != null);
        }
    }

    public OutputOwner owner() {
        synchronized (lock) {
            return owner;
        }
    }
}
