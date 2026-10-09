// Origin: EQO files-attachments, storage layout, browsing, staging and share-intent tests with temporary folders.
package ai.eqo.actions.impl

import android.content.Intent
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SharedStorageTest {
    private lateinit var base: File
    private lateinit var root: File
    private lateinit var ownFiles: File
    private lateinit var stagingRoot: File
    private lateinit var layout: SharedStorageLayout
    private var access = true
    private var now = 1_000_000L

    @Before
    fun setup() {
        base = Files.createTempDirectory("eqo-storage").toFile().canonicalFile
        root = File(base, "storage").apply { mkdirs() }
        ownFiles = File(root, "Android/data/ai.eqo.app/files").apply { mkdirs() }
        stagingRoot = File(root, "Android/data/ai.eqo.app/cache/shares").apply { mkdirs() }
        layout =
            SharedStorageLayout(
                root,
                listOf(ownFiles),
                listOf(stagingRoot),
                SharedFolderAliases.load(
                    androidx.test.core.app.ApplicationProvider
                        .getApplicationContext(),
                ),
            )
        access = true
        now = 1_000_000L
    }

    @After
    fun cleanup() {
        base.deleteRecursively()
    }

    private fun file(
        relative: String,
        text: String = "x",
    ): File = File(root, relative).apply { parentFile?.mkdirs() }.also { it.writeText(text) }

    private fun browser() = SharedFileBrowser(layout) { access }

    // ── layout ──────────────────────────────────────────────

    @Test fun `well known folder names map to the real folders`() {
        assertEquals(File(root, "Download").canonicalPath, layout.folder("Downloads").path)
        assertEquals(File(root, "Download/Taxes").canonicalPath, layout.folder("download/Taxes").path)
        assertEquals(File(root, "Pictures/EQO").canonicalPath, layout.folder("EQO").path)
        assertEquals(File(root, "Pictures/Screenshots").canonicalPath, layout.folder("screenshots/").path)
        assertEquals(root.canonicalPath, layout.folder("").path)
        assertEquals(File(root, "Download/a.pdf").canonicalPath, layout.file("Downloads/a.pdf").path)
    }

    @Test fun `paths outside shared storage and other apps data are refused`() {
        val outside = File(base, "private/shared_prefs/keys.xml")
        outside.parentFile?.mkdirs()
        outside.writeText("secret")
        assertFalse(layout.isAllowed(outside))
        listOf(
            outside.path,
            "Download/../../private/shared_prefs/keys.xml",
            "Android/data/com.other.app/files/x",
            "Android/obb/x",
        ).forEach {
            val failure = runCatching { layout.file(it) }.exceptionOrNull()
            assertTrue(it, failure is SecurityException)
        }
    }

    @Test fun `eqos own folder is allowed but its staging folder is not`() {
        assertTrue(layout.isAllowed(File(ownFiles, "workspace/a.txt")))
        assertTrue(layout.isOwnArea(File(ownFiles, "Pictures/EQO/s.png")))
        assertFalse(layout.isOwnArea(File(root, "Download/a.txt")))
        assertFalse(layout.isAllowed(File(stagingRoot, "x/y.txt")))
    }

    @Test fun `a link inside shared storage that points outside is refused`() {
        val secret = File(base, "private").apply { mkdirs() }
        File(secret, "token.txt").writeText("secret")
        val link = File(root, "Download/innocent").apply { parentFile?.mkdirs() }
        Files.createSymbolicLink(link.toPath(), secret.toPath())
        assertTrue(runCatching { layout.file("Download/innocent/token.txt") }.exceptionOrNull() is SecurityException)
    }

    // ── browsing ────────────────────────────────────────────

    @Test fun `list shows folders first then files and never contents`() {
        file("Download/b.txt", "hello")
        file("Download/A.pdf")
        File(root, "Download/zdir").mkdirs()
        file("Download/.hidden")
        val result = browser().list("Downloads") as BrowseResult.Listing
        val lines = result.text.lines()
        assertTrue(lines[0].startsWith("Folder: "))
        assertEquals(listOf("zdir", "A.pdf", "b.txt"), lines.drop(1).map { it.substringBefore(" [") })
        assertFalse(result.text.contains("hello"))
        assertFalse(result.text.contains(".hidden"))
    }

    @Test fun `browsing outside eqos own folders needs all files access`() {
        file("Download/a.txt")
        access = false
        val refused = browser().list("Downloads") as BrowseResult.Refused
        assertTrue(refused.message.contains("All files access"))
        assertTrue(browser().find("a", null) is BrowseResult.Refused)
        File(ownFiles, "Pictures/EQO").mkdirs()
        assertTrue(browser().list(File(ownFiles, "Pictures/EQO").path) is BrowseResult.Listing)
    }

    @Test fun `find searches inside folders by name and returns names and paths only`() {
        file("Download/Taxes/Resume-2025.pdf", "private words")
        file("Documents/resume_old.docx")
        file("Documents/notes.txt")
        file("Download/.cache/resume-hidden.pdf")
        val result = browser().find("RESUME", null) as BrowseResult.Listing
        assertTrue(result.text, result.text.contains("Found 2 match(es)"))
        assertTrue(result.text.contains("Resume-2025.pdf"))
        assertTrue(result.text.contains("resume_old.docx"))
        assertFalse(result.text.contains("hidden"))
        assertFalse(result.text.contains("private words"))
        val inFolder = browser().find("resume", "Documents") as BrowseResult.Listing
        assertTrue(inFolder.text.contains("Found 1 match(es)"))
    }

    @Test fun `find reports no match and rejects empty or oversized queries`() {
        file("Download/a.txt")
        assertTrue((browser().find("zzz", null) as BrowseResult.Listing).text.startsWith("No files found"))
        assertTrue(browser().find("  ", null) is BrowseResult.Refused)
        assertTrue(browser().find("x".repeat(300), null) is BrowseResult.Refused)
        assertTrue(browser().find("a", "/etc") is BrowseResult.Refused)
        assertTrue(browser().list("/data/data/ai.eqo.app") is BrowseResult.Refused)
    }

    @Test fun `find does not follow links and caps the number of results`() {
        repeat(60) { file("Download/photo-$it.jpg") }
        val outside = File(base, "private").apply { mkdirs() }
        File(outside, "photo-secret.jpg").writeText("s")
        Files.createSymbolicLink(File(root, "Download/linked").toPath(), outside.toPath())
        val result = browser().find("photo", "Download") as BrowseResult.Listing
        assertTrue(result.text.contains("Found 50 match(es)"))
        assertFalse(result.text.contains("photo-secret"))
    }

    // ── staging ─────────────────────────────────────────────

    @Test fun `staging copies the file under a safe name in its own folder`() {
        val staging = ShareStaging(stagingRoot) { now }
        val source = file("Download/My:report?.pdf", "pdf-bytes")
        val staged = staging.stage(source)
        assertEquals("My_report_.pdf", staged.name)
        assertEquals("pdf-bytes", staged.readText())
        assertEquals(stagingRoot.path, staged.parentFile!!.parentFile!!.path)
        assertTrue(source.exists())
    }

    @Test fun `staging refuses a linked source and removes failed partial shares`() {
        val safe = file("Download/source.pdf")
        val linked = File(root, "Download/linked.pdf")
        Files.createSymbolicLink(linked.toPath(), safe.toPath())
        assertTrue(runCatching { ShareStaging(stagingRoot).stage(linked) }.isFailure)
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test fun `safe names cannot escape or hide`() {
        assertEquals("file", ShareStaging.safeName("..."))
        assertEquals("_x.txt", ShareStaging.safeName("../x.txt"))
        assertFalse(ShareStaging.safeName("../../etc/passwd").contains("/"))
        val long = ShareStaging.safeName("a".repeat(300) + ".pdf")
        assertTrue(long.length <= 100 && long.endsWith(".pdf"))
    }

    @Test fun `old shares are swept and fresh ones stay`() {
        val staging = ShareStaging(stagingRoot) { now }
        val old = staging.stage(file("Download/old.txt"))
        now += ShareStaging.DEFAULT_MAX_AGE_MS + 1
        val fresh = staging.stage(file("Download/new.txt"))
        assertEquals(1, staging.sweep())
        assertFalse(old.exists())
        assertFalse(old.parentFile!!.exists())
        assertTrue(fresh.exists())
    }

    @Test fun `discard removes only the given shares`() {
        val staging = ShareStaging(stagingRoot) { now }
        val one = staging.stage(file("Download/one.txt"))
        val two = staging.stage(file("Download/two.txt"))
        staging.discard(listOf(one))
        assertFalse(one.parentFile!!.exists())
        assertTrue(two.exists())
        staging.discard(listOf(File(base, "elsewhere/x.txt")))
        assertTrue(two.exists())
    }

    // ── attachment preparation ──────────────────────────────

    private class FakeLast(
        var file: File? = null,
    ) : LastScreenshotStore {
        override fun get(): File? = file?.takeIf { it.isFile }

        override fun record(file: File) {
            this.file = file
        }
    }

    private fun share(last: LastScreenshotStore = FakeLast()) =
        AttachmentShare(layout, last, ShareStaging(stagingRoot) { now }, { access }) {
            Uri.parse("content://test/${it.name}")
        }

    private fun refusal(
        service: AttachmentShare,
        reference: String,
    ): String = (service.prepare(reference) as PreparedShare.Refused).message

    @Test fun `prepare stages every file and builds uris`() {
        file("Download/a.pdf", "A")
        file("Pictures/b.png", "B")
        val ready = share().prepare("Download/a.pdf|Pictures/b.png") as PreparedShare.Ready
        assertEquals(listOf("a.pdf", "b.png"), ready.files.map { it.displayName })
        assertEquals(listOf("application/pdf", "image/png"), ready.files.map { it.mimeType })
        assertEquals("content://test/a.pdf", ready.files[0].uri.toString())
        assertEquals("A", ready.files[0].staged.readText())
    }

    @Test fun `last_screenshot uses the recorded file and fails clearly when none exists`() {
        val shot = file("Pictures/EQO/eqo-screenshot-1.png", "P")
        val last = FakeLast()
        val none = share(last).prepare("last_screenshot") as PreparedShare.Refused
        assertTrue(none.message.contains("No EQO screenshot"))
        last.record(shot)
        val ready = share(last).prepare("last_screenshot") as PreparedShare.Ready
        assertEquals("eqo-screenshot-1.png", ready.files.single().displayName)
        shot.delete()
        assertTrue(share(last).prepare("last_screenshot") is PreparedShare.Refused)
    }

    @Test fun `prepare refuses missing folder outside oversized and unreadable access cases and stages nothing`() {
        file("Download/ok.txt")
        val big = File(root, "Download/big.bin")
        java.io.RandomAccessFile(big, "rw").use { it.setLength(AttachmentShare.MAX_TOTAL_BYTES + 1) }
        val service = share()
        assertTrue(refusal(service, "Download/missing.txt").contains("not found"))
        assertTrue(refusal(service, "Download").contains("folder"))
        assertTrue(refusal(service, "/data/data/ai.eqo.app/shared_prefs/x.xml").contains("outside"))
        assertTrue(refusal(service, "Download/ok.txt|Download/big.bin").contains("too big"))
        assertTrue(refusal(service, "../x").contains(".."))
        assertTrue(stagingRoot.listFiles().orEmpty().isEmpty())
    }

    @Test fun `prepare sweeps old shares first`() {
        val staging = ShareStaging(stagingRoot) { now }
        val old = staging.stage(file("Download/old.txt"))
        now += ShareStaging.DEFAULT_MAX_AGE_MS + 1
        file("Download/new.txt")
        share().prepare("Download/new.txt")
        assertFalse(old.exists())
    }

    // ── intents ─────────────────────────────────────────────

    private fun shareFile(
        name: String,
        mime: String,
    ) = ShareFile(Uri.parse("content://test/$name"), mime, name, File(name))

    @Test fun `one file is ACTION_SEND with a read grant aimed at the app`() {
        val intent = FileShareIntents.build(listOf(shareFile("a.pdf", "application/pdf")), "com.whatsapp")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals(Uri.parse("content://test/a.pdf"), intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(1, intent.clipData!!.itemCount)
    }

    @Test fun `several files are ACTION_SEND_MULTIPLE with one grant covering all of them`() {
        val files = listOf(shareFile("a.png", "image/png"), shareFile("b.jpg", "image/jpeg"))
        val intent = FileShareIntents.build(files, "com.google.android.gm")
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("image/*", intent.type)
        assertEquals(files.map { it.uri }, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(2, intent.clipData!!.itemCount)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("*/*", FileShareIntents.commonType(listOf("image/png", "application/pdf")))
        assertNull(FileShareIntents.build(files, null).`package`)
    }

    @Test fun `building a share with no files is refused`() {
        val failure = runCatching { FileShareIntents.build(emptyList(), null) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
