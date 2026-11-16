package com.abrenoch.wledgrabber.common.capture;

import android.media.Image;

import com.abrenoch.wledgrabber.common.led.PlaneView;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.nio.ByteBuffer;

/**
 * Android Image to validated PlaneView. The Image must remain open while the
 * PlaneView is used; the capture thread closes it in a finally block.
 */
public final class ImagePlaneView {
    private ImagePlaneView() {
    }

    public static ValidationResult<PlaneView> from(Image image) {
        if (image == null || image.getPlanes().length == 0) {
            return ValidationResult.fail(new com.abrenoch.wledgrabber.common.util.FieldError(
                    com.abrenoch.wledgrabber.common.led.ConfigFields.PLANE,
                    com.abrenoch.wledgrabber.common.util.FieldError.Code.EMPTY,
                    "image/plane missing"));
        }
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        android.graphics.Rect crop = image.getCropRect();
        return PlaneView.create(buffer, image.getWidth(), image.getHeight(),
                plane.getRowStride(), plane.getPixelStride(),
                crop.left, crop.top, crop.width(), crop.height());
    }
}
