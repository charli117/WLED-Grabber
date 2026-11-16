package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.Capabilities;
import com.abrenoch.wledgrabber.common.network.wled.ChannelHint;
import com.abrenoch.wledgrabber.common.network.wled.ProbeError;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigStorage;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.StoredConfig;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.common.util.WledSetupController;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvSetupTest {

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

    private static WledSetupController setup() {
        SettingsDraftController settings = new SettingsDraftController(
                new ConfigRepository(new FakeStorage(ConfigTestData.validDraft().asMap(), 0),
                        new ConfigValidator()));
        assertTrue(settings.load().isValid());
        return new WledSetupController(settings);
    }

    @Test
    public void manualEntryIsAlwaysAvailableAndValidatesAtomically() {
        WledSetupController setup = setup();
        ValidationResult<ConfigSnapshot> valid = setup.applyManual(
                "192.168.1.77", 80, 21324, 4048);
        assertTrue(valid.errors().toString(), valid.isValid());

        ValidationResult<ConfigSnapshot> invalid = setup.applyManual(
                "192.168.1.77", 0, 21324, 4048);
        assertFalse(invalid.isValid());
        assertEquals(ConfigFields.WLED_HTTP_PORT,
                invalid.errorFor(ConfigFields.WLED_HTTP_PORT).field());
        assertEquals(FieldError.Code.OUT_OF_RANGE,
                invalid.errorFor(ConfigFields.WLED_HTTP_PORT).code());
    }

    @Test
    public void probeSuccessAppliesObservedCountAndLeavesRgbwUnconfirmed() {
        WledSetupController setup = setup();
        Capabilities capabilities = new Capabilities("0.15.1", "dev-1", 274, 21324,
                true, ChannelHint.RGBW, "RGBW", "", false, 1000);
        ProbeResult probe = ProbeResult.success(new WledDevice("192.168.1.88", capabilities));

        ValidationResult<ConfigSnapshot> result = setup.applyProbe(probe);
        assertTrue(result.errors().toString(), result.isValid());
        assertEquals("192.168.1.88", setup.settings().draft().get(ConfigFields.WLED_HOST));
        assertEquals("274", setup.settings().draft().get(ConfigFields.WLED_PHYSICAL_LEDS));
        assertEquals("21324", setup.settings().draft().get(ConfigFields.WLED_UDP_PORT));
        assertEquals("unconfirmed", setup.settings().draft().get(ConfigFields.WLED_RGBW_MODE));
    }

    @Test
    public void probeFailureIsExplicitAndDoesNotPretendToBeConnected() {
        WledSetupController setup = setup();
        ProbeResult failure = ProbeResult.failure(ProbeError.TIMEOUT, "timeout", 1000);
        ValidationResult<ConfigSnapshot> result = setup.applyProbe(failure);

        assertFalse(result.isValid());
        assertEquals(FieldError.Code.CAPABILITY_MISSING,
                result.errorFor(ConfigFields.WLED_HOST).code());
    }

    @Test
    public void manualAndProbeShareTheSameFieldErrorCodesAsMobile() {
        WledSetupController setup = setup();
        setup.applyManual("192.168.1.77", 70000, 21324, 4048);
        ValidationResult<ConfigSnapshot> tv = setup.settings().validate();

        SettingsDraftController mobileSettings = new SettingsDraftController(
                new ConfigRepository(new FakeStorage(ConfigTestData.validDraft().asMap(), 0),
                        new ConfigValidator()));
        assertTrue(mobileSettings.load().isValid());
        mobileSettings.set(ConfigFields.WLED_HTTP_PORT, "70000");
        ValidationResult<ConfigSnapshot> mobile = mobileSettings.validate();

        assertFalse(tv.isValid());
        assertFalse(mobile.isValid());
        assertEquals(mobile.errors(), tv.errors());
    }
}
