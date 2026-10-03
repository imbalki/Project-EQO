package ai.eqo.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Gate shown to the user before a provider credential is used for the first
 * time: cloud requests through OpenRouter spend the user's own credits, so
 * the cost disclosure must be accepted before the first key use.
 *
 * The gate is binding state, not UI-only: [ProviderCostDisclosureGate.requirement]
 * is exposed so callers (Settings page, provider activation) can require the
 * disclosure screen before the key round-trips, and [acknowledge] atomically
 * records that the screen was shown and accepted.
 *
 * No credential- or ciphertext-shaped value is ever stored in or emitted by
 * this type.
 */
object ProviderCostDisclosure {
    /** Text surfaced by the disclosure screen; key-matching is not its job. */
    const val DISCLOSURE_TEXT =
        "EQO will send requests to OpenRouter using your own API key. " +
            "OpenRouter credits are real money: model providers charge per request, " +
            "and EQO does not pay any part of that cost. Free models are rate limited " +
            "and may be unavailable. You can revoke the key at any time in Settings."

    /** Text for the required acknowledgement control. */
    const val ACKNOWLEDGEMENT_LABEL =
        "I understand requests may cost money, and I want to use my own key."
}

/** Per-provider disclosure bookkeeping. */
interface ProviderCostDisclosureGate {
    val disclosureEvents: StateFlow<Map<String, Long>>

    /** Whether the disclosure has been accepted for [providerName]. */
    fun isAcknowledged(providerName: String): Boolean

    /**
     * Whether the credential for [providerName] may round-trip for the first
     * time. Returns a binding requirement when the disclosure has never been
     * accepted.
     */
    fun requirementBeforeFirstUse(providerName: String): FirstUseRequirement

    /** Records that the user saw and accepted the disclosure for [providerName]. */
    fun acknowledge(providerName: String): CredentialStoreResult<Unit>
}

/** Result of [ProviderCostDisclosureGate.requirementBeforeFirstUse]. */
sealed class FirstUseRequirement {
    /** The key has not been used yet; the disclosure screen must be shown first. */
    data object ShowDisclosure : FirstUseRequirement()

    /** The disclosure was accepted; the key may round-trip. */
    data object Cleared : FirstUseRequirement()
}

/**
 * In-memory acknowledgement ledger keyed by provider name. Persistence
 * mirrors the credential store's own envelope format and is deliberately
 * joined to it in production wiring; tests inject the store they exercise.
 */
class InMemoryProviderCostDisclosureGate : ProviderCostDisclosureGate {
    private val lock = ReentrantLock()
    private val acknowledged = linkedMapOf<String, Long>()
    private val mutableEvents =
        MutableStateFlow<Map<String, Long>>(emptyMap())

    override val disclosureEvents: StateFlow<Map<String, Long>> = mutableEvents

    override fun isAcknowledged(providerName: String): Boolean = containsAcknowledgement(providerName)

    private fun containsAcknowledgement(providerName: String): Boolean =
        lock.withLock {
            acknowledged.containsKey(providerName)
        }

    override fun requirementBeforeFirstUse(providerName: String): FirstUseRequirement =
        if (isAcknowledged(providerName)) {
            FirstUseRequirement.Cleared
        } else {
            FirstUseRequirement.ShowDisclosure
        }

    override fun acknowledge(providerName: String): CredentialStoreResult<Unit> {
        require(providerName.isNotBlank()) { "Provider name must not be blank." }
        lock.withLock {
            if (acknowledged.containsKey(providerName)) {
                return CredentialStoreResult.Success(Unit)
            }
            acknowledged[providerName] = System.currentTimeMillis()
            mutableEvents.value = acknowledged.toMap()
        }
        return CredentialStoreResult.Success(Unit)
    }
}
