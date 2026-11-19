package com.abrenoch.wledgrabber.tv.activities;

import com.abrenoch.wledgrabber.common.led.Side;

/** A visible label is not evidence: each of the four ordered answers consumes its own send receipt. */
final class CalibrationReview {
    private static final int EDGES = Side.values().length;
    private int nextEdge = -1;
    private long frame, acknowledged = -1, consumed = -1;

    void seek() { nextEdge = -1; nextFrame(); }
    void startEdges() { nextEdge = 0; }
    long nextFrame() { acknowledged = -1; return ++frame; }
    boolean isCurrent(long sequence) { return frame == sequence && !readyToSave(); }
    boolean acknowledge(long sequence) {
        if (!isCurrent(sequence) || sequence <= acknowledged) return false;
        acknowledged = sequence;
        return true;
    }
    boolean canAnswer() { return acknowledged == frame && consumed != frame && !readyToSave(); }
    boolean confirmEdge(int edge) {
        if (!canAnswer() || nextEdge != edge || edge < 0) return false;
        nextEdge++;
        consumed = frame;
        return true;
    }
    boolean readyToSave() { return nextEdge == EDGES; }
}
