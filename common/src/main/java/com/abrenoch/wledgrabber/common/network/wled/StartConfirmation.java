package com.abrenoch.wledgrabber.common.network.wled;

/** Explicit user confirmations required before READY. */
public final class StartConfirmation {
    private final String confirmedDeviceId;
    private final boolean manualWhiteConfirmed;
    private final boolean offsetZeroConfirmed;
    private final boolean mainSegmentOnlyConfirmed;
    private final boolean dmxStartOneConfirmed;
    private final boolean finiteTimeoutConfirmed;
    private final boolean maxBrightnessOverrideOffConfirmed;
    private final boolean otherSendersCheckedConfirmed;
    private final boolean syncDisabledConfirmed;

    public StartConfirmation(String confirmedDeviceId, boolean manualWhiteConfirmed,
                             boolean offsetZeroConfirmed, boolean mainSegmentOnlyConfirmed,
                             boolean dmxStartOneConfirmed, boolean finiteTimeoutConfirmed,
                             boolean maxBrightnessOverrideOffConfirmed,
                             boolean otherSendersCheckedConfirmed,
                             boolean syncDisabledConfirmed) {
        this.confirmedDeviceId = confirmedDeviceId;
        this.manualWhiteConfirmed = manualWhiteConfirmed;
        this.offsetZeroConfirmed = offsetZeroConfirmed;
        this.mainSegmentOnlyConfirmed = mainSegmentOnlyConfirmed;
        this.dmxStartOneConfirmed = dmxStartOneConfirmed;
        this.finiteTimeoutConfirmed = finiteTimeoutConfirmed;
        this.maxBrightnessOverrideOffConfirmed = maxBrightnessOverrideOffConfirmed;
        this.otherSendersCheckedConfirmed = otherSendersCheckedConfirmed;
        this.syncDisabledConfirmed = syncDisabledConfirmed;
    }

    public String confirmedDeviceId() {
        return confirmedDeviceId;
    }

    public boolean manualWhiteConfirmed() {
        return manualWhiteConfirmed;
    }

    public boolean offsetZeroConfirmed() {
        return offsetZeroConfirmed;
    }

    public boolean mainSegmentOnlyConfirmed() {
        return mainSegmentOnlyConfirmed;
    }

    public boolean dmxStartOneConfirmed() {
        return dmxStartOneConfirmed;
    }

    public boolean finiteTimeoutConfirmed() {
        return finiteTimeoutConfirmed;
    }

    public boolean maxBrightnessOverrideOffConfirmed() {
        return maxBrightnessOverrideOffConfirmed;
    }

    public boolean otherSendersCheckedConfirmed() {
        return otherSendersCheckedConfirmed;
    }

    public boolean syncDisabledConfirmed() {
        return syncDisabledConfirmed;
    }

    public boolean allRequired(boolean rgbw) {
        return (!rgbw || manualWhiteConfirmed)
                && offsetZeroConfirmed
                && mainSegmentOnlyConfirmed
                && dmxStartOneConfirmed
                && finiteTimeoutConfirmed
                && maxBrightnessOverrideOffConfirmed
                && otherSendersCheckedConfirmed
                && syncDisabledConfirmed;
    }
}
