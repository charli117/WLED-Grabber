package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/** Derives the two installation settings from consecutive physical LEDs on different edges. */
public final class CalibrationSolver {
    private CalibrationSolver() { }

    public static final class Candidate {
        private final int input;
        private final boolean reversed;

        private Candidate(int input, boolean reversed) {
            this.input = input;
            this.reversed = reversed;
        }

        public int inputPosition() { return input; }
        public boolean reverse() { return reversed; }
    }

    public static boolean supports(LedLayoutConfig layout) {
        return layout != null && layout.gapLength() == 0 && layout.top() > 0 && layout.right() > 0
                && layout.bottom() > 0 && layout.left() > 0;
    }

    public static ValidationResult<Candidate> solve(LedLayoutConfig layout, int secondPhysical,
                                                     Side before, Side after) {
        if (!supports(layout)) {
            return invalid(FieldError.Code.INVALID_VALUE, "Four nonempty edges without gaps required");
        }
        if (secondPhysical <= 0 || secondPhysical >= layout.totalLedCount()) {
            return invalid(FieldError.Code.OUT_OF_RANGE, "Physical pair outside active ring");
        }
        if (before == null || after == null) {
            return invalid(FieldError.Code.EMPTY, "Both observations required");
        }
        // Side values follow the clockwise Classic ring (top, right, bottom, left).
        int direction = Math.floorMod(after.ordinal() - before.ordinal(), 4);
        if (direction != 1 && direction != 3) {
            return invalid(FieldError.Code.CROSS_FIELD, "Observed edges must be adjacent");
        }
        int[] side = LayoutProbe.logicalForSide(layout, after);
        boolean reverse = direction == 3;
        int offset = reverse ? side[side.length - 1] + secondPhysical + 1 : side[0] - secondPhysical;
        return ValidationResult.ok(new Candidate(Math.floorMod(offset, layout.totalLedCount()), reverse));
    }

    private static ValidationResult<Candidate> invalid(FieldError.Code code, String reason) {
        return ValidationResult.fail(new FieldError(ConfigFields.LED_INPUT_POSITION, code, reason));
    }
}
