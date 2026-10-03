// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: server-shared/src/main/java/rikka/shizuku/server/util/AbiUtil.java
package rikka.shizuku.server.util;

import android.os.Build;

public class AbiUtil {

    private static Boolean has32Bit;

    public static boolean has32Bit() {
        if (has32Bit == null) {
            has32Bit = Build.SUPPORTED_32_BIT_ABIS.length > 0;
        }
        return has32Bit;
    }
}
