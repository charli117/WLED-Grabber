package com.abrenoch.wledgrabber.common.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StoredConfig {
    private final Map<String, String> values;
    private final long revision;

    public StoredConfig(Map<String, String> values, long revision) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.revision = revision;
    }

    public Map<String, String> values() {
        return values;
    }

    public long revision() {
        return revision;
    }
}
