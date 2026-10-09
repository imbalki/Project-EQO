// Origin: EQO Files v2, resolver and human selection tests with synthetic shared storage.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentSearch
import android.net.Uri
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
        (AttachmentFileSearch(layout, { true }, zone).search(AttachmentSearch.parse(raw))
            as FileSearchResult.Matches).files

    private fun share(selection: AttachmentSelection? = null): AttachmentShare =
        AttachmentShare(
            layout,
            object : LastScreenshotStore {
                override fun get(): File? = null

                override fun record(file: File) = Unit
            },
            ShareStaging(staging),
            { true },
            selection,
        ) { Uri.parse("content://synthetic/${it.name}") }

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
        Files.createSymbolicLink(File(root, "Download/bill-link.pdf").toPath(), safe.toPath())
        Files.createSymbolicLink(File(root, "Download/linked").toPath(), safe.parentFile.toPath())
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
        assertTrue((share().prepare("find:missing") as PreparedShare.Refused).message.contains("missing"))
        file("Download/ebay-bill-2.pdf")
        val refused = share().prepare("find:latest,type=pdf") as PreparedShare.Refused
        assertTrue(refused.message.contains("Several files match"))
        assertTrue(refused.message.contains("ebay-bill-2.pdf"))
    }

    private class FakeSelection : AttachmentSelection {
        var offered = emptyList<AttachmentChoice>()
        var shown = emptyList<AttachmentChoice>()
        var index: Int? = 1
        var confirm = true
        var beforeConfirm: () -> Unit = {}

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
