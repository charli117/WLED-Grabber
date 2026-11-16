package com.abrenoch.wledgrabber.common.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;

/** Shared read-only preview of a CompiledLayout and its physical mapping. */
public final class LedLayoutPreviewView extends View {
    public interface OnIndexSelectedListener {
        void onIndexSelected(int physicalIndex);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path boundary = new Path();

    private CompiledLayout layout;
    private PreviewModel model = PreviewModel.error("layout unavailable");
    private OnIndexSelectedListener listener;
    private boolean showLabels = true;

    public LedLayoutPreviewView(Context context) {
        super(context);
        init();
    }

    public LedLayoutPreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LedLayoutPreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setLayout(CompiledLayout layout, int selectedPhysical) {
        this.layout = layout;
        this.model = PreviewModel.build(layout, selectedPhysical);
        invalidate();
    }

    public void setError(String message) {
        this.layout = null;
        this.model = PreviewModel.error(message);
        invalidate();
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
        invalidate();
    }

    public void setOnIndexSelectedListener(OnIndexSelectedListener listener) {
        this.listener = listener;
    }

    public int selectedPhysical() {
        return model.selectedPhysical();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (model.hasError()) {
            text.setTextSize(dp(16));
            canvas.drawText(model.error(), getWidth() / 2f, getHeight() / 2f, text);
            return;
        }
        float pad = dp(8);
        float width = Math.max(1, getWidth() - 2 * pad);
        float height = Math.max(1, getHeight() - 2 * pad);
        float left = pad;
        float top = pad;

        double[] c = model.corners();
        boundary.reset();
        boundary.moveTo(left + (float) c[0] * width, top + (float) c[1] * height);
        boundary.lineTo(left + (float) c[2] * width, top + (float) c[3] * height);
        boundary.lineTo(left + (float) c[4] * width, top + (float) c[5] * height);
        boundary.lineTo(left + (float) c[6] * width, top + (float) c[7] * height);
        boundary.close();
        stroke.setColor(Color.CYAN);
        stroke.setStrokeWidth(dp(2));
        canvas.drawPath(boundary, stroke);

        int labelStride = Math.max(1, model.physicalCount() / 32);
        for (PreviewModel.PreviewRect rect : model.rects()) {
            float l = left + (float) rect.hmin() * width;
            float r = left + (float) rect.hmax() * width;
            float t = top + (float) rect.vmin() * height;
            float b = top + (float) rect.vmax() * height;
            if (rect.black() || rect.physicalIndex() < 0) {
                fill.setColor(rect.selected() ? Color.rgb(90, 90, 0) : Color.DKGRAY);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawRect(l, t, r, b, fill);
            }
            if (rect.selected()) {
                fill.setColor(Color.argb(140, 255, 235, 59));
                fill.setStyle(Paint.Style.FILL);
                canvas.drawRect(l, t, r, b, fill);
                stroke.setColor(Color.YELLOW);
                stroke.setStrokeWidth(dp(2));
            } else {
                stroke.setColor(Color.WHITE);
                stroke.setStrokeWidth(dp(0.75f));
            }
            canvas.drawRect(l, t, r, b, stroke);

            if (showLabels && rect.physicalIndex() >= 0
                    && (rect.selected() || rect.physicalIndex() % labelStride == 0)) {
                text.setColor(rect.selected() ? Color.YELLOW : Color.WHITE);
                text.setTextSize(rect.selected() ? dp(16) : dp(10));
                canvas.drawText(Integer.toString(rect.physicalIndex()),
                        (l + r) / 2f, (t + b) / 2f + dp(3), text);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int selected = model.selectedPhysical();
        if (selected < 0) {
            return super.onKeyDown(keyCode, event);
        }
        int next = selected;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            next = Math.min(model.physicalCount() - 1, selected + 1);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            next = Math.max(0, selected - 1);
        } else {
            return super.onKeyDown(keyCode, event);
        }
        select(next);
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || model.hasError()) {
            return super.onTouchEvent(event);
        }
        float pad = dp(8);
        float width = Math.max(1, getWidth() - 2 * pad);
        float height = Math.max(1, getHeight() - 2 * pad);
        double h = (event.getX() - pad) / width;
        double v = (event.getY() - pad) / height;
        PreviewModel.PreviewRect best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PreviewModel.PreviewRect rect : model.rects()) {
            if (rect.physicalIndex() < 0) {
                continue;
            }
            double centerH = (rect.hmin() + rect.hmax()) / 2;
            double centerV = (rect.vmin() + rect.vmax()) / 2;
            double distance = (centerH - h) * (centerH - h) + (centerV - v) * (centerV - v);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = rect;
            }
        }
        if (best != null) {
            select(best.physicalIndex());
            performClick();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void select(int physicalIndex) {
        if (layout == null) {
            return;
        }
        model = PreviewModel.build(layout, physicalIndex);
        if (listener != null) {
            listener.onIndexSelected(model.selectedPhysical());
        }
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
