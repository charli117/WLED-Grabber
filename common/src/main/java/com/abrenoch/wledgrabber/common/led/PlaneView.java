package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.nio.ByteBuffer;

/**
 * Validated RGBA plane view independent of Android Image. Uses buffer position,
 * limit, row stride, pixel stride and cropRect without copying the frame.
 */
public final class PlaneView {
    public static final int MAX_DIMENSION = 640;

    private final ByteBuffer buffer;
    private final int width;
    private final int height;
    private final int rowStride;
    private final int pixelStride;
    private final int offset;
    private final int limit;
    private final int cropLeft;
    private final int cropTop;
    private final int cropWidth;
    private final int cropHeight;
    private ByteBuffer reader;
    private byte[] rowBytes;

    private PlaneView(ByteBuffer buffer, int width, int height, int rowStride, int pixelStride,
                      int offset, int limit, int cropLeft, int cropTop,
                      int cropWidth, int cropHeight) {
        this.buffer = buffer;
        this.width = width;
        this.height = height;
        this.rowStride = rowStride;
        this.pixelStride = pixelStride;
        this.offset = offset;
        this.limit = limit;
        this.cropLeft = cropLeft;
        this.cropTop = cropTop;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
    }

    public static ValidationResult<PlaneView> create(ByteBuffer buffer, int width, int height,
                                                     int rowStride, int pixelStride,
                                                     int cropLeft, int cropTop,
                                                     int cropWidth, int cropHeight) {
        if (buffer == null) {
            return fail("buffer is null");
        }
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return fail("width/height must be 1.." + MAX_DIMENSION);
        }
        if (pixelStride < 4 || rowStride <= 0) {
            return fail("pixelStride must be >=4 and rowStride positive");
        }
        long minimumRow = (long) width * pixelStride;
        if (rowStride < minimumRow) {
            return fail("rowStride is smaller than width*pixelStride");
        }
        if (cropWidth <= 0 || cropHeight <= 0 || cropLeft < 0 || cropTop < 0
                || (long) cropLeft + cropWidth > width
                || (long) cropTop + cropHeight > height) {
            return fail("crop rectangle must stay inside the image");
        }
        int offset = buffer.position();
        int limit = buffer.limit();
        if (offset < 0 || limit > buffer.capacity() || offset >= limit) {
            return fail("buffer position/limit is invalid");
        }
        long lastByte = (long) offset
                + (long) (cropTop + cropHeight - 1) * rowStride
                + (long) (cropLeft + cropWidth - 1) * pixelStride
                + 3;
        if (lastByte >= limit) {
            return fail("buffer limit does not cover the last RGBA byte");
        }
        return ValidationResult.ok(new PlaneView(buffer, width, height, rowStride, pixelStride,
                offset, limit, cropLeft, cropTop, cropWidth, cropHeight));
    }

    /**
     * Copies one crop row into {@code rgb} as consecutive R,G,B bytes. Reading a
     * whole row per JNI crossing keeps the per-frame buffer access bounded; the
     * old per-channel {@code read()} made one JNI call per pixel channel.
     */
    void readRow(int y, byte[] rgb) {
        int rowStart = offset + (cropTop + y) * rowStride + cropLeft * pixelStride;
        int span = (cropWidth - 1) * pixelStride + 4;
        if (rowBytes == null || rowBytes.length < span) {
            rowBytes = new byte[span];
        }
        ByteBuffer source = reader;
        if (source == null) {
            source = buffer.duplicate();
            reader = source;
        }
        // The duplicate keeps the validated limit; only the position moves per row.
        source.position(rowStart);
        source.get(rowBytes, 0, span);
        int out = 0;
        for (int x = 0; x < cropWidth; x++) {
            int in = x * pixelStride;
            rgb[out++] = rowBytes[in];
            rgb[out++] = rowBytes[in + 1];
            rgb[out++] = rowBytes[in + 2];
        }
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
        return pixelStride;
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

    private static ValidationResult<PlaneView> fail(String detail) {
        return ValidationResult.fail(new FieldError(
                com.abrenoch.wledgrabber.common.led.ConfigFields.PLANE,
                FieldError.Code.OUT_OF_RANGE, detail));
    }
}
