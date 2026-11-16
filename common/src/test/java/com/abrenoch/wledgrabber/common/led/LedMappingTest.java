package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedMappingTest {

    private static LedLayoutConfig config(int total, int gapLength, int gapPosition,
                                          int input, boolean reverse, GapMode gapMode) {
        return new LedLayoutConfig(total, 0, 0, 0, gapLength, gapPosition, input, reverse,
                gapMode, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
    }

    private static LedMapping mapping(LedLayoutConfig config, int physicalLedCount) {
        ValidationResult<LedMapping> result = LedMapping.compile(config, physicalLedCount);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static void assertPermutation(LedMapping mapping, int total, int skipStart, int skipEnd) {
        boolean[] seen = new boolean[total];
        int mapped = 0;
        for (int physical = 0; physical < mapping.effectiveLedCount(); physical++) {
            int logical = mapping.physicalToLogical(physical);
            assertTrue("logical out of range: " + logical, logical >= 0 && logical < total);
            if (logical >= skipStart && logical < skipEnd) {
                throw new AssertionError("gap logical slot was mapped: " + logical);
            }
            assertFalse("duplicate logical slot: " + logical, seen[logical]);
            seen[logical] = true;
            mapped++;
        }
        assertEquals(total - (skipEnd - skipStart), mapped);
        for (int logical = 0; logical < total; logical++) {
            boolean inGap = logical >= skipStart && logical < skipEnd;
            assertEquals("logical presence mismatch: " + logical, !inGap, seen[logical]);
        }
    }

    @Test
    public void firstUnit274AnchorsAndPermutation() {
        LedMapping mapping = mapping(config(274, 0, 0, 161, true, GapMode.SKIP), 274);

        assertEquals(160, mapping.physicalToLogical(0));
        assertEquals(135, mapping.physicalToLogical(25));
        assertEquals(134, mapping.physicalToLogical(26));
        assertEquals(0, mapping.physicalToLogical(160));
        assertEquals(273, mapping.physicalToLogical(161));
        assertEquals(161, mapping.physicalToLogical(273));
        assertEquals(274, mapping.effectiveLedCount());
        assertEquals(274, mapping.physicalLedCount());
        assertPermutation(mapping, 274, 0, 0);
    }

    @Test
    public void reference288AnchorsAndPermutation() {
        LedMapping mapping = mapping(config(288, 0, 0, 206, false, GapMode.SKIP), 288);

        assertEquals(206, mapping.physicalToLogical(0));
        assertEquals(237, mapping.physicalToLogical(31));
        assertEquals(238, mapping.physicalToLogical(32));
        assertEquals(287, mapping.physicalToLogical(81));
        assertEquals(0, mapping.physicalToLogical(82));
        assertEquals(115, mapping.physicalToLogical(197));
        assertEquals(116, mapping.physicalToLogical(198));
        assertEquals(165, mapping.physicalToLogical(247));
        assertEquals(166, mapping.physicalToLogical(248));
        assertEquals(205, mapping.physicalToLogical(287));
        assertPermutation(mapping, 288, 0, 0);
    }

    @Test
    public void negativeAndLargeInputAreNormalized() {
        LedMapping negative = mapping(config(10, 0, 0, -1, false, GapMode.SKIP), 10);
        assertEquals(9, negative.physicalToLogical(0));
        assertEquals(0, negative.physicalToLogical(1));

        LedMapping large = mapping(config(10, 0, 0, 1000, false, GapMode.SKIP), 10);
        assertEquals(0, large.physicalToLogical(0));

        LedMapping reversed = mapping(config(10, 0, 0, 0, true, GapMode.SKIP), 10);
        assertEquals(9, reversed.physicalToLogical(0));
        assertEquals(8, reversed.physicalToLogical(1));

        LedMapping reversedInput = mapping(config(10, 0, 0, 161, true, GapMode.SKIP), 10);
        assertEquals(0, reversedInput.physicalToLogical(0));
        assertEquals(9, reversedInput.physicalToLogical(1));
    }

    @Test
    public void skipGapsFirstMiddleLastAndZeroKeepFilteredPermutation() {
        assertPermutation(mapping(config(20, 3, 0, 0, false, GapMode.SKIP), 17), 20, 0, 3);
        assertPermutation(mapping(config(20, 4, 8, 0, false, GapMode.SKIP), 16), 20, 8, 12);
        assertPermutation(mapping(config(20, 3, 17, 0, false, GapMode.SKIP), 17), 20, 17, 20);
        assertPermutation(mapping(config(20, 0, 0, 0, false, GapMode.SKIP), 20), 20, 0, 0);
    }

    @Test
    public void blackGapIsRetainedAndMarked() {
        LedMapping mapping = mapping(config(20, 4, 8, 0, false, GapMode.BLACK), 20);

        assertEquals(20, mapping.effectiveLedCount());
        assertEquals(20, mapping.physicalLedCount());
        assertPermutation(mapping, 20, 0, 0);
        for (int logical = 8; logical < 12; logical++) {
            assertTrue(mapping.isLogicalBlack(logical));
            int physical = mapping.logicalToPhysical(logical);
            assertTrue(physical >= 0);
            assertTrue(mapping.isPhysicalBlack(physical));
        }
    }

    @Test
    public void mGreaterThanKExtendsTailBlack() {
        LedMapping mapping = mapping(config(20, 4, 8, 0, false, GapMode.SKIP), 24);

        assertEquals(16, mapping.effectiveLedCount());
        assertEquals(24, mapping.physicalLedCount());
        assertPermutation(mapping, 20, 8, 12);
        for (int physical = 16; physical < 24; physical++) {
            assertEquals(-1, mapping.physicalToLogical(physical));
            assertTrue(mapping.isPhysicalBlack(physical));
        }
        for (int logical = 8; logical < 12; logical++) {
            assertEquals(-1, mapping.logicalToPhysical(logical));
        }
    }

    @Test
    public void reverseFourCombinationsMatchDefinedOrder() {
        LedMapping forward0 = mapping(config(4, 0, 0, 0, false, GapMode.SKIP), 4);
        assertEquals(0, forward0.physicalToLogical(0));
        assertEquals(1, forward0.physicalToLogical(1));
        assertEquals(2, forward0.physicalToLogical(2));
        assertEquals(3, forward0.physicalToLogical(3));

        LedMapping reverse0 = mapping(config(4, 0, 0, 0, true, GapMode.SKIP), 4);
        assertEquals(3, reverse0.physicalToLogical(0));
        assertEquals(2, reverse0.physicalToLogical(1));
        assertEquals(1, reverse0.physicalToLogical(2));
        assertEquals(0, reverse0.physicalToLogical(3));

        LedMapping forward1 = mapping(config(4, 0, 0, 1, false, GapMode.SKIP), 4);
        assertEquals(1, forward1.physicalToLogical(0));
        assertEquals(2, forward1.physicalToLogical(1));
        assertEquals(3, forward1.physicalToLogical(2));
        assertEquals(0, forward1.physicalToLogical(3));

        LedMapping reverse1 = mapping(config(4, 0, 0, 1, true, GapMode.SKIP), 4);
        assertEquals(0, reverse1.physicalToLogical(0));
        assertEquals(3, reverse1.physicalToLogical(1));
        assertEquals(2, reverse1.physicalToLogical(2));
        assertEquals(1, reverse1.physicalToLogical(3));
    }

    @Test
    public void invalidGapOrInsufficientPhysicalCountIsRejected() {
        assertFalse(LedMapping.compile(config(20, 3, 18, 0, false, GapMode.SKIP), 20).isValid());
        assertFalse(LedMapping.compile(config(20, 20, 0, 0, false, GapMode.SKIP), 20).isValid());
        assertFalse(LedMapping.compile(config(20, 4, 8, 0, false, GapMode.SKIP), 15).isValid());
        assertFalse(LedMapping.compile(config(20, 0, 0, 0, false, GapMode.SKIP), 0).isValid());
    }

    @Test
    public void randomMappingsHaveNoDuplicatesOrOmissions() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 10000; iteration++) {
            int total = random.nextInt(200) + 1;
            GapMode mode = random.nextBoolean() ? GapMode.SKIP : GapMode.BLACK;
            int gapLength;
            if (mode == GapMode.SKIP) {
                gapLength = random.nextInt(total);
            } else {
                gapLength = random.nextInt(total + 1);
            }
            int gapStart = random.nextInt(total - gapLength + 1);
            int effective = mode == GapMode.SKIP ? total - gapLength : total;
            int physical = effective + random.nextInt(21);
            int input = random.nextInt(20001) - 10000;
            boolean reverse = random.nextBoolean();

            ValidationResult<LedMapping> result = LedMapping.compile(
                    config(total, gapLength, gapStart, input, reverse, mode), physical);
            assertTrue("iteration " + iteration + ": " + result.errors(),
                    result.isValid());
            LedMapping mapping = result.value();

            assertEquals(physical, mapping.physicalLedCount());
            assertEquals(effective, mapping.effectiveLedCount());

            boolean[] seen = new boolean[total];
            for (int p = 0; p < physical; p++) {
                if (p >= effective) {
                    assertEquals(-1, mapping.physicalToLogical(p));
                    assertTrue(mapping.isPhysicalBlack(p));
                    continue;
                }
                int logical = mapping.physicalToLogical(p);
                assertTrue(logical >= 0 && logical < total);
                boolean inGap = logical >= gapStart && logical < gapStart + gapLength;
                if (mode == GapMode.SKIP) {
                    assertFalse("SKIP mapped a gap slot", inGap);
                }
                assertFalse("duplicate logical slot " + logical, seen[logical]);
                seen[logical] = true;
                assertEquals(p, mapping.logicalToPhysical(logical));
                if (mode == GapMode.BLACK && inGap) {
                    assertTrue(mapping.isPhysicalBlack(p));
                    assertTrue(mapping.isLogicalBlack(logical));
                }
            }
            for (int logical = 0; logical < total; logical++) {
                boolean inGap = logical >= gapStart && logical < gapStart + gapLength;
                if (mode == GapMode.SKIP && inGap) {
                    assertEquals(-1, mapping.logicalToPhysical(logical));
                    assertFalse(seen[logical]);
                } else {
                    assertTrue(seen[logical]);
                }
            }
        }
    }
}
