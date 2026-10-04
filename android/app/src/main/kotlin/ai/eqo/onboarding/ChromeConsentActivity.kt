/*
 * EQO (TASK-015, issue #20): browser consent (UF-06, screens S-12/S-13/S-14).
 *
 * REQ-CDP-01 / REQ-PRIV-03: explicit informed consent before any screen/context
 * transmission — what is sent, to where, and that redaction is best-effort
 * (REQ-PRIV-04). Declining disables browser control only (REQ-PRIV-03).
 * REQ-CDP-04: no generic "ready" — the row states the consent answer and, separately,
 * whether browser functions are available.
 *
 * Study-flow gate (TASK-010 SF-1): the CDP relay is switched off in this build, so the
 * consent answer is recorded but no browser machinery starts and no page content moves.
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.study.StudyFlowGate
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.content.edit

class ChromeConsentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chrome_consent)
        findViewById<Button>(R.id.chrome_consent_accept_button).setOnClickListener {
            recordConsent(ConsentDecision.ACCEPTED)
        }
        findViewById<Button>(R.id.chrome_consent_decline_button).setOnClickListener {
            recordConsent(ConsentDecision.DECLINED)
        }
        render()
    }

    private fun recordConsent(decision: ConsentDecision) {
        StudySetup.consent = decision
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_CONSENT, decision.name)
        }
        render()
    }

    private fun render() {
        val status = findViewById<TextView>(R.id.chrome_consent_status)
        status.text =
            when (StudySetup.consent) {
                ConsentDecision.UNANSWERED -> ""
                ConsentDecision.ACCEPTED -> getString(R.string.chrome_consent_accepted)
                ConsentDecision.DECLINED -> getString(R.string.chrome_consent_declined)
            }
        val gateNotice = findViewById<TextView>(R.id.chrome_consent_gate_notice)
        gateNotice.text =
            if (StudyFlowGate.permits(StudyFlowGate.StudyTransport.CHROME_CDP)) {
                ""
            } else {
                getString(R.string.chrome_consent_gated_notice)
            }
    }

    companion object {
        const val PREFS = "eqo_study_setup"
        const val KEY_CONSENT = "chrome_consent"

        /** Restores the persisted consent answer (called from the launcher path). */
        fun restoreConsent(context: Context): ConsentDecision =
            when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONSENT, null)) {
                ConsentDecision.ACCEPTED.name -> ConsentDecision.ACCEPTED
                ConsentDecision.DECLINED.name -> ConsentDecision.DECLINED
                else -> ConsentDecision.UNANSWERED
            }
    }
}
