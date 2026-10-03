// Origin: imoonkey/closepaw @ 75dae2653f5a6b25d5df51ee7008b0f830de1536,
//   path: app/src/main/kotlin/ai/closepaw/browser/cdp/CdpTransport.kt
// TASK-010 (issue #15): extracted into :browser-cdp for the Chrome DevTools (CDP) spike.
// Changes vs donor: package renamed to ai.eqo.browser.cdp. This is the transport seam only —
// no concrete WebSocket/socket transport is wired here, so the module holds NO production
// caller that reaches a device (see the loopback-only disposition in the TASK-010 evidence).
package ai.eqo.browser.cdp

import java.io.IOException

interface CdpConnection {
    fun send(text: String)

    fun close()
}

class CdpConnectionClosedException(
    val code: Int,
    val reason: String,
) : IOException("CDP connection closed: code=$code, reason=$reason")

fun interface CdpConnectionFactory {
    suspend fun connect(
        url: String,
        onMessage: (String) -> Unit,
        onFailure: (Throwable) -> Unit,
        onClosed: (CdpConnectionClosedException) -> Unit,
    ): CdpConnection
}
