package com.abrenoch.wledgrabber.common.fixtures;

import java.nio.ByteBuffer;

/**
 * Synthetic RGBA plane shared by sampling/capture tests.
 *
 * <p>The fixture uses the same geometry concepts as an Android Image.Plane:
 * a byte buffer offset, row stride, pixel stride, limit and crop rectangle.
 * Pixels are filled with a deterministic pattern so tests can verify
 * exact address arithmetic without copying a real screen frame.</p>
 */
public final class RgbaPlaneFixture {
    public static final int PIXEL_STRIDE = 4;

    private final byte[] data;
    private final int width;
    private final int height;
    private final int rowStride;
    private final int offset;
    private final int limit;
    private final int cropLeft;
    private final int cropTop;
    private final int cropWidth;
    private final int cropHeight;

    private RgbaPlaneFixture(byte[] data, int width, int height, int rowStride, int offset,
                             int limit, int cropLeft, int cropTop, int cropWidth, int cropHeight) {
        this.data = data;
        this.width = width;
        this.height = height;
        this.rowStride = rowStride;
        this.offset = offset;
        this.limit = limit;
        this.cropLeft = cropLeft;
        this.cropTop = cropTop;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
    }

    public static RgbaPlaneFixture tightlyPacked(int width, int height) {
        return padded(width, height, 0, 0);
    }

    public static RgbaPlaneFixture padded(int width, int height, int rowPadding, int offset) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive");
        }
        if (rowPadding < 0 || offset < 0) {
            throw new IllegalArgumentException("rowPadding and offset must be non-negative");
        }
        int rowStride = width * PIXEL_STRIDE + rowPadding;
        byte[] data = new byte[offset + rowStride * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = offset + y * rowStride + x * PIXEL_STRIDE;
                data[index] = (byte) (x * 17 + y * 31);
                data[index + 1] = (byte) (x * 43 + y * 7);
                data[index + 2] = (byte) (x * 97 + y * 13);
                data[index + 3] = (byte) 0xff;
            }
        }
        int limit = offset + rowStride * (height - 1) + width * PIXEL_STRIDE;
        return new RgbaPlaneFixture(data, width, height, rowStride, offset, limit,
                0, 0, width, height);
    }

    public RgbaPlaneFixture withCrop(int left, int top, int width, int height) {
        if (left < 0 || top < 0 || width <= 0 || height <= 0
                || left + width > this.width || top + height > this.height) {
            throw new IllegalArgumentException("crop must be inside the plane");
        }
        return new RgbaPlaneFixture(data, this.width, this.height, rowStride, offset, limit,
                left, top, width, height);
    }

    public ByteBuffer buffer() {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(offset);
        buffer.limit(limit);
        return buffer;
    }

    /** Returns RGBA packed as 0xRRGGBBAA. */
    public int pixelAt(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("pixel outside plane");
        }
        int index = offset + y * rowStride + x * PIXEL_STRIDE;
        return ((data[index] & 0xff) << 24)
                | ((data[index + 1] & 0xff) << 16)
                | ((data[index + 2] & 0xff) << 8)
                | (data[index + 3] & 0xff);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int rowStride() {
        return rowStride;
    }

    public int pixelStride() {
        return PIXEL_STRIDE;
    }

    public int offset() {
        return offset;
    }

    public int limit() {
        return limit;
    }

    public int cropLeft() {
        return cropLeft;
    }

    public int cropTop() {
        return cropTop;
    }

    public int cropWidth() {
        return cropWidth;
    }

    public int cropHeight() {
        return cropHeight;
    }
}
