package com.abrenoch.wledgrabber.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

/** One guard per preference file. Android may publish an edit to memory even if commit returns false. */
final class PreferenceCommitGuard {
    interface File {
        StoredConfig readMemory();
        boolean commitDelta(Map<String,String> changes, long revision);
    }
    private StoredConfig beforeFailure;

    synchronized StoredConfig read(File file) {
        return beforeFailure != null ? beforeFailure : file.readMemory();
    }

    synchronized boolean write(File file, Map<String,String> values, long revision) {
        StoredConfig memory = file.readMemory();
        if (beforeFailure == null) beforeFailure = memory;
        Map<String,String> delta = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : values.entrySet()) {
            if (!java.util.Objects.equals(memory.values().get(entry.getKey()), entry.getValue())) {
                delta.put(entry.getKey(), entry.getValue());
            }
        }
        if (!file.commitDelta(delta, revision)) return false;
        beforeFailure = null;
        return true;
    }
}
