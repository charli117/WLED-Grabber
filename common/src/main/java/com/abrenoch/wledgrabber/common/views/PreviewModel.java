package com.abrenoch.wledgrabber.common.views;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedMapping;
import com.abrenoch.wledgrabber.common.led.LedSampleRect;
import com.abrenoch.wledgrabber.common.led.Side;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure drawing model for the shared layout preview; never recomputes geometry. */
public final class PreviewModel {
    public static final class PreviewRect {
        private final LedSampleRect geometry;
        private final int physicalIndex;
        private final boolean black;
        private final boolean selected;

        private PreviewRect(LedSampleRect rect, int physicalIndex, boolean black, boolean selected) {
            this.geometry = rect;
            this.physicalIndex = physicalIndex;
            this.black = black;
            this.selected = selected;
        }

        public double hmin() {
            return geometry.hmin();
        }

        public double hmax() {
            return geometry.hmax();
        }

        public double vmin() {
            return geometry.vmin();
        }

        public double vmax() {
            return geometry.vmax();
        }

        public Side side() {
            return geometry.side();
        }

        public int logicalIndex() {
            return geometry.logicalIndex();
        }

        public int physicalIndex() {
            return physicalIndex;
        }

        public boolean black() {
            return black;
        }

        public boolean selected() {
            return selected;
        }
    }

    private final List<PreviewRect> rects;
    private final List<PreviewRect> plainRects;
    private final List<PreviewRect> markedRects;
    private final LedMapping mapping;
    private final double[] corners;
    private final int physicalCount;
    private final int effectiveCount;
    private final int tailCount;
    private final int gapStart;
    private final int gapLength;
    private final GapMode gapMode;
    private final int selectedPhysical;
    private final int selectedLogical;
    private final String error;

    private PreviewModel(List<PreviewRect> rects, List<PreviewRect> plainRects,
                         List<PreviewRect> markedRects, LedMapping mapping,
                         double[] corners, int physicalCount,
                         int effectiveCount, int tailCount, int gapStart, int gapLength,
                         GapMode gapMode, int selectedPhysical, int selectedLogical, String error) {
        // Both factories own their inputs; only the read-only selection view escapes.
        // corners() still returns a defensive copy to callers.
        this.rects = rects;
        this.plainRects = plainRects;
        this.markedRects = markedRects;
        this.mapping = mapping;
        this.corners = corners;
        this.physicalCount = physicalCount;
        this.effectiveCount = effectiveCount;
        this.tailCount = tailCount;
        this.gapStart = gapStart;
        this.gapLength = gapLength;
        this.gapMode = gapMode;
        this.selectedPhysical = selectedPhysical;
        this.selectedLogical = selectedLogical;
        this.error = error;
    }

    public static PreviewModel error(String message) {
        List<PreviewRect> none = Collections.emptyList();
        return new PreviewModel(none, none, none, null,
                new double[]{0, 0, 1, 0, 1, 1, 0, 1}, 0, 0, 0, 0, 0,
                GapMode.SKIP, -1, -1, message == null ? "layout unavailable" : message);
    }

    public static PreviewModel build(CompiledLayout layout, int selectedPhysical) {
        if (layout == null || layout.mapping() == null) {
            return error("layout unavailable");
        }
        LedMapping mapping = layout.mapping();
        int physicalCount = mapping.physicalLedCount();
        int selected = physicalCount == 0 ? -1
                : Math.max(0, Math.min(selectedPhysical, physicalCount - 1));
        List<PreviewRect> plainRects = new ArrayList<>(layout.count());
        List<PreviewRect> markedRects = new ArrayList<>(layout.count());
        for (LedSampleRect rect : layout.rects()) {
            int physical = mapping.logicalToPhysical(rect.logicalIndex());
            boolean black = mapping.isLogicalBlack(rect.logicalIndex()) || physical < 0;
            plainRects.add(new PreviewRect(rect, physical, black, false));
            markedRects.add(new PreviewRect(rect, physical, black, true));
        }
        int selectedLogical = selected >= 0 ? mapping.physicalToLogical(selected) : -1;
        return new PreviewModel(view(plainRects, markedRects, selected),
                plainRects, markedRects, mapping,
                new double[]{layout.tlH(), layout.tlV(), layout.trH(), layout.trV(),
                        layout.brH(), layout.brV(), layout.blH(), layout.blV()},
                physicalCount, mapping.effectiveLedCount(),
                physicalCount - mapping.effectiveLedCount(),
                mapping.gapStart(), mapping.gapLength(), mapping.gapMode(),
                selected, selectedLogical, null);
    }

    /** Re-selects one physical index without rebuilding geometry or its rect instances. */
    public PreviewModel select(int physicalIndex) {
        if (error != null || physicalCount == 0) {
            return this;
        }
        int next = Math.max(0, Math.min(physicalIndex, physicalCount - 1));
        if (next == selectedPhysical) {
            return this;
        }
        return new PreviewModel(view(plainRects, markedRects, next),
                plainRects, markedRects, mapping, corners,
                physicalCount, effectiveCount, tailCount, gapStart, gapLength, gapMode,
                next, mapping.physicalToLogical(next), null);
    }

    private static List<PreviewRect> view(List<PreviewRect> plainRects,
                                          List<PreviewRect> markedRects, int selected) {
        return new AbstractList<PreviewRect>() {
            @Override public PreviewRect get(int index) {
                PreviewRect rect = plainRects.get(index);
                return selected >= 0 && rect.physicalIndex() == selected ? markedRects.get(index) : rect;
            }

            @Override public int size() {
                return plainRects.size();
            }
        };
    }

    public List<PreviewRect> rects() {
        return rects;
    }

    public double[] corners() {
        return corners.clone();
    }

    public int physicalCount() {
        return physicalCount;
    }

    public int effectiveCount() {
        return effectiveCount;
    }

    public int tailCount() {
        return tailCount;
    }

    public int gapStart() {
        return gapStart;
    }

    public int gapLength() {
        return gapLength;
    }

    public GapMode gapMode() {
        return gapMode;
    }

    public int selectedPhysical() {
        return selectedPhysical;
    }

    public int selectedLogical() {
        return selectedLogical;
    }

    public String error() {
        return error;
    }

    public boolean hasError() {
        return error != null;
    }
}
