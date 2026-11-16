package com.abrenoch.wledgrabber.common.led;

/** Immutable Classic layout configuration. Deep geometry bounds are validated by LAY-1. */
public final class LedLayoutConfig {
    private final int top;
    private final int bottom;
    private final int left;
    private final int right;
    private final int gapLength;
    private final int gapPosition;
    private final int inputPosition;
    private final boolean reverse;
    private final GapMode gapMode;
    private final int horizontalDepth;
    private final int verticalDepth;
    private final int overlap;
    private final int edgeGap;
    private final int cornerTlH;
    private final int cornerTlV;
    private final int cornerTrH;
    private final int cornerTrV;
    private final int cornerBrH;
    private final int cornerBrV;
    private final int cornerBlH;
    private final int cornerBlV;

    public LedLayoutConfig(int top, int bottom, int left, int right,
                           int gapLength, int gapPosition, int inputPosition,
                           boolean reverse, GapMode gapMode,
                           int horizontalDepth, int verticalDepth,
                           int overlap, int edgeGap,
                           int cornerTlH, int cornerTlV,
                           int cornerTrH, int cornerTrV,
                           int cornerBrH, int cornerBrV,
                           int cornerBlH, int cornerBlV) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
        this.gapLength = gapLength;
        this.gapPosition = gapPosition;
        this.inputPosition = inputPosition;
        this.reverse = reverse;
        this.gapMode = gapMode;
        this.horizontalDepth = horizontalDepth;
        this.verticalDepth = verticalDepth;
        this.overlap = overlap;
        this.edgeGap = edgeGap;
        this.cornerTlH = cornerTlH;
        this.cornerTlV = cornerTlV;
        this.cornerTrH = cornerTrH;
        this.cornerTrV = cornerTrV;
        this.cornerBrH = cornerBrH;
        this.cornerBrV = cornerBrV;
        this.cornerBlH = cornerBlH;
        this.cornerBlV = cornerBlV;
    }

    public int totalLedCount() {
        return top + bottom + left + right;
    }

    public int effectiveLedCount() {
        return gapMode == GapMode.SKIP ? totalLedCount() - gapLength : totalLedCount();
    }

    public int top() {
        return top;
    }

    public int bottom() {
        return bottom;
    }

    public int left() {
        return left;
    }

    public int right() {
        return right;
    }

    public int gapLength() {
        return gapLength;
    }

    public int gapPosition() {
        return gapPosition;
    }

    public int inputPosition() {
        return inputPosition;
    }

    public boolean reverse() {
        return reverse;
    }

    public GapMode gapMode() {
        return gapMode;
    }

    public int horizontalDepth() {
        return horizontalDepth;
    }

    public int verticalDepth() {
        return verticalDepth;
    }

    public int overlap() {
        return overlap;
    }

    public int edgeGap() {
        return edgeGap;
    }

    public int cornerTlH() {
        return cornerTlH;
    }

    public int cornerTlV() {
        return cornerTlV;
    }

    public int cornerTrH() {
        return cornerTrH;
    }

    public int cornerTrV() {
        return cornerTrV;
    }

    public int cornerBrH() {
        return cornerBrH;
    }

    public int cornerBrV() {
        return cornerBrV;
    }

    public int cornerBlH() {
        return cornerBlH;
    }

    public int cornerBlV() {
        return cornerBlV;
    }
}
