package ai.eqo.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TASK-009 fixes guard: the owner-prompt signals (vibrate / beep) must never
 * crash the records activity. This test APK declares no VIBRATE permission by
 * design, so [android.os.Vibrator.vibrate] throws SecurityException on stock
 * builds; an unguarded call crashed EqoTestTargetActivity the moment a prompt
 * was set and closed every owner touch window before it opened. Every
 * `vibrate(` call in the androidTest sources must therefore sit inside a
 * `runCatching { ... }` block.
 */
class OwnerPromptSafetyTest {
    private fun androidRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "app").isDirectory) {
                return dir
            }
            dir = dir.parentFile
                ?: break
        }
        error("could not locate the android/ root from ${System.getProperty("user.dir")}")
    }

    @Test
    fun everyVibrateCallInTheRecordsHarnessIsGuardedByRunCatching() {
        val moduleDir = File(androidRoot(), "platform-a11y/src/androidTest")
        val offenders = mutableListOf<String>()
        var callsSeen = 0
        val blockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val lineComments = Regex("//.*")
        moduleDir
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                // Comments may mention the call in prose; only real code counts.
                val text = lineComments.replace(blockComments.replace(file.readText(), ""), "")
                var from = 0
                while (true) {
                    val idx = text.indexOf("vibrate(", startIndex = from)
                    if (idx < 0) break
                    from = idx + 1
                    callsSeen++
                    val guardAt = text.lastIndexOf("runCatching", startIndex = idx)
                    val guarded =
                        guardAt >= 0 &&
                            text.substring(guardAt, idx).count { it == '{' } >
                            text.substring(guardAt, idx).count { it == '}' }
                    if (!guarded) {
                        offenders.add("${file.name}: unguarded vibrate( at offset $idx")
                    }
                }
            }
        assertTrue(
            "TASK-009 fixes: expected at least the owner-prompt vibrate( call in the harness, found none",
            callsSeen > 0,
        )
        assertTrue(
            "TASK-009 fixes: owner-prompt signals must not crash the records activity - " +
                "wrap every vibrate( in runCatching: $offenders",
            offenders.isEmpty(),
        )
    }
}
