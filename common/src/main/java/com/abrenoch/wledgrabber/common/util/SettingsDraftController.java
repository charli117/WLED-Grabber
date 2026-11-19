package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutDraft;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared draft/save/cancel controller used by mobile and TV settings hosts. */
public final class SettingsDraftController {
    private final ConfigRepository repository;
    private final ConfigValidator validator = new ConfigValidator();
    private ConfigDraft draft = new ConfigDraft();
    private Map<String, String> savedValues = draft.asMap();
    private ValidationResult<ConfigSnapshot> lastResult;

    public SettingsDraftController(ConfigRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository is required");
        }
        this.repository = repository;
    }

    public ValidationResult<ConfigSnapshot> load() {
        lastResult = repository.load();
        resetDraft();
        return lastResult;
    }

    private void resetDraft() {
        draft = repository.newDraft();
        savedValues = draft.asMap();
    }

    public boolean hasUnsavedChanges() {
        return !savedValues.equals(draft.asMap());
    }

    public ValidationResult<ConfigSnapshot> saveCalibration(int input, boolean reverse) {
        if (hasUnsavedChanges()) {
            return ValidationResult.fail(new FieldError(ConfigFields.STORAGE, FieldError.Code.CROSS_FIELD,
                    "Save or discard current edits before calibrating"));
        }
        lastResult = repository.saveCalibration(draft.baseRevision(), input, reverse);
        if (lastResult.isValid()) resetDraft();
        return lastResult;
    }

    public ConfigDraft draft() {
        return draft;
    }

    public void set(String field, String value) {
        draft.set(field, value);
    }

    public ValidationResult<ConfigSnapshot> validate() {
        lastResult = validator.validateForSave(draft);
        return lastResult;
    }

    /** Layout-only validation for the live editor; device fields are not required. */
    public ValidationResult<LayoutDraft> validateLayout() {
        return validator.validateLayout(draft);
    }

    /**
     * Applies a D-pad step to one numeric layout field. The candidate value goes
     * through {@link ConfigValidator#validateLayout(ConfigDraft)}, so the UI never
     * owns a second range table; rejected values restore the previous text.
     *
     * @return true when the adjusted draft is valid.
     */
    public boolean adjustLayoutField(String field, int delta) {
        if (field == null || delta == 0) {
            return false;
        }
        String previous = draft.get(field);
        int current;
        try {
            current = previous == null || previous.trim().isEmpty()
                    ? 0 : Integer.parseInt(previous.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long next = (long) current + delta;
        if (next < Integer.MIN_VALUE || next > Integer.MAX_VALUE) {
            return false;
        }
        draft.set(field, Long.toString(next));
        if (!validator.validateLayout(draft).isValid()) {
            draft.set(field, previous);
            return false;
        }
        return true;
    }

    /**
     * Sets an exact layout value (typed input or a choice row). Invalid input is
     * rejected atomically and the previous value is restored, matching the stepper.
     */
    public ValidationResult<LayoutDraft> setLayoutField(String field, String value) {
        if (field == null) {
            return ValidationResult.fail(new FieldError(ConfigFields.SCHEMA_VERSION,
                    FieldError.Code.CONFIG_MISSING, "field is null"));
        }
        String previous = draft.get(field);
        draft.set(field, value);
        ValidationResult<LayoutDraft> result = validator.validateLayout(draft);
        if (!result.isValid()) {
            draft.set(field, previous);
        }
        return result;
    }

    /**
     * Applies a group of layout values (presets, auto distribution) atomically:
     * either every candidate value is accepted or the previous draft is restored.
     */
    public ValidationResult<LayoutDraft> setLayoutFields(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return validateLayout();
        }
        Map<String, String> previous = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            previous.put(entry.getKey(), draft.get(entry.getKey()));
            draft.set(entry.getKey(), entry.getValue());
        }
        ValidationResult<LayoutDraft> result = validator.validateLayout(draft);
        if (!result.isValid()) {
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                draft.set(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** Saves the whole draft atomically; invalid drafts never touch storage. */
    public ValidationResult<ConfigSnapshot> save() {
        lastResult = repository.save(draft);
        if (lastResult.isValid()) {
            resetDraft();
        }
        return lastResult;
    }

    public void cancel() {
        resetDraft();
        lastResult = null;
    }

    public ValidationResult<ConfigSnapshot> lastResult() {
        return lastResult;
    }

    public ConfigSnapshot committedSnapshot() {
        return repository.committedSnapshot();
    }
}
