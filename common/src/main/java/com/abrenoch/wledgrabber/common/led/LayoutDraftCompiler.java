package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/**
 * Single draft-to-preview seam for the editor and probe actions. Geometry itself
 * stays in {@link ClassicLedLayout}; this class only supplies the validated draft.
 */
public final class LayoutDraftCompiler {
    private LayoutDraftCompiler() {
    }

    public static ValidationResult<CompiledLayout> compileForPreview(
            ConfigDraft draft, int captureWidth, int captureHeight) {
        ValidationResult<LayoutDraft> validated = new ConfigValidator().validateLayout(draft);
        if (!validated.isValid()) {
            return ValidationResult.fail(validated.errors());
        }
        LayoutDraft layout = validated.value();
        return new ClassicLedLayout().compile(layout.layout(),
                layout.previewPhysicalLedCount(), captureWidth, captureHeight);
    }
}
