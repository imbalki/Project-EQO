// Origin: imbalki/project-eqo (EQO-authored, share contact / share location actions)
@file:Suppress("ReturnCount")

package ai.eqo.actions.impl

import ai.eqo.actions.base.Action
import ai.eqo.actions.base.ActionResult
import ai.eqo.core.agent.Contact
import ai.eqo.core.agent.ContactResolution
import ai.eqo.core.agent.ContactResolver
import ai.eqo.core.agent.failureMessage
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** A position read for one send. Never stored, never logged: [toString] hides the values. */
internal class LocationFix(
    val latitude: Double,
    val longitude: Double,
) {
    override fun toString(): String = "LocationFix(redacted)"
}

/** Source of the phone's position. The Android edge reads it; tests supply fakes. */
internal fun interface LocationSource {
    /** Returns null when the permission is missing or no position is available. */
    suspend fun current(context: Context): LocationFix?
}

/** Tries a fresh fix for a few seconds, then falls back to the newest last-known position. */
internal class AndroidLocationSource(
    private val fixTimeoutMillis: Long = FIX_TIMEOUT_MILLIS,
) : LocationSource {
    @SuppressLint("MissingPermission")
    override suspend fun current(context: Context): LocationFix? {
        val fine = android.Manifest.permission.ACCESS_FINE_LOCATION
        if (context.checkSelfPermission(fine) != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = PROVIDERS.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh =
            providers.filter { it in FRESH_PROVIDERS }.firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(fixTimeoutMillis) { freshFix(manager, provider) }
            }
        val best =
            fresh
                ?: PROVIDERS
                    .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                    .maxByOrNull { it.time }
        return best?.let { LocationFix(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(
        manager: LocationManager,
        provider: String,
    ): Location? =
        suspendCancellableCoroutine { continuation ->
            val signal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val executor = Executor { it.run() }
            manager.getCurrentLocation(provider, signal, executor) { continuation.resume(it) }
        }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 8_000L
        val FRESH_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val PROVIDERS = FRESH_PROVIDERS + LocationManager.PASSIVE_PROVIDER
    }
}

/** The existing send routes, reused as they are. Each returns the route's own honest result. */
internal interface ShareRoutes {
    suspend fun whatsApp(
        phone: String,
        message: String,
    ): ActionResult

    suspend fun sms(
        phone: String,
        message: String,
    ): ActionResult

    suspend fun email(
        to: String,
        subject: String,
        body: String,
        context: Context,
    ): ActionResult
}

/**
 * SHARE_CONTACT and SHARE_LOCATION: build one plain-text message and hand it to the chosen existing route.
 * Nothing here logs or stores a number or a position; failures name contacts, never numbers.
 */
internal class ShareActions(
    private val contactResolver: ContactResolver,
    private val locationSource: LocationSource,
    private val routes: ShareRoutes,
) {
    fun getActions(): List<Action> = listOf(ShareContactAction(), ShareLocationAction())

    private sealed interface Recipient {
        data class Phone(
            val number: String,
        ) : Recipient

        data class Email(
            val address: String,
        ) : Recipient

        data class Refused(
            val result: ActionResult,
        ) : Recipient
    }

    private suspend fun recipient(
        to: String,
        via: String,
    ): Recipient {
        if (via == VIA_EMAIL) {
            val address = to.trim()
            return if (EMAIL.matches(address)) {
                Recipient.Email(address)
            } else {
                Recipient.Refused(ActionResult.Failure("Sending by email needs an email address for the recipient."))
            }
        }
        return when (val resolved = contactResolver.resolveWithDisambiguation(to)) {
            is ContactResolution.Found -> Recipient.Phone(resolved.contact.phoneNumber)
            is ContactResolution.Ambiguous ->
                Recipient.Refused(ActionResult.Failure(ambiguous(to, resolved.matches)))
            is ContactResolution.PermissionDenied ->
                Recipient.Refused(ActionResult.Failure(resolved.failureMessage()))
            is ContactResolution.NotFound ->
                Recipient.Refused(
                    ActionResult.Failure("No saved contact or number matches '${to.trim()}'. Nothing was sent."),
                )
        }
    }

    private suspend fun send(
        recipient: Recipient,
        via: String,
        subject: String,
        text: String,
        context: Context,
    ): ActionResult =
        when (recipient) {
            is Recipient.Refused -> recipient.result
            is Recipient.Email -> routes.email(recipient.address, subject, text, context)
            is Recipient.Phone ->
                if (via == VIA_SMS) routes.sms(recipient.number, text) else routes.whatsApp(recipient.number, text)
        }

    private fun ambiguous(
        query: String,
        matches: List<Contact>,
    ): String = "More than one contact matches '${query.trim()}': ${names(matches)}. Nothing was sent."

    private fun names(matches: List<Contact>): String =
        matches
            .map { it.name }
            .distinct()
            .sorted()
            .joinToString(", ")

    private fun viaOf(params: Map<String, String>): String? =
        params["via"]
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in VIAS }

    private inner class ShareContactAction : Action {
        override val name: String = "SHARE_CONTACT"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val query = params["contact"]?.trim().orEmpty()
            val to = params["to"]?.trim().orEmpty()
            val via = viaOf(params) ?: return ActionResult.Failure("via must be whatsapp, sms or email.")
            if (query.isEmpty() || to.isEmpty()) return ActionResult.Failure("contact and to are required.")
            val contact =
                when (val resolved = contactResolver.resolveWithDisambiguation(query)) {
                    is ContactResolution.Found -> resolved.contact
                    is ContactResolution.Ambiguous ->
                        return ActionResult.Failure(ambiguous(query, resolved.matches))
                    is ContactResolution.PermissionDenied ->
                        return ActionResult.Failure(resolved.failureMessage())
                    is ContactResolution.NotFound ->
                        return ActionResult.Failure("No saved contact named '$query' was found. Nothing was sent.")
                }
            if (contact.source == DIRECT_INPUT) {
                return ActionResult.Failure("Name a saved contact to share, not a phone number. Nothing was sent.")
            }
            val target = recipient(to, via)
            return send(target, via, "Contact: ${contact.name}", "${contact.name}: ${contact.phoneNumber}", context)
        }
    }

    private inner class ShareLocationAction : Action {
        override val name: String = "SHARE_LOCATION"

        override suspend fun execute(
            params: Map<String, String>,
            context: Context,
        ): ActionResult {
            requireRegistryExecution()?.let { return it }
            val to = params["to"]?.trim().orEmpty()
            val via = viaOf(params) ?: return ActionResult.Failure("via must be whatsapp, sms or email.")
            if (to.isEmpty()) return ActionResult.Failure("to is required.")
            val target = recipient(to, via)
            if (target is Recipient.Refused) return target.result
            val fix =
                locationSource.current(context)
                    ?: return ActionResult.Failure(
                        "EQO could not get the phone's location. Is location on? Nothing was sent.",
                    )
            val point = "${coordinate(fix.latitude)},${coordinate(fix.longitude)}"
            val text = "My location: https://maps.google.com/?q=$point"
            return send(target, via, "My location", text, context)
        }
    }

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

    private companion object {
        const val VIA_SMS = "sms"
        const val VIA_EMAIL = "email"
        const val DIRECT_INPUT = "direct_input"
        val VIAS = setOf("whatsapp", VIA_SMS, VIA_EMAIL)
        val EMAIL = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    }
}
