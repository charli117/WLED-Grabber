package com.abrenoch.wledgrabber.common.led;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlackBarCropperTest {
    private static int[] rows(int brightFrom, int brightTo, int height) {
        int[] out = new int[height];
        for (int y = brightFrom; y < brightTo; y++) {
            out[y] = 200;
        }
        return out;
    }

    private static int[] uniform(int length, int value) {
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = value;
        }
        return out;
    }

    @Test
    public void letterboxBarsAreCroppedAfterHysteresis() {
        BlackBarCropper cropper = new BlackBarCropper();
        CropWindow window = null;
        for (int i = 0; i < 3; i++) {
            window = cropper.update(10, 10, rows(3, 7, 10), uniform(10, 200));
        }
        assertEquals(new CropWindow(0, 3, 10, 7), window);
        assertEquals(new CropWindow(0, 3, 10, 7), cropper.committed());
    }

    @Test
    public void allBlackFramesKeepThePreviousWindow() {
        BlackBarCropper cropper = new BlackBarCropper();
        for (int i = 0; i < 3; i++) {
            cropper.update(10, 10, rows(3, 7, 10), uniform(10, 200));
        }
        CropWindow window = cropper.update(10, 10, uniform(10, 0), uniform(10, 0));
        assertEquals(new CropWindow(0, 3, 10, 7), window);
        assertEquals(new CropWindow(0, 3, 10, 7), cropper.committed());
    }

    @Test
    public void aSingleFlashDoesNotMoveTheWindow() {
        BlackBarCropper cropper = new BlackBarCropper();
        for (int i = 0; i < 3; i++) {
            cropper.update(10, 10, rows(3, 7, 10), uniform(10, 200));
        }
        int[] flash = rows(3, 10, 10);
        CropWindow window = cropper.update(10, 10, flash, uniform(10, 200));
        assertEquals(new CropWindow(0, 3, 10, 7), window);
        assertEquals(new CropWindow(0, 3, 10, 7), cropper.committed());
    }

    @Test
    public void stableNewBarsAreAdopted() {
        BlackBarCropper cropper = new BlackBarCropper();
        for (int i = 0; i < 3; i++) {
            cropper.update(10, 10, rows(3, 7, 10), uniform(10, 200));
        }
        CropWindow window = null;
        for (int i = 0; i < 3; i++) {
            window = cropper.update(10, 10, rows(2, 8, 10), uniform(10, 200));
        }
        assertEquals(new CropWindow(0, 2, 10, 8), window);
    }

    @Test
    public void fullFrameIsReportedWhenThereAreNoBars() {
        BlackBarCropper cropper = new BlackBarCropper();
        CropWindow window = null;
        for (int i = 0; i < 3; i++) {
            window = cropper.update(10, 10, uniform(10, 128), uniform(10, 128));
        }
        assertTrue(window.isFullFrame(10, 10));
    }

    @Test
    public void resetForgetsHysteresisHistory() {
        BlackBarCropper cropper = new BlackBarCropper();
        for (int i = 0; i < 3; i++) {
            cropper.update(10, 10, rows(3, 7, 10), uniform(10, 200));
        }
        cropper.reset();
        assertEquals(null, cropper.committed());
    }
}
