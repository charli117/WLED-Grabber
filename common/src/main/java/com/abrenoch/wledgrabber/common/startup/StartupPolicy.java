package com.abrenoch.wledgrabber.common.startup;

/** Shared, fail-closed boot decision. Grants are observations, never persisted as authority. */
public final class StartupPolicy {
    public enum Action { DISABLED, REMIND, START_CAPTURE }

    private final int sdk;
    private final boolean projection;
    private final boolean backgroundLaunch;
    private final boolean background;
    private final boolean battery;

    public StartupPolicy(int sdk, boolean projection, boolean backgroundLaunch,
                         boolean background, boolean battery) {
        this.sdk = sdk;
        this.projection = projection;
        this.backgroundLaunch = backgroundLaunch;
        this.background = background;
        this.battery = battery;
    }

    // Only the Android 10 automatic path has been audited. Other versions keep manual capture.
    public boolean platformSupported() { return sdk == 29; }
    public boolean persistentProjection() { return projection; }
    public boolean backgroundLaunchAllowed() { return backgroundLaunch; }
    public boolean backgroundAllowed() { return background; }
    public boolean batteryExempt() { return battery; }

    public boolean automaticCaptureAvailable() {
        return platformSupported() && projection && backgroundLaunch && background && battery;
    }

    public Action onBoot(boolean enabled, boolean configurationReady) {
        if (!enabled) return Action.DISABLED;
        return configurationReady && automaticCaptureAvailable()
                ? Action.START_CAPTURE : Action.REMIND;
    }
}
