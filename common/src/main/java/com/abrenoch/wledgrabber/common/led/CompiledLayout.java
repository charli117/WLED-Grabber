package com.abrenoch.wledgrabber.common.led;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable compiled layout shared by sampling and preview. */
public final class CompiledLayout {
    private final List<LedSampleRect> rects;
    private final List<PixelRect> pixelRects;
    private final int captureWidth;
    private final int captureHeight;
    private final long generation;
    private final LedMapping mapping;
    private final double tlH;
    private final double tlV;
    private final double trH;
    private final double trV;
    private final double brH;
    private final double brV;
    private final double blH;
    private final double blV;

    public CompiledLayout(List<LedSampleRect> rects, int captureWidth, int captureHeight,
                          long generation, LedMapping mapping) {
        this(rects, captureWidth, captureHeight, generation, mapping,
                0, 0, 1, 0, 1, 1, 0, 1);
    }

    public CompiledLayout(List<LedSampleRect> rects, int captureWidth, int captureHeight,
                          long generation, LedMapping mapping,
                          double tlH, double tlV, double trH, double trV,
                          double brH, double brV, double blH, double blV) {
        if (captureWidth <= 0 || captureHeight <= 0) {
            throw new IllegalArgumentException("capture dimensions must be positive");
        }
        this.rects = Collections.unmodifiableList(new ArrayList<>(rects));
        List<PixelRect> pixels = new ArrayList<>(rects.size());
        for (LedSampleRect rect : rects) {
            pixels.add(rect.toPixelRect(captureWidth, captureHeight));
        }
        this.pixelRects = Collections.unmodifiableList(pixels);
        this.captureWidth = captureWidth;
        this.captureHeight = captureHeight;
        this.generation = generation;
        this.mapping = mapping;
        this.tlH = tlH;
        this.tlV = tlV;
        this.trH = trH;
        this.trV = trV;
        this.brH = brH;
        this.brV = brV;
        this.blH = blH;
        this.blV = blV;
    }

    public CompiledLayout(List<LedSampleRect> rects, int captureWidth, int captureHeight,
                          long generation) {
        this(rects, captureWidth, captureHeight, generation, null);
    }

    public List<LedSampleRect> rects() {
        return rects;
    }

    public List<PixelRect> pixelRects() {
        return pixelRects;
    }

    public int count() {
        return rects.size();
    }

    public int captureWidth() {
        return captureWidth;
    }

    public int captureHeight() {
        return captureHeight;
    }

    public long generation() {
        return generation;
    }

    public LedMapping mapping() {
        return mapping;
    }

    public double tlH() {
        return tlH;
    }

    public double tlV() {
        return tlV;
    }

    public double trH() {
        return trH;
    }

    public double trV() {
        return trV;
    }

    public double brH() {
        return brH;
    }

    public double brV() {
        return brV;
    }

    public double blH() {
        return blH;
    }

    public double blV() {
        return blV;
    }

    public CompiledLayout withGeneration(long newGeneration) {
        return new CompiledLayout(rects, captureWidth, captureHeight, newGeneration, mapping,
                tlH, tlV, trH, trV, brH, brV, blH, blV);
    }
}
