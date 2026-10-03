// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea, path: server/src/main/java/rikka/shizuku/server/api/IContentProviderUtils.java
package rikka.shizuku.server.api;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import rikka.shizuku.server.util.OsUtils;

public class IContentProviderUtils {

    public static Bundle callCompat(@NonNull IContentProvider provider, @Nullable String callingPkg, @Nullable String authority, @Nullable String method, @Nullable String arg, @Nullable Bundle extras) throws RemoteException {
        Bundle result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result = provider.call((new AttributionSource.Builder(OsUtils.getUid())).setPackageName(callingPkg).build(), authority, method, arg, extras);
        } else {
            // minSdk 30 (TASK-007 fix card): the pre-30 call() overloads were dead code
            // (lint [ObsoleteSdkInt]); this is the API 30 signature.
            result = provider.call(callingPkg, (String) null, authority, method, arg, extras);
        }

        return result;
    }
}
