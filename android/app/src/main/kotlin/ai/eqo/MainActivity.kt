package ai.eqo

import ai.eqo.adb.pairing.ActivationStatus
import ai.eqo.adb.pairing.PrivilegedResult
import ai.eqo.adb.pairing.WirelessAdbActivation
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.core.net.toUri

/**
 * Entry activity. Hosts the launcher UI and the Legal / open-source notices screen (UF-14, S-28).
 *
 * TASK-008 (issue #13): also owns the first-run wireless-ADB activation surface. A fresh
 * install shows "activation required", and every privileged entry point refuses with
 * recovery guidance until the pairing activation completes (acceptance criterion 1).
 */
class MainActivity : Activity() {
    /** Install-scoped wireless-ADB activation gate (fresh install = ACTIVATION_REQUIRED). */
    private val activation = WirelessAdbActivation()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findViewById<Button>(R.id.notices_button).setOnClickListener { showNotices() }
        findViewById<Button>(R.id.privileged_action_button).setOnClickListener { runPrivilegedAction() }
    }

    override fun onResume() {
        super.onResume()
        renderActivationState()
    }

    /** Handles `eqo://` deep links (TASK-005: scheme is `eqo`, host `legal`). */
    override fun onStart() {
        super.onStart()
        intent?.data?.takeIf { it.scheme.equals("eqo", ignoreCase = true) }?.let { uri ->
            if (uri.host.equals("legal", ignoreCase = true)) showNotices()
        }
    }

    private fun renderActivationState() {
        val label =
            when (activation.status) {
                ActivationStatus.ACTIVATION_REQUIRED -> getString(R.string.activation_required)
                ActivationStatus.ACTIVE -> getString(R.string.activation_active)
            }
        findViewById<TextView>(R.id.activation_status).text = label
    }

    /** Privileged entry point: refuses with guidance until wireless-ADB activation is done. */
    private fun runPrivilegedAction() {
        val result =
            activation.runPrivileged(getString(R.string.privileged_feature_wireless_adb)) {
                getString(R.string.privileged_granted)
            }
        val message =
            when (result) {
                is PrivilegedResult.Refused -> result.guidance
                is PrivilegedResult.Allowed -> result.value
            }
        AlertDialog
            .Builder(this)
            .setTitle(R.string.activation_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showNotices() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.notices_title)
            .setMessage(R.string.notices_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /** Builder for `eqo://legal` so tests and callers share one canonical deep-link shape. */
    companion object {
        /** Canonical `eqo://legal` deep-link strings (no android.net.Uri in unit tests). */
        const val LEGAL_SCHEME = "eqo"
        const val LEGAL_HOST = "legal"

        fun legalDeepLinkIntent(): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                data = "$LEGAL_SCHEME://$LEGAL_HOST".toUri()
                `package` = "ai.eqo.app"
            }
    }
}
