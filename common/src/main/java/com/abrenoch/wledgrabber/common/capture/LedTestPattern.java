package com.abrenoch.wledgrabber.common.capture;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.LedMapping;
import com.abrenoch.wledgrabber.common.led.LedSampleRect;
import com.abrenoch.wledgrabber.common.led.RgbwConverter;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.led.WhiteMode;

/** Shared solid/four-side/walking test patterns over the compiled physical mapping. */
public final class LedTestPattern {
    public enum Type {
        SOLID,
        SIDES,
        WALKING
    }

    private static final int TOP_COLOR = 0xff0000;   // red
    private static final int RIGHT_COLOR = 0x00ff00; // green
    private static final int BOTTOM_COLOR = 0x0000ff;// blue
    private static final int LEFT_COLOR = 0xffffff;  // white

    private final CompiledLayout layout;
    private final Side[] logicalSides;
    private final int physicalCount;
    private final int channels;
    private final WhiteMode whiteMode;

    public LedTestPattern(CompiledLayout layout, int channels, WhiteMode whiteMode) {
        if (layout == null || layout.mapping() == null
                || (channels != 3 && channels != 4)
                || (channels == 4 && whiteMode == null)) {
            throw new IllegalArgumentException("layout/channels/whiteMode required");
        }
        this.layout = layout;
        this.physicalCount = layout.mapping().physicalLedCount();
        this.channels = channels;
        this.whiteMode = whiteMode;
        this.logicalSides = new Side[layout.count()];
        for (LedSampleRect rect : layout.rects()) {
            if (rect.logicalIndex() >= 0 && rect.logicalIndex() < logicalSides.length) {
                logicalSides[rect.logicalIndex()] = rect.side();
            }
        }
    }

    public boolean render(Type type, int solidRgb, int walkingIndex, byte[] out) {
        if (type == null || out == null || out.length < physicalCount * channels) {
            return false;
        }
        LedMapping mapping = layout.mapping();
        int walking = physicalCount == 0 ? 0 : Math.floorMod(walkingIndex, physicalCount);
        for (int physical = 0; physical < physicalCount; physical++) {
            int offset = physical * channels;
            int logical = mapping.physicalToLogical(physical);
            if (mapping.isPhysicalBlack(physical) || logical < 0) {
                clear(out, offset);
                continue;
            }
            int rgb;
            if (type == Type.SOLID) {
                rgb = solidRgb;
            } else if (type == Type.WALKING) {
                rgb = physical == walking ? (solidRgb == 0 ? LEFT_COLOR : solidRgb) : 0;
            } else {
                rgb = sideColor(logical);
            }
            write(out, offset, rgb);
        }
        return true;
    }

    private int sideColor(int logical) {
        Side side = logical >= 0 && logical < logicalSides.length ? logicalSides[logical] : null;
        if (side == null) {
            return 0;
        }
        switch (side) {
            case TOP:
                return TOP_COLOR;
            case RIGHT:
                return RIGHT_COLOR;
            case BOTTOM:
                return BOTTOM_COLOR;
            case LEFT:
            default:
                return LEFT_COLOR;
        }
    }

    private void write(byte[] out, int offset, int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        if (channels == 3) {
            out[offset] = (byte) red;
            out[offset + 1] = (byte) green;
            out[offset + 2] = (byte) blue;
        } else {
            RgbwConverter.write(red, green, blue, whiteMode, out, offset);
        }
    }

    private void clear(byte[] out, int offset) {
        for (int i = 0; i < channels; i++) {
            out[offset + i] = 0;
        }
    }

    public int physicalCount() {
        return physicalCount;
    }

    public int channels() {
        return channels;
    }

    public int litCount(byte[] frame) {
        int lit = 0;
        for (int physical = 0; physical < physicalCount; physical++) {
            boolean any = false;
            for (int c = 0; c < channels; c++) {
                if (frame[physical * channels + c] != 0) {
                    any = true;
                    break;
                }
            }
            if (any) {
                lit++;
            }
        }
        return lit;
    }
}
