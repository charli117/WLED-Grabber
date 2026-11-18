package com.abrenoch.wledgrabber.common.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SweepGradient;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

/** Animated background shared by the phone and TV capture screens. */
public class SweepGradientView extends View {

    private final Paint paint = new Paint();
    private final int[] colors = {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED};
    private final Rect viewBounds = new Rect();
    private final Matrix gradientMatrix = new Matrix();
    private SweepGradient gradient;
    private int rotationDegrees;

    public SweepGradientView(Context context) {
        this(context, null);
    }

    public SweepGradientView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SweepGradientView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        viewBounds.set(0, 0, width, height);
        gradient = new SweepGradient(viewBounds.exactCenterX(), viewBounds.exactCenterY(), colors, null);
        paint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (gradient == null) return;
        gradientMatrix.setRotate(rotationDegrees, viewBounds.exactCenterX(), viewBounds.exactCenterY());
        gradient.setLocalMatrix(gradientMatrix);
        canvas.drawRect(viewBounds, paint);
        rotationDegrees = (rotationDegrees + 1) % 360;
        postInvalidateOnAnimation();
    }
}
