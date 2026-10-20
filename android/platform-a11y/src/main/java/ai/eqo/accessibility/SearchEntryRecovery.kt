// Origin: EQO guarded app-search entry for runs that resume on an old app screen.
package ai.eqo.accessibility

import ai.eqo.core.agent.AppSearchEntry
import kotlinx.coroutines.delay

/**
 * THE one guarded search entry used by the shopping skill and the 'search X in <app>' skill.
 *
 * Both skills plan OPEN_APP, then click the app's search entry and type the query into a search
 * field. When the app resumes on one of its old screens the entry control is missing. This helper
 * recovers inside the app, in order:
 *  1. type once into a search field that is already open (an editable node whose label, hint,
 *     content description or resource id names search; the old query is selected first),
 *  2. otherwise press Back up to [MAX_BACKS] times, re-checking after each back, never leaving
 *     the app and never backing over a payment/address/checkout screen,
 *  3. otherwise stop with the plain reason "I could not find Search in <app>".
 *
 * Every node action runs through [EqoAutomation]'s takeover/service gate; Back runs through the
 * same gated in-app path as the PRESS_BACK action. No screen or user data is retained.
 */
class SearchEntryRecovery(
    private val automation: EqoAutomation,
    private val pressBackInApp: () -> A11yResult = GenericAppAutomator::pressBackInApp,
) {
    data class Outcome(
        val result: A11yResult,
        val queryEntered: Boolean,
        val missingReason: String? = null,
    )

    /**
     * Recovers after the plan's search-entry click reported the control missing. [openEntry] is
     * the plan's entry click again (production keeps its cold-start retries); it runs after every
     * Back, so "the entry is back" ends the recovery with the plan's own type step still to come.
     */
    suspend fun recover(
        entry: String,
        query: String,
        app: String,
        openEntry: suspend (String) -> A11yResult = { automation.tap(it) },
    ): Outcome {
        val typed = automation.typeIntoSearchField(query)
        return when {
            typed.isSuccess -> Outcome(typed, queryEntered = true)
            missing(typed) -> backToEntry(entry, query, app, openEntry)
            else -> Outcome(typed, queryEntered = false)
        }
    }

    @Suppress("ReturnCount") // One outcome per bounded back: gates, failures and finds exit directly.
    private suspend fun backToEntry(
        entry: String,
        query: String,
        app: String,
        openEntry: suspend (String) -> A11yResult,
    ): Outcome {
        val appPackage = automation.windowPackage()
        var backs = 0
        while (backs < MAX_BACKS) {
            if (!canBack(appPackage)) break
            val back = pressBackInApp()
            if (!back.isSuccess) return Outcome(back, queryEntered = false)
            backs++
            val rechecked = recheck(entry, query, openEntry)
            if (rechecked != null) return rechecked
            delay(BACK_SETTLE_MS)
        }
        return Outcome(
            A11yResult.failure(A11yError.NodeNotFound(entry)),
            queryEntered = false,
            missingReason = AppSearchEntry.missingReason(app),
        )
    }

    /** A checkout screen is never backed out of, and neither is a window that left the app. */
    private fun canBack(appPackage: String?): Boolean {
        if (automation.paymentScreenVisible()) return false
        val current = automation.windowPackage()
        return appPackage == null || current == null || current == appPackage
    }

    /** Re-checks for the entry or an open search field after one Back; null keeps backing. */
    private suspend fun recheck(
        entry: String,
        query: String,
        openEntry: suspend (String) -> A11yResult,
    ): Outcome? {
        val entryBack = openEntry(entry)
        if (entryBack.isSuccess || !missing(entryBack)) return Outcome(entryBack, queryEntered = false)
        val typed = automation.typeIntoSearchField(query)
        return when {
            typed.isSuccess -> Outcome(typed, queryEntered = true)
            missing(typed) -> null
            else -> Outcome(typed, queryEntered = false)
        }
    }

    /** Only "the control is not there (yet)" recovers; gates and rejected actions stop the run. */
    private fun missing(result: A11yResult): Boolean =
        result is A11yResult.Failure &&
            result.error is A11yError.NodeNotFound

    companion object {
        /** Bounded backing: a deeper screen needs at most this many Backs to surface the entry. */
        const val MAX_BACKS = 3

        private const val BACK_SETTLE_MS = 500L
    }
}
