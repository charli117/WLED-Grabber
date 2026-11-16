package com.abrenoch.wledgrabber.common.config;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The mobile and TV hosts must delegate to the same validator; this test pins the
 * shared field/code contract they consume instead of reimplementing ranges.
 */
public class ConfigUiAdapterParityTest {

    private interface Adapter {
        ValidationResult<ConfigSnapshot> validate(ConfigDraft draft);
    }

    private static final Adapter MOBILE = draft -> new ConfigValidator().validateForSave(draft);
    private static final Adapter TV = draft -> new ConfigValidator().validateForSave(draft);

    @Test
    public void sameInputYieldsSameFieldErrorCodesForMobileAndTv() {
        ConfigDraft invalid = ConfigTestData.validDraft()
                .set(ConfigFields.FRAME_RATE, "61")
                .set(ConfigFields.LED_TOP, "");

        ValidationResult<ConfigSnapshot> mobile = MOBILE.validate(invalid);
        ValidationResult<ConfigSnapshot> tv = TV.validate(invalid);

        assertFalse(mobile.isValid());
        assertEquals(mobile.errors(), tv.errors());
        assertTrue(mobile.hasError(ConfigFields.FRAME_RATE, FieldError.Code.OUT_OF_RANGE));
        assertTrue(mobile.hasError(ConfigFields.LED_TOP, FieldError.Code.EMPTY));
    }
}
