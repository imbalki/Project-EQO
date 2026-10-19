// Origin: EQO-authored user-only handle capture; existing screenshot gate, local MediaStore publication.
package ai.eqo.handle

import ai.eqo.accessibility.EQOAccessibilityService
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.platform.R
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Rechecks the gate after capture and publication; a refused/raced capture must leave no public image. */
internal suspend fun <T : Any> captureHandleScreenshot(
    gate: () -> Boolean,
    capture: suspend () -> Boolean,
    publish: suspend () -> T?,
    discard: (T) -> Unit,
): T? {
    var result: T? = null
    if (gate() && capture() && gate()) {
        result = publish()
        var keep = false
        try {
            keep = gate()
        } finally {
            if (!keep) result?.let(discard)
        }
        if (!keep) result = null
    }
    return result
}

internal object HandleScreenshot {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var saved: Uri? = null
    private var dismissed = false

    fun tap(context: Context) {
        if (job?.isActive == true) return
        val service = context as? EQOAccessibilityService
        if (service == null || !prepareHandlePanel(requestPause = true)) {
            status(R.string.edge_screenshot_refused)
            return
        }
        saved = null
        dismissed = false
        job = scope.launch { capture(service) }
    }

    @Suppress("TooGenericExceptionCaught") // UI boundary must not expose node, URI or exception payloads.
    private suspend fun capture(service: EQOAccessibilityService) {
        var temporary: File? = null
        try {
            val identity = identity(service)
            EdgeHandleFeatures.screenshotOverlayRemoved = false
            EdgeHandleFeatures.screenshotHidden = true
            EdgeHandleFeatures.screenshotChanged()
            // API 30 captures the display, including accessibility overlays. Wait for their removal to compose.
            delay(HIDE_SETTLE_MS)
            temporary = File.createTempFile("eqo-capture-", ".png", service.cacheDir)
            val file = temporary
            saved =
                captureHandleScreenshot(
                    gate = {
                        identity != null && identity == identity(service) && captureAllowed(service)
                    },
                    capture = { service.takeScreenshotToFile(file) },
                    publish = { publish(service, file) },
                    discard = { service.contentResolver.delete(it, null, null) },
                )
            if (!dismissed) {
                status(if (saved == null) R.string.edge_screenshot_refused else R.string.edge_screenshot_saved)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!dismissed) status(R.string.edge_screenshot_failed)
        } finally {
            temporary?.delete()
            EdgeHandleFeatures.screenshotHidden = false
            EdgeHandleFeatures.screenshotChanged()
        }
    }

    private fun captureAllowed(service: EQOAccessibilityService): Boolean {
        val enabled =
            HandlePreferences(service).enabled &&
                EdgeHandleFeatures.registry?.enabled(EdgeHandleFeatures.SCREENSHOT_ID) == true
        return if (dismissed || !enabled || !EdgeHandleFeatures.screenshotOverlayRemoved) false else allowed(service)
    }

    private fun identity(service: EQOAccessibilityService): Pair<Int, String>? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()?.let { root.windowId to it }
        } finally {
            root.recycle()
        }
    }

    @Suppress("ReturnCount") // Fail closed before obtaining a root when takeover or service checks refuse capture.
    private fun allowed(service: EQOAccessibilityService): Boolean {
        if (!service.automation.screenshotGate().isSuccess) return false
        val root = service.rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() != service.packageName && !hasPassword(root, intArrayOf(NODE_BUDGET))
        } finally {
            root.recycle()
        }
    }

    // Metadata only; fail closed on oversized or stale trees. Never read or retain screen text.
    internal fun hasPassword(
        node: AccessibilityNodeInfo,
        budget: IntArray,
        depth: Int = 0,
    ): Boolean {
        if (--budget[0] < 0 || depth > MAX_DEPTH || node.isPassword) return true
        return (0 until node.childCount).any { index ->
            val child = node.getChild(index)
            if (child == null) {
                true
            } else {
                try {
                    hasPassword(child, budget, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    internal suspend fun publish(
        context: Context,
        source: File,
    ): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = "eqo-screenshot-${FORMAT.format(LocalDateTime.now())}.png"
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EQO")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                var committed = false
                try {
                    val output = resolver.openOutputStream(uri) ?: throw java.io.IOException("Storage unavailable")
                    output.use { sink -> source.inputStream().use { it.copyTo(sink) } }
                    check(source.length() > 0L)
                    val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    check(resolver.update(uri, ready, null, null) == 1)
                    // Cancellation before returning must not strand a public or pending row.
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    committed = true
                } finally {
                    if (!committed) resolver.delete(uri, null, null)
                }
            }
            uri
        }

    @Suppress("TooGenericExceptionCaught") // Share chooser failures are plain UI errors; retain the saved local image.
    fun share(context: Context) {
        val uri = saved ?: return
        try {
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newRawUri("EQO screenshot", uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            status(R.string.edge_screenshot_share_failed)
        }
    }

    fun dismiss() {
        // Let capture finish cleanup and recheck its gate rather than losing an in-flight MediaStore URI.
        dismissed = true
        saved = null
        status(0)
    }

    private fun status(message: Int) {
        EdgeHandleFeatures.screenshotStatus = message
        EdgeHandleFeatures.screenshotShareAvailable = saved != null
        EdgeHandleFeatures.screenshotChanged()
    }

    private const val HIDE_SETTLE_MS = 200L
    private const val NODE_BUDGET = 2000
    private const val MAX_DEPTH = 64
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
}
