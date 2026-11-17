package com.abrenoch.wledgrabber.common.startup;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.WledScreenService;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;

/** Android adapter. Only user choices are stored; all system grants are read afresh. */
public final class StartupAccess {
    private final Context context;
    private final Preferences preferences;
    private final SharedPreferences setup;

    public StartupAccess(Context context) {
        this.context = context;
        preferences = new Preferences(context);
        setup = context.getSharedPreferences("startup_setup", Context.MODE_PRIVATE);
    }

    /**
     * Reads the same typed snapshot the config system writes. The legacy
     * {@code Preferences.getBoolean(pref_key_boot)} path would throw
     * ClassCastException because the key is stored as a String.
     */
    public boolean bootEnabled() {
        ConfigRepository repository = repository();
        if (!repository.load().isValid()) {
            return false;
        }
        com.abrenoch.wledgrabber.common.led.ConfigSnapshot snapshot =
                repository.committedSnapshot();
        return snapshot != null && snapshot.capture().bootReminder();
    }
    public boolean hasCompletedSetup() { return setup.getBoolean("completed_v1", false); }

    public boolean saveBootEnabled(boolean enabled) {
        ConfigRepository repository = repository();
        return repository.load().isValid() && repository.save(repository.newDraft()
                .set(ConfigFields.BOOT, Boolean.toString(enabled))).isValid();
    }

    public boolean completeSetup(boolean enabled) {
        if (enabled && !status().automaticCaptureAvailable()) return false;
        return saveBootEnabled(enabled) && setup.edit().putBoolean("completed_v1", true).commit();
    }

    public boolean configurationReady() {
        ConfigRepository repository = repository();
        return repository.load().isValid() && repository.validateForStart().isValid();
    }

    private ConfigRepository repository() {
        return new ConfigRepository(new PreferencesConfigStorage(preferences), new ConfigValidator());
    }

    public StartupPolicy status() {
        boolean projection = Build.VERSION.SDK_INT == 29 && allowedOp("android:project_media");
        boolean launch = false;
        boolean battery = Build.VERSION.SDK_INT < 23;
        try {
            DevicePolicyManager policy = (DevicePolicyManager)
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            launch = Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(context);
            launch |= policy != null && policy.isDeviceOwnerApp(context.getPackageName());
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                battery = power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        } catch (RuntimeException unavailable) {
            // A missing vendor API/capability is not a grant.
        }
        boolean background = (Build.VERSION.SDK_INT < 26 || allowedOp("android:run_in_background"))
                && (Build.VERSION.SDK_INT < 28 || allowedOp("android:run_any_in_background"));
        return new StartupPolicy(Build.VERSION.SDK_INT, projection, launch, background, battery);
    }

    @SuppressWarnings("deprecation")
    private boolean allowedOp(String operation) {
        try {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            return ops != null && ops.checkOpNoThrow(operation, Process.myUid(), context.getPackageName())
                    == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException unsupportedOrDenied) {
            // PROJECT_MEDIA is not a public cross-version identifier. In particular API21 rejects it.
            return false;
        }
    }

    public boolean captureServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WledScreenService.class.getName().equals(service.service.getClassName())) return true;
        }
        return false;
    }

    /** Bound automatic launch to one attempt per actual boot, including process recreation. */
    public boolean claimBootAttempt() {
        synchronized (StartupAccess.class) {
            int boot = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
            if (boot < 0 || setup.getInt("attempted_boot", -1) == boot) return false;
            return setup.edit().putInt("attempted_boot", boot).commit();
        }
    }

    /** Administrator commands are shown/copied, never executed by the application. */
    public static String commands(Context context) {
        String prefix = "adb shell cmd appops set --user " + (Process.myUid() / 100000)
                + " " + context.getPackageName() + " ";
        return prefix + "PROJECT_MEDIA allow\n"
                + prefix + "RUN_IN_BACKGROUND allow\n"
                + prefix + "RUN_ANY_IN_BACKGROUND allow\n"
                + prefix + "SYSTEM_ALERT_WINDOW allow\n"
                + "adb shell dumpsys deviceidle whitelist +" + context.getPackageName();
    }
}
