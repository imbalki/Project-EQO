package rikka.shizuku.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * EQO-authored (TASK-007, issue #12).
 *
 * <p>Replaces upstream Shizuku's "the manager app must be installed and it is exactly
 * {@code moe.shizuku.privileged.api}" hard gate
 * (Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea,
 * path: server/src/main/java/rikka/shizuku/server/ShizukuService.java, the
 * {@code getManagerApplicationInfo()}/{@code MANAGER_APPLICATION_ID} equality checks)
 * with an EQO app-id allowlist: a caller whose uid owns one of these package names is
 * the trusted manager of the helper server.</p>
 *
 * <p>The list must stay in lockstep with {@link ServerConstants#MANAGER_APPLICATION_ID}
 * (the id the server delivers the binder to); {@code HelperRenameLockstepTest} enforces
 * that. Unit tests use {@link #isManagerAppId(String)}, which is pure JVM logic.</p>
 */
public final class EqoManagerAllowlist {

    private static final List<String> ALLOWED_MANAGER_APP_IDS =
            Collections.unmodifiableList(Arrays.asList(
                    "ai.eqo.app"
            ));

    private EqoManagerAllowlist() {
    }

    public static List<String> getAllowedManagerAppIds() {
        return ALLOWED_MANAGER_APP_IDS;
    }

    public static boolean isManagerAppId(String packageName) {
        return packageName != null && ALLOWED_MANAGER_APP_IDS.contains(packageName);
    }
}
