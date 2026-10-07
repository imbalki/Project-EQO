package rikka.shizuku.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EqoPermissionRequestsTest {
    @Test
    public void cancellationInvalidatesLateGrantAndAllowsRetry() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        requests.begin(10001, 42, 7, 0);
        requests.cancel(10002, 42);
        requests.cancel(10001, 43);
        assertFalse(requests.begin(10001, 42, 8, 1));
        requests.cancel(10001, 42);
        assertFalse(requests.consume(10001, 10001, 42, 7, 2));
        assertTrue(requests.begin(10001, 42, 8, 3));
    }

    @Test
    public void orderedDenialClearsARequestThatBeginsAfterSynchronousRevocation() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        requests.cancel(10001, 42);
        assertTrue(requests.begin(10001, 42, 7, 1));
        // requestPermission and the close-path denial use the same oneway Binder node.
        assertTrue(requests.consume(10001, 10001, 42, 7, 2));
        assertFalse(requests.consume(10001, 10001, 42, 7, 3));
        assertTrue(requests.begin(10001, 42, 8, 4));
    }

    @Test
    public void exactProcessRequestMayBeConsumedOnlyOnce() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        assertTrue(requests.begin(10001, 42, 7, 0));
        assertTrue(requests.consume(10001, 10001, 42, 7, 1));
        assertFalse(requests.consume(10001, 10001, 42, 7, 2));
    }

    @Test
    public void noRequestAndExpiredRequestFailClosed() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        assertFalse(requests.consume(10001, 10001, 42, 7, 0));
        requests.begin(10001, 42, 7, 0);
        assertFalse(requests.consume(10001, 10001, 42, 7, 60000));
    }

    @Test
    public void anotherUidPidOrCodeCannotConsumeConsent() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        requests.begin(10001, 42, 7, 0);
        assertFalse(requests.consume(10002, 10001, 42, 7, 1));
        assertFalse(requests.consume(10001, 10002, 42, 7, 1));
        assertFalse(requests.consume(10001, 10001, 43, 7, 1));
        assertFalse(requests.consume(10001, 10001, 42, 8, 1));
        assertTrue(requests.consume(10001, 10001, 42, 7, 1));
    }

    @Test
    public void concurrentRequestCannotReplaceVisibleConsentButExpiryAllowsRetry() {
        EqoPermissionRequests requests = new EqoPermissionRequests();
        requests.begin(10001, 42, 7, 0);
        assertFalse(requests.begin(10001, 42, 8, 1));
        assertTrue(requests.begin(10001, 42, 8, 60000));
        assertFalse(requests.consume(10001, 10001, 42, 7, 60001));
        assertTrue(requests.consume(10001, 10001, 42, 8, 60001));
    }
}
