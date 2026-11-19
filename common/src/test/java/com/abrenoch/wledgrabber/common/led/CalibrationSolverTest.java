package com.abrenoch.wledgrabber.common.led;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalibrationSolverTest {
    private static LedLayoutConfig layout(int top, int right, int bottom, int left,
                                          int gap, int input, boolean reverse) {
        return new LedLayoutConfig(top, bottom, left, right, gap, 0, input, reverse,
                GapMode.SKIP, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
    }

    @Test public void bottomToRightAtPhysical26FindsTheFirstUnitsMidEdgeInput() {
        LedLayoutConfig original = layout(86, 49, 90, 49, 0, 0, false);
        CalibrationSolver.Candidate candidate = CalibrationSolver.solve(original, 26,
                Side.BOTTOM, Side.RIGHT).value();
        assertEquals(161, candidate.inputPosition());
        assertTrue(candidate.reverse());
        assertEquals(0, original.inputPosition());
        assertFalse(original.reverse());
    }
    @Test public void unsupportedOrUnobservedBoundariesCannotProduceACandidate() {
        LedLayoutConfig ring = layout(2, 3, 4, 5, 0, 0, false);
        assertFalse(CalibrationSolver.solve(ring, 2, Side.TOP, Side.BOTTOM).isValid());
        assertFalse(CalibrationSolver.solve(ring, 2, Side.TOP, Side.TOP).isValid());
        assertFalse(CalibrationSolver.solve(ring, 2, null, Side.RIGHT).isValid());
        assertFalse(CalibrationSolver.solve(ring, 0, Side.TOP, Side.RIGHT).isValid());
        assertFalse(CalibrationSolver.solve(ring, 14, Side.TOP, Side.RIGHT).isValid());
        assertFalse(CalibrationSolver.solve(layout(2,3,4,5,1,0,false), 2, Side.TOP, Side.RIGHT).isValid());
        assertFalse(CalibrationSolver.solve(layout(2,3,4,0,0,0,false), 2, Side.TOP, Side.RIGHT).isValid());
    }
    @Test public void observedBoundariesRoundTripAgainstTheProductionMapping() {
        // Oracle: read which side physically lights up under a known installation.
        // This deliberately does not calculate the candidate from the inverse formula.
        int cases = 0;
        for (int top = 1; top <= 4; top++) for (int right = 1; right <= 4; right++) {
            for (int bottom = 1; bottom <= 4; bottom++) for (int left = 1; left <= 4; left++) {
                int count = top + right + bottom + left;
                Side[] sides = new Side[count];
                for (int i = 0; i < count; i++) {
                    sides[i] = i < top ? Side.TOP : i < top + right ? Side.RIGHT
                            : i < top + right + bottom ? Side.BOTTOM : Side.LEFT;
                }
                for (int input = 0; input < count; input++) for (boolean reverse : new boolean[]{false, true}) {
                    LedLayoutConfig config = layout(top, right, bottom, left, 0, input, reverse);
                    LedMapping mapping = LedMapping.compile(config, count + 2).value();
                    for (int p = 1; p < count; p++) {
                        Side before = sides[mapping.physicalToLogical(p - 1)];
                        Side after = sides[mapping.physicalToLogical(p)];
                        if (before == after) continue;
                        CalibrationSolver.Candidate candidate = CalibrationSolver.solve(config, p, before, after).value();
                        assertEquals(input, candidate.inputPosition());
                        assertEquals(reverse, candidate.reverse());
                        cases++;
                    }
                }
            }
        }
        assertEquals(18432, cases);
    }
}
