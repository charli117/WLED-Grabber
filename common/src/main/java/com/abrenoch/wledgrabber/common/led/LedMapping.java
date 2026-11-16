package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.Arrays;

/**
 * Physical-to-logical mapping generated once and shared by sampling, average mode,
 * preview and test patterns. Tail LEDs beyond K are always black.
 */
public final class LedMapping {
    private final int[] physicalToLogical;
    private final int[] logicalToPhysical;
    private final boolean[] physicalBlack;
    private final boolean[] logicalBlack;
    private final int effectiveLedCount;
    private final int physicalLedCount;
    private final int gapStart;
    private final int gapLength;
    private final GapMode gapMode;

    private LedMapping(int[] physicalToLogical, int[] logicalToPhysical,
                       boolean[] physicalBlack, boolean[] logicalBlack,
                       int effectiveLedCount, int physicalLedCount,
                       int gapStart, int gapLength, GapMode gapMode) {
        this.physicalToLogical = physicalToLogical;
        this.logicalToPhysical = logicalToPhysical;
        this.physicalBlack = physicalBlack;
        this.logicalBlack = logicalBlack;
        this.effectiveLedCount = effectiveLedCount;
        this.physicalLedCount = physicalLedCount;
        this.gapStart = gapStart;
        this.gapLength = gapLength;
        this.gapMode = gapMode;
    }

    public static ValidationResult<LedMapping> compile(LedLayoutConfig config,
                                                       int physicalLedCount) {
        if (config == null) {
            return fail("layout config missing");
        }
        int total = config.totalLedCount();
        if (total <= 0) {
            return fail("total logical LED count must be positive");
        }
        if (physicalLedCount < 1 || physicalLedCount > 65536) {
            return fail("physical LED count must be 1..65536");
        }
        int gapStart = config.gapPosition();
        int gapLength = config.gapLength();
        if (gapStart < 0 || gapLength < 0 || (long) gapStart + gapLength > total) {
            return fail("gap must stay inside the logical ring");
        }
        GapMode gapMode = config.gapMode() == null ? GapMode.SKIP : config.gapMode();
        if (gapMode == GapMode.SKIP && gapLength >= total) {
            return fail("SKIP gap cannot remove every LED");
        }
        int effective = gapMode == GapMode.SKIP ? total - gapLength : total;
        if (effective <= 0) {
            return fail("effective LED count must be positive");
        }
        if (physicalLedCount < effective) {
            return fail("physical LED count is below effective layout count");
        }

        int[] selected = new int[effective];
        int cursor = 0;
        for (int logical = 0; logical < total; logical++) {
            boolean inGap = logical >= gapStart && logical < gapStart + gapLength;
            if (gapMode == GapMode.SKIP && inGap) {
                continue;
            }
            selected[cursor++] = logical;
        }

        int[] physicalToLogical = new int[physicalLedCount];
        Arrays.fill(physicalToLogical, -1);
        boolean[] physicalBlack = new boolean[physicalLedCount];
        int[] logicalToPhysical = new int[total];
        Arrays.fill(logicalToPhysical, -1);
        boolean[] logicalBlack = new boolean[total];

        for (int logical = gapStart; logical < gapStart + gapLength; logical++) {
            logicalBlack[logical] = true;
        }

        int q = Math.floorMod(config.inputPosition(), effective);
        for (int physical = 0; physical < physicalLedCount; physical++) {
            if (physical >= effective) {
                physicalBlack[physical] = true;
                continue;
            }
            int selectedIndex = config.reverse()
                    ? Math.floorMod(q - 1 - physical, effective)
                    : Math.floorMod(physical + q, effective);
            int logical = selected[selectedIndex];
            physicalToLogical[physical] = logical;
            logicalToPhysical[logical] = physical;
            if (gapMode == GapMode.BLACK
                    && logical >= gapStart && logical < gapStart + gapLength) {
                physicalBlack[physical] = true;
            }
        }

        return ValidationResult.ok(new LedMapping(physicalToLogical, logicalToPhysical,
                physicalBlack, logicalBlack, effective, physicalLedCount,
                gapStart, gapLength, gapMode));
    }

    public int physicalToLogical(int physicalIndex) {
        return physicalToLogical[physicalIndex];
    }

    public int logicalToPhysical(int logicalIndex) {
        return logicalToPhysical[logicalIndex];
    }

    public boolean isPhysicalBlack(int physicalIndex) {
        return physicalBlack[physicalIndex];
    }

    public boolean isLogicalBlack(int logicalIndex) {
        return logicalBlack[logicalIndex];
    }

    public int[] physicalToLogical() {
        return physicalToLogical.clone();
    }

    public int[] logicalToPhysical() {
        return logicalToPhysical.clone();
    }

    public boolean[] physicalBlack() {
        return physicalBlack.clone();
    }

    public boolean[] logicalBlack() {
        return logicalBlack.clone();
    }

    public int effectiveLedCount() {
        return effectiveLedCount;
    }

    public int physicalLedCount() {
        return physicalLedCount;
    }

    public int gapStart() {
        return gapStart;
    }

    public int gapLength() {
        return gapLength;
    }

    public GapMode gapMode() {
        return gapMode;
    }

    private static ValidationResult<LedMapping> fail(String detail) {
        return ValidationResult.fail(new FieldError(
                com.abrenoch.wledgrabber.common.led.ConfigFields.LAYOUT_GEOMETRY,
                FieldError.Code.CROSS_FIELD, detail));
    }
}
