// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/util/UserHandleCompat.java
package rikka.shizuku.server.util;

public class UserHandleCompat {

    public static final int PER_USER_RANGE = 100000;

    public static int getUserId(int uid) {
        return uid / PER_USER_RANGE;
    }

    public static int getAppId(int uid) {
        return uid % PER_USER_RANGE;
    }
}

