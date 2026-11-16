package com.abrenoch.wledgrabber.common.led;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RgbwConversionTest {

    @Test
    public void minSubtractAndPreserveVectorsMatchTheDesignTable() {
        assertEquals(0x000000ff, RgbwConverter.pack(255, 255, 255, WhiteMode.MIN_SUBTRACT));
        assertEquals(0xffffffff, RgbwConverter.pack(255, 255, 255, WhiteMode.PRESERVE));
        assertEquals(0xff000000, RgbwConverter.pack(255, 0, 0, WhiteMode.MIN_SUBTRACT));
        assertEquals(0xff000000, RgbwConverter.pack(255, 0, 0, WhiteMode.PRESERVE));
        assertEquals(0x0014321e, RgbwConverter.pack(30, 50, 80, WhiteMode.MIN_SUBTRACT));
        assertEquals(0x1e32501e, RgbwConverter.pack(30, 50, 80, WhiteMode.PRESERVE));
        assertEquals(0x00000080, RgbwConverter.pack(128, 128, 128, WhiteMode.MIN_SUBTRACT));
        assertEquals(0x80808080, RgbwConverter.pack(128, 128, 128, WhiteMode.PRESERVE));
        assertEquals(0x00010201, RgbwConverter.pack(1, 2, 3, WhiteMode.MIN_SUBTRACT));
        assertEquals(0x01020301, RgbwConverter.pack(1, 2, 3, WhiteMode.PRESERVE));
    }

    @Test
    public void byteOutputIsRgbwOrderWithoutGammaOrChannelSwap() {
        byte[] out = new byte[4];
        RgbwConverter.write(255, 255, 255, WhiteMode.MIN_SUBTRACT, out, 0);
        assertArrayEquals(new byte[]{0, 0, 0, (byte) 255}, out);

        RgbwConverter.write(30, 50, 80, WhiteMode.PRESERVE, out, 0);
        assertArrayEquals(new byte[]{30, 50, 80, 30}, out);
    }
}
