package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

/**
 * Pure resize decision shared with the capture service: a new source size is fit
 * to the capture budget, the layout is recompiled and the generation advances.
 * Callers swap the reader/surface; this class owns no Android objects.
 */
public final class CaptureResizePlanner {
    public static final class Plan {
        private final boolean changed;
        private final int width;
        private final int height;
        private final long generation;
        private final CompiledLayout layout;

        private Plan(boolean changed, int width, int height, long generation,
                     CompiledLayout layout) {
            this.changed = changed;
            this.width = width;
            this.height = height;
            this.generation = generation;
            this.layout = layout;
        }

        public boolean changed() {
            return changed;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public long generation() {
            return generation;
        }

        public CompiledLayout layout() {
            return layout;
        }
    }

    private CaptureResizePlanner() {
    }

    public static ValidationResult<Plan> plan(LedLayoutConfig config, int physicalLedCount,
                                              int sourceWidth, int sourceHeight,
                                              int maxDimension, int currentWidth,
                                              int currentHeight, long currentGeneration) {
        CaptureSizing.Size size = CaptureSizing.fit(sourceWidth, sourceHeight, maxDimension);
        if (size.width() == currentWidth && size.height() == currentHeight) {
            return ValidationResult.ok(new Plan(false, currentWidth, currentHeight,
                    currentGeneration, null));
        }
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                config, physicalLedCount, size.width(), size.height());
        if (!compiled.isValid()) {
            return ValidationResult.fail(compiled.errors());
        }
        long generation = currentGeneration + 1;
        return ValidationResult.ok(new Plan(true, size.width(), size.height(), generation,
                compiled.value().withGeneration(generation)));
    }
}
