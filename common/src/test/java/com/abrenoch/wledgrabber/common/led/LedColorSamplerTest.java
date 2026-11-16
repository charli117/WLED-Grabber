package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LedColorSamplerTest {
    private final LedColorSampler sampler = new LedColorSampler();

    private static final class PlaneData {
        private final ByteBuffer buffer;
        private final PlaneView view;

        private PlaneData(ByteBuffer buffer, PlaneView view) {
            this.buffer = buffer;
            this.view = view;
        }
    }

    private static PlaneData randomPlane(Random random) {
        int width = random.nextInt(48) + 1;
        int height = random.nextInt(48) + 1;
        int rowPadding = random.nextInt(6);
        int pixelStride = 4 + random.nextInt(3);
        int rowStride = width * pixelStride + rowPadding;
        int offset = random.nextInt(6);
        int capacity = offset + rowStride * height;
        byte[] bytes = new byte[capacity];
        random.nextBytes(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.position(offset);
        // Last row is intentionally compact: no trailing padding is promised.
        buffer.limit(offset + rowStride * (height - 1) + width * pixelStride);

        int cropWidth = random.nextInt(width) + 1;
        int cropHeight = random.nextInt(height) + 1;
        int cropLeft = random.nextInt(width - cropWidth + 1);
        int cropTop = random.nextInt(height - cropHeight + 1);
        ValidationResult<PlaneView> result = PlaneView.create(buffer, width, height,
                rowStride, pixelStride, cropLeft, cropTop, cropWidth, cropHeight);
        assertTrue(result.errors().toString(), result.isValid());
        return new PlaneData(buffer, result.value());
    }

    private static CompiledLayout randomLayout(Random random, int width, int height) {
        int count = random.nextInt(20) + 1;
        List<LedSampleRect> rects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double hmin = random.nextDouble() * 0.9;
            double hmax = Math.min(1.0, hmin + 0.01 + random.nextDouble() * 0.09);
            double vmin = random.nextDouble() * 0.9;
            double vmax = Math.min(1.0, vmin + 0.01 + random.nextDouble() * 0.09);
            rects.add(new LedSampleRect(hmin, hmax, vmin, vmax, Side.TOP, i));
        }
        return new CompiledLayout(rects, width, height, 0);
    }

    private static int oracle(PlaneData data, PixelRect rect) {
        PlaneView view = data.view;
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int y = rect.y0(); y < rect.y1(); y++) {
            for (int x = rect.x0(); x < rect.x1(); x++) {
                int index = view.offset() + (view.cropTop() + y) * view.rowStride()
                        + (view.cropLeft() + x) * view.pixelStride();
                red += data.buffer.get(index) & 0xff;
                green += data.buffer.get(index + 1) & 0xff;
                blue += data.buffer.get(index + 2) & 0xff;
            }
        }
        int area = rect.area();
        int r = (int) (red / area);
        int g = (int) (green / area);
        int b = (int) (blue / area);
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    @Test
    public void randomFramesMatchNaiveOracle() {
        Random random = new Random(42);
        for (int iteration = 0; iteration < 1000; iteration++) {
            PlaneData plane = randomPlane(random);
            CompiledLayout layout = randomLayout(random, plane.view.cropWidth(),
                    plane.view.cropHeight());
            int[] out = new int[layout.count()];
            assertTrue(sampler.sample(plane.view, layout, out));
            for (int i = 0; i < layout.count(); i++) {
                assertEquals("iteration " + iteration + " rect " + i,
                        oracle(plane, layout.pixelRects().get(i)), out[i]);
            }
        }
    }

    @Test
    public void solidGrayCheckerAndSmallRectMatchNaiveOracle() {
        PlaneData solid = plane(10, 10, 0, 4, 0, 0, 0, 10, 10, (x, y) -> new int[]{12, 34, 56});
        CompiledLayout full = fullFrame(10, 10);
        int[] solidOut = new int[1];
        assertTrue(sampler.sample(solid.view, full, solidOut));
        assertEquals(0xff0c2238, solidOut[0]);

        PlaneData gray = plane(2, 2, 0, 4, 0, 0, 0, 2, 2,
                (x, y) -> (x + y) % 2 == 0 ? new int[]{0, 0, 0} : new int[]{255, 255, 255});
        int[] grayOut = new int[1];
        assertTrue(sampler.sample(gray.view, fullFrame(2, 2), grayOut));
        assertEquals(0xff7f7f7f, grayOut[0]);

        PlaneData checker = plane(8, 8, 0, 4, 0, 0, 0, 8, 8,
                (x, y) -> (x + y) % 2 == 0 ? new int[]{255, 0, 0} : new int[]{0, 255, 0});
        int[] checkerOut = new int[1];
        assertTrue(sampler.sample(checker.view, fullFrame(8, 8), checkerOut));
        assertEquals(0xff7f7f00, checkerOut[0]);

        PlaneData small = plane(8, 8, 0, 4, 0, 0, 0, 8, 8, (x, y) -> new int[]{x, y, x + y});
        int[] smallOut = new int[1];
        assertTrue(sampler.sample(small.view, rectLayout(0.25, 0.5, 0.25, 0.5, 8, 8), smallOut));
        assertEquals(0xff020205, smallOut[0]);
    }

    @Test
    public void supportsNonZeroPositionPaddedStrideAndCompactLastRow() {
        PlaneData data = plane(4, 3, 3, 5, 7, 1, 1, 2, 2,
                (x, y) -> new int[]{x * 10 + y, x * 20 + y, x * 30 + y});
        int[] out = new int[1];
        CompiledLayout layout = rectLayout(0, 0, 1, 1, 2, 2);
        assertTrue(sampler.sample(data.view, layout, out));
        assertEquals(oracle(data, layout.pixelRects().get(0)), out[0]);
    }

    @Test
    public void malformedPlanesAreRejected() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[32]);
        assertFalse(PlaneView.create(null, 2, 2, 8, 4, 0, 0, 2, 2).isValid());
        assertFalse(PlaneView.create(buffer, 2, 2, 8, 3, 0, 0, 2, 2).isValid());
        assertFalse(PlaneView.create(buffer, 2, 2, 4, 4, 0, 0, 2, 2).isValid());
        assertFalse(PlaneView.create(buffer, 641, 1, 641 * 4, 4, 0, 0, 641, 1).isValid());
        assertFalse(PlaneView.create(buffer, 2, 2, 8, 4, 1, 0, 2, 2).isValid());
        buffer.position(24);
        buffer.limit(32);
        assertFalse(PlaneView.create(buffer, 2, 2, 8, 4, 0, 0, 2, 2).isValid());
    }

    @Test
    public void hotPathReusesIntegralArraysForOneHundredThousandFrames() {
        PlaneData data = plane(32, 18, 0, 4, 0, 0, 0, 32, 18, (x, y) -> new int[]{x, y, 1});
        CompiledLayout layout = randomLayout(new Random(7), 32, 18);
        int[] out = new int[layout.count()];
        for (int i = 0; i < 100000; i++) {
            assertTrue(sampler.sample(data.view, layout, out));
        }
        assertEquals(3, sampler.allocationCount());
        assertEquals(32, sampler.integralWidth());
        assertEquals(18, sampler.integralHeight());
    }

    @Test
    public void integralMemoryStaysWithinDocumentedBudget() {
        long bytes = IntegralImage.requiredBytes(640, 640);
        assertEquals(3L * 641 * 641 * 4, bytes);
        assertTrue(bytes < 12L * 1024 * 1024);
    }

    @Test
    public void captureSizingCapsLongestSideWithoutUpscaling() {
        CaptureSizing.Size hd = CaptureSizing.fit(1920, 1080, 640);
        assertEquals(640, hd.width());
        assertEquals(360, hd.height());

        CaptureSizing.Size small = CaptureSizing.fit(320, 200, 640);
        assertEquals(320, small.width());
        assertEquals(200, small.height());

        CaptureSizing.Size square = CaptureSizing.fit(1000, 1000, 640);
        assertEquals(640, square.width());
        assertEquals(640, square.height());

        CaptureSizing.Size tall = CaptureSizing.fit(1, 1000, 640);
        assertEquals(1, tall.width());
        assertEquals(640, tall.height());
    }

    private static CompiledLayout fullFrame(int width, int height) {
        List<LedSampleRect> rects = new ArrayList<>();
        rects.add(new LedSampleRect(0, 1, 0, 1, Side.TOP, 0));
        return new CompiledLayout(rects, width, height, 0);
    }

    private static CompiledLayout rectLayout(double hmin, double hmax, double vmin, double vmax,
                                             int width, int height) {
        List<LedSampleRect> rects = new ArrayList<>();
        rects.add(new LedSampleRect(hmin, hmax, vmin, vmax, Side.TOP, 0));
        return new CompiledLayout(rects, width, height, 0);
    }

    private interface ColorAt {
        int[] rgb(int x, int y);
    }

    private static PlaneData plane(int width, int height, int rowPadding, int pixelStride,
                                   int offset, int cropLeft, int cropTop,
                                   int cropWidth, int cropHeight, ColorAt colorAt) {
        int rowStride = width * pixelStride + rowPadding;
        byte[] bytes = new byte[offset + rowStride * height];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int[] rgb = colorAt.rgb(x, y);
                int index = offset + y * rowStride + x * pixelStride;
                buffer.put(index, (byte) rgb[0]);
                buffer.put(index + 1, (byte) rgb[1]);
                buffer.put(index + 2, (byte) rgb[2]);
                buffer.put(index + 3, (byte) 0xff);
            }
        }
        buffer.position(offset);
        buffer.limit(offset + rowStride * (height - 1) + width * pixelStride);
        ValidationResult<PlaneView> result = PlaneView.create(buffer, width, height,
                rowStride, pixelStride, cropLeft, cropTop, cropWidth, cropHeight);
        assertTrue(result.errors().toString(), result.isValid());
        return new PlaneData(buffer, result.value());
    }
}
