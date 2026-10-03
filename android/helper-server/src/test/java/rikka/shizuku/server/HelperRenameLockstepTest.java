package rikka.shizuku.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/** EQO-authored TASK-007: pure JVM guards for the D-004 server/client rename. */
public class HelperRenameLockstepTest {
    private static final Path ANDROID_ROOT = findAndroidRoot();

    @Test
    public void mainSourcesAndManifestsHaveNoOldManagerPermissions() throws IOException {
        // androidTest deliberately contains the mismatched-permission negative fixture.
        for (String module : List.of("helper-server", "helper-client", "app")) {
            Path main = ANDROID_ROOT.resolve(module + "/src/main");
            assertTrue("Missing main source tree: " + main, Files.isDirectory(main));
            try (Stream<Path> paths = Files.walk(main)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(HelperRenameLockstepTest::isSource).toList()) {
                    assertFalse("Old manager permission in " + path,
                            new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8).contains("moe.shizuku.manager.permission"));
                }
            }
        }
    }

    @Test
    public void serverClientAndManifestRenameStringsAgree() throws IOException {
        String provider = read("helper-client/src/main/java/rikka/shizuku/ShizukuProvider.java");
        String sender = read("helper-server/src/main/java/rikka/shizuku/server/BinderSender.java");
        String manifest = read("helper-server/src/main/AndroidManifest.xml");
        String permission = "ai.eqo.app.helper.permission.API_V23";
        String managerPermission = "ai.eqo.app.helper.permission.MANAGER";
        String binderExtra = "ai.eqo.app.helper.intent.extra.BINDER";

        assertEquals(permission, ServerConstants.PERMISSION);
        assertEquals(permission, literal(provider, "PERMISSION"));
        assertEquals(permission, literal(sender, "PERMISSION"));
        assertEquals(managerPermission, literal(sender, "PERMISSION_MANAGER"));
        assertTrue(manifest.contains("android:name=\"" + permission + "\""));
        assertTrue(manifest.contains("android:name=\"" + managerPermission + "\""));
        assertTrue(manifest.contains("android:permissionGroup=\"ai.eqo.app.helper.permission-group.API\""));
        assertTrue(manifest.contains("android:authorities=\"${applicationId}.helper\""));

        assertEquals("ai.eqo.app", ServerConstants.MANAGER_APPLICATION_ID);
        assertEquals(ServerConstants.MANAGER_APPLICATION_ID, literal(provider, "MANAGER_APPLICATION_ID"));
        assertEquals(List.of(ServerConstants.MANAGER_APPLICATION_ID), EqoManagerAllowlist.getAllowedManagerAppIds());
        assertTrue(read("app/build.gradle.kts").contains("applicationId = \"ai.eqo.app\""));
        assertEquals(binderExtra, literal(provider, "EXTRA_BINDER"));
        assertEquals(binderExtra, literal(read(
                "helper-server/src/main/java/moe/shizuku/starter/ServiceStarter.java"), "EXTRA_BINDER"));
        assertEquals(binderExtra, literal(read(
                "helper-server/src/main/java/moe/shizuku/manager/ShizukuManagerProvider.kt"), "EXTRA_BINDER"));
        assertTrue(read("helper-server/src/main/java/rikka/shizuku/server/ShizukuService.java")
                .contains("extra.putParcelable(\"" + binderExtra + "\""));
        assertEquals("ai.eqo.app.helper.action.BINDER_RECEIVED", literal(provider, "ACTION_BINDER_RECEIVED"));
    }

    @Test
    public void allowlistAcceptsOnlyExactEqoAppId() {
        assertTrue(EqoManagerAllowlist.isManagerAppId("ai.eqo.app"));
        for (String rejected : new String[] {null, "", "ai.eqo", "ai.eqo.app.test", "ai.eqo.apps",
                "AI.eqo.app", " ai.eqo.app", "ai.eqo.app ", "moe.shizuku.privileged.api", "other.app"}) {
            assertFalse("Unexpected allowlist match: " + rejected, EqoManagerAllowlist.isManagerAppId(rejected));
        }
    }

    private static String read(String relativePath) throws IOException {
        return new String(Files.readAllBytes(ANDROID_ROOT.resolve(relativePath)), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String literal(String source, String name) {
        Matcher matcher = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]+)\"").matcher(source);
        assertTrue("Missing string constant: " + name, matcher.find());
        String value = matcher.group(1);
        assertFalse("Ambiguous string constant: " + name, matcher.find());
        return value;
    }

    private static boolean isSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".xml")
                || name.endsWith(".aidl") || name.endsWith(".cpp") || name.endsWith(".h");
    }

    private static Path findAndroidRoot() {
        for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("helper-server/src/main"))) {
                return dir;
            }
            if (Files.isDirectory(dir.resolve("android/helper-server/src/main"))) {
                return dir.resolve("android");
            }
        }
        throw new IllegalStateException("Cannot locate Android source root from user.dir");
    }
}
