/*
 * EQO (TASK-015, issue #20): per-capability readiness for the guided onboarding.
 *
 * PRD REQ-ADB-00 wording for the whole setup surface: "Each check is separate; a pass
 * on one never implies a pass on another" (UF-08 capability dashboard, REQ-ADB-10,
 * REQ-CDP-04). A capability's state comes from ITS OWN probe and from nothing else —
 * this file makes that rule structural: a status names the probe that produced it and
 * [ReadinessSnapshot.record] refuses a status whose probe does not belong to it.
 */
package ai.eqo.study

/**
 * The capabilities the first-run flow sets up (TASK-015 scope: key, accessibility,
 * wireless ADB, helper, Chrome consent). Each row traces to its PRD requirement and
 * user flow.
 */
enum class CapabilityId(
    /** PRD requirement IDs this capability's readiness state satisfies. */
    val requirementIds: String,
    /** USER-FLOWS.md flow ID. */
    val userFlow: String,
    /** The probe that may report state for this capability (never another one). */
    val probeName: String,
) {
    MODEL_KEY(requirementIds = "REQ-BYOK-01/02/04", userFlow = "UF-02", probeName = "key-store-and-live-validation"),
    ACCESSIBILITY(
        requirementIds = "REQ-A11Y-01/02/04",
        userFlow = "UF-03",
        probeName = "enabled-accessibility-services",
    ),
    WIRELESS_ADB(requirementIds = "REQ-ADB-01..06", userFlow = "UF-04", probeName = "pairing-activation-checks"),
    HELPER(requirementIds = "REQ-ADB-07/08/09", userFlow = "UF-05", probeName = "helper-binder-authorization"),
    CHROME_CONSENT(
        requirementIds = "REQ-CDP-01/02/03/04, REQ-PRIV-03/04",
        userFlow = "UF-06",
        probeName = "consent-record-and-devtools-probes",
    ),
}

/**
 * Readiness state of exactly one capability.
 *
 * There is deliberately no combined "setup complete" boolean derived from the rows:
 * [StudyFlowGate] keeps two transports unreachable, so a study build can honestly show
 * per-capability truth (including [GATED]) instead of a green light it cannot back.
 */
enum class CapabilityState {
    /** The user has not started this step. */
    NOT_STARTED,

    /** The step is in progress (guidance shown, waiting on the user or a probe). */
    IN_PROGRESS,

    /** This capability's own probe passed. Proves nothing about any other capability. */
    READY,

    /** This capability's own probe failed; [CapabilityStatus.guidance] names the repair. */
    FAILED,

    /**
     * The capability is deliberately unreachable in the shipped study flow
     * ([StudyFlowGate]); the row still shows its own state and says why.
     */
    GATED,

    /**
     * A capability this one depends on broke, so this row must be re-probed before it
     * can claim anything (USER-FLOWS.md §16.2: "Dependent — re-check needed"). This is
     * explicit invalidation for re-checking, never a derived READY or a derived failure.
     */
    DEPENDENT_RECHECK,
}

/**
 * One capability's readiness row.
 *
 * @param probeName must equal [CapabilityId.probeName] for [id] — the anti-inference
 *   rule: a row is only ever written by its own probe.
 * @param guidance recovery guidance for [CapabilityState.FAILED]/[GATED]; non-empty for
 *   those states (PRD REQ-ADB-12: "no check may end in unknown without an explanatory
 *   reason string").
 */
data class CapabilityStatus(
    val id: CapabilityId,
    val state: CapabilityState,
    val probeName: String,
    val detail: String = "",
    val guidance: String = "",
    val checkedAtMs: Long = 0L,
) {
    init {
        require(probeName == id.probeName) {
            "capability ${id.name} may only be written by its own probe '${id.probeName}', got '$probeName'"
        }
        when (state) {
            CapabilityState.FAILED,
            CapabilityState.GATED,
            CapabilityState.DEPENDENT_RECHECK,
            -> require(guidance.isNotBlank()) { "${id.name} in $state must carry recovery guidance" }
            else -> Unit
        }
    }
}

/**
 * The capability dashboard (UF-08). Rows are keyed by [CapabilityId]; a row's state is
 * only ever the state its own probe reported.
 */
class ReadinessSnapshot {
    private val rows = linkedMapOf<CapabilityId, CapabilityStatus>()

    /** Records one probe result. Later reports for the same capability replace earlier ones. */
    fun record(status: CapabilityStatus): ReadinessSnapshot {
        rows[status.id] = status
        return this
    }

    fun rowFor(id: CapabilityId): CapabilityStatus? = rows[id]

    /** Every capability that has been probed, in declaration order. */
    fun rows(): List<CapabilityStatus> = CapabilityId.entries.mapNotNull { rows[it] }

    /**
     * True when every capability has been probed and reports READY. Nothing is assumed
     * for a capability that was never probed: an unprobed capability makes this false.
     */
    fun allReady(): Boolean =
        rows.size == CapabilityId.entries.size &&
            rows.values.all { it.state == CapabilityState.READY }

    /** Capabilities that need the user's attention, worst state first. */
    fun needingAttention(): List<CapabilityStatus> =
        rows.values
            .filter { it.state != CapabilityState.READY }
            .sortedBy { it.state.order }

    /**
     * USER-FLOWS.md §16.2: when the helper or the wireless-ADB plane breaks, the rows
     * that DEPEND on it flip to "Dependent — re-check needed" (explicit invalidation for
     * re-probing) while unrelated rows — model key and accessibility — are untouched.
     * A dependent row never silently keeps a stale READY and never inherits a failure.
     */
    fun noteDependencyBroken(broken: CapabilityId): ReadinessSnapshot {
        DEPENDENTS[broken].orEmpty().forEach { dependent ->
            rows[dependent] =
                CapabilityStatus(
                    id = dependent,
                    state = CapabilityState.DEPENDENT_RECHECK,
                    probeName = dependent.probeName,
                    detail = "depends on ${broken.name}, which is not ready",
                    guidance =
                        "Dependent — re-check needed: $dependent re-checks its own probes once " +
                            "${broken.name} is ready again.",
                )
        }
        return this
    }

    // Explicit severity ranks for the attention list; they are ordering constants, not
    // measurements (0 = most urgent), so a named constant would add noise without meaning.
    @Suppress("MagicNumber")
    private val CapabilityState.order: Int
        get() =
            when (this) {
                CapabilityState.FAILED -> 0
                CapabilityState.DEPENDENT_RECHECK -> 1
                CapabilityState.GATED -> 2
                CapabilityState.IN_PROGRESS -> 3
                CapabilityState.NOT_STARTED -> 4
                CapabilityState.READY -> 5
            }

    companion object {
        /** Readiness dependencies (USER-FLOWS.md §16.2). Model and accessibility have none. */
        private val DEPENDENTS: Map<CapabilityId, List<CapabilityId>> =
            mapOf(
                CapabilityId.HELPER to listOf(CapabilityId.CHROME_CONSENT),
                CapabilityId.WIRELESS_ADB to listOf(CapabilityId.HELPER, CapabilityId.CHROME_CONSENT),
            )
    }
}
