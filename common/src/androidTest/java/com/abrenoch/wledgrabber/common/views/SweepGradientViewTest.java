package com.abrenoch.wledgrabber.common.views;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class SweepGradientViewTest {
    private static class RecordingCanvas extends Canvas {
        Shader drawn;

        @Override public void drawRect(Rect bounds, Paint paint) {
            drawn = paint.getShader();
        }
    }

    @Test public void repeatedMeasureKeepsShaderUntilBoundsChange() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SweepGradientView view = new SweepGradientView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            RecordingCanvas canvas = new RecordingCanvas();
            int spec = View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY);
            view.measure(spec, spec);
            view.layout(0, 0, 80, 80);
            view.draw(canvas);
            Shader first = canvas.drawn;
            assertNotNull(first);
            canvas.drawn = null;
            view.forceLayout();
            view.measure(spec, spec);
            view.layout(0, 0, 80, 80);
            view.draw(canvas);
            assertSame(first, canvas.drawn);
            view.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY), spec);
            view.layout(0, 0, 120, 80);
            view.draw(canvas);
            assertNotNull(canvas.drawn);
            assertNotSame(first, canvas.drawn);
        });
    }
}
