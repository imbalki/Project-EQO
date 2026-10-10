// Origin: EQO Files v2, resolver and human selection tests with synthetic shared storage.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentFailure
import ai.eqo.core.agent.AttachmentSearch
import android.net.Uri
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AttachmentFileSearchTest {
    private lateinit var root: File
    private lateinit var staging: File
    private lateinit var layout: SharedStorageLayout
    private val zone = ZoneId.of("Asia/Kolkata")

    @Before fun setup() {
        root = Files.createTempDirectory("eqo-find-attachments").toFile().canonicalFile
        staging = File(root, "staged").apply { mkdirs() }
        layout =
            SharedStorageLayout(
                root,
                stagingAreas = listOf(staging),
                aliases =
                    SharedFolderAliases.load(
                        androidx.test.core.app.ApplicationProvider
                            .getApplicationContext(),
                    ),
            )
    }

    @After fun cleanup() {
        root.deleteRecursively()
    }

    private fun file(
        name: String,
        day: String = "2026-10-07",
    ): File =
        File(root, name).apply {
            parentFile?.mkdirs()
            writeText("synthetic")
            setLastModified(
                LocalDate
                    .parse(day)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli(),
            )
        }

    private fun search(raw: String): List<File> =
        (
            AttachmentFileSearch(layout, { true }, zone).search(AttachmentSearch.parse(raw))
                as FileSearchResult.Matches
        ).files

    private fun share(
        selection: AttachmentSelection? = null,
        recorded: File? = null,
    ): AttachmentShare =
        AttachmentShare(
            layout,
            object : LastScreenshotStore {
                override fun get(): File? = recorded?.takeIf { it.isFile }

                override fun record(file: File) = Unit
            },
            ShareStaging(staging),
            { true },
            selection,
        ) { Uri.parse("content://synthetic/${it.name}") }

    @Test fun staleRecordedScreenshotFallsBackButFreshEqoCaptureKeepsItsIdentity() =
        runBlocking {
            val eqo = file("Pictures/EQO/eqo.png", "2026-10-01")
            file("Pictures/Screenshots/new.png")
            val ui = FakeSelection()
            val fallback = share(ui, eqo).prepareOnIo("last_screenshot") as PreparedShare.Ready
            assertEquals("new.png", fallback.files.single().displayName)
            fallback.files.forEach { it.staged.delete() }
            eqo.setLastModified(System.currentTimeMillis())
            val captured = share(ui, eqo).prepareOnIo("last_screenshot") as PreparedShare.Ready
            assertEquals("eqo.png", captured.files.single().displayName)
        }

    @Test fun missingEqoScreenshotUsesNewestGalleryWithDisclosureBeforeStaging() =
        runBlocking {
            file("Pictures/Screenshots/old.png", "2026-10-01")
            file("DCIM/Screenshots/new.png")
            val ui = FakeSelection()
            ui.beforeConfirm = { assertTrue(staging.listFiles().orEmpty().isEmpty()) }
            val ready = share(ui).prepareOnIo("last_screenshot") as PreparedShare.Ready
            assertEquals("new.png", ready.files.single().displayName)
            assertEquals("new.png", ui.shown.single().name)
            assertTrue(ui.offered.isEmpty())
        }

    @Test fun galleryFallbackNeedsForegroundConfirmationAndRespectsCancelAndDeletion() =
        runBlocking {
            val screenshot = file("Pictures/Screenshots/new.png")
            assertTrue(share().prepareOnIo("last_screenshot") is PreparedShare.Refused)
            val ui = FakeSelection()
            ui.confirm = false
            assertTrue(share(ui).prepareOnIo("last_screenshot") is PreparedShare.Refused)
            ui.confirm = true
            ui.beforeConfirm = { screenshot.delete() }
            assertTrue(share(ui).prepareOnIo("last_screenshot") is PreparedShare.Refused)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun tiedNewestGalleryScreenshotsRequireHumanChoice() =
        runBlocking {
            file("Pictures/Screenshots/a.png")
            file("DCIM/Screenshots/b.png")
            val ui = FakeSelection()
            val ready = share(ui).prepareOnIo("last_screenshot") as PreparedShare.Ready
            assertEquals(2, ui.offered.size)
            assertEquals("b.png", ready.files.single().displayName)
        }

    @Test fun allWordsTypeAndFolderMustMatchAndNewestComesFirst() {
        file("Download/EBAY-BILL-old.PDF", "2026-10-01")
        file("Download/bill-ebay-new.pdf")
        file("Download/ebay-other.pdf")
        file("Download/ebay-bill.png")
        file("Documents/ebay-bill.pdf")
        assertEquals(
            listOf("bill-ebay-new.pdf", "EBAY-BILL-old.PDF"),
            search("find:ebay bill,type=pdf,folder=Download").map { it.name },
        )
    }

    @Test fun coexistingDownloadAliasesCannotHideAnAmbiguousMatch() {
        file("Download/bill.pdf")
        file("Downloads/bill-2.pdf")
        assertEquals(setOf("bill.pdf", "bill-2.pdf"), search("find:bill,folder=downloads").map { it.name }.toSet())
    }

    @Test fun dateUsesLocalDayAndInclusiveRange() {
        file("Download/start.pdf", "2026-10-01")
        file("Download/end.pdf")
        file("Download/after.pdf", "2026-10-08")
        assertEquals(listOf("end.pdf"), search("find:type=pdf,date=2026-10-07").map { it.name })
        assertEquals(
            listOf("end.pdf", "start.pdf"),
            search("find:type=pdf,date=2026-10-01..2026-10-07").map { it.name },
        )
    }

    @Test fun screenshotMatchesBothGalleryFoldersAndNotOrdinaryPhotos() {
        file("Pictures/Screenshots/s.png")
        file("DCIM/Screenshots/d.jpg")
        file("Pictures/EQO/e.png")
        file("DCIM/Camera/photo.png")
        file("Download/screenshot.pdf")
        assertEquals(setOf("s.png", "d.jpg", "e.png"), search("find:type=screenshot").map { it.name }.toSet())
        assertEquals(4, search("find:type=image").size)
    }

    @Test fun documentVideoAndAudioExtensionsAreMatched() {
        file("Documents/a.docx")
        file("DCIM/a.mp4")
        file("Music/a.mp3")
        assertEquals("a.docx", search("find:type=doc").single().name)
        assertEquals("a.mp4", search("find:type=video").single().name)
        assertEquals("a.mp3", search("find:type=audio").single().name)
    }

    @Test fun excludedOtherAppsStagingHiddenAndLinksNeverMatch() {
        file("Android/data/other.app/files/bill.pdf")
        file("Android/obb/other.app/bill.pdf")
        file("staged/bill.pdf")
        file("Download/.hidden/bill.pdf")
        val safe = file("Download/bill.pdf")
        val linked = file("Download/bill-link.pdf")
        val targetDir = File(root, "Documents").apply { mkdirs() }
        val linkedDir = File(root, "Download/linked").apply { mkdirs() }
        layout =
            SharedStorageLayout(
                root,
                stagingAreas = listOf(staging),
                aliases = layout.aliases,
                isLink = { it == linked || it == linkedDir },
            )
        assertTrue(targetDir.isDirectory)
        assertEquals(listOf(safe), search("find:bill"))
        assertTrue(runCatching { layout.file("Download/bill-link.pdf") }.exceptionOrNull() is SecurityException)
        assertTrue(runCatching { layout.folder("Download/linked") }.exceptionOrNull() is SecurityException)
        assertTrue(
            AttachmentFileSearch(layout, { true }).search(
                AttachmentSearch.parse("find:bill,folder=Android/data/other.app"),
            ) is FileSearchResult.Refused,
        )
    }

    @Test fun permissionAndTimeCapsFailClosed() {
        file("Download/a.pdf")
        assertTrue(
            AttachmentFileSearch(layout, { false }).search(AttachmentSearch.parse("find:a"))
                is FileSearchResult.Refused,
        )
        var nanos = 0L
        val bounded =
            AttachmentFileSearch(layout, { true }, zone) {
                nanos += 3_000_000_000
                nanos
            }
        assertTrue(bounded.search(AttachmentSearch.parse("find:a")) is FileSearchResult.Refused)
    }

    @Test fun resultCapNeverTurnsPartialSearchIntoAUniqueMatch() {
        repeat(201) { file("Download/bill-$it.pdf") }
        assertTrue(
            AttachmentFileSearch(layout, { true }).search(AttachmentSearch.parse("find:bill"))
                is FileSearchResult.Refused,
        )
    }

    @Test fun oneZeroAndAmbiguousMatchesHaveDistinctOutcomes() {
        file("Download/ebay-bill.pdf")
        assertTrue(share().prepare("find:ebay bill") is PreparedShare.Ready)
        assertEquals(
            "no_matching_file",
            AttachmentFailure.kind(
                (share().prepare("find:missing") as PreparedShare.Refused).message,
            ),
        )
        file("Download/ebay-bill-2.pdf")
        val refused = share().prepare("find:latest,type=pdf") as PreparedShare.Refused
        assertEquals("attachment_selection_required", AttachmentFailure.kind(refused.message))
        assertFalse(refused.message.contains("ebay-bill-2.pdf"))
    }

    @Test fun realmeTreeIsBoundedAndFilenameDatesBeatCopiedFileTimes() =
        runBlocking {
            repeat(26) { index ->
                val minute = index.toString().padStart(2, '0')
                file("DCIM/Screenshots/Screenshot_2026-10-09-18-$minute-00-00_fake.jpg", "2026-10-01")
            }
            file("DCIM/Camera/IMG_20261010.jpg", "2026-10-10")
            file("Download/" + "deep/".repeat(10) + "unrelated.pdf")
            assertFalse(File(root, "Pictures/Screenshots").exists())
            // Old whole-storage search deterministically refuses on this unrelated depth limit.
            assertTrue(
                AttachmentFileSearch(layout, { true }).search(
                    AttachmentSearch.parse("find:type=image"),
                ) is FileSearchResult.Refused,
            )
            val ui = FakeSelection()
            val ready = share(ui).prepareOnIo("find:type=screenshot,latest") as PreparedShare.Ready
            assertEquals("Screenshot_2026-10-09-18-25-00-00_fake.jpg", ready.files.single().displayName)
            assertEquals(26, search("find:type=screenshot,date=2026-10-09").size)
            assertTrue(search("find:type=screenshot,date=2026-10-01").isEmpty())
            assertEquals("IMG_20261010.jpg", search("find:type=photo,latest").single().name)
            assertTrue(ui.folders.isEmpty())
        }

    @Test fun zeroMatchesRequestsPickerAndCancellationIsNeedsYou() =
        runBlocking {
            val ui = FakeSelection()
            val refused = share(ui).prepareOnIo("find:type=screenshot,latest") as PreparedShare.Refused
            assertEquals(listOf("DCIM/Screenshots"), ui.folders)
            assertEquals("attachment_cancelled", AttachmentFailure.kind(refused.message))
            assertTrue(refused.message.contains("Needs you"))
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun pickerStagesOnlyChosenDocumentAndReleasesGrantBeforeConfirmation() =
        runBlocking {
            val ui = FakeSelection()
            var released = false
            ui.document =
                PickedAttachment(
                    AttachmentChoice("synthetic-bill.pdf", 1_000L, 9),
                    "application/pdf",
                    { "synthetic".byteInputStream() },
                    { released = true },
                )
            ui.beforeConfirm = { assertTrue(released) }
            val ready = share(ui).prepareOnIo("find:missing bill") as PreparedShare.Ready
            assertEquals(listOf("Download"), ui.folders)
            assertEquals("synthetic-bill.pdf", ui.shown.single().name)
            assertEquals(
                "synthetic",
                ready.files
                    .single()
                    .staged
                    .readText(),
            )
            assertEquals(1, staging.listFiles().orEmpty().size)
        }

    @Test fun differentFileUsesPickerAndStillRequiresFreshSendConfirmation() =
        runBlocking {
            file("Download/first.pdf")
            val ui = FakeSelection()
            ui.different = true
            ui.confirm = false
            var released = false
            ui.document =
                PickedAttachment(
                    AttachmentChoice("replacement.pdf", 2_000L, 4),
                    "application/pdf",
                    { "fake".byteInputStream() },
                    { released = true },
                )
            val result = share(ui).prepareOnIo("find:first") as PreparedShare.Refused
            assertEquals("replacement.pdf", ui.shown.single().name)
            assertEquals("attachment_cancelled", AttachmentFailure.kind(result.message))
            assertTrue(released)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun brokenPickerStreamHasSpecificReasonAndNoGrantOrCopiesRemain() =
        runBlocking {
            val ui = FakeSelection()
            var released = false
            ui.document =
                PickedAttachment(
                    AttachmentChoice("failure.pdf", 0, 0),
                    "application/pdf",
                    { throw java.io.IOException("PRIVATE filename must not escape") },
                    { released = true },
                )
            val result = share(ui).prepareOnIo("find:missing") as PreparedShare.Refused
            assertEquals("staging_failed", AttachmentFailure.kind(result.message))
            assertFalse(result.message.contains("PRIVATE"))
            assertTrue(released)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun incompleteSearchFallsBackToPickerInsteadOfGenericFailure() =
        runBlocking {
            file("Download/" + "deep/".repeat(10) + "bill.pdf")
            val ui = FakeSelection()
            val result = share(ui).prepareOnIo("find:bill,type=pdf") as PreparedShare.Refused
            assertEquals(listOf("Download"), ui.folders)
            assertEquals("attachment_cancelled", AttachmentFailure.kind(result.message))
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun providerUriFailureDiscardsAllStagedCopiesAndCarriesNoFilename() =
        runBlocking {
            file("Download/provider-test.pdf")
            val share =
                AttachmentShare(
                    layout,
                    object : LastScreenshotStore {
                        override fun get(): File? = null

                        override fun record(file: File) = Unit
                    },
                    ShareStaging(staging),
                    { true },
                    FakeSelection(),
                ) { throw IllegalArgumentException("PRIVATE provider-test.pdf") }
            val result = share.prepareOnIo("find:provider-test") as PreparedShare.Refused
            assertEquals("provider_failed", AttachmentFailure.kind(result.message))
            assertFalse(result.message.contains("PRIVATE"))
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    @Test fun oversizedPickerMetadataAndStreamUseSizeDiagnosisAndReleaseGrant() =
        runBlocking {
            for (declared in listOf(AttachmentShare.MAX_TOTAL_BYTES + 1, 0L)) {
                val ui = FakeSelection()
                var released = false
                ui.document =
                    PickedAttachment(
                        AttachmentChoice("large.pdf", 0, declared),
                        "application/pdf",
                        { java.io.ByteArrayInputStream(ByteArray((AttachmentShare.MAX_TOTAL_BYTES + 1).toInt())) },
                        { released = true },
                    )
                val result = share(ui).prepareOnIo("find:missing") as PreparedShare.Refused
                assertEquals("attachment_too_large", AttachmentFailure.kind(result.message))
                assertTrue(released)
                assertTrue(staging.listFiles().orEmpty().isEmpty())
            }
        }

    @Test fun cancellationAtOutgoingProviderBoundaryDiscardsEveryCopy() =
        runBlocking {
            file("Download/cancel.pdf")
            lateinit var job: kotlinx.coroutines.Deferred<PreparedShare>
            val share =
                AttachmentShare(
                    layout,
                    object : LastScreenshotStore {
                        override fun get(): File? = null

                        override fun record(file: File) = Unit
                    },
                    ShareStaging(staging),
                    { true },
                    FakeSelection(),
                ) {
                    job.cancel()
                    Uri.parse("content://synthetic/cancel")
                }
            job =
                async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    share.prepareOnIo("find:cancel")
                }
            job.start()
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }

    private class FakeSelection : AttachmentSelection {
        var offered = emptyList<AttachmentChoice>()
        var shown = emptyList<AttachmentChoice>()
        var index: Int? = 1
        var confirm = true
        var beforeConfirm: () -> Unit = {}
        var document: PickedAttachment? = null
        val folders = mutableListOf<String>()
        var different = false

        override suspend fun pick(initialFolder: String): PickedAttachment? {
            folders += initialFolder
            return document
        }

        override suspend fun confirm(files: List<AttachmentChoice>): AttachmentDecision =
            if (different) {
                different = false
                AttachmentDecision.DIFFERENT
            } else {
                if (showResolved(files)) AttachmentDecision.SEND else AttachmentDecision.CANCEL
            }

        override suspend fun choose(
            search: String,
            files: List<AttachmentChoice>,
        ): Int? {
            offered = files
            return index
        }

        override suspend fun showResolved(files: List<AttachmentChoice>): Boolean {
            shown = files
            beforeConfirm()
            return confirm
        }
    }

    @Test fun humanPicksFromEightAndExactNameIsShownBeforeStaging() =
        runBlocking {
            repeat(10) { file("Download/bill-$it.pdf") }
            val ui = FakeSelection()
            ui.beforeConfirm = { assertTrue(staging.listFiles().orEmpty().isEmpty()) }
            val result = share(ui).prepareOnIo("find:bill") as PreparedShare.Ready
            assertEquals(8, ui.offered.size)
            assertEquals("bill-1.pdf", ui.shown.single().name)
            assertEquals(ui.shown.single().name, result.files.single().displayName)
            assertTrue(ui.offered.all { it.bytes > 0 && it.modifiedMillis > 0 })
        }

    @Test fun uniqueResultStillDisclosedAndMissingUiRefusesToSend() =
        runBlocking {
            file("Download/bill.pdf")
            val ui = FakeSelection()
            assertTrue(share(ui).prepareOnIo("find:bill") is PreparedShare.Ready)
            assertTrue(ui.offered.isEmpty())
            assertEquals("bill.pdf", ui.shown.single().name)
            assertTrue(share().prepareOnIo("find:bill") is PreparedShare.Refused)
        }

    @Test fun cancelInvalidChoiceAndConfirmationDenialStageNothing() =
        runBlocking {
            file("Download/bill.pdf")
            file("Download/bill-2.pdf")
            val ui = FakeSelection()
            for (index in listOf(null, -1, 8)) {
                ui.index = index
                assertTrue(share(ui).prepareOnIo("find:bill") is PreparedShare.Refused)
            }
            ui.index = 0
            ui.confirm = false
            assertTrue(share(ui).prepareOnIo("find:bill") is PreparedShare.Refused)
            assertFalse(staging.listFiles().orEmpty().isNotEmpty())
        }

    @Test fun selectedFileIsRecheckedAfterHumanWait() =
        runBlocking {
            val selected = file("Download/bill.pdf")
            val ui = FakeSelection()
            ui.beforeConfirm = { selected.delete() }
            assertTrue(share(ui).prepareOnIo("find:bill") is PreparedShare.Refused)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        }
}
