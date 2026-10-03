// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/UserService.java
package rikka.shizuku.server;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.ddm.DdmHandleAppName;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UserService {

    private static String TAG;

    public static void setTag(String tag) {
        UserService.TAG = tag;
    }

    // TASK-007 fix card: the [DiscouragedPrivateApi] lint finding on the mInitialApplication
    // reflection in create() is deliberate and cannot be removed without gutting the helper -
    // driving hidden framework APIs is this fork's entire purpose (upstream did the same through
    // dev.rikka.tools.refine stubs; the fork reflects instead, see task-007-helper-spike.md
    // "Scope of the fork"). Kept as this one-site annotation, not a lint baseline and not a rule
    // suppression; flagged for the security pass in the evidence file.
    @SuppressLint("DiscouragedPrivateApi")
    @Nullable
    public static Pair<IBinder, String> create(String[] args) {
        String name = null;
        String token = null;
        String pkg = null;
        String cls = null;
        int uid = -1;

        for (String arg : args) {
            if (arg.startsWith("--debug-name=")) {
                name = arg.substring(13);
            } else if (arg.startsWith("--token=")) {
                token = arg.substring(8);
            } else if (arg.startsWith("--package=")) {
                pkg = arg.substring(10);
            } else if (arg.startsWith("--class=")) {
                cls = arg.substring(8);
            } else if (arg.startsWith("--uid=")) {
                uid = Integer.parseInt(arg.substring(6));
            }
        }

        int userId = uid / 100000;

        Log.i(TAG, String.format("starting service %s/%s...", pkg, cls));

        IBinder service;

        try {
            ActivityThread activityThread = ActivityThread.systemMain();
            Context systemContext = activityThread.getSystemContext();

            DdmHandleAppName.setAppName(name != null ? name : pkg + ":user_service", userId);

            // TASK-007 (issue #12): upstream gets this UserHandle and the hidden
            // Context.createPackageContextAsUser call through refine-time stubs
            // (UserHandleHidden / ContextHidden + Refine.unsafeCast, rewritten to the real
            // framework classes by dev.rikka.tools.refine). The EQO fork runs without that
            // plugin, so the same two hidden calls go through reflection - the same technique
            // this method already uses for mPackageInfo / makeApplication below.
            // minSdk 30 (TASK-007 fix card): the pre-N UserHandle(int) constructor branch was
            // dead code (lint [ObsoleteSdkInt]); UserHandle.of is the only variant used here.
            UserHandle userHandle =
                    (UserHandle) UserHandle.class.getMethod("of", int.class).invoke(null, userId);
            Context context = (Context) Context.class
                    .getMethod("createPackageContextAsUser", String.class, int.class, UserHandle.class)
                    .invoke(systemContext, pkg, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY, userHandle);
            Field mPackageInfo = context.getClass().getDeclaredField("mPackageInfo");
            mPackageInfo.setAccessible(true);
            Object loadedApk = mPackageInfo.get(context);
            Method makeApplication = loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
            Application application = (Application) makeApplication.invoke(loadedApk, true, null);
            Field mInitialApplication = activityThread.getClass().getDeclaredField("mInitialApplication");
            mInitialApplication.setAccessible(true);
            mInitialApplication.set(activityThread, application);

            ClassLoader classLoader = application.getClassLoader();
            Class<?> serviceClass = classLoader.loadClass(cls);
            Constructor<?> constructorWithContext = null;
            try {
                constructorWithContext = serviceClass.getConstructor(Context.class);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            if (constructorWithContext != null) {
                service = (IBinder) constructorWithContext.newInstance(application);
            } else {
                service = (IBinder) serviceClass.newInstance();
            }
        } catch (Throwable tr) {
            Log.w(TAG, String.format("unable to start service %s/%s...", pkg, cls), tr);
            return null;
        }

        return new Pair<>(service, token);
    }
}
