package com.abrenoch.wledgrabber.common.views;

import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LedLayoutPreviewTest {

    private static CompiledLayout layout(int top, int bottom, int left, int right,
                                         int gapLength, int gapPosition, int input,
                                         boolean reverse, GapMode gapMode, int physical,
                                         int width, int height) {
        LedLayoutConfig config = new LedLayoutConfig(top, bottom, left, right,
                gapLength, gapPosition, input, reverse, gapMode, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result = new ClassicLedLayout().compile(
                config, physical, width, height);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    @Test
    public void firstUnit274PreviewMatchesMappingAndCorners() {
        CompiledLayout layout = layout(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 274, 640, 360);
        PreviewModel model = PreviewModel.build(layout, 0);

        assertFalse(model.hasError());
        assertEquals(274, model.rects().size());
        assertEquals(0, model.selectedPhysical());
        assertEquals(160, model.selectedLogical());
        assertEquals(0, model.tailCount());
        assertEquals(274, model.effectiveCount());
        assertEquals(0, model.gapLength());
        assertTrue(model.corners()[0] == 0.0 && model.corners()[2] == 1.0
                && model.corners()[5] == 1.0);

        Set<Integer> physicalIndices = new HashSet<>();
        for (PreviewModel.PreviewRect rect : model.rects()) {
            assertTrue(rect.physicalIndex() >= 0);
            assertTrue(physicalIndices.add(rect.physicalIndex()));
            assertFalse(rect.black());
        }
        assertEquals(274, physicalIndices.size());
    }

    @Test
    public void reference288PreviewUsesItsOwnMapping() {
        CompiledLayout layout = layout(116, 72, 50, 50, 0, 0, 206, false,
                GapMode.SKIP, 288, 640, 360);
        PreviewModel model = PreviewModel.build(layout, 0);

        assertEquals(288, model.rects().size());
        assertEquals(206, model.selectedLogical());
        assertEquals(288, model.physicalCount());
        assertEquals(0, model.tailCount());
    }

    @Test
    public void skipGapHasNoPhysicalIndexAndIsBlack() {
        CompiledLayout layout = layout(10, 0, 0, 0, 3, 2, 0, false,
                GapMode.SKIP, 7, 100, 100);
        PreviewModel model = PreviewModel.build(layout, 0);

        assertEquals(10, model.rects().size());
        assertEquals(7, model.effectiveCount());
        assertEquals(0, model.tailCount());
        assertEquals(GapMode.SKIP, model.gapMode());
        for (PreviewModel.PreviewRect rect : model.rects()) {
            if (rect.logicalIndex() >= 2 && rect.logicalIndex() < 5) {
                assertEquals(-1, rect.physicalIndex());
                assertTrue(rect.black());
            } else {
                assertTrue(rect.physicalIndex() >= 0);
                assertFalse(rect.black());
            }
        }
    }

    @Test
    public void blackGapKeepsPhysicalIndicesButMarksThemBlack() {
        CompiledLayout layout = layout(10, 0, 0, 0, 3, 2, 0, false,
                GapMode.BLACK, 10, 100, 100);
        PreviewModel model = PreviewModel.build(layout, 0);

        assertEquals(10, model.effectiveCount());
        assertEquals(0, model.tailCount());
        assertEquals(GapMode.BLACK, model.gapMode());
        for (PreviewModel.PreviewRect rect : model.rects()) {
            if (rect.logicalIndex() >= 2 && rect.logicalIndex() < 5) {
                assertTrue(rect.physicalIndex() >= 0);
                assertTrue(rect.black());
            }
        }
    }

    @Test
    public void reverseSelectionMovesInPhysicalOrder() {
        CompiledLayout layout = layout(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 274, 640, 360);
        assertEquals(160, PreviewModel.build(layout, 0).selectedLogical());
        assertEquals(159, PreviewModel.build(layout, 1).selectedLogical());
        assertEquals(0, PreviewModel.build(layout, 160).selectedLogical());
        assertEquals(273, PreviewModel.build(layout, 161).selectedLogical());
    }

    @Test
    public void tailCountAndUnmappedPhysicalSelectionAreVisible() {
        CompiledLayout layout = layout(4, 0, 0, 0, 1, 1, 0, false,
                GapMode.SKIP, 5, 100, 100);
        PreviewModel model = PreviewModel.build(layout, 4);

        assertEquals(5, model.physicalCount());
        assertEquals(3, model.effectiveCount());
        assertEquals(2, model.tailCount());
        assertEquals(4, model.selectedPhysical());
        assertEquals(-1, model.selectedLogical());
    }

    @Test
    public void portraitAndTrapezoidCornersStayInsideTheUnitDomain() {
        CompiledLayout portrait = layout(0, 5, 5, 0, 0, 0, 0, false,
                GapMode.SKIP, 10, 360, 640);
        PreviewModel portraitModel = PreviewModel.build(portrait, 3);
        assertEquals(10, portraitModel.rects().size());
        for (PreviewModel.PreviewRect rect : portraitModel.rects()) {
            assertTrue(rect.hmin() >= 0 && rect.hmax() <= 1);
            assertTrue(rect.vmin() >= 0 && rect.vmax() <= 1);
        }

        LedLayoutConfig trapezoid = new LedLayoutConfig(4, 4, 4, 4, 0, 0, 0, false,
                GapMode.SKIP, 10, 10, 0, 0, 10, 10, 90, 10, 90, 90, 10, 90);
        CompiledLayout trapezoidLayout = new ClassicLedLayout().compile(
                trapezoid, 16, 100, 100).value();
        PreviewModel model = PreviewModel.build(trapezoidLayout, 0);
        assertEquals(10.0 / 100.0, model.corners()[0], 1e-9);
        assertEquals(0.9, model.corners()[2], 1e-9);
        assertEquals(0.9, model.corners()[5], 1e-9);
        assertEquals(0.1, model.corners()[6], 1e-9);
    }

    @Test
    public void selectionClampsAndErrorModelHasNoRects() {
        CompiledLayout layout = layout(4, 0, 0, 0, 0, 0, 0, false,
                GapMode.SKIP, 4, 100, 100);
        assertEquals(0, PreviewModel.build(layout, -5).selectedPhysical());
        assertEquals(3, PreviewModel.build(layout, 999).selectedPhysical());

        PreviewModel error = PreviewModel.error("bad layout");
        assertTrue(error.hasError());
        assertEquals("bad layout", error.error());
        assertTrue(error.rects().isEmpty());
        assertNotNull(error.corners());
    }

    @Test
    public void selectionReusesRectsWithoutChangingEarlierSnapshots() {
        CompiledLayout layout = layout(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 274, 640, 360);
        PreviewModel first = PreviewModel.build(layout, 0);
        PreviewModel second = first.select(1);

        assertEquals(0, first.selectedPhysical());
        assertEquals(160, first.selectedLogical());
        assertTrue(first.rects().get(160).selected());
        assertFalse(first.rects().get(159).selected());
        assertEquals(1, second.selectedPhysical());
        assertEquals(159, second.selectedLogical());
        assertFalse(second.rects().get(160).selected());
        assertTrue(second.rects().get(159).selected());
        assertSame(first.rects().get(0), second.rects().get(0));
        assertSame(first.rects().get(160), second.select(0).rects().get(160));
        assertSame(second, second.select(1));

        PreviewModel current = second;
        Set<PreviewModel.PreviewRect> seen = new HashSet<>(first.rects());
        for (int selection = 0; selection < 274 * 3; selection++) {
            current = current.select(selection % 274);
            int selectedRects = 0;
            for (PreviewModel.PreviewRect rect : current.rects()) {
                seen.add(rect);
                if (rect.selected()) {
                    selectedRects++;
                    assertEquals(current.selectedPhysical(), rect.physicalIndex());
                }
            }
            assertEquals(1, selectedRects);
        }
        assertEquals("only the selected and unselected variants exist per rectangle",
                274 * 2, seen.size());
    }
}
