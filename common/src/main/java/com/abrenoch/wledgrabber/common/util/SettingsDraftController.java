package com.abrenoch.wledgrabber.common.util;

import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;

/** Shared draft/save/cancel controller used by mobile and TV settings hosts. */
public final class SettingsDraftController {
    private final ConfigRepository repository;
    private final ConfigValidator validator = new ConfigValidator();
    private ConfigDraft draft = new ConfigDraft();
    private ValidationResult<ConfigSnapshot> lastResult;

    public SettingsDraftController(ConfigRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository is required");
        }
        this.repository = repository;
    }

    public ValidationResult<ConfigSnapshot> load() {
        lastResult = repository.load();
        draft = repository.newDraft();
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

    /** Saves the whole draft atomically; invalid drafts never touch storage. */
    public ValidationResult<ConfigSnapshot> save() {
        lastResult = repository.save(draft);
        if (lastResult.isValid()) {
            draft = repository.newDraft();
        }
        return lastResult;
    }

    public void cancel() {
        draft = repository.newDraft();
        lastResult = null;
    }

    public ValidationResult<ConfigSnapshot> lastResult() {
        return lastResult;
    }

    public ConfigSnapshot committedSnapshot() {
        return repository.committedSnapshot();
    }
}
