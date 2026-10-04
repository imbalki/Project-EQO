/*
 * EQO (TASK-015, issue #20): guided wireless ADB (UF-04/UF-05, screens S-08..S-11).
 *
 * Two ports, two names (REQ-ADB-05): the PAIRING port is the one inside the "Pair device
 * with pairing code" dialog; the CONNECTION port is the "IP address & port" value on the
 * wireless debugging screen. Each of the five activation checks reports its own state
 * (REQ-ADB-07/08/09) and every failure carries its own recovery guidance (REQ-ADB-12).
 *
 * Study-flow gate (TASK-008 SF-1): the shipped study build does NOT dispatch the
 * trust-all connect plane. `StudyFlowGate.WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW` is
 * false, so the checks are shown as "did not run" with the pending-work reason instead
 * of running `WirelessAdbActivationRunner` (the AdbTlsClient connect path). Flipping the
 * gate is a code change that must land together with server-key pinning.
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.adb.pairing.ActivationCheck
import ai.eqo.study.StudyFlowGate
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class WirelessAdbSetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wireless_adb)
        findViewById<Button>(R.id.wireless_adb_run_button).setOnClickListener { runChecks() }
        findViewById<Button>(R.id.setup_return_button).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        runChecks()
        findViewById<TextView>(R.id.wireless_adb_guidance).text = ""
        val gateNotice = findViewById<TextView>(R.id.wireless_adb_gate_notice)
        gateNotice.text =
            if (StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)) {
                ""
            } else {
                getString(R.string.wireless_adb_gated_notice)
            }
    }

    /**
     * Runs the five activation checks in order. In the shipped study flow the connect
     * plane is gated off (SF-1), so every check reports "did not run" with the reason —
     * never a fake pass and never silence.
     */
    private fun runChecks() {
        val permitted = StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)
        ActivationCheck.entries.forEach { check ->
            val label =
                when (check) {
                    ActivationCheck.PAIR -> R.string.wireless_adb_check_pair
                    ActivationCheck.CONNECT -> R.string.wireless_adb_check_connect
                    ActivationCheck.HELPER_START -> R.string.wireless_adb_check_helper_start
                    ActivationCheck.AUTHORIZE -> R.string.wireless_adb_check_authorize
                    ActivationCheck.BINDER_HEALTH -> R.string.wireless_adb_check_binder
                }
            val state =
                if (permitted) {
                    // Future wiring: ActivationSequence(WirelessAdbActivationRunner(...)) once
                    // StudyFlowGate.WIRELESS_CONNECT_PLANE_IN_STUDY_FLOW is true (server-key
                    // pinning landed). Not reachable in this build by design.
                    getString(R.string.wireless_adb_check_not_run)
                } else {
                    "${getString(R.string.state_gated)} — ${getString(R.string.wireless_adb_check_not_run)}"
                }
            renderCheck(check, "${getString(label)}: $state")
        }
        val guidance =
            if (permitted) {
                ""
            } else {
                getString(R.string.wireless_adb_gated_notice)
            }
        findViewById<TextView>(R.id.wireless_adb_guidance).text = guidance
        listOf(R.id.pairing_code_input, R.id.pairing_port_input, R.id.connection_port_input).forEach {
            findViewById<android.widget.EditText>(it).isEnabled = permitted
        }
    }

    private fun renderCheck(
        check: ActivationCheck,
        text: String,
    ) {
        val viewId =
            when (check) {
                ActivationCheck.PAIR -> R.id.check_pair_status
                ActivationCheck.CONNECT -> R.id.check_connect_status
                ActivationCheck.HELPER_START -> R.id.check_helper_start_status
                ActivationCheck.AUTHORIZE -> R.id.check_authorize_status
                ActivationCheck.BINDER_HEALTH -> R.id.check_binder_status
            }
        findViewById<TextView>(viewId).text = text
    }
}
