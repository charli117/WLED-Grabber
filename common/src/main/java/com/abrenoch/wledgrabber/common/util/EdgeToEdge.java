package com.abrenoch.wledgrabber.common.util;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Applies system-bar/IME insets for targetSdk 35 edge-to-edge activities. */
public final class EdgeToEdge {
    private EdgeToEdge() {
    }

    public static void apply(Activity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }
}
