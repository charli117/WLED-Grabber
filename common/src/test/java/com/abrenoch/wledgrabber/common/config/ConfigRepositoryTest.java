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
    public void oneSavePublishesExactlyOneRevision() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 0);
        ConfigRepository repository = new ConfigRepository(storage, new ConfigValidator());

        assertTrue(repository.load().isValid());
        assertEquals(0, repository.committedSnapshot().revision());

        ValidationResult<ConfigSnapshot> first = repository.save(ConfigTestData.validDraft());
        assertTrue(first.errors().toString(), first.isValid());
        assertEquals(1, first.value().revision());
        assertEquals(1, repository.committedSnapshot().revision());

        ValidationResult<ConfigSnapshot> second = repository.save(ConfigTestData.validDraft());
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
        ValidationResult<ConfigSnapshot> result = repository.save(ConfigTestData.validDraft());

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
