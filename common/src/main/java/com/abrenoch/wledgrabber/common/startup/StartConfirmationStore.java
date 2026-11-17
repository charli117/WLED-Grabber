package com.abrenoch.wledgrabber.common.startup;

import android.content.Context;
import android.content.SharedPreferences;

import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;

/**
 * Persists the user's WLED addressing confirmations per device identity. These
 * are user choices, not system grants; readiness still requires a fresh probe
 * whose device id matches the confirmation.
 */
public final class StartConfirmationStore {
    private static final String PREFS = "wled_start_confirmation";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_MANUAL_WHITE = "manual_white";
    private static final String KEY_OFFSET_ZERO = "offset_zero";
    private static final String KEY_MAIN_SEGMENT = "main_segment_only";
    private static final String KEY_DMX_START = "dmx_start_one";
    private static final String KEY_FINITE_TIMEOUT = "finite_timeout";
    private static final String KEY_MAX_BRIGHTNESS = "max_brightness_off";
    private static final String KEY_OTHER_SENDERS = "other_senders";
    private static final String KEY_SYNC = "sync_disabled";

    private final SharedPreferences prefs;

    public StartConfirmationStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** @return the stored confirmation, or null when the user has not confirmed one. */
    public StartConfirmation load() {
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return null;
        }
        return new StartConfirmation(deviceId,
                prefs.getBoolean(KEY_MANUAL_WHITE, false),
                prefs.getBoolean(KEY_OFFSET_ZERO, false),
                prefs.getBoolean(KEY_MAIN_SEGMENT, false),
                prefs.getBoolean(KEY_DMX_START, false),
                prefs.getBoolean(KEY_FINITE_TIMEOUT, false),
                prefs.getBoolean(KEY_MAX_BRIGHTNESS, false),
                prefs.getBoolean(KEY_OTHER_SENDERS, false),
                prefs.getBoolean(KEY_SYNC, false));
    }

    public boolean save(StartConfirmation confirmation) {
        if (confirmation == null || confirmation.confirmedDeviceId() == null
                || confirmation.confirmedDeviceId().trim().isEmpty()) {
            return false;
        }
        return prefs.edit()
                .putString(KEY_DEVICE_ID, confirmation.confirmedDeviceId())
                .putBoolean(KEY_MANUAL_WHITE, confirmation.manualWhiteConfirmed())
                .putBoolean(KEY_OFFSET_ZERO, confirmation.offsetZeroConfirmed())
                .putBoolean(KEY_MAIN_SEGMENT, confirmation.mainSegmentOnlyConfirmed())
                .putBoolean(KEY_DMX_START, confirmation.dmxStartOneConfirmed())
                .putBoolean(KEY_FINITE_TIMEOUT, confirmation.finiteTimeoutConfirmed())
                .putBoolean(KEY_MAX_BRIGHTNESS, confirmation.maxBrightnessOverrideOffConfirmed())
                .putBoolean(KEY_OTHER_SENDERS, confirmation.otherSendersCheckedConfirmed())
                .putBoolean(KEY_SYNC, confirmation.syncDisabledConfirmed())
                .commit();
    }

    public void clear() {
        prefs.edit().clear().commit();
    }
}
