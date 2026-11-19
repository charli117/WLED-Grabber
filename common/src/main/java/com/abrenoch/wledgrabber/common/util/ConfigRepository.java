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
    // Activities create separate repository and adapter instances for the same preferences.
    private static final Object WRITE_LOCK = new Object();
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
            StoredConfig raw = storage.load();
            return new ConfigDraft(raw.values(), raw.revision());
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

        synchronized (WRITE_LOCK) {
            try {
                long current = storage.load().revision();
                // A new unversioned draft may initialize storage, never overwrite an existing revision.
                long expected = Math.max(0, draft.baseRevision());
                if (current != expected) return conflict();
                return writeValidated(draft, validation.value(), current + 1);
            } catch (RuntimeException e) {
                return storageError(e.getMessage());
            }
        }
    }

    /** Merge only the observed wiring fields, never the editor's normalized full draft. */
    ValidationResult<ConfigSnapshot> saveCalibration(long expected, int input, boolean reverse) {
        synchronized (WRITE_LOCK) {
            try {
                StoredConfig actual = storage.load();
                if (expected != actual.revision() || committed == null) return conflict();
                if (committed.layout().inputPosition() == input && committed.layout().reverse() == reverse) {
                    return ValidationResult.ok(committed);
                }
                ConfigDraft candidate = new ConfigDraft(actual.values(), expected)
                        .set(ConfigFields.LED_INPUT_POSITION, Integer.toString(input))
                        .set(ConfigFields.LED_REVERSE, Boolean.toString(reverse));
                ValidationResult<ConfigSnapshot> valid = validator.validateForSave(candidate);
                return valid.isValid() ? writeValidated(candidate, valid.value(), expected + 1) : valid;
            } catch (RuntimeException e) {
                return storageError(e.getMessage());
            }
        }
    }

    private ValidationResult<ConfigSnapshot> writeValidated(ConfigDraft draft, ConfigSnapshot valid,
                                                              long revision) {
        if (!storage.writeAll(draft.asMap(), revision)) return storageError("commit failed");
        committed = valid.withRevision(revision);
        return ValidationResult.ok(committed);
    }

    private static ValidationResult<ConfigSnapshot> conflict() {
        return ValidationResult.fail(new FieldError(ConfigFields.STORAGE,
                FieldError.Code.STALE_OBSERVATION, "Configuration changed. Reload before saving."));
    }

    private static ValidationResult<ConfigSnapshot> storageError(String detail) {
        return ValidationResult.fail(new FieldError(ConfigFields.STORAGE, FieldError.Code.STORAGE, detail));
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
