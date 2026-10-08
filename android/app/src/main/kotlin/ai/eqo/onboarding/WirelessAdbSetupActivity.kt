/*
 * EQO (TASK-015, issue #20; enabled by TASK-080): guided wireless ADB (UF-04/UF-05, S-08..S-11).
 *
 * Two ports, two names (REQ-ADB-05): the PAIRING port is the one inside the "Pair device
 * with pairing code" dialog; the CONNECTION port is the "IP address & port" value on the
 * wireless debugging screen. Each of the five activation checks reports its own state
 * (REQ-ADB-07/08/09) and every failure carries its own recovery guidance (REQ-ADB-12).
 *
 * TASK-080 closed TASK-008 SF-1: pairing enrolls the server key and the connect plane pins
 * it, failing closed (see android/Phase-One/evidence/task-080-wireless-pairing.md). The
 * owner sees one of four plain-language states: not paired, paired and connected,
 * connection lost, needs re-pair. ADB is used only to start the privileged helper.
 *
 * Opening this setup screen discovers local ports. Pairing and helper consent require user actions.
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.adb.pairing.ActivationCheck
import ai.eqo.adb.pairing.ActivationReport
import ai.eqo.adb.pairing.ActivationSequence
import ai.eqo.adb.pairing.AdbPairingCode
import ai.eqo.adb.pairing.CheckOutcome
import ai.eqo.adb.pairing.HelperStartCommand
import ai.eqo.adb.pairing.PairingInput
import ai.eqo.adb.pairing.WirelessAdbActivationRunner
import ai.eqo.adb.pairing.WirelessAdbEndpoints
import ai.eqo.adb.pairing.WirelessLink
import ai.eqo.adb.pairing.WirelessLinkState
import ai.eqo.study.StudyFlowGate
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Suppress("TooManyFunctions") // Activity lifecycle plus setup UI handlers; transport remains separate.
class WirelessAdbSetupActivity : Activity() {
    private lateinit var worker: ExecutorService
    private var permissionPrompt: HelperPermissionPrompt? = null

    @Volatile
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.wireless_adb)
        worker = Executors.newSingleThreadExecutor()
        findViewById<Button>(R.id.wireless_adb_run_button).setOnClickListener { pairAndConnect() }
        findViewById<Button>(R.id.wireless_adb_reconnect_button).setOnClickListener { reconnect() }
        findViewById<Button>(R.id.wireless_adb_forget_button).setOnClickListener { forget() }
        findViewById<Button>(R.id.setup_return_button).setOnClickListener { finish() }
        findViewById<Button>(R.id.wireless_developer_options).setOnClickListener { openDeveloperOptions() }
        findViewById<Button>(R.id.wireless_discovery_start).setOnClickListener {
            startDiscovery(requestNotifications = true)
        }
        listOf(R.id.pairing_code_input, R.id.pairing_port_input, R.id.connection_port_input).forEach {
            findViewById<EditText>(it).isEnabled = isPermitted
        }
        render()
        startDiscovery()
    }

    override fun onStart() {
        super.onStart()
        WirelessPairingSession.observer = {
            render()
            renderDiscovery()
        }
        renderDiscovery()
    }

    override fun onStop() {
        WirelessPairingSession.observer = null
        permissionPrompt?.close()
        super.onStop()
    }

    override fun onDestroy() {
        permissionPrompt?.close()
        worker.shutdownNow()
        super.onDestroy()
    }

    private val isPermitted: Boolean get() = StudyFlowGate.permits(StudyFlowGate.StudyTransport.WIRELESS_CONNECT_PLANE)

    private fun keyStore() = StudySetup.keyStore(applicationContext)

    private fun pairAndConnect() {
        if (!canConnect()) return
        val code = (AdbPairingCode.parse(rawCode()) as? AdbPairingCode.ParseResult.Ok)?.code
        val state = WirelessPairingSession.state
        val pairingPort = state.pairingPort ?: portOf(R.id.pairing_port_input)
        val connectionPort = state.connectionPort ?: portOf(R.id.connection_port_input)
        if (code == null || pairingPort == null || connectionPort == null) {
            showMessage(getString(R.string.wireless_adb_bad_input))
            return
        }
        val endpoints = WirelessAdbEndpoints(pairingPort, connectionPort)
        // The code is single-use: do not keep it on screen or in the view state.
        findViewById<EditText>(R.id.pairing_code_input).setText("")
        runInBackground { sequence -> sequence.run(PairingInput(endpoints, code)) }
    }

    private fun reconnect() {
        if (!canConnect()) return
        val connectionPort = WirelessPairingSession.state.connectionPort ?: portOf(R.id.connection_port_input)
        if (connectionPort == null) {
            showMessage(getString(R.string.wireless_adb_bad_port))
            return
        }
        val enrolled = keyStore().enrollment.current() != null
        runInBackground { sequence ->
            sequence.reconnect(WirelessAdbEndpoints.forReconnect(connectionPort), enrolled)
        }
    }

    private fun forget() {
        if (busy || WirelessPairingSession.busy) return
        keyStore().enrollment.clear()
        WirelessPairingSession.message = null
        StudySetup.wirelessReport = null
        showMessage(getString(R.string.wireless_adb_forgotten))
        render()
    }

    private fun runInBackground(block: (ActivationSequence) -> ActivationReport) {
        busy = true
        WirelessPairingSession.busy = true
        WirelessPairingSession.message = null
        showMessage(getString(R.string.wireless_adb_working))
        val keys = keyStore()
        val command =
            runCatching {
                HelperStartCommand.build(applicationInfo.nativeLibraryDir, applicationInfo.sourceDir)
            }.getOrNull()
        val prompt = HelperPermissionPrompt(this)
        permissionPrompt = prompt
        worker.execute {
            val runner = WirelessAdbActivationRunner(keys, StudyHelperHooks(prompt), helperStartCommand = command)
            val report = block(ActivationSequence(runner, StudySetup.wirelessAdb))
            runOnUiThread {
                busy = false
                WirelessPairingSession.busy = false
                StudySetup.wirelessReport = report
                render()
            }
        }
    }

    private fun render() {
        val report = StudySetup.wirelessReport
        val enrolled = keyStore().enrollment.current() != null
        val state = WirelessLink.stateOf(enrolled, report)
        val stateText =
            getString(
                when (state) {
                    WirelessLinkState.NOT_PAIRED -> R.string.wireless_link_not_paired
                    WirelessLinkState.PAIRED_AND_CONNECTED -> R.string.wireless_link_connected
                    WirelessLinkState.CONNECTION_LOST -> R.string.wireless_link_lost
                    WirelessLinkState.NEEDS_REPAIR -> R.string.wireless_link_repair
                },
            )
        val labelled = getString(R.string.wireless_adb_state_label, stateText)
        findViewById<TextView>(R.id.wireless_adb_link_state).text = labelled
        ActivationCheck.entries.forEach { check ->
            val outcome = report?.outcomeOf(check)
            val line =
                when (outcome) {
                    null -> getString(R.string.state_not_set_up)
                    is CheckOutcome.Passed -> getString(R.string.wireless_adb_check_passed)
                    is CheckOutcome.NotRun -> getString(R.string.wireless_adb_check_not_run)
                    is CheckOutcome.Failed -> outcome.failure.guidance
                }
            val name = getString(checkLabel(check))
            findViewById<TextView>(statusViewId(check)).text = getString(R.string.wireless_adb_check_line, name, line)
        }
        findViewById<TextView>(R.id.wireless_adb_guidance).text = report?.firstFailure?.guidance.orEmpty()
    }

    private fun showMessage(message: String) {
        findViewById<TextView>(R.id.wireless_adb_guidance).text = message
    }

    private fun canConnect(): Boolean {
        val wifi = WirelessPairingSession.state.networkId != null
        if (!wifi) showMessage(getString(R.string.wireless_wifi_needed))
        val idle = !busy && !WirelessPairingSession.busy
        return isPermitted && idle && wifi
    }

    private fun startDiscovery(requestNotifications: Boolean = false) {
        if (!isPermitted) return
        if (requestNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION)
        }
        startForegroundService(Intent(this, WirelessPairingService::class.java))
    }

    private fun openDeveloperOptions() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { showMessage(getString(R.string.wireless_settings_unavailable)) }
    }

    private fun renderDiscovery() {
        val state = WirelessPairingSession.state
        val message =
            when {
                state.networkId == null -> R.string.wireless_wifi_needed
                state.endpoints() != null -> R.string.wireless_ports_found
                state.timedOut && state.connectionPort == null -> R.string.wireless_debugging_off
                state.manualFallback -> R.string.wireless_manual_fallback
                else -> R.string.wireless_discovery_waiting
            }
        findViewById<TextView>(R.id.wireless_discovery_status).setText(message)
        listOf(R.id.pairing_port_input, R.id.connection_port_input).forEach {
            findViewById<EditText>(it).visibility = if (state.manualFallback) View.VISIBLE else View.GONE
        }
        WirelessPairingSession.message?.let { showMessage(it) }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION = 80
    }
}

private fun parsePort(raw: String): Int? =
    raw
        .trim()
        .toIntOrNull()
        ?.takeIf { it in WirelessAdbEndpoints.PORT_MIN..WirelessAdbEndpoints.PORT_MAX }

private fun checkLabel(check: ActivationCheck): Int =
    when (check) {
        ActivationCheck.PAIR -> R.string.wireless_adb_check_pair
        ActivationCheck.CONNECT -> R.string.wireless_adb_check_connect
        ActivationCheck.HELPER_START -> R.string.wireless_adb_check_helper_start
        ActivationCheck.AUTHORIZE -> R.string.wireless_adb_check_authorize
        ActivationCheck.BINDER_HEALTH -> R.string.wireless_adb_check_binder
    }

private fun statusViewId(check: ActivationCheck): Int =
    when (check) {
        ActivationCheck.PAIR -> R.id.check_pair_status
        ActivationCheck.CONNECT -> R.id.check_connect_status
        ActivationCheck.HELPER_START -> R.id.check_helper_start_status
        ActivationCheck.AUTHORIZE -> R.id.check_authorize_status
        ActivationCheck.BINDER_HEALTH -> R.id.check_binder_status
    }

private fun Activity.portOf(id: Int): Int? = parsePort(findViewById<EditText>(id).text.toString())

private fun Activity.rawCode(): String = findViewById<EditText>(R.id.pairing_code_input).text.toString()
