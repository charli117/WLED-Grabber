package com.abrenoch.wledgrabber.common.capture;

/**
 * Pure Image acquire/close contract. The actual ImageReader listener mirrors
 * this try/finally: every accepted image is closed exactly once, even if the
 * sampling callback throws, and stale generations never acquire.
 */
public final class ImageLifecycle {
    public interface ImageHandle {
        void close();
    }

    public interface ImageSource {
        ImageHandle acquire();
    }

    public interface Consumer {
        void accept(ImageHandle image);
    }

    private final ImageSource source;
    private final GenerationGate gate;
    private int acquired;
    private int closed;
    private int rejected;

    public ImageLifecycle(ImageSource source, GenerationGate gate) {
        if (source == null || gate == null) {
            throw new IllegalArgumentException("source and gate are required");
        }
        this.source = source;
        this.gate = gate;
    }

    public boolean consume(long generation, Consumer consumer) {
        if (!gate.isCurrent(generation)) {
            rejected++;
            return false;
        }
        ImageHandle image;
        try {
            image = source.acquire();
        } catch (RuntimeException e) {
            return false;
        }
        if (image == null) {
            return false;
        }
        acquired++;
        try {
            if (consumer != null) {
                consumer.accept(image);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            image.close();
            closed++;
        }
    }

    public int acquired() {
        return acquired;
    }

    public int closed() {
        return closed;
    }

    public int rejected() {
        return rejected;
    }

    public int open() {
        return acquired - closed;
    }
}
