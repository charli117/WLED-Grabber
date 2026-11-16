package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassicLedLayoutTest {
    private static final double DELTA = 5e-5;
    private final ClassicLedLayout layout = new ClassicLedLayout();

    private static LedLayoutConfig config(int top, int bottom, int left, int right,
                                          int hDepth, int vDepth, int overlap, int edgeGap) {
        return new LedLayoutConfig(top, bottom, left, right, 0, 0, 0, false,
                GapMode.SKIP, hDepth, vDepth, overlap, edgeGap,
                0, 0, 100, 0, 100, 100, 0, 100);
    }

    private static LedLayoutConfig corners(int top, int bottom, int left, int right,
                                           int hDepth, int vDepth, int overlap, int edgeGap,
                                           int tlH, int tlV, int trH, int trV,
                                           int brH, int brV, int blH, int blV) {
        return new LedLayoutConfig(top, bottom, left, right, 0, 0, 0, false,
                GapMode.SKIP, hDepth, vDepth, overlap, edgeGap,
                tlH, tlV, trH, trV, brH, brV, blH, blV);
    }

    private CompiledLayout valid(LedLayoutConfig config, int width, int height) {
        ValidationResult<CompiledLayout> result = layout.compile(config, width, height);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private ValidationResult<CompiledLayout> invalid(LedLayoutConfig config) {
        ValidationResult<CompiledLayout> result = layout.compile(config, 640, 360);
        assertFalse(result.isValid());
        return result;
    }

    private static int sideCount(CompiledLayout compiled, Side side) {
        int count = 0;
        for (LedSampleRect rect : compiled.rects()) {
            if (rect.side() == side) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void firstUnit274GoldenAndSideCounts() {
        CompiledLayout compiled = valid(config(86, 90, 49, 49, 8, 5, 0, 0), 640, 360);

        assertEquals(274, compiled.count());
        assertEquals(86, sideCount(compiled, Side.TOP));
        assertEquals(49, sideCount(compiled, Side.RIGHT));
        assertEquals(90, sideCount(compiled, Side.BOTTOM));
        assertEquals(49, sideCount(compiled, Side.LEFT));

        LedSampleRect top0 = compiled.rects().get(0);
        assertEquals(0.0, top0.hmin(), DELTA);
        assertEquals(1.0 / 86.0, top0.hmax(), DELTA);
        assertEquals(0.0, top0.vmin(), DELTA);
        assertEquals(0.08, top0.vmax(), DELTA);

        LedSampleRect right0 = compiled.rects().get(86);
        assertEquals(0.95, right0.hmin(), DELTA);
        assertEquals(1.0, right0.hmax(), DELTA);
        assertEquals(0.0, right0.vmin(), DELTA);
        assertEquals(1.0 / 49.0, right0.vmax(), DELTA);

        LedSampleRect bottom0 = compiled.rects().get(86 + 49);
        assertEquals(89.0 / 90.0, bottom0.hmin(), DELTA);
        assertEquals(1.0, bottom0.hmax(), DELTA);
        assertEquals(0.92, bottom0.vmin(), DELTA);
        assertEquals(1.0, bottom0.vmax(), DELTA);

        LedSampleRect left0 = compiled.rects().get(86 + 49 + 90);
        assertEquals(0.0, left0.hmin(), DELTA);
        assertEquals(0.05, left0.hmax(), DELTA);
        assertEquals(48.0 / 49.0, left0.vmin(), DELTA);
        assertEquals(1.0, left0.vmax(), DELTA);
    }

    @Test
    public void reference288GoldenWithOverlap() {
        CompiledLayout compiled = valid(config(116, 72, 50, 50, 15, 8, 10, 0), 640, 360);

        assertEquals(288, compiled.count());
        assertEquals(116, sideCount(compiled, Side.TOP));
        assertEquals(50, sideCount(compiled, Side.RIGHT));
        assertEquals(72, sideCount(compiled, Side.BOTTOM));
        assertEquals(50, sideCount(compiled, Side.LEFT));

        LedSampleRect top58 = compiled.rects().get(58);
        assertEquals(58.0 / 116.0 - 0.1, top58.hmin(), DELTA);
        assertEquals(59.0 / 116.0 + 0.1, top58.hmax(), DELTA);
        assertEquals(0.0, top58.vmin(), DELTA);
        assertEquals(0.15, top58.vmax(), DELTA);

        LedSampleRect right0 = compiled.rects().get(116);
        assertEquals(0.92, right0.hmin(), DELTA);
        assertEquals(1.0, right0.hmax(), DELTA);
        assertEquals(0.0, right0.vmin(), DELTA);
        assertEquals(0.12, right0.vmax(), DELTA);

        LedSampleRect bottom0 = compiled.rects().get(116 + 50);
        assertEquals(71.0 / 72.0 - 0.1, bottom0.hmin(), DELTA);
        assertEquals(1.0, bottom0.hmax(), DELTA);
        assertEquals(0.85, bottom0.vmin(), DELTA);
        assertEquals(1.0, bottom0.vmax(), DELTA);

        LedSampleRect left0 = compiled.rects().get(116 + 50 + 72);
        assertEquals(0.0, left0.hmin(), DELTA);
        assertEquals(0.08, left0.hmax(), DELTA);
        assertEquals(49.0 / 50.0 - 0.1, left0.vmin(), DELTA);
        assertEquals(1.0, left0.vmax(), DELTA);
    }

    @Test
    public void nonSixteenByNineUsesActualAspectRatio() {
        CompiledLayout compiled = valid(config(10, 10, 0, 0, 8, 5, 0, 10), 640, 480);

        // edgeVGap=0.05; edgeHGap=0.05*480/640=0.0375; stepH=(1-0.075)/10=0.0925
        LedSampleRect top0 = compiled.rects().get(0);
        assertEquals(0.0375, top0.hmin(), DELTA);
        assertEquals(0.13, top0.hmax(), DELTA);
        assertEquals(0.0, top0.vmin(), DELTA);
        assertEquals(0.08, top0.vmax(), DELTA);
    }

    @Test
    public void trapezoidCornersStayMonotonicAndBounded() {
        CompiledLayout compiled = valid(corners(4, 4, 4, 4, 10, 10, 0, 0,
                10, 10, 90, 10, 90, 90, 10, 90), 100, 100);

        assertEquals(16, compiled.count());
        for (int i = 0; i < compiled.count(); i++) {
            LedSampleRect rect = compiled.rects().get(i);
            assertTrue(rect.isValidNormalized());
            assertEquals(i, rect.logicalIndex());
        }
        LedSampleRect top0 = compiled.rects().get(0);
        LedSampleRect top3 = compiled.rects().get(3);
        assertTrue(top3.hmin() > top0.hmin());

        LedSampleRect bottom0 = compiled.rects().get(8);
        LedSampleRect bottom3 = compiled.rects().get(11);
        assertTrue(bottom0.hmin() > bottom3.hmin());

        LedSampleRect left0 = compiled.rects().get(12);
        LedSampleRect left3 = compiled.rects().get(15);
        assertTrue(left0.vmin() > left3.vmin());
    }

    @Test
    public void singleSideTopAndRightWork() {
        CompiledLayout topOnly = valid(config(1, 0, 0, 0, 8, 5, 0, 0), 640, 360);
        assertEquals(1, topOnly.count());
        assertEquals(Side.TOP, topOnly.rects().get(0).side());

        CompiledLayout rightOnly = valid(config(0, 0, 0, 1, 8, 5, 0, 0), 640, 360);
        assertEquals(1, rightOnly.count());
        assertEquals(Side.RIGHT, rightOnly.rects().get(0).side());
    }

    @Test
    public void narrowHorizontalRectsAreNotRejectedByFourDecimalView() {
        CompiledLayout compiled = valid(corners(1000, 0, 0, 0, 8, 5, 0, 0,
                0, 0, 1, 0, 1, 100, 0, 100), 640, 360);

        assertEquals(1000, compiled.count());
        assertEquals(1, compiled.pixelRects().get(0).x1() - compiled.pixelRects().get(0).x0());
        for (PixelRect rect : compiled.pixelRects()) {
            assertTrue(rect.width() >= 1);
            assertTrue(rect.height() >= 1);
        }
    }

    @Test
    public void narrowVerticalRectsAreNotRejectedByFourDecimalView() {
        CompiledLayout compiled = valid(corners(0, 0, 0, 1000, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 1, 0, 1), 640, 360);

        assertEquals(1000, compiled.count());
        assertEquals(1, compiled.pixelRects().get(0).y1() - compiled.pixelRects().get(0).y0());
        for (PixelRect rect : compiled.pixelRects()) {
            assertTrue(rect.width() >= 1);
            assertTrue(rect.height() >= 1);
        }
    }

    @Test
    public void selfIntersectingAndZeroAreaQuadsAreRejected() {
        invalid(corners(4, 4, 4, 4, 10, 10, 0, 0,
                0, 0, 100, 100, 0, 100, 100, 0));
        invalid(corners(4, 4, 4, 4, 10, 10, 0, 0,
                0, 0, 100, 0, 100, 0, 0, 0));
    }

    @Test
    public void edgeGapThatCollapsesTangentialRoomIsRejected() {
        invalid(corners(2, 0, 0, 0, 8, 5, 0, 50,
                0, 0, 1, 0, 1, 100, 0, 100));
    }

    @Test
    public void allSidesZeroIsRejected() {
        invalid(config(0, 0, 0, 0, 8, 5, 0, 0));
    }

    @Test
    public void fourDecimalGoldenMatchesReferenceOutputs() {
        CompiledLayout reference288 = valid(config(116, 72, 50, 50, 15, 8, 10, 0), 640, 360);
        LedSampleRect top58 = reference288.rects().get(58);
        assertEquals("0.4000", format(top58.hmin()));
        assertEquals("0.6086", format(top58.hmax()));
        assertEquals("0.0000", format(top58.vmin()));
        assertEquals("0.1500", format(top58.vmax()));

        CompiledLayout firstUnit274 = valid(config(86, 90, 49, 49, 8, 5, 0, 0), 640, 360);
        assertEquals("0.0116", format(firstUnit274.rects().get(0).hmax()));
        assertEquals("0.9889", format(firstUnit274.rects().get(86 + 49).hmin()));
        assertEquals("0.9200", format(firstUnit274.rects().get(86 + 49).vmin()));
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    @Test
    public void randomValidAndInvalidConfigurationsStayBounded() {
        Random random = new Random(42);
        int validCount = 0;
        int invalidCount = 0;
        for (int iteration = 0; iteration < 10000; iteration++) {
            if (iteration % 7 == 0) {
                // Deterministic self-intersecting quadrilateral; must be rejected, not crash.
                LedLayoutConfig invalid = corners(4, 4, 4, 4, 10, 10, 0, 0,
                        0, 0, 100, 100, 0, 100, 100, 0);
                assertFalse(layout.compile(invalid, 640, 360).isValid());
                invalidCount++;
                continue;
            }
            int top = random.nextInt(20) + 1;
            int bottom = random.nextInt(20) + 1;
            int left = random.nextInt(20) + 1;
            int right = random.nextInt(20) + 1;
            int hDepth = random.nextInt(50) + 1;
            int vDepth = random.nextInt(50) + 1;
            int overlap = random.nextInt(21);
            int edgeGap = random.nextInt(21);
            int tlH = random.nextInt(30);
            int trH = 70 + random.nextInt(31);
            int blH = random.nextInt(30);
            int brH = 70 + random.nextInt(31);
            int tlV = random.nextInt(30);
            int trV = random.nextInt(30);
            int blV = 70 + random.nextInt(31);
            int brV = 70 + random.nextInt(31);

            LedLayoutConfig config = corners(top, bottom, left, right, hDepth, vDepth,
                    overlap, edgeGap, tlH, tlV, trH, trV, brH, brV, blH, blV);
            ValidationResult<CompiledLayout> result = layout.compile(config, 640, 360);
            if (!result.isValid()) {
                invalidCount++;
                continue;
            }
            validCount++;
            CompiledLayout compiled = result.value();
            assertEquals(top + bottom + left + right, compiled.count());
            for (LedSampleRect rect : compiled.rects()) {
                assertTrue(Double.isFinite(rect.hmin()));
                assertTrue(Double.isFinite(rect.hmax()));
                assertTrue(Double.isFinite(rect.vmin()));
                assertTrue(Double.isFinite(rect.vmax()));
                assertTrue(rect.isValidNormalized());
            }
            for (PixelRect rect : compiled.pixelRects()) {
                assertTrue(rect.x0() >= 0);
                assertTrue(rect.x1() <= 640);
                assertTrue(rect.y0() >= 0);
                assertTrue(rect.y1() <= 360);
                assertTrue(rect.width() >= 1);
                assertTrue(rect.height() >= 1);
            }
        }
        assertTrue(validCount > 0);
        assertTrue(invalidCount > 0);
    }
}
