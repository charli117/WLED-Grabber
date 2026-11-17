package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LayoutProbeTest {
    private static LedLayoutConfig config(int top, int bottom, int left, int right,
                                          boolean reverse, GapMode gapMode,
                                          int gapPosition, int gapLength) {
        return new LedLayoutConfig(top, bottom, left, right, gapLength, gapPosition, 0, reverse,
                gapMode, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
    }

    private static int[] range(int from, int toExclusive) {
        int[] out = new int[toExclusive - from];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }

    private static CompiledLayout compile(LedLayoutConfig config) {
        ValidationResult<CompiledLayout> result =
                new ClassicLedLayout().compile(config, 640, 360);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    @Test
    public void sideRangesFollowTheLogicalRing() {
        LedLayoutConfig layout = config(86, 90, 49, 49, false, GapMode.SKIP, 0, 0);
        assertArrayEquals(range(0, 86), LayoutProbe.logicalForSide(layout, Side.TOP));
        assertArrayEquals(range(86, 135), LayoutProbe.logicalForSide(layout, Side.RIGHT));
        assertArrayEquals(range(135, 225), LayoutProbe.logicalForSide(layout, Side.BOTTOM));
        assertArrayEquals(range(225, 274), LayoutProbe.logicalForSide(layout, Side.LEFT));
    }

    @Test
    public void inputProbeLightsOnlyTheFirstPhysicalLed() {
        // First unit: T86/B90/L49/R49, Input 161, Reverse on. Physical 0 maps to
        // logical 160; the raw input index 161 maps to physical 273.
        LedLayoutConfig layout = new LedLayoutConfig(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
        CompiledLayout compiled = compile(layout);

        assertEquals(273, compiled.mapping().logicalToPhysical(161));
        int firstPhysicalLogical = LayoutProbe.firstPhysicalLogical(compiled);
        assertEquals(160, firstPhysicalLogical);

        byte[] frame = LayoutProbe.buildFrame(compiled,
                new int[]{firstPhysicalLogical}, 255, 255, 255);
        int lit = -1;
        for (int physical = 0; physical < compiled.mapping().physicalLedCount(); physical++) {
            if ((frame[physical * 3] & 0xff) != 0) {
                assertTrue("only one LED may be lit", lit < 0);
                lit = physical;
            }
        }
        assertEquals(0, lit);
    }

    @Test
    public void frameLightsOnlyTheSelectedEdge() {
        CompiledLayout compiled = compile(
                config(2, 2, 1, 1, false, GapMode.SKIP, 0, 0));
        byte[] frame = LayoutProbe.buildFrame(compiled, LayoutProbe.logicalForSide(
                config(2, 2, 1, 1, false, GapMode.SKIP, 0, 0), Side.TOP), 10, 20, 30);
        LedMapping mapping = compiled.mapping();
        for (int physical = 0; physical < mapping.physicalLedCount(); physical++) {
            int logical = mapping.physicalToLogical(physical);
            boolean expected = logical == 0 || logical == 1;
            int r = frame[physical * 3] & 0xff;
            int g = frame[physical * 3 + 1] & 0xff;
            int b = frame[physical * 3 + 2] & 0xff;
            if (expected) {
                assertEquals(10, r);
                assertEquals(20, g);
                assertEquals(30, b);
            } else {
                assertEquals(0, r);
                assertEquals(0, g);
                assertEquals(0, b);
            }
        }
    }

    @Test
    public void reverseFlipsThePhysicalOrder() {
        CompiledLayout compiled = compile(
                config(2, 2, 1, 1, true, GapMode.SKIP, 0, 0));
        byte[] frame = LayoutProbe.buildFrame(compiled, new int[]{0}, 255, 0, 0);
        LedMapping mapping = compiled.mapping();
        int lit = -1;
        for (int physical = 0; physical < mapping.physicalLedCount(); physical++) {
            if ((frame[physical * 3] & 0xff) != 0) {
                lit = physical;
                break;
            }
        }
        assertEquals(mapping.logicalToPhysical(0), lit);
    }

    @Test
    public void rgbwFrameUsesFourChannelsAndWhiteSynthesis() {
        CompiledLayout compiled = compile(
                config(2, 2, 1, 1, false, GapMode.SKIP, 0, 0));
        int physical = compiled.mapping().logicalToPhysical(0);

        byte[] minSubtract = LayoutProbe.buildFrame(compiled, new int[]{0}, 4,
                WhiteMode.MIN_SUBTRACT, 255, 255, 255);
        assertEquals(compiled.mapping().physicalLedCount() * 4, minSubtract.length);
        assertArrayEquals(new byte[]{0, 0, 0, (byte) 255},
                java.util.Arrays.copyOfRange(minSubtract, physical * 4, physical * 4 + 4));

        byte[] preserve = LayoutProbe.buildFrame(compiled, new int[]{0}, 4,
                WhiteMode.PRESERVE, 255, 255, 255);
        assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255},
                java.util.Arrays.copyOfRange(preserve, physical * 4, physical * 4 + 4));
    }

    @Test
    public void gapBlackLedsAreNotLit() {
        CompiledLayout compiled = compile(
                config(4, 0, 0, 0, false, GapMode.SKIP, 1, 2));
        byte[] frame = LayoutProbe.buildFrame(compiled, range(0, 4), 255, 255, 255);
        LedMapping mapping = compiled.mapping();
        for (int logical = 0; logical < 4; logical++) {
            int physical = mapping.logicalToPhysical(logical);
            boolean black = mapping.isLogicalBlack(logical);
            int value = physical < 0 ? 0 : frame[physical * 3] & 0xff;
            assertEquals("logical " + logical, black ? 0 : 255, value);
        }
    }
}
