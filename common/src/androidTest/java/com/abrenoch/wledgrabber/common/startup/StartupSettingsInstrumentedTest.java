package com.abrenoch.wledgrabber.common.startup;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real preferences, isolated from the user's WLED configuration and from other test classes. */
public class StartupSettingsInstrumentedTest {
    private Context isolated;
    private final Set<SharedPreferences> stores = new HashSet<>();

    @Before public void isolateStorage() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String prefix = "startup-test-" + UUID.randomUUID() + "-";
        isolated = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                SharedPreferences prefs = super.getSharedPreferences(prefix + name, mode);
                stores.add(prefs);
                return prefs;
            }
        };
    }

    @After public void cleanOnlyTestStores() {
        for (SharedPreferences prefs : stores) prefs.edit().clear().commit();
    }

    @Test public void firstOpenDefaultsToBootCaptureWithoutInventingCompletedSetup() {
        StartupAccess access = new StartupAccess(isolated);
        assertTrue(access.bootEnabled());
        assertFalse(access.hasCompletedSetup());
    }
    @Test public void explicitDisableAndCompletionSurviveReconstructionWithoutChangingFrameRate() {
        com.abrenoch.wledgrabber.common.util.ConfigRepository repository =
                new com.abrenoch.wledgrabber.common.util.ConfigRepository(
                        new com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage(
                                new com.abrenoch.wledgrabber.common.util.Preferences(isolated)),
                        new com.abrenoch.wledgrabber.common.util.ConfigValidator());
        assertTrue(repository.load().isValid());
        assertTrue(repository.save(repository.newDraft().set(
                com.abrenoch.wledgrabber.common.led.ConfigFields.FRAME_RATE, "25")).isValid());
        assertTrue(new StartupAccess(isolated).completeSetup(false));

        StartupAccess restored = new StartupAccess(isolated);
        assertFalse(restored.bootEnabled());
        assertTrue(restored.hasCompletedSetup());
        assertTrue(repository.load().isValid());
        assertEquals(25, repository.committedSnapshot().capture().frameRate());
    }

    @Test public void missingGrantsCannotBePersistedAsSuccessfulAutomaticSetup() {
        StartupAccess access = new StartupAccess(isolated);
        org.junit.Assume.assumeFalse(access.status().automaticCaptureAvailable());
        assertFalse(access.completeSetup(true));
        assertFalse(new StartupAccess(isolated).hasCompletedSetup());
        assertTrue(new StartupAccess(isolated).bootEnabled());
    }
}
