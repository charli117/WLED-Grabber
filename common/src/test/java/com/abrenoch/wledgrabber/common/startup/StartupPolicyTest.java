package com.abrenoch.wledgrabber.common.startup;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class StartupPolicyTest {
    @Test
    public void configuredAndroid10WithAllGrantsStartsANewCaptureAtBoot() {
        StartupPolicy status = new StartupPolicy(29, true, true, true, true);
        assertEquals(StartupPolicy.Action.START_CAPTURE, status.onBoot(true, true));
    }
    @Test
    public void explicitDisableAlwaysWinsOverCapabilities() {
        assertEquals(StartupPolicy.Action.DISABLED,
                new StartupPolicy(29, true, true, true, true).onBoot(false, true));
    }

    @Test
    public void missingConfigurationOrAnyGrantFallsBackToReminder() {
        assertEquals(StartupPolicy.Action.REMIND,
                new StartupPolicy(29, true, true, true, true).onBoot(true, false));
        boolean[][] grants = {{false, true, true, true}, {true, false, true, true},
                {true, true, false, true}, {true, true, true, false}};
        for (boolean[] grant : grants) {
            assertEquals(StartupPolicy.Action.REMIND,
                    new StartupPolicy(29, grant[0], grant[1], grant[2], grant[3]).onBoot(true, true));
        }
    }

    @Test
    public void otherAndroidVersionsNeverInheritTheAndroid10Exception() {
        for (int sdk : new int[] {21, 23, 26, 28, 30, 33, 34, 35, 36}) {
            assertEquals("API " + sdk, StartupPolicy.Action.REMIND,
                    new StartupPolicy(sdk, true, true, true, true).onBoot(true, true));
        }
    }
}
