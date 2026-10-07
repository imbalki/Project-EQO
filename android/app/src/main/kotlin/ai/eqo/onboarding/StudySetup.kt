/*
 * EQO (TASK-015, issue #20): the process-wide study setup state and the per-capability
 * probes behind the setup hub (UF-08).
 *
 * Every probe answers for ITS OWN capability and nothing else (PRD REQ-ADB-12 /
 * REQ-CDP-04: "no generic ready"), so a broken capability never paints another row.
 */
package ai.eqo.onboarding

import ai.eqo.adb.pairing.ActivationReport
import ai.eqo.adb.pairing.ActivationStatus
import ai.eqo.adb.pairing.AdbCryptoKeyStore
import ai.eqo.adb.pairing.WirelessAdbActivation
import ai.eqo.adb.pairing.WirelessLink
import ai.eqo.adb.pairing.WirelessLinkState
import ai.eqo.helper.client.HelperActivationState
import ai.eqo.study.CapabilityId
import ai.eqo.study.CapabilityState
import ai.eqo.study.CapabilityStatus
import ai.eqo.study.ReadinessSnapshot
import ai.eqo.study.StudyFlowGate
import android.content.Context
import android.provider.Settings
import java.io.File

/** The user's browser-consent answer (UF-06). */
enum class ConsentDecision {
    UNANSWERED,
    ACCEPTED,
    DECLINED,
}

/** Where the model-key step stands (UF-02). */
enum class ModelKeyState {
    NOT_SET,
    VALIDATING,
    CONNECTED,
    FAILED,
}

/**
 * One place for the study build's setup state: the wireless-ADB activation gate, the
 * browser-consent answer and the model-key state. Nothing here is derived from another
 * capability's state.
 */
object StudySetup {
    /** Install-scoped wireless-ADB activation gate (fresh install = ACTIVATION_REQUIRED). */
    val wirelessAdb: WirelessAdbActivation = WirelessAdbActivation()

    /** Latest wireless-ADB activation report of this process (null until the owner ran it). */
    @Volatile
    var wirelessReport: ActivationReport? = null

    /**
     * App-private, non-backed-up key store holding the client key and the server enrollment
     * (TASK-080). The same directory is used by every caller so pairing and connect agree.
     */
    fun keyStore(context: Context): AdbCryptoKeyStore {
        val dir = File(context.applicationContext.noBackupFilesDir, "adb-keys")
        return AdbCryptoKeyStore(dir)
    }

    /** Helper binder state (TASK-007). Attached by the screens that display it. */
    val helper: HelperActivationState = HelperActivationState()

    @Volatile
    var consent: ConsentDecision = ConsentDecision.UNANSWERED

    @Volatile
    var modelKey: ModelKeyState = ModelKeyState.NOT_SET

    /** Component name of the single EQO accessibility service (TASK-009). */
    const val ACCESSIBILITY_COMPONENT = "ai.eqo.app/ai.eqo.accessibility.EQOAccessibilityService"

    /** Reads Android's own enabled-services setting. Read-only: EQO never writes it. */
    fun accessibilityServiceEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure
                .getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
        return enabled.split(':').any { it.equals(ACCESSIBILITY_COMPONENT, ignoreCase = true) }
    }

    fun probeModelKey(): CapabilityStatus =
        when (modelKey) {
            ModelKeyState.NOT_SET ->
                status(CapabilityId.MODEL_KEY, CapabilityState.NOT_STARTED, detail = "no key stored yet")
            ModelKeyState.VALIDATING ->
                status(CapabilityId.MODEL_KEY, CapabilityState.IN_PROGRESS, detail = "validating with the provider")
            ModelKeyState.CONNECTED ->
                status(
                    CapabilityId.MODEL_KEY,
                    CapabilityState.READY,
                    detail = "key validated and stored in the Keystore",
                )
            ModelKeyState.FAILED ->
                status(
                    CapabilityId.MODEL_KEY,
                    CapabilityState.FAILED,
                    detail = "the provider rejected the last validation",
                    guidance = ai.eqo.study.FailureClass.MODEL_UNAUTHORIZED.repair,
                )
        }

    fun probeAccessibility(context: Context): CapabilityStatus =
        if (accessibilityServiceEnabled(context)) {
            status(CapabilityId.ACCESSIBILITY, CapabilityState.READY, detail = "service is enabled in Android settings")
        } else {
            status(
                CapabilityId.ACCESSIBILITY,
                CapabilityState.FAILED,
                detail = "service is not enabled",
                guidance = ai.eqo.study.FailureClass.A11Y_LOST.repair,
            )
        }

    fun probeWirelessAdb(context: Context? = null): CapabilityStatus {
        val enrolled = context?.let { keyStore(it).enrollment.current() != null } ?: false
        val report = wirelessReport
        return when {
            !StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE) ->
                status(
                    CapabilityId.WIRELESS_ADB,
                    CapabilityState.GATED,
                    detail = "the wireless connect step is switched off in this build",
                    guidance = "Pending ${StudyFlowGate.WIRELESS_CONNECT_SAFEGUARD}.",
                )
            report == null ->
                status(
                    CapabilityId.WIRELESS_ADB,
                    CapabilityState.NOT_STARTED,
                    detail = if (enrolled) "paired earlier; not connected yet" else "not paired",
                )
            else -> wirelessLinkStatus(WirelessLink.stateOf(enrolled, report), report)
        }
    }

    private fun wirelessLinkStatus(
        link: WirelessLinkState,
        report: ActivationReport,
    ): CapabilityStatus {
        if (link == WirelessLinkState.PAIRED_AND_CONNECTED && wirelessAdb.status == ActivationStatus.ACTIVE) {
            return status(CapabilityId.WIRELESS_ADB, CapabilityState.READY, detail = "all activation checks passed")
        }
        val detail =
            when (link) {
                WirelessLinkState.NOT_PAIRED -> "not paired"
                WirelessLinkState.NEEDS_REPAIR -> "needs re-pair"
                WirelessLinkState.CONNECTION_LOST -> "connection lost"
                WirelessLinkState.PAIRED_AND_CONNECTED -> "paired and connected, but a helper check did not pass"
            }
        return status(
            CapabilityId.WIRELESS_ADB,
            CapabilityState.FAILED,
            detail = report.firstFailure?.let { "${it.check.name}: $detail" } ?: detail,
            guidance = report.firstFailure?.guidance ?: "Run the wireless steps again from Wireless debugging.",
        )
    }

    /** Helper readiness comes from the helper binder itself (TASK-007), never from the ADB row. */
    fun probeHelper(helperState: HelperActivationState.State): CapabilityStatus =
        when (helperState) {
            HelperActivationState.State.ACTIVE ->
                status(CapabilityId.HELPER, CapabilityState.READY, detail = "helper binder is alive")
            HelperActivationState.State.NEEDS_REACTIVATION ->
                status(
                    CapabilityId.HELPER,
                    CapabilityState.FAILED,
                    detail = "helper binder died",
                    guidance = ai.eqo.study.FailureClass.BINDER_DEAD.repair,
                )
            HelperActivationState.State.INACTIVE ->
                status(CapabilityId.HELPER, CapabilityState.NOT_STARTED, detail = "helper not started yet")
        }

    fun probeChromeConsent(): CapabilityStatus {
        if (consent == ConsentDecision.UNANSWERED) {
            return status(CapabilityId.CHROME_CONSENT, CapabilityState.NOT_STARTED, detail = "no consent decision yet")
        }
        val detail =
            if (consent == ConsentDecision.ACCEPTED) {
                "consent recorded: allowed"
            } else {
                "consent recorded: declined — browser control stays off"
            }
        return if (!StudyFlowGate.permits(StudyFlowGate.StudyTransport.CHROME_CDP)) {
            status(
                CapabilityId.CHROME_CONSENT,
                CapabilityState.GATED,
                detail = detail,
                guidance =
                    "Browser functions are switched off in this study build pending " +
                        "${StudyFlowGate.CHROME_CDP_PENDING_WORK}. No page content is sent.",
            )
        } else {
            status(CapabilityId.CHROME_CONSENT, CapabilityState.READY, detail = detail)
        }
    }

    /** Builds the dashboard from the five independent probes. */
    fun snapshot(
        context: Context,
        helperState: HelperActivationState.State,
    ): ReadinessSnapshot =
        ReadinessSnapshot()
            .record(probeModelKey())
            .record(probeAccessibility(context))
            .record(probeWirelessAdb(context))
            .record(probeHelper(helperState))
            .record(probeChromeConsent())

    private fun status(
        id: CapabilityId,
        state: CapabilityState,
        detail: String = "",
        guidance: String = "",
    ): CapabilityStatus =
        CapabilityStatus(
            id = id,
            state = state,
            probeName = id.probeName,
            detail = detail,
            guidance = guidance,
            checkedAtMs = System.currentTimeMillis(),
        )
}
