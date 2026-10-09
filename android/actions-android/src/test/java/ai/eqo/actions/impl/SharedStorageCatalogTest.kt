// Origin: EQO Files v2, synthetic OEM layouts, learned buckets and MediaStore collection contract.
package ai.eqo.actions.impl

import ai.eqo.core.agent.AttachmentSearch
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SharedStorageCatalogTest {
    private lateinit var root: File
    private lateinit var layout: SharedStorageLayout
    private lateinit var context: Context
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day =
        LocalDate
            .parse("2026-10-07")
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = Files.createTempDirectory("eqo-oem-folders").toFile().canonicalFile
        layout = SharedStorageLayout(root, aliases = SharedFolderAliases.load(context))
    }

    @After fun cleanup() {
        root.deleteRecursively()
    }

    private fun file(relative: String): File =
        File(root, relative).apply {
            parentFile?.mkdirs()
            writeText("synthetic")
            setLastModified(day)
        }

    private class MemoryMap : SharedFolderMapStore {
        var map: Map<String, List<String>>? = null
        var writes = 0

        override fun read(): Map<String, List<String>>? = map

        override fun write(map: Map<String, List<String>>) {
            this.map = map
            writes++
        }
    }

    private fun find(
        query: String,
        catalog: SharedStorageCatalog,
    ): List<File> =
        (
            AttachmentFileSearch(layout, { true }, zone, catalog).search(AttachmentSearch.parse(query))
                as FileSearchResult.Matches
        ).files

    @Test fun realmeSamsungAndXiaomiLayoutsUseTheSameDataTable() {
        val layouts =
            listOf(
                listOf("Pictures/Screenshots/r.png", "DCIM/Camera/r.jpg", "Download/r.pdf"),
                listOf("DCIM/Screenshots/s.png", "DCIM/Camera/s.jpg", "Download/s.pdf"),
                listOf("Pictures/Screenshot/x.png", "Camera/x.jpg", "Downloads/x.pdf"),
            )
        for (paths in layouts) {
            paths.forEach(::file)
            val catalog = SharedStorageCatalog(layout, SharedMediaSource { emptyList() }, MemoryMap())
            assertEquals(
                paths[0],
                find("find:type=screenshot", catalog).single().relativeTo(root).invariantSeparatorsPath,
            )
            assertEquals(paths[1], find("find:type=camera", catalog).single().relativeTo(root).invariantSeparatorsPath)
            assertEquals(
                paths[2],
                find("find:type=downloads", catalog).single().relativeTo(root).invariantSeparatorsPath,
            )
            assertEquals(2, find("find:type=gallery", catalog).size)
            paths.forEach { File(root, it).delete() }
        }
    }

    @Test fun mediaBucketLearnsBrandSpecificPathAndMimeAndDatesTakePriority() {
        file("Vendor/GalleryShots/receipt.bin").setLastModified(day - 10 * DAY_MS)
        val entries =
            listOf(
                SharedMediaEntry("Vendor/GalleryShots/", "receipt.bin", "Screenshots", "image/png", day, day),
            )
        val store = MemoryMap()
        val catalog = SharedStorageCatalog(layout, SharedMediaSource { entries }, store)
        assertEquals("receipt.bin", find("find:type=screenshot,date=2026-10-07", catalog).single().name)
        assertEquals(listOf("Vendor/GalleryShots"), store.map?.get("screenshot"))
        assertEquals(File(root, "Vendor/GalleryShots"), layout.folder("Screenshots"))
        assertFalse(store.map.toString().contains("receipt.bin"))
        val share =
            AttachmentShare(
                layout,
                object : LastScreenshotStore {
                    override fun get(): File? = null

                    override fun record(file: File) = Unit
                },
                ShareStaging(File(root, "staging")),
                { true },
                catalog = catalog,
            ) {
                Uri.parse("content://synthetic/${it.name}")
            }
        val ready = share.prepare("find:type=screenshot") as PreparedShare.Ready
        assertEquals("image/png", ready.files.single().mimeType)
    }

    @Test fun oneTimeMapPersistsAndExplicitOrZeroMatchSearchRescans() {
        file("Pictures/Screenshots/a.png")
        val store = MemoryMap()
        val source = SharedMediaSource { emptyList() }
        val first = SharedStorageCatalog(layout, source, store)
        find("find:type=screenshot", first)
        find("find:type=screenshot", SharedStorageCatalog(layout, source, store))
        assertEquals(1, store.writes)
        find("find:type=screenshot,rescan=true", first)
        assertEquals(2, store.writes)
        find("find:missing,type=screenshot", first)
        assertEquals(3, store.writes)
        val prefs = PrefsSharedFolderMapStore(context)
        prefs.write(requireNotNull(store.map))
        assertEquals(store.map, PrefsSharedFolderMapStore(context).read())
    }

    @Test fun mediaIndexCannotHideSecondFilesystemMatchOrExposeForbiddenFolders() {
        val indexed = file("Documents/a.pdf")
        file("Documents/b.pdf")
        file("Android/data/other/files/private.pdf")
        file("Android/obb/other/private.pdf")
        file(".hidden/private.pdf")
        val entries =
            listOf(
                SharedMediaEntry("Documents/", indexed.name, "Documents", "application/pdf", day, day),
                SharedMediaEntry("Android/data/other/files/", "private.pdf", "Documents", "application/pdf", day, day),
                SharedMediaEntry("Android/obb/other/", "private.pdf", "Documents", "application/pdf", day, day),
                SharedMediaEntry(".hidden/", "private.pdf", "Documents", "application/pdf", day, day),
            )
        val store = MemoryMap()
        val catalog = SharedStorageCatalog(layout, SharedMediaSource { entries }, store)
        assertEquals(setOf("a.pdf", "b.pdf"), find("find:type=pdf", catalog).map { it.name }.toSet())
        assertFalse(store.map.toString().contains("Android/data"))
        assertFalse(store.map.toString().contains("Android/obb"))
    }

    @Test fun aliasAssetCoversMessagingMediaAndBluetoothWithoutHardcodedResolverPaths() {
        val paths =
            listOf(
                "Android/media/com.whatsapp/WhatsApp/Media/a.pdf",
                "WhatsApp/Media/b.pdf",
                "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/c.pdf",
                "Telegram/d.pdf",
                "Pictures/Instagram/e.png",
                "Bluetooth/f.pdf",
            )
        paths.forEach(::file)
        val catalog = SharedStorageCatalog(layout, SharedMediaSource { emptyList() }, MemoryMap())
        assertEquals(setOf("a.pdf", "b.pdf"), find("find:type=pdf,folder=whatsapp", catalog).map { it.name }.toSet())
        assertEquals(
            "a.pdf",
            find(
                "find:type=pdf,folder=Android/media/com.whatsapp/WhatsApp/Media",
                catalog,
            ).single().name,
        )
        assertEquals("c.pdf", find("find:type=pdf,folder=whatsapp_business", catalog).single().name)
        assertEquals("d.pdf", find("find:type=pdf,folder=telegram", catalog).single().name)
        assertEquals("e.png", find("find:type=image,folder=instagram", catalog).single().name)
        assertEquals("f.pdf", find("find:type=pdf,folder=bluetooth", catalog).single().name)
    }

    @Test fun adapterQueriesAllCollectionsAndConvertsSecondsWithoutReadingFileContents() {
        val provider = FakeMediaProvider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val entries = AndroidSharedMediaSource(context).entries()
        assertEquals(5, provider.uris.size)
        assertTrue(provider.uris.any { it.path?.contains("images") == true })
        assertTrue(provider.uris.any { it.path?.contains("video") == true })
        assertTrue(provider.uris.any { it.path?.contains("audio") == true })
        assertTrue(provider.uris.any { it.path?.contains("downloads") == true })
        assertTrue(provider.uris.any { it.path?.contains("file") == true })
        assertEquals(1, entries.size)
        assertEquals(123_000L, entries.single().modifiedMillis)
        assertEquals("Screenshots", entries.single().bucket)
        assertFalse(provider.columns.contains(MediaStore.MediaColumns.DATA))
        provider.filesWithoutBucket = true
        assertEquals("Screenshots", AndroidSharedMediaSource(context).entries().single().bucket)
        provider.denied = true
        assertTrue(AndroidSharedMediaSource(context).entries().isEmpty())
    }

    private class FakeMediaProvider : ContentProvider() {
        val uris = mutableListOf<Uri>()
        var columns = emptyList<String>()
        var denied = false
        var filesWithoutBucket = false

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): MatrixCursor {
            if (denied) throw SecurityException("synthetic permission denial")
            uris += uri
            columns = projection!!.toList()
            val missingBucket =
                filesWithoutBucket &&
                    uri.path?.contains("file") == true &&
                    MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME in columns
            require(!missingBucket) { "synthetic OEM missing bucket column" }
            return MatrixCursor(projection).apply {
                val values = arrayOf<Any>("Vendor/GalleryShots/", "receipt.bin", "image/png", 123L, 100L, "Screenshots")
                addRow(values.take(projection.size).toTypedArray())
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    companion object {
        private const val DAY_MS = 86_400_000L
    }
}
