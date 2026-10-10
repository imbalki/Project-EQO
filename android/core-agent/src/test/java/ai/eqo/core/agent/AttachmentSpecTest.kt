// Origin: EQO files-attachments, attachment parameter parsing, validation and plan preview tests.
package ai.eqo.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSpecTest {
    private val enabled =
        setOf("SEND_EMAIL", "SEND_WHATSAPP", "SEND_SMS", "TAKE_SCREENSHOT", "FIND_FILES", "LIST_FILES")
    private val phone = "+91XXXXXXXXXX"

    private fun plan(
        action: String,
        vararg params: Pair<String, String>,
    ): String {
        val body = params.joinToString(",") { "\"${it.first}\":\"${it.second}\"" }
        return """{"steps":[{"action":"$action","params":{$body}}]}"""
    }

    @Test fun parseSplitsOnBarAndNewlineAndDropsBlanks() {
        val parsed = AttachmentSpec.parse(" a.pdf | b/c.jpg\n\nlast_screenshot |")
        assertEquals(listOf("a.pdf", "b/c.jpg", "last_screenshot"), parsed)
        assertTrue(AttachmentSpec.parse(null).isEmpty())
        assertTrue(AttachmentSpec.parse("  ").isEmpty())
    }

    @Test fun lastScreenshotIsRecognisedIgnoringCase() {
        assertTrue(AttachmentSpec.isLastScreenshot(" Last_Screenshot "))
        assertFalse(AttachmentSpec.isLastScreenshot("last_screenshot.png"))
    }

    @Test fun errorsRejectTraversalLinksControlCharactersDuplicatesAndTooMany() {
        assertTrue(AttachmentSpec.errors("Download/report.pdf|last_screenshot").isEmpty())
        assertTrue(AttachmentSpec.errors("../secret").isNotEmpty())
        assertTrue(AttachmentSpec.errors("Download/../../x").isNotEmpty())
        assertTrue(AttachmentSpec.errors("..\\x").isNotEmpty())
        assertTrue(AttachmentSpec.errors("content://media/external/file/1").isNotEmpty())
        assertTrue(AttachmentSpec.errors("file:///data/x").isNotEmpty())
        assertTrue(AttachmentSpec.errors("a\u0007b").isNotEmpty())
        assertTrue(AttachmentSpec.errors("a.pdf|a.pdf").isNotEmpty())
        assertTrue(AttachmentSpec.errors((1..11).joinToString("|") { "f$it.txt" }).isNotEmpty())
        assertTrue(AttachmentSpec.errors("x".repeat(2000)).isNotEmpty())
    }

    @Test fun displayNamesShowOnlyFileNamesAndTheScreenshotWording() {
        assertEquals(
            listOf(
                "\"report.pdf\"",
                "your recent EQO screenshot, or newest gallery screenshot (name confirmed before sending)",
                "\"c.jpg\"",
            ),
            AttachmentSpec.displayNames("/storage/emulated/0/Download/report.pdf|last_screenshot|Pictures\\c.jpg"),
        )
    }

    @Test fun displayNameEscapesHiddenCharactersSoTheOwnerSeesTheRealName() {
        val shown = AttachmentSpec.displayName("Download/invoice‮fdp.exe")
        assertTrue(shown, shown.contains("\\u{202E}"))
    }

    @Test fun previewNamesEveryAttachedFile() {
        val steps =
            RegistryPlanVocabulary.parse(
                plan(
                    "SEND_EMAIL",
                    "to" to "owner@example.test",
                    "subject" to "Papers",
                    "body" to "Attached",
                    "attachment" to "Download/a.pdf|Download/b.pdf|last_screenshot",
                ),
                enabled,
            )
        val preview = TaskPlanPreview.describe(steps)
        assertTrue(preview, preview.contains("attaching 3 files: \"a.pdf\", \"b.pdf\", your recent EQO screenshot"))
        assertTrue(preview, preview.contains("or newest gallery screenshot (name confirmed before sending)"))
    }

    @Test fun previewUsesSingularForOneFileAndIsUnchangedWithoutAttachment() {
        val withFile =
            RegistryPlanVocabulary.parse(
                plan("SEND_WHATSAPP", "contact" to phone, "message" to "hi", "attachment" to "Download/a.pdf"),
                enabled,
            )
        assertTrue(TaskPlanPreview.describe(withFile).endsWith("; attaching 1 file: \"a.pdf\""))
        val without =
            RegistryPlanVocabulary.parse(plan("SEND_WHATSAPP", "contact" to phone, "message" to "hi"), enabled)
        assertFalse(TaskPlanPreview.describe(without).contains("attaching"))
    }

    @Test fun validatorRejectsUnsafeAttachmentValuesFromThePlanner() {
        val bad =
            plan("SEND_SMS", "contact" to phone, "message" to "hi", "attachment" to "../../data/secret")
        val failure = runCatching { RegistryPlanVocabulary.parse(bad, enabled) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("SEND_SMS"))
    }

    @Test fun attachmentIsRejectedOnActionsThatDoNotDeclareIt() {
        val failure =
            runCatching {
                RegistryPlanVocabulary.parse(plan("FIND_FILES", "query" to "cv", "attachment" to "a.pdf"), enabled)
            }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun promptTeachesAttachmentAndTheReadOnlyFileActions() {
        val prompt = RegistryPlanVocabulary.prompt(enabled)
        assertTrue(prompt.contains("attachment"))
        assertTrue(prompt.contains("last_screenshot"))
        assertTrue(prompt.contains("FIND_FILES"))
        assertTrue(prompt.contains("LIST_FILES"))
        assertTrue(prompt.contains("find:ebay bill"))
        assertTrue(prompt.contains("date=2026-10-01..2026-10-07"))
        assertTrue(prompt.contains("Never invent a path"))
        assertTrue(prompt.contains("tied newest files require human choice"))
    }

    @Test fun searchFormsParseAndValidate() {
        listOf(
            "find:ebay bill",
            "find:type=screenshot,date=2026-10-07",
            "find:type=pdf,name=invoice,folder=Download",
            "find:latest,type=image",
            "find:name=Invoice,date=2026-10-01..2026-10-07",
        ).forEach {
            assertTrue(it, AttachmentSpec.errors(it).isEmpty())
        }
        val search = AttachmentSearch.parse("find:ebay bill,type=PDF,date=2026-10-01..2026-10-07")
        assertEquals(listOf("ebay", "bill"), search.words)
        assertEquals("pdf", search.type)
        assertEquals("2026-10-01", search.firstDay.toString())
        assertEquals("2026-10-07", search.lastDay.toString())
        assertTrue(AttachmentSearch.parse("find:latest,type=image").latest)
    }

    @Test fun invalidSearchFiltersAreRejected() {
        listOf(
            "find:",
            "find:type=exe",
            "find:date=today",
            "find:date=2026-02-30",
            "find:date=2026-10-07..2026-10-01",
            "find:folder=../data",
            "find:name=../private",
            "find:url=content://x",
            "find:name=a,name=b",
            "find:latest,latest",
            "find:a,",
            "find:name=x\u0007",
            "find:date=2026-1-1",
            "find:unknown=x",
        ).forEach {
            assertTrue(it, AttachmentSpec.errors(it).isNotEmpty())
        }
    }

    @Test fun searchPreviewNamesTheApprovedQuery() {
        val steps =
            RegistryPlanVocabulary.parse(
                plan("SEND_WHATSAPP", "contact" to phone, "message" to "hi", "attachment" to "find:ebay bill"),
                enabled,
            )
        assertTrue(TaskPlanPreview.describe(steps).endsWith("; attach a file matching \"ebay bill\""))
    }

    @Test fun schemaDeclaresTheOptionalParameterOnExactlyTheThreeSendActions() {
        val withAttachment =
            ActionSchema.ALL_ACTIONS
                .filter { definition -> definition.params.any { it.name == AttachmentSpec.PARAM } }
                .map { it.name }
                .toSet()
        assertEquals(AttachmentSpec.ACTIONS, withAttachment)
        AttachmentSpec.ACTIONS.forEach { name ->
            assertFalse(
                name,
                ActionSchema
                    .getAction(name)!!
                    .params
                    .first { it.name == AttachmentSpec.PARAM }
                    .required,
            )
        }
        // File browsing stays in the Advanced category, so macros and routines may never run it unattended.
        assertEquals(ActionRisk.ADVANCED_CONTROL, ActionSchema.riskForAction("FIND_FILES"))
        assertEquals(ActionRisk.ADVANCED_CONTROL, ActionSchema.riskForAction("LIST_FILES"))
    }
}
