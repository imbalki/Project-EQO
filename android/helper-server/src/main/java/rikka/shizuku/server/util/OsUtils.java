// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/util/OsUtils.java
package rikka.shizuku.server.util;

import android.os.SELinux;

public class OsUtils {

    private static final int UID = android.system.Os.getuid();
    private static final int PID = android.system.Os.getpid();
    private static final String SELINUX_CONTEXT;

    static {
        String context;
        try {
            context = SELinux.getContext();
        } catch (Throwable tr) {
            context =null;
        }
        SELINUX_CONTEXT = context;
    }


    public static int getUid() {
        return UID;
    }

    public static int getPid() {
        return PID;
    }

    public static String getSELinuxContext() {
        return SELINUX_CONTEXT;
    }
}

