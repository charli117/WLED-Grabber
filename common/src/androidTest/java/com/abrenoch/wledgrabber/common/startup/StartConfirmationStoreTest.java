package com.abrenoch.wledgrabber.common.startup;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class StartConfirmationStoreTest {
    private StartConfirmationStore store;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new StartConfirmationStore(context);
        store.clear();
    }

    @After
    public void tearDown() {
        store.clear();
    }

    @Test
    public void emptyStoreHasNoConfirmation() {
        assertNull(store.load());
    }

    @Test
    public void savesAndRestoresEveryFlag() {
        StartConfirmation confirmation = new StartConfirmation("device-1", true,
                true, true, true, true, true, true, true);
        assertTrue(store.save(confirmation));

        StartConfirmation loaded = store.load();
        assertNotNull(loaded);
        assertEquals("device-1", loaded.confirmedDeviceId());
        assertTrue(loaded.manualWhiteConfirmed());
        assertTrue(loaded.offsetZeroConfirmed());
        assertTrue(loaded.mainSegmentOnlyConfirmed());
        assertTrue(loaded.dmxStartOneConfirmed());
        assertTrue(loaded.finiteTimeoutConfirmed());
        assertTrue(loaded.maxBrightnessOverrideOffConfirmed());
        assertTrue(loaded.otherSendersCheckedConfirmed());
        assertTrue(loaded.syncDisabledConfirmed());
        assertTrue(loaded.allRequired(true));
    }

    @Test
    public void clearRemovesTheStoredDevice() {
        store.save(new StartConfirmation("device-2", false,
                true, true, true, true, true, true, true));
        store.clear();
        assertNull(store.load());
    }

    @Test
    public void rgbwWithoutManualWhiteIsNotComplete() {
        StartConfirmation confirmation = new StartConfirmation("device-3", false,
                true, true, true, true, true, true, true);
        assertFalse(confirmation.allRequired(true));
        assertTrue(confirmation.allRequired(false));
    }
}
