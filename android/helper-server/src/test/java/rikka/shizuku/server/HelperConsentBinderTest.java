// EQO-authored production-handler regression for consent transport and authenticated identity.
package rikka.shizuku.server;

import static org.junit.Assert.*;
import static rikka.shizuku.ShizukuApiConstants.*;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HelperConsentBinderTest {
    private static final int UID = 10042;
    private static final int PID = 4242;
    private TestService server;
    private Callback callback;
    private IShizukuService remote;
    private int requestFlags = -1;
    private int confirmationFlags = -1;

    static class TestService extends ShizukuService {
        List<String> packages = Collections.singletonList("ai.eqo.app");
        TestService() { super(false); }
        @Override protected List<String> consentPackages(int uid) { return packages; }
    }

    static class Callback extends IShizukuApplication.Stub {
        int shown;
        Boolean allowed;
        @Override public void bindApplication(Bundle data) { }
        @Override public void showPermissionConfirmation(int uid, int pid, String pkg, int code) {
            assertEquals(UID, uid);
            assertEquals(PID, pid);
            assertEquals("ai.eqo.app", pkg);
            shown++;
        }
        @Override public void dispatchRequestPermissionResult(int code, Bundle data) {
            allowed = data.getBoolean(REQUEST_PERMISSION_REPLY_ALLOWED);
        }
    }

    @Before public void setup() {
        ShadowBinder.setCallingUid(UID);
        ShadowBinder.setCallingPid(PID);
        server = new TestService();
        callback = new Callback();
        Bundle attach = new Bundle();
        attach.putString(ATTACH_APPLICATION_PACKAGE_NAME, "ai.eqo.app");
        attach.putInt(ATTACH_APPLICATION_API_VERSION, SERVER_VERSION);
        server.attachApplication(callback, attach);
        // Force generated AIDL Proxy/Stub parcel handling, rather than a Java interface fake.
        remote = IShizukuService.Stub.asInterface(new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == 15) requestFlags = flags;
                if (code == 105) confirmationFlags = flags;
                int pid = Binder.getCallingPid();
                // Host Binder doesn't emulate remote oneway PID=0; model that platform rule explicitly.
                if ((flags & IBinder.FLAG_ONEWAY) != 0) ShadowBinder.setCallingPid(0);
                try { return server.onTransact(code, data, reply, flags); }
                finally { ShadowBinder.setCallingPid(pid); }
            }
        });
    }

    private Bundle decision(boolean allow) {
        Bundle result = new Bundle();
        result.putBoolean(REQUEST_PERMISSION_REPLY_ALLOWED, allow);
        return result;
    }

    @Test public void proxyGrantUsesSynchronousAuthenticatedTransactions() throws Exception {
        remote.requestPermission(7);
        assertEquals(1, callback.shown);
        assertFalse(remote.checkSelfPermission());
        remote.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true));
        assertEquals(Boolean.TRUE, callback.allowed);
        assertTrue(remote.checkSelfPermission());
        assertEquals(0, requestFlags & IBinder.FLAG_ONEWAY);
        assertEquals(0, confirmationFlags & IBinder.FLAG_ONEWAY);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
    }

    @Test public void denyFailsClosedAndImmediateRetryCanGrant() throws Exception {
        remote.requestPermission(7);
        remote.dispatchPermissionConfirmationResult(UID, PID, 7, decision(false));
        assertFalse(remote.checkSelfPermission());
        assertEquals(Boolean.FALSE, callback.allowed);
        remote.requestPermission(8);
        remote.dispatchPermissionConfirmationResult(UID, PID, 8, decision(true));
        assertTrue(remote.checkSelfPermission());
    }

    @Test public void cancellationInvalidatesOldReplyAndAllowsImmediateRetry() throws Exception {
        remote.requestPermission(7);
        remote.updateFlagsForUid(UID, ConfigManager.MASK_PERMISSION, 0);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        remote.requestPermission(8);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        remote.dispatchPermissionConfirmationResult(UID, PID, 8, decision(true));
        assertTrue(remote.checkSelfPermission());
        remote.updateFlagsForUid(UID, ConfigManager.MASK_PERMISSION, 0);
        assertFalse(remote.checkSelfPermission());
    }

    @Test public void onewayPidZeroCannotRequestOrConfirmEvenWithSuppliedRealPid() {
        ShadowBinder.setCallingPid(0);
        assertThrows(SecurityException.class, () -> server.requestPermission(7));
        assertEquals(0, callback.shown);
        ShadowBinder.setCallingPid(PID);
        server.requestPermission(7);
        ShadowBinder.setCallingPid(0);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        ShadowBinder.setCallingPid(PID);
        assertFalse(server.checkSelfPermission());
        server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(false));
    }

    @Test public void unexpectedOnewayTransportIsRejectedBeforeDispatch() {
        for (int code : new int[] {15, 105}) {
            Parcel data = Parcel.obtain();
            try {
                assertThrows(SecurityException.class,
                        () -> server.onTransact(code, data, null, IBinder.FLAG_ONEWAY));
            } finally { data.recycle(); }
        }
        assertEquals(0, callback.shown);
        assertFalse(server.checkSelfPermission());
    }

    @Test public void otherClientAndUidCannotConfirmOrRevokeOwnRequest() throws Exception {
        remote.requestPermission(7);
        ShadowBinder.setCallingPid(PID + 1);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        assertThrows(IllegalStateException.class,
                () -> server.updateFlagsForUid(UID, ConfigManager.MASK_PERMISSION, 0));
        ShadowBinder.setCallingPid(PID);
        ShadowBinder.setCallingUid(UID + 1);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        assertThrows(SecurityException.class,
                () -> server.updateFlagsForUid(UID, ConfigManager.MASK_PERMISSION, 0));
        ShadowBinder.setCallingUid(UID);
        remote.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true));
        assertTrue(remote.checkSelfPermission());
    }

    @Test public void expiredProductionRequestCannotGrantAndCanRetry() throws Exception {
        remote.requestPermission(7);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(61));
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        assertFalse(remote.checkSelfPermission());
        remote.requestPermission(8);
        remote.dispatchPermissionConfirmationResult(UID, PID, 8, decision(true));
        assertTrue(remote.checkSelfPermission());
    }

    @Test public void attachedSiblingProcessCannotGrantOriginalProcess() throws Exception {
        remote.requestPermission(7);
        ShadowBinder.setCallingPid(PID + 1);
        Bundle attach = new Bundle();
        attach.putString(ATTACH_APPLICATION_PACKAGE_NAME, "ai.eqo.app");
        attach.putInt(ATTACH_APPLICATION_API_VERSION, SERVER_VERSION);
        server.attachApplication(new Callback(), attach);
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true)));
        assertThrows(SecurityException.class,
                () -> server.dispatchPermissionConfirmationResult(UID, PID + 1, 7, decision(true)));
        remote.updateFlagsForUid(UID, ConfigManager.MASK_PERMISSION, 0);
        assertFalse(remote.checkSelfPermission());
        ShadowBinder.setCallingPid(PID);
        remote.dispatchPermissionConfirmationResult(UID, PID, 7, decision(true));
        assertTrue(remote.checkSelfPermission());
        ShadowBinder.setCallingPid(PID + 1);
        assertFalse(remote.checkSelfPermission());
    }

    @Test public void sharedUidCannotRequestConsent() throws Exception {
        server.packages = Arrays.asList("ai.eqo.app", "hostile.app");
        remote.requestPermission(7);
        assertEquals(0, callback.shown);
        assertEquals(Boolean.FALSE, callback.allowed);
        assertFalse(remote.checkSelfPermission());
    }
}
