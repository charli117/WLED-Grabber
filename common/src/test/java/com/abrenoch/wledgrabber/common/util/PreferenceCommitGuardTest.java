package com.abrenoch.wledgrabber.common.util;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class PreferenceCommitGuardTest {
    @Test public void failedInMemoryChangesAreNotVisibleAndRetryRepairsOnlyDifferences() {
        class Memory implements PreferenceCommitGuard.File {
            Map<String,String> raw = new LinkedHashMap<>(), written;
            long revision = 1; boolean fail = true;
            public StoredConfig readMemory() { return new StoredConfig(raw, revision); }
            public boolean commitDelta(Map<String,String> delta, long rev) {
                raw.putAll(delta); written = delta; revision = rev; return !fail;
            }
        }
        Memory prefs = new Memory(); prefs.raw.put("input", "0"); prefs.raw.put("quality", "unchanged");
        PreferenceCommitGuard guard = new PreferenceCommitGuard();
        Map<String,String> candidate = new LinkedHashMap<>(prefs.raw); candidate.put("input", "85");
        assertFalse(guard.write(prefs, candidate, 2));
        assertEquals(1, guard.read(prefs).revision());
        assertEquals("0", guard.read(prefs).values().get("input"));
        assertEquals(1, prefs.written.size());
        prefs.fail = false; candidate.put("input", "161");
        assertTrue(guard.write(prefs, candidate, 2));
        assertEquals("161", guard.read(prefs).values().get("input"));
        assertEquals(2, guard.read(prefs).revision());
    }
}
