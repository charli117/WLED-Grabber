package com.abrenoch.wledgrabber.common.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation coverage for one-edit visibility and repository bypass resistance.
 * Commit-failure retention is covered by the JVM FakeStorage test; a real
 * SharedPreferences commit cannot be forced to fail deterministically.
 */
public class PreferencesConfigStorageInstrumentedTest {
    private Context context;
    private SharedPreferences raw;
    private ConfigRepository repository;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        raw = PreferenceManager.getDefaultSharedPreferences(context);
        raw.edit().clear().commit();
        repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(context)),
                new ConfigValidator());
    }

    @Test
    public void saveWritesOneVisibleRevisionAndBypassDoesNotReplaceSnapshot() {
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        assertTrue(loaded.errors().toString(), loaded.isValid());

        ConfigDraft draft = repository.newDraft()
                .set(ConfigFields.WLED_HOST, "192.168.1.50")
                .set(ConfigFields.WLED_PHYSICAL_LEDS, "274")
                .set(ConfigFields.WLED_RGBW_MODE, "rgb")
                .set(ConfigFields.LED_TOP, "86");

        ValidationResult<ConfigSnapshot> saved = repository.save(draft);
        assertTrue(saved.errors().toString(), saved.isValid());
        assertEquals(1, saved.value().revision());
        assertNotNull(repository.committedSnapshot());
        assertEquals(1, repository.committedSnapshot().revision());
        assertEquals("192.168.1.50", raw.getString(ConfigFields.WLED_HOST, null));
        assertEquals("1", raw.getString("pref_key_config_revision", null));

        // Simulate a UI bypassing the repository. The committed snapshot must not move.
        raw.edit().putString(ConfigFields.LED_TOP, "not-a-number").commit();
        assertEquals(1, repository.committedSnapshot().revision());
        assertEquals(86, repository.committedSnapshot().layout().top());

        ValidationResult<ConfigSnapshot> reloaded = repository.load();
        assertFalse(reloaded.isValid());
        assertEquals(1, repository.committedSnapshot().revision());
    }
}
