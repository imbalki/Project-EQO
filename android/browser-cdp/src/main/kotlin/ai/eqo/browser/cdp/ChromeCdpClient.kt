// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/ChromeCdpClient.kt
// TASK-010 (issue #15): extracted into :browser-cdp. Changes vs donor: package renamed to
// ai.eqo.browser.cdp; the shizuku PageTarget import dropped (PageTarget now lives in
// DevtoolsHttpProtocol.kt in this package); the synthetic dialog-query method string is
// rebranded to Eqo; import block re-sorted to this repo's ktlint import-ordering rule.
package ai.eqo.browser.cdp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ChromeCdpClient(
    private val connectionFactory: CdpConnectionFactory,
    /**
     * Per-CDP-command timeout. Each `cdp(method, ...)` from the agent script is wrapped in
     * `withTimeout(commandTimeoutMs)`; the script's outer `timeout_ms` is a separate, larger
     * budget for the whole script. Default is generous because the wireless-ADB self-pair
     * relay adds adbd-loopback latency on top of Chrome's response time.
     */
    private val commandTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS,
    private val onTransportFailure: (Throwable) -> Unit = {},
) {
    private val nextId = AtomicInteger(1)
    private val recoveryMutex = Mutex()

    /**
     * Serializes the entire `switchDirectPageTarget` body — concurrent CDP calls with
     * `targetId` set must not both capture the same `previous`, open two WS independently,
     * and let last-write-to-`current` win (orphaning the loser).
     */
    private val switchMutex = Mutex()

    /**
     * Invoked after a successful target switch — either via the `targetId` option in [send]
     * (direct-page mode opens a fresh WS, attach mode opens a fresh CDP session). The callback
     * receives the EXPLICIT session/target the activation produced, NOT a snapshot of mutable
     * `activeSessionId`/`activeTargetId`. Settable post-construction so production wiring can
     * install the hook without pushing it through every test factory override.
     */
    @Volatile
    var onTargetActivated: (suspend (sessionId: String?, targetId: String?) -> Unit)? = null

    val eventBuffer = ChromeCdpEventBuffer()

    @Volatile
    var activeSessionId: String? = null
        private set

    @Volatile
    var activeTargetId: String? = null
        private set

    @Volatile
    var isBroken: Boolean = false
        private set

    @Volatile
    private var current: LiveConnection? = null
    private var directPageWebSocketBase: String? = null

    /** Visible for tests: count of WS connections still considered active (not closed by us). */
    val activeConnectionCount: Int
        get() = if (current?.active == true) 1 else 0

    suspend fun connect(wsUrl: String) {
        // Unlink and close any existing connection BEFORE swapping in the new one so the old
        // connection's callbacks see `current !== this` and drop without touching shared state.
        val prev = current
        current = null
        prev?.closeQuietly("CDP connection replaced")
        isBroken = false
        directPageWebSocketBase = null
        current = openConnection(wsUrl)
    }

    fun useDirectPageTarget(
        targetId: String,
        wsUrl: String,
    ) {
        activeTargetId = targetId
        activeSessionId = null
        directPageWebSocketBase = wsUrl.substringBeforeLast('/', missingDelimiterValue = wsUrl)
    }

    suspend fun attachToTarget(targetId: String): String {
        val result =
            sendRaw(
                "Target.attachToTarget",
                buildJsonObject {
                    put("targetId", targetId)
                    put("flatten", true)
                },
                sessionId = null,
            )
        val sessionId =
            result.jsonObject["sessionId"]?.jsonPrimitive?.content
                ?: throw CdpException(-1, "No sessionId in attachToTarget response")
        activeSessionId = sessionId
        activeTargetId = targetId
        return sessionId
    }

    suspend fun attachToFirstRealPage(targets: List<PageTarget>): String {
        val target = ChromeCdpTarget.firstRealPage(targets)
        val targetId =
            target?.id ?: run {
                val result =
                    sendRaw(
                        "Target.createTarget",
                        buildJsonObject { put("url", "about:blank") },
                        sessionId = null,
                    )
                result.jsonObject["targetId"]?.jsonPrimitive?.content
                    ?: throw CdpException(-1, "Failed to create target")
            }
        return attachToTarget(targetId)
    }

    suspend fun send(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        options: CdpOptions = CdpOptions(),
    ): JsonElement {
        if (method == DIALOG_QUERY_METHOD) {
            return resolveDialogState(options)
        }

        if (options.targetId != null) {
            if (directPageWebSocketBase != null) {
                val switched = switchDirectPageTarget(options.targetId)
                if (switched) onTargetActivated?.invoke(null, options.targetId)
                return sendRaw(method, params, sessionId = null)
            }
            val sid = attachToTarget(options.targetId)
            onTargetActivated?.invoke(sid, options.targetId)
            return sendRaw(method, params, sid)
        }

        val sessionId = routeSessionId(method, options)

        return try {
            sendRaw(method, params, sessionId)
        } catch (e: CdpException) {
            if (!isStaleSessionError(e) || sessionId == null || sessionId != activeSessionId) throw e

            recoveryMutex.withLock {
                if (activeSessionId != sessionId) {
                    sendRaw(method, params, activeSessionId)
                } else {
                    val recovered =
                        try {
                            recoverStaleSession()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (recoveryError: Exception) {
                            throw CdpException(
                                -1,
                                "Session recovery failed: ${recoveryError.message}",
                                recoveryError,
                            )
                        }
                    if (recovered) {
                        sendRaw(method, params, activeSessionId)
                    } else {
                        throw e
                    }
                }
            }
        }
    }

    fun drainEvents(): List<CdpIncoming.Event> = eventBuffer.drain()

    fun close() {
        val prev = current
        current = null
        prev?.closeQuietly("Client closed")
        eventBuffer.clear()
        activeSessionId = null
        activeTargetId = null
        directPageWebSocketBase = null
    }

    private fun routeSessionId(
        method: String,
        options: CdpOptions,
    ): String? {
        if (options.sessionId != null) return options.sessionId
        if (method.startsWith("Target.") || method.startsWith("Browser.")) return null
        if (directPageWebSocketBase != null) return null
        return activeSessionId
            ?: throw CdpException(-1, "No active page session; call attachToTarget first")
    }

    private suspend fun switchDirectPageTarget(targetId: String): Boolean {
        switchMutex.withLock {
            val base = directPageWebSocketBase ?: return false
            if (targetId == activeTargetId) return false
            val previous = current
            val wsUrl = "$base/$targetId"
            // Open new first, then swap `current` so the previous connection's incoming callbacks
            // observe `current !== this` and drop.
            current = openConnection(wsUrl)
            isBroken = false
            activeTargetId = targetId
            activeSessionId = null
            previous?.closeQuietly("CDP connection switched")
            return true
        }
    }

    private suspend fun sendRaw(
        method: String,
        params: JsonObject,
        sessionId: String?,
    ): JsonElement {
        val live = current ?: throw CdpException(-1, "Not connected")
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<JsonElement>()
        if (!live.addPending(id, deferred)) {
            return deferred.await()
        }
        // Defense-in-depth: a switch may have completed between our `live = current` read
        // and addPending. Skip the send so we don't issue a CDP frame on a soon-to-be-closed WS.
        if (current !== live) {
            live.pending.remove(id)
            throw CdpException(-1, "CDP connection switched")
        }
        try {
            val msg = buildCdpRequest(id, method, params, sessionId)
            try {
                live.raw!!.send(msg)
            } catch (t: Exception) {
                val transportError = IOException("CDP transport send failed: ${t.message}", t)
                markTransportBroken(transportError, live)
                throw transportError
            }
            return try {
                withTimeout(commandTimeoutMs) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                throw CdpException(
                    -1,
                    "CDP command '$method' timed out after ${commandTimeoutMs}ms " +
                        "(per-command cap; script-level timeout_ms is a separate, larger budget)",
                )
            }
        } finally {
            live.pending.remove(id)
        }
    }

    private fun handleMessage(
        source: LiveConnection,
        text: String,
    ) {
        when (val msg = parseCdpMessage(text)) {
            is CdpIncoming.Response -> {
                val deferred = source.pending.remove(msg.id) ?: return
                if (msg.error != null) {
                    deferred.completeExceptionally(CdpException(msg.error.code, msg.error.message))
                } else {
                    deferred.complete(msg.result ?: JsonNull)
                }
            }
            is CdpIncoming.Event -> {
                // Drop stale events from a switched-away WS so they cannot pollute the buffer.
                if (current === source) {
                    eventBuffer.add(msg)
                    routeDialogEvent(msg)
                }
            }
        }
    }

    private fun routeDialogEvent(event: CdpIncoming.Event) {
        if (event.method != DIALOG_OPENING_EVENT && event.method != DIALOG_CLOSED_EVENT) return
        val targetKey = dialogTargetKey(event.sessionId) ?: return
        when (event.method) {
            DIALOG_OPENING_EVENT -> {
                val params = event.params
                eventBuffer.dialogTracker.setOpen(
                    targetKey,
                    DialogStateTracker.DialogState(
                        type = params["type"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                        message = params["message"]?.jsonPrimitive?.contentOrNull ?: "",
                        defaultPrompt = params["defaultPrompt"]?.jsonPrimitive?.contentOrNull,
                        hasBrowserHandler =
                            params["hasBrowserHandler"]?.jsonPrimitive?.booleanOrNull
                                ?: false,
                        url = params["url"]?.jsonPrimitive?.contentOrNull,
                    ),
                )
            }
            DIALOG_CLOSED_EVENT -> eventBuffer.dialogTracker.setClosed(targetKey)
        }
    }

    /**
     * Resolves the synthetic [DIALOG_QUERY_METHOD] query against the in-memory tracker. Returns
     * `JsonNull` when no dialog is open for the resolved target. The query never leaves the
     * device — it does NOT round-trip to Chrome — because the source of truth is the per-target
     * dialog tracker fed by `Page.javascriptDialogOpening` / `Closed` events.
     */
    private fun resolveDialogState(options: CdpOptions): JsonElement {
        val key = dialogTargetKey(options.sessionId, options.targetId) ?: return JsonNull
        val state = eventBuffer.dialogTracker.get(key) ?: return JsonNull
        return buildJsonObject {
            put("type", state.type)
            put("message", state.message)
            put("defaultPrompt", state.defaultPrompt)
            put("hasBrowserHandler", state.hasBrowserHandler)
            put("url", state.url)
        }
    }

    /**
     * Picks the per-target tracker key. Attach mode emits events with a non-null `sessionId`;
     * direct mode opens one WS per target so events have no `sessionId` and we fall back to
     * `activeTargetId`. Explicit overrides take priority.
     */
    private fun dialogTargetKey(
        explicitSessionId: String? = null,
        explicitTargetId: String? = null,
    ): String? =
        explicitSessionId
            ?: explicitTargetId
            ?: activeSessionId
            ?: activeTargetId

    private fun handleFailure(
        source: LiveConnection,
        error: Throwable,
    ) {
        markTransportBroken(error, source)
    }

    private fun handleClosed(
        source: LiveConnection,
        error: CdpConnectionClosedException,
    ) {
        markTransportBroken(error, source)
    }

    private fun markTransportBroken(
        error: Throwable,
        source: LiveConnection,
    ) {
        // Stale failure from a switched-away WS — already drained by closeQuietly.
        if (current !== source) return
        val cdpError = CdpException(-1, error.message ?: "Connection failed")
        isBroken = true
        source.pending.values.forEach { it.completeExceptionally(cdpError) }
        source.pending.clear()
        onTransportFailure(error)
    }

    private suspend fun recoverStaleSession(): Boolean {
        val result = sendRaw("Target.getTargets", JsonObject(emptyMap()), sessionId = null)
        val infos = result.jsonObject["targetInfos"]?.jsonArray ?: return false

        val firstPage =
            infos
                .mapNotNull { it.jsonObject }
                .firstOrNull { info ->
                    val type = info["type"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                    val url = info["url"]?.jsonPrimitive?.contentOrNull ?: return@firstOrNull false
                    ChromeCdpTarget.isRealPage(type, url)
                } ?: return false

        val targetId = firstPage["targetId"]?.jsonPrimitive?.content ?: return false
        attachToTarget(targetId)
        return true
    }

    private fun isStaleSessionError(e: CdpException): Boolean = "Session with given id not found" in e.message

    /**
     * One CDP WebSocket plus its own pending-request map. The per-connection map plus the
     * `lock`-guarded transition between "active, accepting" and "closed, drained" is the
     * atomicity boundary: addPending and closeQuietly are mutually exclusive, so a sendRaw
     * cannot register on a connection that has just been drained.
     */
    private class LiveConnection {
        private val lock = Any()

        @Volatile var active: Boolean = true
            private set

        @Volatile var raw: CdpConnection? = null
        val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()

        /**
         * Atomically registers `deferred` under `id` if the connection is still active.
         * Returns true if registered; false if already closed (deferred completed exceptionally).
         */
        fun addPending(
            id: Int,
            deferred: CompletableDeferred<JsonElement>,
        ): Boolean {
            synchronized(lock) {
                if (!active) {
                    deferred.completeExceptionally(
                        CdpException(-1, "CDP connection no longer active"),
                    )
                    return false
                }
                pending[id] = deferred
                return true
            }
        }

        fun closeQuietly(reason: String) {
            val toFail: List<CompletableDeferred<JsonElement>>
            synchronized(lock) {
                if (!active) return
                active = false
                toFail = pending.values.toList()
                pending.clear()
            }
            val err = CdpException(-1, reason)
            toFail.forEach { it.completeExceptionally(err) }
            try {
                raw?.close()
            } catch (_: Throwable) {
                // Best-effort; the per-connection drain above is what matters.
            }
        }
    }

    private suspend fun openConnection(wsUrl: String): LiveConnection {
        val live = LiveConnection()
        live.raw =
            connectionFactory.connect(
                wsUrl,
                { text -> handleMessage(live, text) },
                { error -> handleFailure(live, error) },
                { error -> handleClosed(live, error) },
            )
        return live
    }

    companion object {
        /**
         * Default per-CDP-command cap. Picked to comfortably cover a relay hop: every CDP frame
         * goes through an in-app TCP relay -> adbd -> Chrome's abstract socket, which adds
         * loopback latency on top of Chrome's own response time.
         */
        const val DEFAULT_COMMAND_TIMEOUT_MS: Long = 30_000L

        /**
         * Synthetic CDP-shaped method name resolved by [resolveDialogState] without touching the
         * wire. Lets page code query the in-memory dialog tracker through the existing `cdp()`
         * channel and gives helpers a stable key that avoids colliding with real CDP namespaces.
         */
        const val DIALOG_QUERY_METHOD: String = "Eqo.getDialog"

        const val DIALOG_OPENING_EVENT: String = "Page.javascriptDialogOpening"
        const val DIALOG_CLOSED_EVENT: String = "Page.javascriptDialogClosed"
    }
}
