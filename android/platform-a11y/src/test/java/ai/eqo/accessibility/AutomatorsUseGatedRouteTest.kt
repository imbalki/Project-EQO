/*
 * EQO (TASK-012, issue #17): security follow-up SF-1 — automators must never
 * reach the raw service action surface directly; every action goes through the
 * takeover-gated facade. This guard FAILS against the pre-fix sources (which
 * called service.findAndClick / performImeEnter / performGlobalAction /
 * performScroll / clickCoordinates directly).
 */
package ai.eqo.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AutomatorsUseGatedRouteTest {
    private fun androidRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "app").isDirectory) {
                return dir
            }
            dir = dir.parentFile ?: break
        }
        error("could not locate the android/ root from ${System.getProperty("user.dir")}")
    }

    private val automatorFiles =
        listOf(
            "GenericAppAutomator.kt",
            "WhatsAppAutomator.kt",
            "TelegramAutomator.kt",
            "SmsAutomator.kt",
        )

    /** Source guard, not a Kotlin type proof: strip comments/literals before scanning. */
    private fun codeOnly(text: String): String =
        Regex("""\x22{3}[\s\S]*?\x22{3}|\x22(?:\\.|[^\x22\\])*\x22|/\*[\s\S]*?\*/|//[^\n]*""")
            .replace(text, " ")

    private val rawCall =
        Regex(
            """\b([A-Za-z_]\w*)\s*\??\.\s*(findAndClick|findAndClickById|findAndType|findAndTypeById|""" +
                """performImeEnter|performScroll|performGlobalAction|performGlobalBack|performGlobalHome|""" +
                """performAction|clickCoordinates)\s*\(""",
        )

    private fun hasGatedProvider(code: String): Boolean =
        Regex(
            """var\s+actionsProvider\s*:\s*\(\)\s*->\s*GatedServiceActions\?\s*=\s*\{\s*""" +
                """EQOAccessibilityService\.getInstance\(\)\?\.gatedActions\s*}""",
        ).containsMatchIn(code)

    private fun hasActionsBinding(code: String): Boolean {
        val provider =
            hasGatedProvider(code) &&
                Regex("""val\s+actions\s*=\s*actionsProvider\(\)""").containsMatchIn(code)
        val direct =
            Regex("""val\s+service\s*=\s*EQOAccessibilityService\.getInstance\(\)""").containsMatchIn(code) &&
                Regex("""val\s+actions\s*=\s*service\.gatedActions\b""").containsMatchIn(code)
        val bindings = Regex("""\b(?:val|var)\s+actions\b""").findAll(code).count()
        return bindings == 1 && (provider || direct)
    }

    private fun hasRoute(text: String): Boolean {
        val code = codeOnly(text)
        return (hasGatedProvider(code) || hasActionsBinding(code)) &&
            (
                Regex("""\bgated\s*\{\s*it\.\w+\s*\(""").containsMatchIn(code) ||
                    hasActionsBinding(code) &&
                    Regex("""\bactions\.\w+\s*\(""").containsMatchIn(code)
            )
    }

    private fun offenders(text: String): List<String> {
        val code = codeOnly(text)
        val gatedCoordinates =
            Regex("""\bgated\s*\{\s*it\.clickCoordinates\s*\(""")
                .findAll(code)
                .map { it.range.last }
                .toSet()
        return rawCall
            .findAll(code)
            .filterNot { call ->
                (hasGatedProvider(code) || hasActionsBinding(code)) &&
                    (
                        call.groupValues[1] == "actions" &&
                            hasActionsBinding(code) ||
                            call.groupValues[1] == "it" &&
                            call.range.last in gatedCoordinates
                    )
            }.map { it.value }
            .toList()
    }

    @Test
    fun aliasedMultilineCallsAndFakeCommentRoutesCannotEvadeTheGuard() {
        listOf("svc", "ops", "renamed", "node").forEach { receiver ->
            assertTrue(offenders("$receiver\n . performGlobalAction(1)").isNotEmpty())
        }
        assertTrue(!hasRoute("// actions.findAndClick(\"Send\")\n// gated { it.pressBack() }"))
        assertTrue(!hasRoute("val fake = \"actions.findAndClick(\"" + "Send\")\""))
    }

    @Test
    fun noAutomatorCallsTheRawServiceActionSurface() {
        val mainDir = File(androidRoot(), "platform-a11y/src/main")
        val found =
            automatorFiles.flatMap { name ->
                val file = mainDir.walkTopDown().first { it.name == name }
                offenders(file.readText()).map { "$name: $it" }
            }
        assertTrue("SF-1: raw (ungated) calls found: $found", found.isEmpty())
    }

    @Test
    fun everyAutomatorRoutesThroughTheGatedFacade() {
        val mainDir = File(androidRoot(), "platform-a11y/src/main")
        automatorFiles.forEach { name ->
            val text = mainDir.walkTopDown().first { it.name == name }.readText()
            assertTrue("SF-1: $name must bind and call the gated facade", hasRoute(text))
        }
    }
}
