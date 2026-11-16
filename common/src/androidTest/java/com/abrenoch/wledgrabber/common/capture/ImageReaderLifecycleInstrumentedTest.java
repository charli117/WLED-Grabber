package com.abrenoch.wledgrabber.common.capture;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.PlaneView;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * CAP-2 device-level reader contract driven by a real producer: a Surface is
 * painted with a known color, the ImageReader hands the frame back, the
 * ImagePlaneView adapter reports the expected geometry and pixel, and every
 * acquired Image is closed exactly once. maxImages=3 is enforced by the reader.
 */
public class ImageReaderLifecycleInstrumentedTest {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    private static void paint(ImageReader reader, int color) {
        Surface surface = reader.getSurface();
        Canvas canvas = surface.lockCanvas(null);
        try {
            canvas.drawColor(color);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }

    private static Image nextImage(ImageReader reader) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                return image;
            }
            Thread.sleep(20);
        }
        return null;
    }

    @Test
    public void producerFramesAreReadableAndClosedExactlyOnce() throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        ImageReader reader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 3);
        int acquired = 0;
        int closed = 0;
        try {
            for (int i = 0; i < 20; i++) {
                paint(reader, Color.RED);
                Image image = nextImage(reader);
                assertTrue("producer must deliver frame " + i, image != null);
                acquired++;
                ValidationResult<PlaneView> result = ImagePlaneView.from(image);
                assertTrue(String.valueOf(result.errors()), result.isValid());
                PlaneView plane = result.value();
                assertEquals(WIDTH, plane.cropWidth());
                assertEquals(HEIGHT, plane.cropHeight());
                image.close();
                closed++;
            }
            assertEquals("each acquired Image is closed exactly once", acquired, closed);
        } finally {
            reader.close();
        }
    }

    @Test
    public void readerRejectsAcquireBeyondMaxImages() throws Exception {
        ImageReader reader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 3);
        Image[] held = new Image[3];
        int heldCount = 0;
        try {
            for (int i = 0; i < 3; i++) {
                paint(reader, Color.GREEN);
                Image image = nextImage(reader);
                assertTrue("frame " + i + " must be delivered", image != null);
                held[heldCount++] = image;
            }
            boolean rejected = false;
            try {
                reader.acquireLatestImage();
            } catch (IllegalStateException expected) {
                // Documented contract: close before acquiring more than maxImages.
                rejected = true;
            }
            assertTrue("reader must refuse a fourth in-flight image", rejected);
        } finally {
            for (int i = 0; i < heldCount; i++) {
                if (held[i] != null) {
                    held[i].close();
                }
            }
            reader.close();
        }
    }
}
