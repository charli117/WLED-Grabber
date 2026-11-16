package com.abrenoch.wledgrabber.common.fixtures;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class RgbaPlaneFixtureTest {

    @Test
    public void tightlyPackedPlaneHasExpectedGeometryAndPattern() {
        RgbaPlaneFixture plane = RgbaPlaneFixture.tightlyPacked(2, 2);

        assertEquals(2, plane.width());
        assertEquals(2, plane.height());
        assertEquals(4, plane.pixelStride());
        assertEquals(8, plane.rowStride());
        assertEquals(0, plane.offset());
        assertEquals(16, plane.limit());
        assertEquals(0, plane.cropLeft());
        assertEquals(0, plane.cropTop());
        assertEquals(2, plane.cropWidth());
        assertEquals(2, plane.cropHeight());

        assertEquals(0x000000ff, plane.pixelAt(0, 0));
        assertEquals(0x112b61ff, plane.pixelAt(1, 0));
        assertEquals(0x1f070dff, plane.pixelAt(0, 1));
        assertEquals(0x30326eff, plane.pixelAt(1, 1));

        ByteBuffer buffer = plane.buffer();
        assertEquals(0, buffer.position());
        assertEquals(16, buffer.limit());
    }

    @Test
    public void paddedPlaneHonorsOffsetRowStrideLimitAndCrop() {
        RgbaPlaneFixture plane = RgbaPlaneFixture.padded(2, 2, 6, 8);

        assertEquals(8, plane.offset());
        assertEquals(14, plane.rowStride());
        assertEquals(4, plane.pixelStride());
        assertEquals(30, plane.limit());
        assertEquals(0x1f070dff, plane.pixelAt(0, 1));
        assertEquals(0x30326eff, plane.pixelAt(1, 1));

        ByteBuffer buffer = plane.buffer();
        assertEquals(8, buffer.position());
        assertEquals(30, buffer.limit());

        RgbaPlaneFixture cropped = plane.withCrop(1, 0, 1, 2);
        assertEquals(1, cropped.cropLeft());
        assertEquals(0, cropped.cropTop());
        assertEquals(1, cropped.cropWidth());
        assertEquals(2, cropped.cropHeight());
        assertEquals(plane.pixelAt(1, 0), cropped.pixelAt(1, 0));
        assertNotEquals(plane.cropWidth(), cropped.cropWidth());
    }
}
