// Origin: EQO TASK-074 (#20), sole bounded HTTP seam for the weather request.
package ai.eqo.actions.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal interface InformationHttp {
    fun online(): Boolean

    suspend fun weather(location: String): String
}

internal class AndroidInformationHttp(
    private val context: Context,
) : InformationHttp {
    override fun online(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
        return capabilities != null &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override suspend fun weather(location: String): String =
        withContext(Dispatchers.IO) {
            check(online()) { "Offline." }
            // Caller cannot change the scheme, host, port or query. No redirects, credentials, or full-body reads.
            val url = URL("https://wttr.in/${URLEncoder.encode(location, "UTF-8")}?format=%C,+%t")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Weather service unavailable." }
                val bytes =
                    connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (output.size() <= MAX_BYTES) {
                            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                check(bytes.size <= MAX_BYTES) { "Weather response too large." }
                bytes.toString(Charsets.UTF_8).trim().also { check(it.isNotBlank()) { "Weather response empty." } }
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        private const val TIMEOUT_MS = 3000
        private const val MAX_BYTES = 4096
        private const val BUFFER_BYTES = 1024
    }
}
