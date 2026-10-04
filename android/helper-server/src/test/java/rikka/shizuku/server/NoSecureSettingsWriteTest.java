package rikka.shizuku.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/** EQO-authored TASK-007 SF-1: all modules, not just the helper or accessibility. */
public class NoSecureSettingsWriteTest {
    // Preserve string literals (including commands/URIs), remove only comments.
    private static final Pattern TOKENS = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*+\"|'(?:\\\\.|[^'\\\\])*+'|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->");
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("WRITE_SECURE_SETTINGS"),
            Pattern.compile("\\bSecure\\s*\\.\\s*put\\w*"),
            Pattern.compile("\\bsettings\\s+(?:--user\\s+\\S+\\s+)?(?:put|delete|reset)\\s+secure\\b"),
            // Direct provider writes and reflection need review; no production use today.
            Pattern.compile("content://settings/secure"),
            Pattern.compile("[\"'](?:android\\.provider\\.Settings\\$)?Secure[\"']"));

    @Test
    public void everyModuleMainSourceAndManifestRejectsSecureSettingsWrites() throws IOException {
        Path root = findAndroidRoot();
        int scanned = 0;
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path main = module.resolve("src/main");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> paths = Files.walk(main)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        // Scan every text source/resource, including native code and scripts.
                        byte[] bytes = Files.readAllBytes(path);
                        boolean binary = false;
                        for (byte b : bytes) {
                            if (b == 0) {
                                binary = true;
                                break;
                            }
                        }
                        if (!binary) {
                            assertSafe(path.toString(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                            scanned++;
                        }
                    }
                }
            }
        }
        assertTrue("No main sources scanned", scanned > 0);
    }

    @Test
    public void guardRejectsRepresentativeRegressionsButAllowsReadsAndComments() {
        for (String source : List.of(
                "import static android.Manifest.permission.WRITE_SECURE_SETTINGS;",
                "<uses-permission android:name=\"android.permission.WRITE_SECURE_SETTINGS\" />",
                "Settings.Secure\n . putString(resolver, key, value);",
                "import static android.provider.Settings.Secure.putInt;",
                "\"settings --user 0 put secure enabled_accessibility_services ai.eqo.app\"",
                "\"settings delete secure key\"",
                "resolver.update(Uri.parse(\"content://settings/secure\"), values, null, null);")) {
            assertTrue("Guard missed: " + source, unsafe(source));
        }
        assertFalse(unsafe("Settings.Secure.getString(resolver, key);"));
        assertFalse(unsafe("// WRITE_SECURE_SETTINGS\n/* Settings.Secure.putInt() */"));
        assertFalse(unsafe("<!-- android.permission.WRITE_SECURE_SETTINGS -->"));
        assertTrue(unsafe("\"content://settings/secure\" // comment"));
    }

    private static void assertSafe(String path, String source) {
        assertFalse("Secure settings write or permission in " + path, unsafe(source));
    }

    private static boolean unsafe(String source) {
        String code = TOKENS.matcher(source).replaceAll(match -> {
            String token = match.group();
            return token.startsWith("//") || token.startsWith("/*") || token.startsWith("<!--") ? " " : java.util.regex.Matcher.quoteReplacement(token);
        });
        return FORBIDDEN.stream().anyMatch(pattern -> pattern.matcher(code).find());
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
