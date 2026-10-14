package ai.eqo.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistryPreviewTextTest {
    @Test fun everyCatalogActionHasPlainWordingWithAndWithoutParameters() {
        val hiddenActions = setOf("WAIT", "ASK_USER")
        val rawIdentifier = Regex("[A-Z]+_[A-Z_]+|[a-z]+_[a-z_]+")
        ActionSchema.ALL_ACTIONS.filterNot { it.name in hiddenActions }.forEach { action ->
            val params = action.params.associate { it.name to "example" }
            listOf(emptyMap(), params).forEach { input ->
                val preview = RegistryPreviewText.describe(action.name, input)
                assertTrue(action.name, preview.isNotBlank())
                assertFalse(action.name, preview.contains(action.name))
                assertFalse("${action.name}: $preview", rawIdentifier.containsMatchIn(preview))
                action.params.forEach { param ->
                    assertFalse("${action.name}: ${param.name}", preview.contains("${param.name}="))
                }
            }
        }
    }

    @Test fun requestedExamplesAreExact() {
        assertEquals(
            "turn the flashlight on",
            RegistryPreviewText.describe("TOGGLE_FLASHLIGHT", mapOf("state" to "on")),
        )
        assertEquals(
            "tap \"Search\" in the current app (may submit or send)",
            RegistryPreviewText.describe("CLICK_TEXT", mapOf("text" to "Search")),
        )
        assertEquals(
            "type \"banana\" into \"Search\" in the current app",
            RegistryPreviewText.describe("TYPE_TEXT", mapOf("content" to "banana", "searchText" to "Search")),
        )
        assertEquals(
            "press Enter in the current app (may submit or send)",
            RegistryPreviewText.describe("PRESS_ENTER", emptyMap()),
        )
    }

    @Test fun toggleDefaultsAndOffUseNaturalLanguage() {
        assertEquals(
            "switch the flashlight to the opposite setting",
            RegistryPreviewText.describe("TOGGLE_FLASHLIGHT", emptyMap()),
        )
        assertEquals("turn Wi-Fi off", RegistryPreviewText.describe("TOGGLE_WIFI", mapOf("state" to "off")))
        assertEquals(
            "set screen brightness to \"50\" percent",
            RegistryPreviewText.describe("SET_BRIGHTNESS", emptyMap()),
        )
    }

    @Test fun allDisplayedCatalogValuesAreEscapedWithoutMutatingParameters() {
        val payload = "hello\n\r\t\u202E\u2066\u200F\uFEFF\\\"नमस्ते 😀"
        val shown = TaskDisplayText.escape(PlanOutputReferences.describe(payload)).replace("\"", "\\\"")
        ActionSchema.ALL_ACTIONS.filterNot { it.name == "ASK_USER" }.forEach { action ->
            val params = action.params.associate { it.name to payload }
            val before = params.toMap()
            val preview = RegistryPreviewText.describe(action.name, params)
            assertFalse(action.name, preview.any { Character.getType(it) == Character.FORMAT.toInt() })
            assertFalse(action.name, preview.any { Character.isISOControl(it) })
            action.params.filterNot { it.name in setOf("draftOnly", "video") }.forEach { param ->
                val single = RegistryPreviewText.describe(action.name, mapOf(param.name to payload))
                assertTrue("${action.name}: ${param.name}", single.contains("\"$shown\""))
            }
            assertEquals(action.name, before, params)
        }
    }

    @Test fun previousOutputsAreDescribedBeforeEscapingAndNotRecursivelyRendered() {
        assertEquals(
            "type \"the result result of step 1\\u{202E}\" into \"Search\" in the current app",
            RegistryPreviewText.describe(
                "TYPE_TEXT",
                mapOf("content" to "{{step:1:result}}\u202E", "searchText" to "Search"),
            ),
        )
        assertEquals(
            "tap \"{content}\\\"\\\\\" in the current app (may submit or send)",
            RegistryPreviewText.describe("CLICK_TEXT", mapOf("text" to "{content}\"\\")),
        )
    }

    @Test fun sendsDraftsAndManualGroupEntryAreNotConfused() {
        val message = mapOf("contact" to "Sam", "message" to "Hello")
        assertEquals(
            "draft a WhatsApp message to \"Sam\" saying \"Hello\"; you press Send",
            RegistryPreviewText.describe("SEND_WHATSAPP", message + ("draftOnly" to "true")),
        )
        listOf("SEND_WHATSAPP", "SEND_TELEGRAM", "SEND_SMS").forEach { action ->
            assertTrue(action, RegistryPreviewText.describe(action, message).contains("EQO attempts to press Send"))
        }
        assertEquals(
            "open WhatsApp for group \"Family\"; you find the group, enter \"Hello\" and press Send",
            RegistryPreviewText.describe("SEND_WHATSAPP_GROUP", mapOf("groupName" to "Family", "message" to "Hello")),
        )
        assertTrue(
            RegistryPreviewText
                .describe(
                    "SEND_EMAIL",
                    mapOf("to" to "Sam", "subject" to "Greeting", "body" to "Hello"),
                ).contains("EQO attempts to press Send; delivery is not verified"),
        )
        assertTrue(
            RegistryPreviewText.describe("SOCIAL_CREATE_POST", mapOf("topic" to "release")).startsWith("draft"),
        )
    }

    @Test fun extraDetailsUseEnglishLabelsAndKeepCatalogOrder() {
        assertEquals(
            "create a to-do item titled \"Shop\"; due date: \"tomorrow\"; importance level: \"high\"",
            RegistryPreviewText.describe(
                "CREATE_TASK",
                linkedMapOf("priority" to "high", "dueDate" to "tomorrow", "title" to "Shop"),
            ),
        )
        assertEquals(
            "open website \"https://example.org\"; browser to use: \"Brave\"",
            RegistryPreviewText.describe("OPEN_URL", mapOf("url" to "https://example.org", "browser" to "Brave")),
        )
        assertTrue(
            RegistryPreviewText
                .describe(
                    "SEND_SMS",
                    mapOf("contact" to "Sam", "message" to "Hi", "attachment" to "find:invoice\u202E"),
                ).contains("files to attach: \"find:invoice\\u{202E}\""),
        )
    }

    @Test fun unsupportedActionsQuestionsAndUnknownParametersFailClosed() {
        listOf("ASK_USER", "NOT_A_REAL_ACTION").forEach { action ->
            val failure = runCatching { RegistryPreviewText.describe(action, emptyMap()) }.exceptionOrNull()
            assertTrue(action, failure is IllegalArgumentException)
        }
        assertTrue(
            runCatching {
                RegistryPreviewText.describe("OPEN_APP", mapOf("unexpectedKey" to "value"))
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }
}
