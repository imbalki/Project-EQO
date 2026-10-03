/*
 * EQO (TASK-010, issue #15): verified DevTools socket + endpoint check.
 *
 * Acceptance criteria: "After restart, the endpoint is verified before any action" (AC2) and
 * the task Scope's "Chrome must create its own socket; forwarding does not create it". This
 * file decides whether Chrome's `chrome_devtools_remote` abstract socket is actually bound and
 * whether its DevTools HTTP endpoint answers, so the setup orchestrator can gate every action
 * on a real verification rather than assuming success.
 *
 * Token matching is extracted from the donor (imoonkey/closepaw): abstract sockets appear in
 * `/proc/net/unix` as `@<name>`, and Chrome appends `_<pid>` on some builds, so the match is
 * `@<name>` exactly OR `@<name>_<pid>` — never a bare substring (which would accept
 * `@chrome_devtools_remote_unrelated`).
 *
 * The `/proc/net/unix` read and the DevTools GET are both injected so this is host-testable
 * without a device; production supplies the real file reader and a loopback-only DevTools GET.
 */
package ai.eqo.browser.cdp

import java.io.File

/** Result of asking whether the DevTools socket/endpoint is currently usable. */
sealed class EndpointVerification {
    /** The socket is bound and the DevTools endpoint answered /json/version. */
    data class Verified(
        val version: DevtoolsVersion,
    ) : EndpointVerification()

    /** `/proc/net/unix` is readable but the socket name is absent. */
    object NotBound : EndpointVerification()

    /** The socket is bound but the DevTools HTTP endpoint did not answer. */
    object Unreachable : EndpointVerification()

    /** The socket state could not be read — surface "re-check" rather than guess. */
    object Unknown : EndpointVerification()
}

/**
 * Reads `/proc/net/unix` (two-stage: an injected reader for the app-uid path, plus an optional
 * shell-uid fallback for OEMs where SELinux denies app-uid access) and token-matches the
 * Chrome DevTools abstract socket.
 */
class DevtoolsSocketProbe(
    private val procNetUnixText: () -> String? = { defaultProcNetUnix() },
    private val shellFallbackText: (() -> String?)? = null,
) {
    enum class Result { Bound, NotBound, Unknown }

    /** @return Bound when Chrome has bound the devtools socket, else NotBound / Unknown. */
    fun probe(): Result {
        val text = procNetUnixText()
        if (text != null) return parse(text)
        val fallback = shellFallbackText?.invoke() ?: return Result.Unknown
        return parse(fallback)
    }

    companion object {
        const val CHROME_DEVTOOLS_SOCKET = "chrome_devtools_remote"

        /**
         * `/proc/net/unix` lines look like:
         * `0000000000000000: 00000003 00000000 00010000 0001 01 1234 @chrome_devtools_remote_5678`
         * Match `@<name>` exactly OR `@<name>_<pid>` (Chrome appends `_<pid>` on some builds).
         *
         * EQO tightens the donor's match: the `_<pid>` suffix must be all digits, so
         * `@chrome_devtools_remote_unrelated_thing` is rejected (the donor's prefix-only match
         * would have accepted any `_<suffix>`). The token is the whole trimmed line when it has
         * no space, so a bare `@<name>` line is still recognized.
         */
        fun containsAbstractSocket(
            procContent: String,
            socketName: String,
        ): Boolean {
            val needleExact = "@$socketName"
            return procContent.lineSequence().any { line ->
                val token = line.trim().substringAfterLast(' ')
                when {
                    token == needleExact -> true
                    token.startsWith("${needleExact}_") ->
                        token.substring(needleExact.length + 1).all { it.isDigit() }
                    else -> false
                }
            }
        }

        internal fun parse(procContent: String): Result =
            if (containsAbstractSocket(procContent, CHROME_DEVTOOLS_SOCKET)) {
                Result.Bound
            } else {
                Result.NotBound
            }

        private fun defaultProcNetUnix(): String? = runCatching { File("/proc/net/unix").readText() }.getOrNull()
    }
}

/**
 * End-to-end endpoint verification used by the setup orchestrator's post-restart gate (AC2).
 * Combines the socket probe with a DevTools `/json/version` GET so "verified" means Chrome's
 * DevTools endpoint actually answers — not merely that a matching kernel socket line exists.
 *
 * [devtoolsGet] returns the raw HTTP response bytes for a DevTools path (e.g. "/json/version").
 * It is injected and MUST be loopback-only in production (see the TASK-010 evidence's
 * loopback-only disposition); no remote transport is wired here.
 */
class DevtoolsEndpoint(
    private val socketProbe: DevtoolsSocketProbe = DevtoolsSocketProbe(),
    private val devtoolsGet: (String) -> ByteArray,
) {
    fun verify(): EndpointVerification {
        val socketResult = socketProbe.probe()
        if (socketResult != DevtoolsSocketProbe.Result.Bound) {
            return if (socketResult == DevtoolsSocketProbe.Result.Unknown) {
                EndpointVerification.Unknown
            } else {
                EndpointVerification.NotBound
            }
        }
        return try {
            val body = DevtoolsHttpProtocol.parseHttpBody(devtoolsGet("/json/version"))
            EndpointVerification.Verified(DevtoolsHttpProtocol.parseVersion(body))
        } catch (e: CdpSetupError.MalformedResponse) {
            EndpointVerification.Unreachable
        } catch (e: Throwable) {
            EndpointVerification.Unreachable
        }
    }
}
