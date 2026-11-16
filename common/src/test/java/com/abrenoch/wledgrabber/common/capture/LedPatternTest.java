package com.abrenoch.wledgrabber.common.capture;

import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.LedMapping;
import com.abrenoch.wledgrabber.common.led.WhiteMode;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedPatternTest {

    private static CompiledLayout layout(int top, int bottom, int left, int right,
                                         int gapLength, int gapPosition, int input,
                                         boolean reverse, GapMode gapMode, int physical) {
        LedLayoutConfig config = new LedLayoutConfig(top, bottom, left, right,
                gapLength, gapPosition, input, reverse, gapMode, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result = new ClassicLedLayout().compile(
                config, physical, 640, 360);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static int physicalForLogical(CompiledLayout layout, int logical) {
        LedMapping mapping = layout.mapping();
        for (int physical = 0; physical < mapping.physicalLedCount(); physical++) {
            if (mapping.physicalToLogical(physical) == logical) {
                return physical;
            }
        }
        return -1;
    }

    private static byte[] frame(CompiledLayout layout, int channels) {
        return new byte[layout.mapping().physicalLedCount() * channels];
    }

    @Test
    public void sidesPatternUsesActualSideOfEachPhysicalLed() {
        CompiledLayout firstUnit = layout(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 274);
        LedTestPattern pattern = new LedTestPattern(firstUnit, 3, null);
        byte[] frame = frame(firstUnit, 3);
        assertTrue(pattern.render(LedTestPattern.Type.SIDES, 0, 0, frame));

        assertArrayEquals(new byte[]{(byte) 255, 0, 0},
                slice(frame, physicalForLogical(firstUnit, 0) * 3, 3));
        assertArrayEquals(new byte[]{0, (byte) 255, 0},
                slice(frame, physicalForLogical(firstUnit, 86) * 3, 3));
        assertArrayEquals(new byte[]{0, 0, (byte) 255},
                slice(frame, physicalForLogical(firstUnit, 135) * 3, 3));
        assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255},
                slice(frame, physicalForLogical(firstUnit, 225) * 3, 3));

        CompiledLayout reference = layout(116, 72, 50, 50, 0, 0, 206, false,
                GapMode.SKIP, 288);
        LedTestPattern referencePattern = new LedTestPattern(reference, 3, null);
        byte[] referenceFrame = frame(reference, 3);
        assertTrue(referencePattern.render(LedTestPattern.Type.SIDES, 0, 0, referenceFrame));
        assertArrayEquals(new byte[]{(byte) 255, 0, 0},
                slice(referenceFrame, physicalForLogical(reference, 0) * 3, 3));
        assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255},
                slice(referenceFrame, physicalForLogical(reference, 238) * 3, 3));
    }

    @Test
    public void blackAndSkipGapsPlusTailStayBlack() {
        CompiledLayout blackGap = layout(10, 0, 0, 0, 3, 2, 0, false,
                GapMode.BLACK, 12);
        LedTestPattern blackPattern = new LedTestPattern(blackGap, 3, null);
        byte[] blackFrame = frame(blackGap, 3);
        assertTrue(blackPattern.render(LedTestPattern.Type.SOLID, 0xffffff, 0, blackFrame));
        LedMapping mapping = blackGap.mapping();
        for (int physical = 0; physical < mapping.physicalLedCount(); physical++) {
            int logical = mapping.physicalToLogical(physical);
            boolean inGap = logical >= 2 && logical < 5;
            boolean tail = physical >= mapping.effectiveLedCount();
            if (inGap || tail) {
                assertArrayEquals(new byte[3], slice(blackFrame, physical * 3, 3));
            } else {
                assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255},
                        slice(blackFrame, physical * 3, 3));
            }
        }

        CompiledLayout skipGap = layout(10, 0, 0, 0, 3, 2, 0, false,
                GapMode.SKIP, 7);
        LedTestPattern skipPattern = new LedTestPattern(skipGap, 3, null);
        byte[] skipFrame = frame(skipGap, 3);
        assertTrue(skipPattern.render(LedTestPattern.Type.SOLID, 0xffffff, 0, skipFrame));
        assertEquals(7, skipPattern.litCount(skipFrame));
        assertEquals(7, skipGap.mapping().physicalLedCount());
    }

    @Test
    public void walkingPatternLightsExactlyOnePhysicalLedPerIndex() {
        CompiledLayout layout = layout(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 274);
        LedTestPattern pattern = new LedTestPattern(layout, 3, null);
        byte[] frame = frame(layout, 3);
        for (int physical = 0; physical < 274; physical++) {
            assertTrue(pattern.render(LedTestPattern.Type.WALKING, 0xffffff, physical, frame));
            assertEquals(1, pattern.litCount(frame));
            assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255},
                    slice(frame, physical * 3, 3));
        }
    }

    @Test
    public void rgbwPatternsUseTheSharedConversionVectors() {
        CompiledLayout layout = layout(2, 0, 0, 0, 0, 0, 0, false,
                GapMode.SKIP, 2);
        byte[] minSubtract = frame(layout, 4);
        new LedTestPattern(layout, 4, WhiteMode.MIN_SUBTRACT)
                .render(LedTestPattern.Type.SOLID, 0xffffff, 0, minSubtract);
        for (int physical = 0; physical < 2; physical++) {
            assertArrayEquals(new byte[]{0, 0, 0, (byte) 255},
                    slice(minSubtract, physical * 4, 4));
        }

        byte[] preserve = frame(layout, 4);
        new LedTestPattern(layout, 4, WhiteMode.PRESERVE)
                .render(LedTestPattern.Type.SOLID, 0xffffff, 0, preserve);
        for (int physical = 0; physical < 2; physical++) {
            assertArrayEquals(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255},
                    slice(preserve, physical * 4, 4));
        }
    }

    @Test
    public void sessionRefusesCaptureAndFinishesAfterTenSeconds() {
        CompiledLayout layout = layout(4, 0, 0, 0, 0, 0, 0, false,
                GapMode.SKIP, 4);
        TestPatternSession session = new TestPatternSession(
                new LedTestPattern(layout, 3, null));
        byte[] frame = frame(layout, 3);

        assertFalse(session.start(true, LedTestPattern.Type.SIDES, 0, 1000));
        assertEquals(TestPatternSession.State.ERROR, session.state());

        assertTrue(session.start(false, LedTestPattern.Type.SIDES, 0, 1000));
        assertEquals(TestPatternSession.State.RUNNING, session.render(frame, 1000));
        assertEquals(TestPatternSession.State.RUNNING, session.render(frame, 10999));
        assertEquals(1, session.remainingMillis(10999));
        assertEquals(TestPatternSession.State.FINISHED, session.render(frame, 11000));
        assertEquals(0, session.remainingMillis(11000));
    }

    @Test
    public void cancelStopsFurtherFrames() {
        CompiledLayout layout = layout(4, 0, 0, 0, 0, 0, 0, false,
                GapMode.SKIP, 4);
        TestPatternSession session = new TestPatternSession(
                new LedTestPattern(layout, 3, null));
        assertTrue(session.start(false, LedTestPattern.Type.WALKING, 0xffffff, 0));
        session.cancel();
        byte[] frame = frame(layout, 3);
        assertEquals(TestPatternSession.State.CANCELLED, session.render(frame, 1));
        assertEquals(TestPatternSession.State.CANCELLED, session.render(frame, 2));
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(source, offset, slice, 0, length);
        return slice;
    }
}
