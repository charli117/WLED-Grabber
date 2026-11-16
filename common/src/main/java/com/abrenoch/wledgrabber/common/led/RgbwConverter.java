package com.abrenoch.wledgrabber.common.led;

/** Single RGBW helper used by sampled frames and test patterns. */
public final class RgbwConverter {
    private RgbwConverter() {
    }

    /** Packs 0xRRGGBBWW. */
    public static int pack(int red, int green, int blue, WhiteMode mode) {
        int white = Math.min(red, Math.min(green, blue));
        if (mode == WhiteMode.MIN_SUBTRACT) {
            return ((red - white) << 24)
                    | ((green - white) << 16)
                    | ((blue - white) << 8)
                    | white;
        }
        return (red << 24) | (green << 16) | (blue << 8) | white;
    }

    /** Writes RGBW bytes at offset; caller provides at least 4 bytes. */
    public static void write(int red, int green, int blue, WhiteMode mode,
                             byte[] out, int offset) {
        int packed = pack(red, green, blue, mode);
        out[offset] = (byte) ((packed >>> 24) & 0xff);
        out[offset + 1] = (byte) ((packed >>> 16) & 0xff);
        out[offset + 2] = (byte) ((packed >>> 8) & 0xff);
        out[offset + 3] = (byte) (packed & 0xff);
    }
}
