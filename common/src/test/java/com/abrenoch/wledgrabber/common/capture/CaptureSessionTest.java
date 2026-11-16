package com.abrenoch.wledgrabber.common.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaptureSessionTest {

    private static CaptureSession flowToCapturing() {
        CaptureSession session = new CaptureSession();
        assertTrue(session.beginStart());
        assertTrue(session.validationPassed());
        assertTrue(session.onConsentResult(true));
        assertTrue(session.onServiceForegrounded());
        assertTrue(session.requestProjection());
        assertTrue(session.onProjectionCreated(true));
        assertTrue(session.createDisplay(true));
        return session;
    }

    @Test
    public void refusalCreatesNoCaptureResources() {
        CaptureSession session = new CaptureSession();
        assertTrue(session.beginStart());
        assertTrue(session.validationPassed());
        assertFalse(session.onConsentResult(false));
        assertEquals(CaptureSession.State.IDLE, session.state());
        assertEquals(0, session.projectionRequests());
        assertEquals(0, session.displayCreations());
        assertFalse(session.beginStop());
    }

    @Test
    public void duplicateResultsAndRepeatedClicksAreRejected() {
        CaptureSession session = new CaptureSession();
        assertTrue(session.beginStart());
        assertFalse(session.beginStart());
        assertTrue(session.validationPassed());
        assertFalse(session.validationPassed());
        assertTrue(session.onConsentResult(true));
        assertFalse(session.onConsentResult(true));
        assertTrue(session.onServiceForegrounded());
        assertFalse(session.onServiceForegrounded());
        assertTrue(session.requestProjection());
        assertFalse(session.requestProjection());
        assertTrue(session.onProjectionCreated(true));
        assertFalse(session.onProjectionCreated(true));
        assertTrue(session.createDisplay(true));
        assertFalse(session.createDisplay(true));
        assertEquals(1, session.displayCreations());
        assertFalse(session.beginStart());
    }

    @Test
    public void foregroundAndProjectionOrderIsEnforced() {
        CaptureSession session = new CaptureSession();
        assertTrue(session.beginStart());
        assertTrue(session.validationPassed());
        assertTrue(session.onConsentResult(true));
        assertFalse(session.requestProjection());
        assertFalse(session.createDisplay(true));
        assertTrue(session.onServiceForegrounded());
        assertTrue(session.requestProjection());
        assertFalse(session.createDisplay(true));
        assertTrue(session.onProjectionCreated(true));
        assertTrue(session.createDisplay(true));
    }

    @Test
    public void rejectionIsOnlyPossibleAfterForegrounding() {
        CaptureSession session = new CaptureSession();
        assertTrue(session.beginStart());
        assertTrue(session.validationPassed());
        assertTrue(session.onConsentResult(true));

        // CAP-1 regression: a start must never abort before startForeground().
        assertFalse(session.rejectStart());
        assertEquals(CaptureSession.State.STARTING, session.state());

        assertTrue(session.onServiceForegrounded());
        assertTrue(session.rejectStart());
        assertEquals(CaptureSession.State.IDLE, session.state());
        assertEquals(0, session.projectionRequests());
        assertEquals(0, session.displayCreations());
    }

    @Test
    public void projectionOrDisplayFailureCreatesNoCapturingSession() {
        CaptureSession projectionFailure = new CaptureSession();
        assertTrue(projectionFailure.beginStart());
        assertTrue(projectionFailure.validationPassed());
        assertTrue(projectionFailure.onConsentResult(true));
        assertTrue(projectionFailure.onServiceForegrounded());
        assertTrue(projectionFailure.requestProjection());
        assertFalse(projectionFailure.onProjectionCreated(false));
        assertFalse(projectionFailure.createDisplay(true));
        assertEquals(0, projectionFailure.displayCreations());

        CaptureSession displayFailure = new CaptureSession();
        assertTrue(displayFailure.beginStart());
        assertTrue(displayFailure.validationPassed());
        assertTrue(displayFailure.onConsentResult(true));
        assertTrue(displayFailure.onServiceForegrounded());
        assertTrue(displayFailure.requestProjection());
        assertTrue(displayFailure.onProjectionCreated(true));
        assertFalse(displayFailure.createDisplay(false));
        assertEquals(0, displayFailure.displayCreations());
        assertEquals(CaptureSession.State.IDLE, displayFailure.state());
    }

    @Test
    public void stopAndRevocationCannotRestartWithoutNewConsent() {
        CaptureSession stopped = flowToCapturing();
        assertTrue(stopped.beginStop());
        assertFalse(stopped.beginStart());
        assertTrue(stopped.stopCompleted());
        assertEquals(CaptureSession.State.RELEASED, stopped.state());
        assertTrue(stopped.beginStart());
        assertTrue(stopped.validationPassed());
        assertTrue(stopped.onConsentResult(true));

        CaptureSession revoked = flowToCapturing();
        assertTrue(revoked.onProjectionRevoked());
        assertEquals(CaptureSession.State.NEEDS_CONSENT, revoked.state());
        assertFalse(revoked.beginStop());
        assertTrue(revoked.beginStart());
        assertTrue(revoked.validationPassed());
        assertTrue(revoked.onConsentResult(true));
    }

    @Test
    public void serviceRebuildRequiresANewVisibleConsentFlow() {
        CaptureSession session = flowToCapturing();
        assertEquals(1, session.displayCreations());
        assertTrue(session.serviceRebuilt());
        assertEquals(CaptureSession.State.NEEDS_CONSENT, session.state());
        assertFalse(session.beginStop());

        assertTrue(session.beginStart());
        assertFalse(session.onConsentResult(true));
        assertTrue(session.validationPassed());
        assertTrue(session.onConsentResult(true));
        assertTrue(session.onServiceForegrounded());
        assertTrue(session.requestProjection());
        assertTrue(session.onProjectionCreated(true));
        assertTrue(session.createDisplay(true));
        assertEquals(2, session.displayCreations());
    }

    @Test
    public void oldConsentTokenIsNotReusedAcrossSessions() {
        CaptureSession session = flowToCapturing();
        long firstEpoch = session.epoch();
        assertTrue(session.beginStop());
        assertTrue(session.stopCompleted());
        assertTrue(session.beginStart());
        assertTrue(session.epoch() > firstEpoch);
        assertFalse(session.onConsentResult(true));
        assertEquals(1, session.displayCreations());
    }
}
