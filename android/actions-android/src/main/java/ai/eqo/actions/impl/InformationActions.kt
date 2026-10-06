// Origin: yashab-cyber/opendroid @ 6ff5a061755b597b0558fed1f565587837ed4d51
// Origin: path: app/src/main/java/com/opendroid/ai/actions/InformationActions.kt; EQO TASK-074 gated/manual outcomes.
package ai.eqo.actions.impl

import ai.eqo.accessibility.A11yError
import ai.eqo.accessibility.A11yResult
import ai.eqo.accessibility.EqoAutomation
import ai.eqo.accessibility.UntrustedScreenText
import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

internal class InformationActions(
    private val launcher: GatedIntentLauncher,
    private val permissions: PermissionRequester,
    private val http: InformationHttp,
    private val automation: () -> EqoAutomation?,
) {
    fun getActions(): List<Action> =
        listOf(
            RegisteredExecutor("WEB_SEARCH") { params, _ -> web(params.getValue("query")) },
            RegisteredExecutor("GET_NEWS") { params, _ ->
                search("https://news.google.com/search", "news ${params["topic"] ?: "latest news"}")
            },
            RegisteredExecutor("GET_WEATHER", ::weather),
            RegisteredExecutor("SUMMARIZE_URL") { params, _ -> summarize(params.getValue("url")) },
            RegisteredExecutor("TRANSLATE") { params, _ -> translate(params) },
            RegisteredExecutor("DEFINE_WORD") { params, _ -> web("define ${params.getValue("word")}") },
            RegisteredExecutor("FACT_CHECK") { params, _ -> web("fact check ${params.getValue("claim")}") },
            RegisteredExecutor("CALCULATE") { params, _ -> calculate(params.getValue("expression")) },
            RegisteredExecutor("CONVERT_UNITS") { params, _ -> web(conversion(params.getValue("value"), params)) },
            RegisteredExecutor("CURRENCY_CONVERT") { params, _ -> web(conversion(params.getValue("amount"), params)) },
            RegisteredExecutor("CHECK_STOCK") { params, _ -> web("stock ${params.getValue("symbol")}") },
        )

    private fun web(query: String): ActionResult = search(GOOGLE_SEARCH, query)

    private fun search(
        base: String,
        query: String,
    ): ActionResult {
        if (!http.online()) return ActionResult.Failure("No internet connection is available; no search was opened.")
        val uri = "$base?q=${encode(query)}".toUri()
        launcher.open(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult.UserActionRequired(
            "Search opened. Review the results there; EQO has not retrieved or verified them.",
        )
    }

    private fun summarize(url: String): ActionResult =
        when {
            !PublicWebAddress.allowed(url) ->
                ActionResult.Failure("Only public HTTPS web addresses without credentials are supported.")
            !http.online() -> ActionResult.Failure("No internet connection is available; no page was opened.")
            else -> {
                launcher.open(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult.UserActionRequired(
                    "Page opened. The donor action does not summarize its contents; no summary was generated.",
                )
            }
        }

    private fun translate(params: Map<String, String>): ActionResult {
        if (!http.online()) {
            return ActionResult.Failure("No internet connection is available; no translator was opened.")
        }
        val from = params["from"]?.ifBlank { "auto" } ?: "auto"
        val to = params["to"]?.ifBlank { "en" } ?: "en"
        val text = params.getValue("text")
        val uri = "https://translate.google.com/?sl=${encode(from)}&tl=${encode(to)}&text=${encode(text)}&op=translate"
        launcher.open(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ActionResult.UserActionRequired(
            "Google Translate opened. Review the translation there; no translated text was retrieved.",
        )
    }

    private fun calculate(expression: String): ActionResult {
        val result = SimpleCalculation.evaluate(expression)
        return if (result == null) {
            web(expression)
        } else {
            ActionResult.Success(mapOf("message" to "$expression = $result"))
        }
    }

    private suspend fun weather(
        params: Map<String, String>,
        context: Context,
    ): ActionResult {
        if (!http.online()) return ActionResult.Failure("No internet connection is available; weather was not fetched.")
        val gate =
            automation()?.runAction { A11yResult.success("Ready.") }
                ?: A11yResult.failure(A11yError.AccessibilityDisabled)
        val requested = params["location"]?.trim().orEmpty()
        return when {
            !gate.isSuccess -> gate.toActionResult()
            requested.isBlank() || requested == "current location" ->
                when (val located = currentLocation(context)) {
                    is Located.At -> fetchWeather(located.coordinates)
                    is Located.Refused -> located.result
                }
            else -> fetchWeather(requested)
        }
    }

    private sealed interface Located {
        data class At(
            val coordinates: String,
        ) : Located

        data class Refused(
            val result: ActionResult,
        ) : Located
    }

    private suspend fun currentLocation(context: Context): Located =
        when {
            !permissions.request(
                ActionPermission.Runtime(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    "Allow approximate location to check local weather.",
                ),
            ) -> Located.Refused(ActionResult.Failure(LOCATION_NOT_REQUESTED))
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED -> Located.Refused(ActionResult.Failure(LOCATION_NOT_GRANTED))
            else -> {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val last = manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (last == null) {
                    Located.Refused(
                        ActionResult.NeedsInput(
                            "Which city should I check the weather for?",
                            metadata = mapOf("paramKey" to "location"),
                        ),
                    )
                } else {
                    // Coordinates preserve donor fallback without a second hidden network path through Geocoder.
                    Located.At("${last.latitude},${last.longitude}")
                }
            }
        }

    private suspend fun fetchWeather(location: String): ActionResult {
        val weatherText =
            try {
                http.weather(location)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        return if (weatherText == null) {
            web("weather in $location")
        } else {
            // Takeover after a suspended fetch is still enforced by the launcher's gate.
            launcher.open(
                Intent(
                    Intent.ACTION_VIEW,
                    "$GOOGLE_SEARCH?q=${encode("weather in $location")}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            val fenced = UntrustedScreenText.wrap(weatherText.take(MAX_WEATHER_CHARS))
            ActionResult.Success(mapOf("message" to "Weather service response:\n$fenced\nSearch opened for details."))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val GOOGLE_SEARCH = "https://www.google.com/search"
        const val LOCATION_NOT_REQUESTED = "Location permission was not granted. Supply a city name instead."
        const val LOCATION_NOT_GRANTED = "Location access is not granted. Supply a city name instead."
        const val MAX_WEATHER_CHARS = 4096
    }
}

private fun conversion(
    amount: String,
    params: Map<String, String>,
): String = "convert $amount ${params["from"].orEmpty()} to ${params["to"].orEmpty()}"

/** Donor only evaluates a number or ONE binary operation, not a programming language. */
internal object SimpleCalculation {
    private const val MAX_EXPRESSION_CHARS = 256
    private const val ZERO_DIVISOR = 0.0
    private const val NUMBER = "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"
    private val operation = Regex("^($NUMBER)([+*/-])($NUMBER)$")

    fun evaluate(expression: String): Double? {
        if (expression.length > MAX_EXPRESSION_CHARS) return null
        val clean = expression.replace(" ", "")
        val match = operation.matchEntire(clean)
        val result = if (match == null) clean.toDoubleOrNull() else apply(match)
        return result?.takeIf { it.isFinite() }
    }

    private fun apply(match: MatchResult): Double? {
        val (leftText, operator, rightText) = match.destructured
        val left = leftText.toDoubleOrNull()
        val right = rightText.toDoubleOrNull()
        return if (left == null || right == null) {
            null
        } else {
            when (operator) {
                "+" -> left + right
                "-" -> left - right
                "*" -> left * right
                else -> if (right == ZERO_DIVISOR) null else left / right
            }
        }
    }
}

/** Browser hand-off validation, not a promise to control browser DNS or later redirects. */
internal object PublicWebAddress {
    private const val HTTPS_PORT = 443
    private val internalSuffixes = listOf(".localhost", ".local", ".internal")

    fun allowed(raw: String): Boolean =
        try {
            val uri = URI(raw)
            uri.scheme == "https" &&
                uri.rawUserInfo == null &&
                uri.port in setOf(-1, HTTPS_PORT) &&
                uri.rawFragment == null &&
                isPublicHost(uri.host?.lowercase(Locale.ROOT).orEmpty())
        } catch (_: Exception) {
            false
        }

    private fun isPublicHost(host: String): Boolean =
        host.contains('.') &&
            !host.endsWith('.') &&
            internalSuffixes.none { host.endsWith(it) } &&
            host != "localhost" &&
            !host.contains(':') &&
            !host.startsWith('[') &&
            !host.all { it.isDigit() || it == '.' } &&
            !host.startsWith("0x")
}
