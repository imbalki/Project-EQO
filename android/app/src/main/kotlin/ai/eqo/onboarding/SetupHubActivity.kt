/*
 * EQO (TASK-015, issue #20): the setup hub — the capability readiness dashboard
 * (UF-08, screen S-19) and the entry point of the guided onboarding.
 *
 * Every row shows ITS OWN capability's readiness state; nothing is inferred from
 * another row (PRD REQ-ADB-10 / REQ-CDP-04). Rows with work to do carry the repair
 * guidance inline (REQ-ADB-12: nothing ends in "unknown" without a reason).
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.legal.LegalNoticesActivity
import ai.eqo.study.CapabilityId
import ai.eqo.study.CapabilityState
import ai.eqo.study.CapabilityStatus
import ai.eqo.task.TaskActivity
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes

class SetupHubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_hub)
        // The consent answer persists across runs (UF-06); restore it before probing rows.
        StudySetup.consent = ChromeConsentActivity.restoreConsent(applicationContext)
        findViewById<TextView>(R.id.row_model_key).setOnClickListener {
            startActivity(Intent(this, ModelKeySetupActivity::class.java))
        }
        findViewById<TextView>(R.id.row_accessibility).setOnClickListener {
            startActivity(Intent(this, AccessibilitySetupActivity::class.java))
        }
        findViewById<TextView>(R.id.row_wireless_adb).setOnClickListener {
            startActivity(Intent(this, WirelessAdbSetupActivity::class.java))
        }
        findViewById<TextView>(R.id.row_helper).setOnClickListener {
            startActivity(Intent(this, WirelessAdbSetupActivity::class.java))
        }
        findViewById<TextView>(R.id.row_chrome_consent).setOnClickListener {
            startActivity(Intent(this, ChromeConsentActivity::class.java))
        }
        findViewById<Button>(R.id.setup_recheck_button).setOnClickListener {
            render()
            Toast.makeText(this, R.string.setup_hub_recheck_complete, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.setup_task_button).setOnClickListener {
            startActivity(Intent(this, TaskActivity::class.java))
        }
        findViewById<Button>(R.id.setup_legal_button).setOnClickListener {
            startActivity(Intent(this, LegalNoticesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { StudySetup.helper.attach() }
        render()
    }

    override fun onPause() {
        runCatching { StudySetup.helper.detach() }
        super.onPause()
    }

    /** Re-reads every capability through its own probe and renders one row per capability. */
    private fun render() {
        val snapshot = StudySetup.snapshot(applicationContext, helperState = StudySetup.helper.state)
        renderRow(R.id.row_model_key, R.string.setup_hub_row_model_key, snapshot.rowFor(CapabilityId.MODEL_KEY))
        renderRow(
            R.id.row_accessibility,
            R.string.setup_hub_row_accessibility,
            snapshot.rowFor(CapabilityId.ACCESSIBILITY),
        )
        renderRow(
            R.id.row_wireless_adb,
            R.string.setup_hub_row_wireless_adb,
            snapshot.rowFor(CapabilityId.WIRELESS_ADB),
        )
        renderRow(R.id.row_helper, R.string.setup_hub_row_helper, snapshot.rowFor(CapabilityId.HELPER))
        renderRow(
            R.id.row_chrome_consent,
            R.string.setup_hub_row_chrome_consent,
            snapshot.rowFor(CapabilityId.CHROME_CONSENT),
        )
    }

    private fun renderRow(
        viewId: Int,
        labelId: Int,
        status: CapabilityStatus?,
    ) {
        val state = status?.state ?: CapabilityState.NOT_STARTED
        val text =
            buildString {
                append(getString(labelId))
                append(" — ")
                append(getString(stateLabel(state)))
                status?.takeIf { it.detail.isNotBlank() }?.let { append("\n").append(it.detail) }
                status?.takeIf { it.guidance.isNotBlank() && state != CapabilityState.READY }?.let {
                    append("\n").append(it.guidance)
                }
            }
        findViewById<TextView>(viewId).text = text
    }

    /**
     * Helper readiness is reported by the helper binder itself (TASK-007). The study hub
     * reads the binder state; it never derives it from another capability.
     */
    companion object {
        /** USER-FLOWS.md §16.2 row-state wording. */
        @StringRes
        fun stateLabel(state: CapabilityState): Int =
            when (state) {
                CapabilityState.NOT_STARTED -> R.string.state_not_set_up
                CapabilityState.IN_PROGRESS -> R.string.state_checking
                CapabilityState.READY -> R.string.state_ready
                CapabilityState.FAILED -> R.string.state_needs_attention
                CapabilityState.GATED -> R.string.state_gated
                CapabilityState.DEPENDENT_RECHECK -> R.string.state_dependent
            }
    }
}
