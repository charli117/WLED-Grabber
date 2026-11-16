package com.abrenoch.wledgrabber.common.views;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedMapping;
import com.abrenoch.wledgrabber.common.led.LedSampleRect;
import com.abrenoch.wledgrabber.common.led.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure drawing model for the shared layout preview; never recomputes geometry. */
public final class PreviewModel {
    public static final class PreviewRect {
        private final double hmin;
        private final double hmax;
        private final double vmin;
        private final double vmax;
        private final Side side;
        private final int logicalIndex;
        private final int physicalIndex;
        private final boolean black;
        private final boolean selected;

        private PreviewRect(LedSampleRect rect, int physicalIndex, boolean black, boolean selected) {
            this.hmin = rect.hmin();
            this.hmax = rect.hmax();
            this.vmin = rect.vmin();
            this.vmax = rect.vmax();
            this.side = rect.side();
            this.logicalIndex = rect.logicalIndex();
            this.physicalIndex = physicalIndex;
            this.black = black;
            this.selected = selected;
        }

        public double hmin() {
            return hmin;
        }

        public double hmax() {
            return hmax;
        }

        public double vmin() {
            return vmin;
        }

        public double vmax() {
            return vmax;
        }

        public Side side() {
            return side;
        }

        public int logicalIndex() {
            return logicalIndex;
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

    private PreviewModel(List<PreviewRect> rects, double[] corners, int physicalCount,
                         int effectiveCount, int tailCount, int gapStart, int gapLength,
                         GapMode gapMode, int selectedPhysical, int selectedLogical, String error) {
        this.rects = Collections.unmodifiableList(rects);
        this.corners = corners.clone();
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
        return new PreviewModel(Collections.emptyList(),
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
        List<PreviewRect> rects = new ArrayList<>(layout.count());
        for (LedSampleRect rect : layout.rects()) {
            int physical = mapping.logicalToPhysical(rect.logicalIndex());
            boolean black = mapping.isLogicalBlack(rect.logicalIndex()) || physical < 0;
            boolean isSelected = selected >= 0 && physical == selected;
            rects.add(new PreviewRect(rect, physical, black, isSelected));
        }
        int selectedLogical = selected >= 0 ? mapping.physicalToLogical(selected) : -1;
        return new PreviewModel(rects,
                new double[]{layout.tlH(), layout.tlV(), layout.trH(), layout.trV(),
                        layout.brH(), layout.brV(), layout.blH(), layout.blV()},
                physicalCount, mapping.effectiveLedCount(),
                physicalCount - mapping.effectiveLedCount(),
                mapping.gapStart(), mapping.gapLength(), mapping.gapMode(),
                selected, selectedLogical, null);
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
