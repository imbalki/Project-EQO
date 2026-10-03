// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/util/HandlerUtil.java
package rikka.shizuku.server.util;

import android.os.Handler;
import android.os.Looper;

import java.util.Objects;

public class HandlerUtil {

    private static Handler mainHandler;

    public static void setMainHandler(Handler mainHandler) {
        HandlerUtil.mainHandler = mainHandler;
    }

    public static Handler getMainHandler() {
        Objects.requireNonNull(mainHandler, "Please call setMainHandler first");
        return HandlerUtil.mainHandler;
    }
}
