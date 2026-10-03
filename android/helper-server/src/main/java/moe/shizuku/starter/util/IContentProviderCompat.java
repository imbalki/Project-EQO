// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea, path: starter/src/main/java/moe/shizuku/starter/util/IContentProviderCompat.java
package moe.shizuku.starter.util;

import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class IContentProviderCompat {

    @Nullable
    public static Bundle call(@NonNull IContentProvider provider, @Nullable String attributeTag, @Nullable String callingPkg, @Nullable String authority, @Nullable String method, @Nullable String arg, @Nullable Bundle extras) throws RemoteException {
        Bundle reply;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                reply = provider.call((new AttributionSource.Builder(Os.getuid())).setAttributionTag(attributeTag).setPackageName(callingPkg).build(), authority, method, arg, extras);
            } catch (LinkageError e) {
                reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
            }
        } else {
            // minSdk 30 (TASK-007 fix card): the pre-30 call() overloads were dead code
            // (lint [ObsoleteSdkInt]); this is the API 30 signature.
            reply = provider.call(callingPkg, attributeTag, authority, method, arg, extras);
        }

        return reply;
    }
}
