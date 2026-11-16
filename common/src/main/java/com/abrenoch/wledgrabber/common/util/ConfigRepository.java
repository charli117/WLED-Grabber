package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.ValidatedConfig;

/**
 * Sole composition entry for configuration persistence.
 *
 * <p>Only a successful atomic write publishes a new revision. A running session keeps
 * the snapshot it captured at start; saving never mutates an already borrowed snapshot.</p>
 */
public final class ConfigRepository {
    private final ConfigStorage storage;
    private final ConfigValidator validator;
    private volatile ConfigSnapshot committed;

    public ConfigRepository(ConfigStorage storage, ConfigValidator validator) {
        if (storage == null || validator == null) {
            throw new IllegalArgumentException("storage and validator are required");
        }
        this.storage = storage;
        this.validator = validator;
    }

    /** Loads and validates the stored snapshot; invalid storage never replaces the last committed one. */
    public ValidationResult<ConfigSnapshot> load() {
        try {
            StoredConfig stored = storage.load();
            ValidationResult<ConfigSnapshot> result =
                    validator.validateForSave(new ConfigDraft(stored.values()));
            if (!result.isValid()) {
                return result;
            }
            ConfigSnapshot snapshot = result.value().withRevision(stored.revision());
            committed = snapshot;
            return ValidationResult.ok(snapshot);
        } catch (RuntimeException e) {
            return ValidationResult.fail(new FieldError(ConfigFields.STORAGE,
                    FieldError.Code.STORAGE, e.getMessage()));
        }
    }

    public ConfigSnapshot committedSnapshot() {
        return committed;
    }

    /** Returns an editable draft from the committed snapshot or raw storage values. */
    public ConfigDraft newDraft() {
        if (committed != null) {
            return ConfigDraft.from(committed);
        }
        try {
            return new ConfigDraft(storage.load().values());
        } catch (RuntimeException e) {
            return new ConfigDraft();
        }
    }

    /**
     * Validates and persists the whole draft in one edit. The revision is published only
     * after the storage commit succeeds; a failed write leaves the previous snapshot intact.
     */
    public ValidationResult<ConfigSnapshot> save(ConfigDraft draft) {
        ValidationResult<ConfigSnapshot> validation = validator.validateForSave(draft);
        if (!validation.isValid()) {
            return validation;
        }

        long currentRevision = committed != null ? committed.revision() : 0;
        if (committed == null) {
            try {
                currentRevision = storage.load().revision();
            } catch (RuntimeException ignored) {
                currentRevision = 0;
            }
        }
        long newRevision = currentRevision + 1;

        boolean written;
        try {
            written = storage.writeAll(draft.asMap(), newRevision);
        } catch (RuntimeException e) {
            written = false;
        }
        if (!written) {
            return ValidationResult.fail(new FieldError(ConfigFields.STORAGE,
                    FieldError.Code.STORAGE, "commit failed"));
        }

        ConfigSnapshot snapshot = validation.value().withRevision(newRevision);
        committed = snapshot;
        return ValidationResult.ok(snapshot);
    }

    /** Start validation only; there is deliberately no force/override argument. */
    public ValidationResult<ValidatedConfig> validateForStart() {
        if (committed == null) {
            ValidationResult<ConfigSnapshot> loaded = load();
            if (!loaded.isValid()) {
                return ValidationResult.fail(loaded.errors());
            }
        }
        return validator.validateForStart(committed);
    }
}
