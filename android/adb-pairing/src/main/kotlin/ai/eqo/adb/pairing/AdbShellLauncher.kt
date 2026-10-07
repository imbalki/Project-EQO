/*
 * EQO (TASK-080, issue #20): the one thing EQO uses wireless ADB for - starting the
 * privileged helper.
 *
 * [HelperStartCommand] builds the single command the helper activation needs
 * (`<nativeLibraryDir>/libeqo-starter.so --apk=<base.apk>`, as recorded in
 * task-007-helper-spike.md) from two app-supplied paths and refuses anything that is not a
 * plain absolute path. [AdbShellLauncher] runs exactly that command over the pinned,
 * enrolled connect plane ([AdbTlsClient]); it has no entry point for any other command, so
 * ADB gives EQO no new capability beyond starting its own helper.
 */
package ai.eqo.adb.pairing

import java.io.ByteArrayOutputStream
import java.io.IOException

/** Builds and validates the helper start command. */
object HelperStartCommand {
    const val STARTER_NAME = "libeqo-starter.so"
    private val plainPath = Regex("/[A-Za-z0-9_.=+~/-]+")

    /** @throws IllegalArgumentException when a path contains anything but plain path characters. */
    fun build(
        nativeLibraryDir: String,
        apkPath: String,
    ): String {
        require(plainPath.matches(nativeLibraryDir)) { "native library dir is not a plain absolute path" }
        require(plainPath.matches(apkPath) && apkPath.endsWith(".apk")) { "apk path is not a plain absolute .apk path" }
        require(!nativeLibraryDir.contains("..") && !apkPath.contains("..")) { "paths must not contain .." }
        return "${nativeLibraryDir.trimEnd('/')}/$STARTER_NAME --apk=$apkPath"
    }

    /** True only for a command [build] could have produced. Used by [AdbShellLauncher]. */
    fun isHelperStart(command: String): Boolean {
        val marker = "/$STARTER_NAME --apk="
        val at = command.indexOf(marker)
        if (at <= 0) return false
        return runCatching { build(command.substring(0, at), command.substring(at + marker.length)) == command }
            .getOrDefault(false)
    }
}

internal class AdbShellLauncher(
    private val keyStore: AdbCryptoKeyStore,
) {
    /**
     * Runs [command] (which must be a [HelperStartCommand]) on the enrolled device and returns
     * its (bounded) console output. Throws [ServerPinException] if the server is not the
     * enrolled one; no byte is written to an unpinned server.
     */
    fun runHelperStart(
        host: String,
        port: Int,
        command: String,
        timeoutMs: Int,
    ): String {
        require(HelperStartCommand.isHelperStart(command)) { "only the helper start command may be run over ADB" }
        val channel = AdbTlsClient.connectWithStls(host, port, keyStore, timeoutMs)
        channel.use { return exchange(it, command) }
    }

    private fun exchange(
        channel: AdbTlsClient.TlsChannel,
        command: String,
    ): String {
        val input = channel.inputStream
        val out = channel.outputStream
        // After the TLS handshake adbd speaks first with A_CNXN; never send our own (see AdbTlsClient).
        expectFrame(AdbProtocol.Message.read(input), AdbProtocol.A_CNXN, "adbd did not open with A_CNXN")
        val open = AdbProtocol.openShell(LOCAL_ID, command)
        AdbProtocol.Message.write(out, open.command, open.arg0, open.arg1, open.payload)
        val reply = AdbProtocol.Message.read(input)
        expectFrame(reply, AdbProtocol.A_OKAY, "adbd refused the shell service")
        val remoteId = reply.arg0
        val sink = ByteArrayOutputStream()
        while (true) {
            val msg = AdbProtocol.Message.read(input)
            when (msg.command) {
                AdbProtocol.A_WRTE -> {
                    val room = MAX_OUTPUT - sink.size()
                    if (room > 0) sink.write(msg.payload, 0, minOf(msg.payload.size, room))
                    AdbProtocol.Message.write(out, AdbProtocol.A_OKAY, LOCAL_ID, remoteId, ByteArray(0))
                }
                AdbProtocol.A_CLSE -> return sink.toString(Charsets.UTF_8.name())
                else -> expectFrame(msg, AdbProtocol.A_WRTE, "unexpected adb frame while starting the helper")
            }
        }
    }

    private fun expectFrame(
        msg: AdbProtocol.Message,
        command: Int,
        failure: String,
    ) {
        if (msg.command != command) throw IOException(failure)
    }

    private companion object {
        const val LOCAL_ID = 1
        const val MAX_OUTPUT = 16 * 1024
    }
}
