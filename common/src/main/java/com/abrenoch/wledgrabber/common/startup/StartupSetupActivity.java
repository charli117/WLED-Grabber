package com.abrenoch.wledgrabber.common.startup;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.abrenoch.wledgrabber.common.R;

/** Setup only: observes access and saves a boot preference; never starts capture. */
public final class StartupSetupActivity extends AppCompatActivity {
    private StartupAccess access;
    private SwitchCompat bootEnabled;
    private Button continueButton;
    private TextView error;

    /** Returns true only when the first-run page was opened. */
    public static boolean showIfNeeded(Activity activity) {
        if (new StartupAccess(activity).hasCompletedSetup()) return false;
        open(activity);
        return true;
    }

    /** Explicit entry point for revisiting setup from either mobile or TV settings. */
    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, StartupSetupActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup_setup);
        setTitle(R.string.startup_title);
        access = new StartupAccess(this);
        bootEnabled = findViewById(R.id.startup_boot_enabled);
        continueButton = findViewById(R.id.startup_continue);
        error = findViewById(R.id.startup_error);

        // Edits stay on this page until completion. Back must not persist a draft.
        bootEnabled.setChecked(access.bootEnabled());
        bootEnabled.setOnCheckedChangeListener((button, checked) -> {
            error.setVisibility(View.GONE);
            refreshStatus();
        });
        ((TextView) findViewById(R.id.startup_commands)).setText(StartupAccess.commands(this));
        findViewById(R.id.startup_copy_commands).setOnClickListener(view -> copyCommands());
        findViewById(R.id.startup_battery_settings).setOnClickListener(view ->
                openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        findViewById(R.id.startup_overlay_settings).setOnClickListener(view ->
                openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Settings.ACTION_SETTINGS));
        continueButton.setOnClickListener(view -> complete(bootEnabled.isChecked()));
        findViewById(R.id.startup_manual_only).setOnClickListener(view -> complete(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning from Settings or ADB never treats the previously displayed grants as authority.
        refreshStatus();
    }

    private void refreshStatus() {
        StartupPolicy status = access.status();
        TextView platform = findViewById(R.id.startup_platform_status);
        platform.setText(getString(status.platformSupported()
                ? R.string.startup_platform_supported : R.string.startup_platform_unsupported,
                Build.VERSION.SDK_INT));
        showCapability(R.id.startup_projection_status, R.string.startup_projection,
                status.persistentProjection());
        showCapability(R.id.startup_background_launch_status, R.string.startup_background_launch,
                status.backgroundLaunchAllowed());
        showCapability(R.id.startup_background_status, R.string.startup_background,
                status.backgroundAllowed());
        showCapability(R.id.startup_battery_status, R.string.startup_battery,
                status.batteryExempt());
        findViewById(R.id.startup_battery_settings).setEnabled(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !status.batteryExempt());
        findViewById(R.id.startup_overlay_settings).setEnabled(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !status.backgroundLaunchAllowed());
        boolean allowed = status.automaticCaptureAvailable();
        continueButton.setEnabled(!bootEnabled.isChecked() || allowed);
        ((TextView) findViewById(R.id.startup_completion_hint)).setText(
                !bootEnabled.isChecked() ? R.string.startup_manual_hint
                        : allowed ? R.string.startup_access_present : R.string.startup_access_missing);
    }

    private void showCapability(int viewId, int labelId, boolean granted) {
        ((TextView) findViewById(viewId)).setText(getString(R.string.startup_capability_status,
                getString(labelId), getString(granted
                        ? R.string.startup_granted : R.string.startup_not_granted)));
    }

    private void complete(boolean enabled) {
        // Recheck on click too: access can change while this page remains visible.
        if (enabled && !access.status().automaticCaptureAvailable()) {
            refreshStatus();
            showError(R.string.startup_access_missing);
            return;
        }
        if (!access.completeSetup(enabled)) {
            showError(R.string.startup_save_failed);
            return;
        }
        finish();
    }

    private void openSettings(String action, String fallbackAction) {
        error.setVisibility(View.GONE);
        try {
            startActivity(new Intent(action, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            try {
                startActivity(new Intent(fallbackAction));
                Toast.makeText(this, R.string.startup_settings_fallback, Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException | SecurityException noSettings) {
                showError(R.string.startup_settings_unavailable);
            }
        }
    }

    private void copyCommands() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            showError(R.string.startup_clipboard_unavailable);
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.startup_adb_title),
                StartupAccess.commands(this)));
        Toast.makeText(this, R.string.startup_commands_copied, Toast.LENGTH_SHORT).show();
    }

    private void showError(int messageId) {
        error.setText(messageId);
        error.setVisibility(View.VISIBLE);
    }
}
