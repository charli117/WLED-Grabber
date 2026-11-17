package com.abrenoch.wledgrabber.common.led;

/**
 * Layout-scoped validated draft: geometry plus the user's physical LED observation.
 *
 * <p>{@code physicalLedCount} is 0 while the count is still unconfirmed. The preview
 * compiler treats that as "use the effective layout ring" so geometry stays editable
 * before the device endpoint exists.</p>
 */
public final class LayoutDraft {
    private final LedLayoutConfig layout;
    private final int physicalLedCount;

    public LayoutDraft(LedLayoutConfig layout, int physicalLedCount) {
        if (layout == null) {
            throw new IllegalArgumentException("layout is required");
        }
        if (physicalLedCount < 0) {
            throw new IllegalArgumentException("physical LED count must not be negative");
        }
        this.layout = layout;
        this.physicalLedCount = physicalLedCount;
    }

    public LedLayoutConfig layout() {
        return layout;
    }

    public int physicalLedCount() {
        return physicalLedCount;
    }

    public boolean physicalConfirmed() {
        return physicalLedCount > 0;
    }

    public int effectiveLedCount() {
        return layout.effectiveLedCount();
    }

    /**
     * Physical count used for a visual preview. A count below the effective ring
     * still renders the full geometry so the editor can show the mismatch instead
     * of replacing the preview with an error.
     */
    public int previewPhysicalLedCount() {
        int effective = Math.max(1, layout.effectiveLedCount());
        return physicalLedCount > 0 ? Math.max(physicalLedCount, effective) : effective;
    }
}
