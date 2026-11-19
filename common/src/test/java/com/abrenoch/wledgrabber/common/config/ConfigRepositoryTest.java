package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.ValidatedConfig;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigStorage;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.StoredConfig;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ConfigRepositoryTest {

    private static final class FakeStorage implements ConfigStorage {
        private Map<String, String> values;
        private long revision;
        private boolean failWrite;
        private int writes;

        FakeStorage(Map<String, String> values, long revision) {
            this.values = new LinkedHashMap<>(values);
            this.revision = revision;
        }

        @Override
        public StoredConfig load() {
            return new StoredConfig(values, revision);
        }

        @Override
        public boolean writeAll(Map<String, String> values, long revision) {
            writes++;
            if (failWrite) {
                return false;
            }
            this.values = new LinkedHashMap<>(values);
            this.revision = revision;
            return true;
        }
    }

    @Test
    public void calibrationCommitsOnlyTheTwoChosenFieldsOnceAndRejectsConcurrentEditors() {
        Map<String, String> initial = ConfigTestData.validDraft().set(ConfigFields.LED_INPUT_POSITION, "0")
                .set(ConfigFields.LED_REVERSE, "false").asMap();
        FakeStorage storage = new FakeStorage(initial, 7);
        SettingsDraftController wizard = new SettingsDraftController(new ConfigRepository(storage, new ConfigValidator()));
        SettingsDraftController other = new SettingsDraftController(new ConfigRepository(storage, new ConfigValidator()));
        wizard.load(); other.load();
        assertFalse(wizard.hasUnsavedChanges());
        wizard.set(ConfigFields.FRAME_RATE, "15");
        assertTrue(wizard.hasUnsavedChanges());
        assertFalse(wizard.saveCalibration(161, true).isValid());
        wizard.cancel();
        assertEquals(0, storage.writes);
        assertTrue(wizard.saveCalibration(161, true).isValid());
        Map<String,String> expected = new LinkedHashMap<>(initial);
        expected.put(ConfigFields.LED_INPUT_POSITION, "161"); expected.put(ConfigFields.LED_REVERSE, "true");
        assertEquals(expected, storage.values);
        assertTrue(wizard.saveCalibration(161, true).isValid());
        assertFalse(other.saveCalibration(161, true).isValid());
        assertFalse(other.save().isValid());
        assertEquals(1, storage.writes);
        assertEquals(8, storage.revision);
    }

    @Test
    public void staleDraftCannotOverwriteNewerSettingsEvenFromSameRepository() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 11);
        ConfigRepository editor = new ConfigRepository(storage, new ConfigValidator());
        editor.load();
        ConfigDraft first = editor.newDraft(), old = editor.newDraft().copy();
        assertTrue(editor.save(first.set(ConfigFields.LED_INPUT_POSITION, "85")).isValid());
        assertFalse(editor.save(old.set(ConfigFields.FRAME_RATE, "30")).isValid());
        assertEquals("85", storage.values.get(ConfigFields.LED_INPUT_POSITION));
        assertEquals(12, storage.revision);
        assertFalse("Dropping revision metadata must not grant overwrite authority",
                editor.save(new ConfigDraft(old.asMap())).isValid());
    }

    @Test
    public void oneSavePublishesExactlyOneRevision() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 0);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());

        assertTrue(repository.load().isValid());
        assertEquals(0, repository.committedSnapshot().revision());

        ValidationResult<ConfigSnapshot> first = repository.save(ConfigTestData.validDraft());
        assertTrue(first.errors().toString(), first.isValid());
        assertEquals(1, first.value().revision());
        assertEquals(1, repository.committedSnapshot().revision());

        ValidationResult<ConfigSnapshot> second = repository.save(repository.newDraft());
        assertTrue(second.isValid());
        assertEquals(2, second.value().revision());
        assertEquals(2, storage.writes);
    }

    @Test
    public void failedWriteKeepsCommittedSnapshotAndRevision() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 7);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());
        ConfigSnapshot before = repository.committedSnapshot();

        storage.failWrite = true;
        ValidationResult<ConfigSnapshot> result = repository.save(repository.newDraft());

        assertFalse(result.isValid());
        assertTrue(result.hasError(ConfigFields.STORAGE, FieldError.Code.STORAGE));
        assertNotNull(before);
        assertEquals(7, repository.committedSnapshot().revision());
        assertEquals(7, storage.revision);
    }

    @Test
    public void invalidDraftDoesNotTouchStorage() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 3);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());

        ValidationResult<ConfigSnapshot> result = repository.save(
                ConfigTestData.validDraft().set(ConfigFields.LED_TOP, "not-a-number"));

        assertFalse(result.isValid());
        assertEquals(0, storage.writes);
        assertEquals(3, repository.committedSnapshot().revision());
    }

    @Test
    public void invalidStoredValuesDoNotReplaceCommittedSnapshot() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 5);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());
        assertEquals(5, repository.committedSnapshot().revision());

        Map<String, String> broken = new LinkedHashMap<>(ConfigTestData.validDraft().asMap());
        broken.put(ConfigFields.FRAME_RATE, "0");
        storage.values = broken;

        ValidationResult<ConfigSnapshot> reloaded = repository.load();
        assertFalse(reloaded.isValid());
        assertEquals(5, repository.committedSnapshot().revision());
    }

    @Test
    public void insufficientCountCannotBeForcedIntoStartConfiguration() {
        FakeStorage storage = new FakeStorage(
                ConfigTestData.validDraftWithPhysicalLeds(100).asMap(), 0);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());
        assertTrue(repository.save(ConfigTestData.validDraftWithPhysicalLeds(100)).isValid());

        ValidationResult<ValidatedConfig> start = repository.validateForStart();
        assertFalse(start.isValid());
        assertTrue(start.hasError(ConfigFields.WLED_PHYSICAL_LEDS, FieldError.Code.CROSS_FIELD));
    }

    @Test
    public void saveDoesNotMutatePreviouslyBorrowedSnapshot() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 0);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());

        ConfigSnapshot borrowed = repository.committedSnapshot();
        assertTrue(repository.save(ConfigTestData.validDraft()).isValid());

        assertEquals(0, borrowed.revision());
        assertEquals(1, repository.committedSnapshot().revision());
        assertNotSame(borrowed, repository.committedSnapshot());
    }

    @Test
    public void newDraftRoundTripsCommittedValues() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 11);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());
        assertTrue(repository.load().isValid());

        ConfigDraft draft = repository.newDraft();
        assertEquals("86", draft.get(ConfigFields.LED_TOP));
        assertEquals("274", draft.get(ConfigFields.WLED_PHYSICAL_LEDS));
        assertEquals("192.168.1.50", draft.get(ConfigFields.WLED_HOST));
        assertTrue(new ConfigValidator().validateForSave(draft).isValid());
    }
}
