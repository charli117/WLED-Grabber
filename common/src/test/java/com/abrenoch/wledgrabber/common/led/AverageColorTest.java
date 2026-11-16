package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AverageColorTest {
    private final LedColorSampler sampler = new LedColorSampler();

    @Test
    public void averageRgbwKeepsBlackGapAndTailBlack() {
        PlaneView plane = plane(16, 16, (x, y) -> new int[]{10, 20, 30});
        CompiledLayout layout = layout(8, 8, 0, 0, 4, 0, GapMode.BLACK, 20, 16, 16);

        byte[] out = new byte[20 * 4];
        assertTrue(sampler.samplePhysical(plane, layout, true,
                RgbwMode.RGBW, WhiteMode.MIN_SUBTRACT, out));

        for (int physical = 0; physical < 20; physical++) {
            int offset = physical * 4;
            if (layout.mapping().isPhysicalBlack(physical)) {
                assertArrayEquals(new byte[4], slice(out, offset, 4));
            } else {
                assertArrayEquals(new byte[]{0, 10, 20, 10}, slice(out, offset, 4));
            }
        }
    }

    @Test
    public void averagePreserveAndRgbModesUseTheSameMask() {
        PlaneView plane = plane(16, 16, (x, y) -> new int[]{10, 20, 30});
        CompiledLayout layout = layout(8, 8, 0, 0, 4, 0, GapMode.BLACK, 20, 16, 16);

        byte[] preserve = new byte[20 * 4];
        assertTrue(sampler.samplePhysical(plane, layout, true,
                RgbwMode.RGBW, WhiteMode.PRESERVE, preserve));
        byte[] rgb = new byte[20 * 3];
        assertTrue(sampler.samplePhysical(plane, layout, true,
                RgbwMode.RGB, null, rgb));

        for (int physical = 0; physical < 20; physical++) {
            if (layout.mapping().isPhysicalBlack(physical)) {
                assertArrayEquals(new byte[4], slice(preserve, physical * 4, 4));
                assertArrayEquals(new byte[3], slice(rgb, physical * 3, 3));
            } else {
                assertArrayEquals(new byte[]{10, 20, 30, 10}, slice(preserve, physical * 4, 4));
                assertArrayEquals(new byte[]{10, 20, 30}, slice(rgb, physical * 3, 3));
            }
        }
    }

    @Test
    public void perLedSamplingUsesTheSameMappingAndBlackMask() {
        PlaneView plane = plane(2, 1, (x, y) -> x == 0
                ? new int[]{255, 0, 0} : new int[]{0, 255, 0});
        CompiledLayout layout = layout(2, 0, 0, 0, 1, 0, GapMode.BLACK, 2, 2, 1);

        byte[] out = new byte[2 * 4];
        assertTrue(sampler.samplePhysical(plane, layout, false,
                RgbwMode.RGBW, WhiteMode.MIN_SUBTRACT, out));

        assertArrayEquals(new byte[4], slice(out, 0, 4));
        assertArrayEquals(new byte[]{0, (byte) 255, 0, 0}, slice(out, 4, 4));
    }

    @Test
    public void invalidModesOrBuffersAreRejected() {
        PlaneView plane = plane(4, 1, (x, y) -> new int[]{1, 2, 3});
        CompiledLayout layout = layout(4, 0, 0, 0, 0, 0, GapMode.SKIP, 4, 4, 1);

        assertFalse(sampler.samplePhysical(plane, layout, true,
                RgbwMode.RGBW, null, new byte[16]));
        assertFalse(sampler.samplePhysical(plane, layout, true,
                RgbwMode.UNCONFIRMED, WhiteMode.MIN_SUBTRACT, new byte[16]));
        assertFalse(sampler.samplePhysical(plane, layout, true,
                RgbwMode.RGBW, WhiteMode.MIN_SUBTRACT, new byte[15]));
    }

    private static CompiledLayout layout(int top, int bottom, int left, int right,
                                         int gapLength, int gapPosition, GapMode gapMode,
                                         int physical, int width, int height) {
        LedLayoutConfig config = new LedLayoutConfig(top, bottom, left, right,
                gapLength, gapPosition, 0, false, gapMode, 8, 5, 0, 0,
                0, 0, 100, 0, 100, 100, 0, 100);
        ValidationResult<CompiledLayout> result = new ClassicLedLayout().compile(
                config, physical, width, height);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private interface ColorAt {
        int[] rgb(int x, int y);
    }

    private static PlaneView plane(int width, int height, ColorAt colorAt) {
        int rowStride = width * 4;
        ByteBuffer buffer = ByteBuffer.wrap(new byte[rowStride * height]);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] rgb = colorAt.rgb(x, y);
                int index = y * rowStride + x * 4;
                buffer.put(index, (byte) rgb[0]);
                buffer.put(index + 1, (byte) rgb[1]);
                buffer.put(index + 2, (byte) rgb[2]);
                buffer.put(index + 3, (byte) 0xff);
            }
        }
        ValidationResult<PlaneView> result = PlaneView.create(buffer, width, height,
                rowStride, 4, 0, 0, width, height);
        assertTrue(result.errors().toString(), result.isValid());
        return result.value();
    }

    private static byte[] slice(byte[] source, int offset, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(source, offset, copy, 0, length);
        return copy;
    }
}
