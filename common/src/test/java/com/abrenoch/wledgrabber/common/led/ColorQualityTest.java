package com.abrenoch.wledgrabber.common.led;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColorQualityTest {
    @Test
    public void conversionTablesRoundTripWithinQuantisationError() {
        int previous = -1;
        for (int value = 0; value < 256; value++) {
            int linear = ColorQuality.toLinear(value);
            int roundTrip = ColorQuality.toSrgb(linear);
            // Gamma 2.2 compresses the bottom of the scale into few 12-bit
            // codes; four codes of error is the documented quantisation bound.
            assertTrue("value " + value + " -> " + roundTrip,
                    Math.abs(roundTrip - value) <= 4);
            assertTrue("linear table must be non-decreasing", linear >= previous);
            previous = linear;
        }
        assertEquals(0, ColorQuality.toLinear(0));
        assertEquals(ColorQuality.LINEAR_MAX, ColorQuality.toLinear(255));
    }

    @Test
    public void neutralSaturationLeavesTheColorUntouched() {
        int[] rgb = {12, 34, 56};
        ColorQuality.applySaturation(rgb, QualitySettings.NEUTRAL_SATURATION);
        assertArrayEquals(new int[]{12, 34, 56}, rgb);
    }

    @Test
    public void zeroSaturationProducesLuma() {
        int[] rgb = {200, 40, 90};
        ColorQuality.applySaturation(rgb, 0);
        assertEquals(rgb[0], rgb[1]);
        assertEquals(rgb[1], rgb[2]);
    }

    @Test
    public void highSaturationClampsAndKeepsChannelsInRange() {
        int[] rgb = {200, 40, 90};
        ColorQuality.applySaturation(rgb, 200);
        for (int channel : rgb) {
            assertTrue(channel >= 0 && channel <= 255);
        }
        assertTrue(rgb[0] >= 200);
        assertTrue(rgb[1] <= 40);
    }

    @Test
    public void smoothingZeroKeepsCurrentAndHundredKeepsPrevious() {
        int[] current = {100, 100, 100};
        ColorQuality.smooth(current, new int[]{0, 0, 0}, 0);
        assertArrayEquals(new int[]{100, 100, 100}, current);
        int[] frozen = {100, 100, 100};
        ColorQuality.smooth(frozen, new int[]{10, 20, 30}, 100);
        assertArrayEquals(new int[]{10, 20, 30}, frozen);
        int[] half = {100, 100, 100};
        ColorQuality.smooth(half, new int[]{0, 0, 0}, 50);
        assertArrayEquals(new int[]{50, 50, 50}, half);
    }

    @Test
    public void offSettingsAreNeutral() {
        assertTrue(QualitySettings.OFF.isOff());
        assertFalse(new QualitySettings(true, false, 100, 0).isOff());
        assertFalse(new QualitySettings(false, true, 100, 0).isOff());
        assertFalse(new QualitySettings(false, false, 120, 0).isOff());
        assertFalse(new QualitySettings(false, false, 100, 20).isOff());
    }

    @Test
    public void hdrCompensationRaisesChromaAndKeepsNeutralGrayNeutral() {
        int[] gray = {100, 100, 100};
        ColorQuality.applyHdrCompensation(gray);
        assertEquals(gray[0], gray[1]);
        assertEquals(gray[1], gray[2]);

        int[] rgb = {200, 40, 90};
        int before = Math.max(rgb[0], Math.max(rgb[1], rgb[2]))
                - Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        ColorQuality.applyHdrCompensation(rgb);
        int after = Math.max(rgb[0], Math.max(rgb[1], rgb[2]))
                - Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        assertTrue("chroma must rise: " + before + " -> " + after, after > before);
        for (int channel : rgb) {
            assertTrue(channel >= 0 && channel <= 255);
        }
    }

    @Test
    public void hdrCompensationClampsExtremesAndIsANoOpForPureWhite() {
        int[] white = {255, 255, 255};
        ColorQuality.applyHdrCompensation(white);
        assertArrayEquals(new int[]{255, 255, 255}, white);

        int[] black = {0, 0, 0};
        ColorQuality.applyHdrCompensation(black);
        assertArrayEquals(new int[]{0, 0, 0}, black);

        int[] saturated = {255, 0, 0};
        ColorQuality.applyHdrCompensation(saturated);
        for (int channel : saturated) {
            assertTrue(channel >= 0 && channel <= 255);
        }
    }

    @Test
    public void hdrCompensationIsAnExplicitQualityFlag() {
        QualitySettings neutral = new QualitySettings(false, false, 100, 0);
        assertTrue(neutral.isOff());
        QualitySettings hdr = neutral.withHdrCompensation(true);
        assertTrue(hdr.hdrCompensation());
        assertFalse(hdr.isOff());
        assertFalse("original settings stay immutable", neutral.hdrCompensation());
        assertFalse(hdr.withHdrCompensation(false).hdrCompensation());
    }
}
