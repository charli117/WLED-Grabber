package com.abrenoch.wledgrabber.common.util;

import java.util.Map;

/** Platform-independent persistence boundary; the Android adapter uses one SharedPreferences edit. */
public interface ConfigStorage {
    StoredConfig load();

    /** Persists all values in one edit and returns true only when commit succeeded. */
    boolean writeAll(Map<String, String> values, long revision);
}
