package com.abrenoch.wledgrabber.common.capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.view.Display;

import androidx.core.content.ContextCompat;

/** Shared Android power signals for output owners; a wake broadcast never starts output. */
public final class ScreenOffReceiver extends BroadcastReceiver {
    private final Runnable stop;

    public ScreenOffReceiver(Runnable stop) {
        this.stop = stop;
    }

    public void register(Context context) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        stop.run();
    }

    public static boolean isAwake(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        DisplayManager displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display screen = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (power == null || !power.isInteractive() || screen == null) {
            return false;
        }
        int state = screen.getState();
        return state == Display.STATE_ON || state == Display.STATE_VR;
    }
}
