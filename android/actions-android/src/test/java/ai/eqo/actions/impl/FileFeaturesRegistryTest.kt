// Origin: EQO files-attachments, registry-level tests for FIND_FILES, LIST_FILES, TAKE_SCREENSHOT and attachments.
package ai.eqo.actions.impl

import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.TakeoverDetector
import ai.eqo.actions.base.ActionResult
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class FileFeaturesRegistryTest {
    @Test fun missingAccessOffersExactSettingsThenContinuesAfterGrant() =
        runTest {
            access = false
            File(root, "Download").mkdirs()
            File(root, "Download/sample.txt").writeText("test")
            var prompts = 0
            val granting =
                AndroidActionRegistry.createWithStore(
                    context,
                    PermissionRequester {
                        val permission = it as ActionPermission.SpecialAccess
                        assertEquals(
                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            permission.settingsAction,
                        )
                        assertTrue(permission.packageScoped)
                        assertTrue(permission.explanation.contains("Turn on All files access"))
                        prompts++
                        access = true
                        true
                    },
                    { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, takeover) },
                    UnknownActionSink {},
                    RegistryOptions().also {
                        it.allFilesAccess = { access }
                        it.storageRoot = root
                    },
                )
            val result = granting.execute("FIND_FILES", mapOf("query" to "sample", "folder" to "Downloads"))
            assertTrue(result.success)
            assertEquals(1, prompts)
        }

    private lateinit var context: Application
    private lateinit var base: File
    private lateinit var root: File
    private lateinit var registry: AndroidActionRegistry
    private lateinit var takeover: TakeoverDetector
    private val last = FakeLastScreenshot()
    private var access = true
    private var secure = false
    private var writerCalls = 0
    private var writerResult = true
    private val disclosed = mutableListOf<String>()
    private val selection =
        object : AttachmentSelection {
            override suspend fun choose(
                search: String,
                files: List<AttachmentChoice>,
            ): Int = 0

            override suspend fun showResolved(files: List<AttachmentChoice>): Boolean {
                assertTrue(stagedCopies().isEmpty())
                assertNull(started())
                disclosed += files.map { it.name }
                return true
            }
        }

    private class FakeLastScreenshot : LastScreenshotStore {
        var file: File? = null

        override fun get(): File? = file?.takeIf { it.isFile }

        override fun record(file: File) {
            this.file = file
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        base = Files.createTempDirectory("eqo-files").toFile().canonicalFile
        root = File(base, "storage").apply { mkdirs() }
        last.file = null
        disclosed.clear()
        access = true
        secure = false
        writerCalls = 0
        writerResult = true
        takeover = TakeoverDetector()
        EqoSharedFileProvider.stagingRoot(context).deleteRecursively()
        val options =
            RegistryOptions().also {
                it.allFilesAccess = { access }
                it.storageRoot = root
                it.lastScreenshotStore = last
                it.attachmentSelection = selection
                it.shareUri = { file -> Uri.parse("content://ai.eqo.test/${file.parentFile!!.name}/${file.name}") }
                it.screenshotWriter =
                    ScreenshotWriter { target ->
                        writerCalls++
                        if (writerResult) target.writeBytes(byteArrayOf(1, 2, 3))
                        writerResult
                    }
            }
        registry =
            AndroidActionRegistry.createWithStore(
                context,
                PermissionRequester { true },
                { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, takeover, { secure }) },
                UnknownActionSink {},
                options,
            )
    }

    @After
    fun cleanup() {
        base.deleteRecursively()
        EqoSharedFileProvider.stagingRoot(context).deleteRecursively()
    }

    private fun file(
        relative: String,
        text: String = "x",
    ) = File(root, relative).apply { parentFile?.mkdirs() }.also { it.writeText(text) }

    private fun stagedCopies() =
        EqoSharedFileProvider
            .stagingRoot(context)
            .walkTopDown()
            .filter { it.isFile }
            .toList()

    private fun started() = shadowOf(context).nextStartedActivity

    private fun canResolve(intent: Intent) {
        val info =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = intent.`package` ?: "test.recipient"
                        name = "ShareActivity"
                        applicationInfo = ApplicationInfo().apply { packageName = intent.`package` ?: "test.recipient" }
                    }
            }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
    }

    // ── FIND_FILES / LIST_FILES ─────────────────────────────

    @Test
    fun `FIND_FILES is registered read only and its output is fenced as untrusted`() =
        runTest {
            assertTrue("FIND_FILES" in registry.enabledActionNames)
            file("Download/Resume.pdf", "contents never shown")
            val result = registry.execute("FIND_FILES", mapOf("query" to "resume"))
            assertTrue(result.success)
            assertTrue(result.data!!.contains("Resume.pdf"))
            assertTrue(result.data!!.contains("UNTRUSTED"))
            assertFalse(result.data!!.contains("contents never shown"))
        }

    @Test
    fun `LIST_FILES lists a named shared folder through folder or the older path name`() =
        runTest {
            file("Download/one.txt")
            assertTrue(registry.execute("LIST_FILES", mapOf("folder" to "Downloads")).data!!.contains("one.txt"))
            assertTrue(registry.execute("LIST_FILES", mapOf("path" to "downloads")).data!!.contains("one.txt"))
        }

    @Test
    fun `LIST_FILES keeps listing EQOs workspace for plain relative folders`() =
        runTest {
            val workspace = File(context.getExternalFilesDir(null), "workspace/out/docs").apply { mkdirs() }
            File(workspace, "a.txt").writeText("x")
            assertTrue(registry.execute("LIST_FILES", mapOf("path" to "out/docs")).data!!.contains("a.txt"))
        }

    @Test
    fun `file search needs All files access and says where to turn it on`() =
        runTest {
            file("Download/Resume.pdf")
            access = false
            val find = registry.execute("FIND_FILES", mapOf("query" to "resume"))
            assertFalse(find.success)
            assertTrue(find.error!!.contains("All files access"))
            assertFalse(registry.execute("LIST_FILES", mapOf("folder" to "Downloads")).success)
        }

    @Test
    fun `file search refuses private locations`() =
        runTest {
            val private = "/data/data/ai.eqo.app"
            assertFalse(registry.execute("FIND_FILES", mapOf("query" to "x", "folder" to private)).success)
            assertFalse(registry.execute("LIST_FILES", mapOf("folder" to "/data/data/ai.eqo.app/shared_prefs")).success)
        }

    // ── TAKE_SCREENSHOT ─────────────────────────────────────

    @Test
    fun `screenshot is saved in the EQO folder and recorded as last_screenshot`() =
        runTest {
            val result = registry.execute("TAKE_SCREENSHOT", emptyMap())
            assertTrue(result.error, result.success)
            val saved = last.file!!
            assertEquals(File(root, "Pictures/EQO").path, saved.parent)
            assertTrue(saved.name.startsWith("eqo-screenshot-") && saved.name.endsWith(".png"))
            assertEquals(1, writerCalls)
        }

    @Test
    fun `without All files access the screenshot goes to EQOs private folder instead`() =
        runTest {
            access = false
            assertTrue(registry.execute("TAKE_SCREENSHOT", emptyMap()).success)
            assertTrue(last.file!!.path.startsWith(context.getExternalFilesDir(null)!!.path))
        }

    @Test
    fun `protected windows are never captured`() =
        runTest {
            secure = true
            val result = registry.execute("TAKE_SCREENSHOT", emptyMap())
            assertFalse(result.success)
            assertEquals(0, writerCalls)
            assertNull(last.file)
        }

    @Test
    fun `a window that turns protected during capture drops the picture`() =
        runTest {
            val options =
                RegistryOptions().also {
                    it.allFilesAccess = { true }
                    it.storageRoot = root
                    it.lastScreenshotStore = last
                    it.screenshotWriter =
                        ScreenshotWriter { target ->
                            target.writeBytes(byteArrayOf(1))
                            secure = true
                            true
                        }
                }
            val racing =
                AndroidActionRegistry.createWithStore(
                    context,
                    PermissionRequester { true },
                    { EqoAutomation({ null }, { EqoAutomation.ServiceState.AVAILABLE }, takeover, { secure }) },
                    UnknownActionSink {},
                    options,
                )
            assertFalse(racing.execute("TAKE_SCREENSHOT", emptyMap()).success)
            assertNull(last.file)
            assertTrue(File(root, "Pictures/EQO").listFiles().orEmpty().isEmpty())
        }

    @Test
    fun `screenshot failure or takeover records nothing`() =
        runTest {
            writerResult = false
            assertFalse(registry.execute("TAKE_SCREENSHOT", emptyMap()).success)
            assertNull(last.file)
            writerResult = true
            takeover.onAgentActionStarted()
            takeover.onTouch(TakeoverDetector.TouchSource.USER)
            takeover.onAgentActionFinished()
            writerCalls = 0
            assertFalse(registry.execute("TAKE_SCREENSHOT", emptyMap()).success)
            assertEquals(0, writerCalls)
            assertNull(last.file)
        }

    // ── attachments ─────────────────────────────────────────

    @Test fun runtimeSearchAndGalleryFallbackReachAllThreeShareIntentsOnlyAfterDisclosure() =
        runTest {
            file("Pictures/Screenshots/gallery.png")
            file("Download/ebay-bill.pdf")
            val references = listOf("find:latest,type=screenshot", "find:ebay bill", "last_screenshot")
            for (action in listOf("SEND_WHATSAPP", "SEND_EMAIL", "SEND_SMS")) {
                for (reference in references) {
                    val pdf = reference.contains("ebay")
                    val mime = if (pdf) "application/pdf" else "image/png"
                    val packageName =
                        when (action) {
                            "SEND_EMAIL" -> "com.google.android.gm"
                            "SEND_WHATSAPP" -> "com.whatsapp"
                            else -> "com.google.android.apps.messaging"
                        }
                    org.robolectric.shadows.ShadowTelephony.ShadowSms.setDefaultSmsPackage(
                        if (action == "SEND_SMS") packageName else null,
                    )
                    canResolve(Intent(Intent.ACTION_SEND).setType(mime).setPackage(packageName))
                    canResolve(Intent(Intent.ACTION_SEND).setType(mime))
                    val params =
                        if (action == "SEND_EMAIL") {
                            emailParams
                        } else {
                            mapOf("contact" to "+15551234567", "message" to "")
                        }
                    val result = registry.execute(action, params + ("attachment" to reference))
                    assertTrue("$action: ${result.error}", result is ActionResult.UserActionRequired)
                    val intent = started()
                    val name = if (pdf) "ebay-bill.pdf" else "gallery.png"
                    assertEquals(name, disclosed.last())
                    assertEquals(name, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.lastPathSegment)
                    assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    EqoSharedFileProvider.stagingRoot(context).deleteRecursively()
                }
            }
        }

    private val emailParams =
        mapOf("to" to "owner@example.test", "subject" to "Papers", "body" to "See file")

    @Test
    fun `email with an attachment opens Gmail with a stream uri and read grant`() =
        runTest {
            file("Download/report.pdf", "PDF")
            canResolve(Intent(Intent.ACTION_SEND).setType("application/pdf").setPackage("com.google.android.gm"))
            val result = registry.execute("SEND_EMAIL", emailParams + ("attachment" to "Downloads/report.pdf"))
            assertTrue(result is ActionResult.UserActionRequired)
            val intent = started()
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("com.google.android.gm", intent.`package`)
            assertEquals("application/pdf", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertArrayEquals(arrayOf("owner@example.test"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
            assertEquals("Papers", intent.getStringExtra(Intent.EXTRA_SUBJECT))
            assertEquals("See file", intent.getStringExtra(Intent.EXTRA_TEXT))
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("report.pdf", uri.lastPathSegment)
            assertEquals("PDF", stagedCopies().single().readText())
        }

    @Test
    fun `email with several attachments uses ACTION_SEND_MULTIPLE`() =
        runTest {
            file("Download/a.png")
            file("Pictures/b.png")
            canResolve(Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").setPackage("com.google.android.gm"))
            registry.execute("SEND_EMAIL", emailParams + ("attachment" to "Downloads/a.png|Pictures/b.png"))
            val intent = started()
            assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
            assertEquals(2, intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.size)
        }

    @Test
    fun `email attachment can be the latest screenshot`() =
        runTest {
            registry.execute("TAKE_SCREENSHOT", emptyMap())
            canResolve(Intent(Intent.ACTION_SEND).setType("image/png").setPackage("com.google.android.gm"))
            registry.execute("SEND_EMAIL", emailParams + ("attachment" to "last_screenshot"))
            val intent = started()
            assertEquals("image/png", intent.type)
            val name = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!.lastPathSegment!!
            assertTrue(name.startsWith("eqo-screenshot-"))
        }

    @Test
    fun `no Gmail means no email and the staged copies are removed`() =
        runTest {
            file("Download/report.pdf")
            val result = registry.execute("SEND_EMAIL", emailParams + ("attachment" to "Downloads/report.pdf"))
            assertFalse(result.success)
            assertNull(started())
            assertTrue(stagedCopies().isEmpty())
        }

    @Test
    fun `email without an attachment is unchanged`() =
        runTest {
            canResolve(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            registry.execute("SEND_EMAIL", emailParams)
            val intent = started()
            assertEquals(Intent.ACTION_SENDTO, intent.action)
            assertNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }

    @Test
    fun `WhatsApp with an attachment shares to WhatsApp and the chat number`() =
        runTest {
            file("Pictures/photo.jpg")
            val result =
                registry.execute(
                    "SEND_WHATSAPP",
                    mapOf(
                        "contact" to "+15551234567",
                        "message" to "look",
                        "attachment" to "Pictures/photo.jpg",
                    ),
                )
            // The accessibility service is not running here, so EQO cannot press Send and says so.
            assertTrue(result is ActionResult.UserActionRequired)
            val intent = started()
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("com.whatsapp", intent.`package`)
            assertEquals("image/jpeg", intent.type)
            assertEquals("look", intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals("15551234567@s.whatsapp.net", intent.getStringExtra("jid"))
            assertNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }

    @Test
    fun `WhatsApp without an attachment keeps the original link flow`() =
        runTest {
            registry.execute("SEND_WHATSAPP", mapOf("contact" to "+15551234567", "message" to "hello"))
            val intent = started()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }

    @Test
    fun `SMS with an attachment needs a messaging app and stages nothing without one`() =
        runTest {
            file("Pictures/photo.jpg")
            val result =
                registry.execute(
                    "SEND_SMS",
                    mapOf(
                        "contact" to "+15551234567",
                        "message" to "look",
                        "attachment" to "Pictures/photo.jpg",
                    ),
                )
            assertFalse(result.success)
            assertNull(started())
            assertTrue(stagedCopies().isEmpty())
        }

    @Test
    fun `unsafe or unavailable attachments stop the send before anything opens`() =
        runTest {
            file("Download/ok.txt")
            access = false
            listOf(
                "Downloads/ok.txt" to "attachment_cancelled",
                "Downloads/missing.txt" to "attachment_cancelled",
                "/data/data/ai.eqo.app/shared_prefs/x.xml" to "attachment_not_allowed",
                "../etc/hosts" to "attachment_invalid",
                "last_screenshot" to "attachment_cancelled",
            ).forEach { (attachment, expected) ->
                val message =
                    registry.execute(
                        "SEND_WHATSAPP",
                        mapOf(
                            "contact" to "+15551234567",
                            "message" to "hi",
                            "attachment" to attachment,
                        ),
                    )
                assertFalse(attachment, message.success)
                assertTrue("$attachment -> ${message.error}", message.error!!.contains(expected))
            }
            assertNull(started())
            assertTrue(stagedCopies().isEmpty())
        }
}
