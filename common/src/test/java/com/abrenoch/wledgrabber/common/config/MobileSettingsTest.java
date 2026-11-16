package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigStorage;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.StoredConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobileSettingsTest {

    private static final class FakeStorage implements ConfigStorage {
        private Map<String, String> values;
        private long revision;
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
            this.values = new LinkedHashMap<>(values);
            this.revision = revision;
            return true;
        }
    }

    private static SettingsDraftController controller(FakeStorage storage) {
        return new SettingsDraftController(new ConfigRepository(
                storage, new ConfigValidator()));
    }

    @Test
    public void loadEditSaveWholeDraftAndResetDraft() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 3);
        SettingsDraftController controller = controller(storage);
        assertTrue(controller.load().isValid());
        assertEquals("192.168.1.50", controller.draft().get(ConfigFields.WLED_HOST));

        controller.set(ConfigFields.WLED_HOST, "192.168.1.60");
        ValidationResult<ConfigSnapshot> saved = controller.save();
        assertTrue(saved.errors().toString(), saved.isValid());
        assertEquals(4, saved.value().revision());
        assertEquals("192.168.1.60", controller.committedSnapshot().device().host());
        assertEquals("192.168.1.60", controller.draft().get(ConfigFields.WLED_HOST));
        assertEquals(1, storage.writes);
    }

    @Test
    public void invalidSaveDoesNotTouchStorageOrCommittedSnapshot() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 3);
        SettingsDraftController controller = controller(storage);
        assertTrue(controller.load().isValid());

        controller.set(ConfigFields.WLED_HTTP_PORT, "0");
        ValidationResult<ConfigSnapshot> result = controller.save();

        assertFalse(result.isValid());
        assertEquals(0, storage.writes);
        assertEquals(3, controller.committedSnapshot().revision());
        assertEquals("0", controller.draft().get(ConfigFields.WLED_HTTP_PORT));
    }

    @Test
    public void cancelRestoresCommittedDraft() {
        FakeStorage storage = new FakeStorage(ConfigTestData.validDraft().asMap(), 3);
        SettingsDraftController controller = controller(storage);
        assertTrue(controller.load().isValid());

        controller.set(ConfigFields.WLED_HOST, "broken");
        controller.cancel();

        assertEquals("192.168.1.50", controller.draft().get(ConfigFields.WLED_HOST));
    }

    @Test
    public void mobileAndTvUseTheSameDraftErrorCodesAndSnapshot() {
        FakeStorage mobileStorage = new FakeStorage(ConfigTestData.validDraft().asMap(), 0);
        FakeStorage tvStorage = new FakeStorage(ConfigTestData.validDraft().asMap(), 0);
        SettingsDraftController mobile = controller(mobileStorage);
        SettingsDraftController tv = controller(tvStorage);
        assertTrue(mobile.load().isValid());
        assertTrue(tv.load().isValid());

        for (SettingsDraftController controller : new SettingsDraftController[]{mobile, tv}) {
            controller.set(ConfigFields.LED_TOP, "");
            controller.set(ConfigFields.FRAME_RATE, "61");
            controller.set(ConfigFields.WLED_PROTOCOL, "not-a-protocol");
        }
        ValidationResult<ConfigSnapshot> mobileErrors = mobile.validate();
        ValidationResult<ConfigSnapshot> tvErrors = tv.validate();
        assertEquals(mobileErrors.errors(), tvErrors.errors());
        assertFalse(mobileErrors.isValid());

        for (SettingsDraftController controller : new SettingsDraftController[]{mobile, tv}) {
            controller.set(ConfigFields.LED_TOP, "86");
            controller.set(ConfigFields.FRAME_RATE, "60");
            controller.set(ConfigFields.WLED_PROTOCOL, "auto");
        }
        ValidationResult<ConfigSnapshot> mobileSaved = mobile.save();
        ValidationResult<ConfigSnapshot> tvSaved = tv.save();
        assertTrue(mobileSaved.isValid());
        assertTrue(tvSaved.isValid());
        assertEquals(1, mobileSaved.value().revision());
        assertEquals(1, tvSaved.value().revision());
        assertEquals(mobileSaved.value().capture().frameRate(),
                tvSaved.value().capture().frameRate());
        assertEquals(mobileSaved.value().layout().top(), tvSaved.value().layout().top());
    }
}
