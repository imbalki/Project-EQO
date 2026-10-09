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
import ai.eqo.study.ReadinessSnapshot
import ai.eqo.task.TaskActivity
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes

class SetupHubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup_hub)
        findViewById<Button>(R.id.explain_settings).setOnClickListener {
            startActivity(Intent(this, ai.eqo.explain.ExplainSettingsActivity::class.java))
        }
        findViewById<android.widget.CheckBox>(R.id.require_plan_approval).apply {
            isChecked =
                ai.eqo.task.PlanApprovalSettings
                    .required(this@SetupHubActivity)
            setOnCheckedChangeListener { _, checked ->
                ai.eqo.task.PlanApprovalSettings
                    .setRequired(this@SetupHubActivity, checked)
            }
        }
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
        findViewById<TextView>(R.id.row_all_files).setOnClickListener { openAllFilesAccessSettings() }
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
        val next = nextCapability(snapshot)
        findViewById<TextView>(R.id.setup_next).text =
            if (next ==
                null
            ) {
                getString(R.string.setup_all_ready)
            } else {
                getString(R.string.setup_next, getString(rowHint(next, snapshot.rowFor(next)?.state)))
            }
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
        renderAllFilesRow()
        rowIds.forEach { (id, viewId) ->
            val view = findViewById<TextView>(viewId)
            view.isSelected = id == next
            view.setTypeface(null, if (id == next) Typeface.BOLD else Typeface.NORMAL)
            if (id == next) view.text = getString(R.string.setup_row_next, view.text)
        }
    }

    /** Separate from the readiness rows: optional, owner-granted in Settings, never part of "next step". */
    private fun renderAllFilesRow() {
        val granted = AllFilesAccess.isGranted()
        findViewById<TextView>(R.id.row_all_files).text =
            getString(
                R.string.setup_row_format,
                getString(R.string.setup_hub_row_all_files),
                getString(stateLabel(AllFilesAccess.state(granted))),
                getString(if (granted) R.string.all_files_granted else R.string.all_files_explainer),
            )
    }

    private fun openAllFilesAccessSettings() {
        try {
            startActivity(AllFilesAccess.settingsIntent(this))
        } catch (_: android.content.ActivityNotFoundException) {
            try {
                startActivity(AllFilesAccess.fallbackIntent())
            } catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(this, R.string.all_files_no_settings, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderRow(
        viewId: Int,
        labelId: Int,
        status: CapabilityStatus?,
    ) {
        val state = status?.state ?: CapabilityState.NOT_STARTED
        val hint = rowHint(status?.id ?: CapabilityId.MODEL_KEY, state)
        val guidance =
            if (status?.id == CapabilityId.CHROME_CONSENT && StudySetup.consent != ConsentDecision.UNANSWERED) {
                getString(
                    if (StudySetup.consent == ConsentDecision.DECLINED) {
                        R.string.chrome_consent_declined
                    } else {
                        R.string.chrome_consent_accepted
                    },
                )
            } else {
                getString(hint)
            }
        val failedCheck =
            status?.detail?.substringBefore(":")?.takeIf { name ->
                status.id == CapabilityId.WIRELESS_ADB &&
                    state == CapabilityState.FAILED &&
                    ai.eqo.adb.pairing.ActivationCheck.entries
                        .any { it.name == name }
            }
        val detail = failedCheck?.let { getString(R.string.setup_wireless_failed_check, it, guidance) } ?: guidance
        findViewById<TextView>(viewId).text =
            getString(R.string.setup_row_format, getString(labelId), getString(stateLabel(state)), detail)
    }

    /**
     * Helper readiness is reported by the helper binder itself (TASK-007). The study hub
     * reads the binder state; it never derives it from another capability.
     */
    companion object {
        val rowIds =
            linkedMapOf(
                CapabilityId.MODEL_KEY to R.id.row_model_key,
                CapabilityId.ACCESSIBILITY to R.id.row_accessibility,
                CapabilityId.WIRELESS_ADB to R.id.row_wireless_adb,
                CapabilityId.HELPER to R.id.row_helper,
                CapabilityId.CHROME_CONSENT to R.id.row_chrome_consent,
            )

        fun nextCapability(snapshot: ReadinessSnapshot): CapabilityId? {
            val pending = rowIds.keys.filter { snapshot.rowFor(it)?.state != CapabilityState.READY }
            return pending.firstOrNull {
                snapshot.rowFor(it)?.state !in listOf(CapabilityState.GATED, CapabilityState.IN_PROGRESS)
            } ?: pending.firstOrNull()
        }

        @StringRes
        fun rowHint(
            id: CapabilityId,
            state: CapabilityState?,
        ): Int =
            when (state) {
                CapabilityState.READY -> R.string.hint_ready
                CapabilityState.IN_PROGRESS -> R.string.hint_wait
                CapabilityState.GATED -> R.string.hint_blocked
                else -> hintLabel(id)
            }

        @StringRes
        fun hintLabel(id: CapabilityId): Int =
            when (id) {
                CapabilityId.MODEL_KEY -> R.string.hint_model
                CapabilityId.ACCESSIBILITY -> R.string.hint_accessibility
                CapabilityId.WIRELESS_ADB -> R.string.hint_wireless
                CapabilityId.HELPER -> R.string.hint_helper
                CapabilityId.CHROME_CONSENT -> R.string.hint_browser
            }

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
