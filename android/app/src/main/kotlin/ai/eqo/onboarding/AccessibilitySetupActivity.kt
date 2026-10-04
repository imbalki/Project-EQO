/*
 * EQO (TASK-015, issue #20): accessibility setup (UF-03, screens S-06/S-07/S-25).
 *
 * REQ-A11Y-01/02/03: EQO OPENS Android's own settings screens and READS the current
 * state; the user taps every grant themselves and no copy claims an EQO grant. The
 * Android 13+ restricted-settings repair card is the documented Android-owned path.
 * EQO never writes Settings.Secure (TASK-007 SF-1 / EqoNeverGrantsTest).
 */
package ai.eqo.onboarding

import ai.eqo.R
import ai.eqo.accessibility.AccessibilitySetupGuide
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class AccessibilitySetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.accessibility_setup)
        findViewById<Button>(R.id.accessibility_app_settings_button).setOnClickListener {
            startActivity(AccessibilitySetupGuide.appSettingsIntent(this))
        }
        findViewById<Button>(R.id.accessibility_settings_button).setOnClickListener {
            startActivity(AccessibilitySetupGuide.accessibilitySettingsIntent())
        }
        findViewById<Button>(R.id.accessibility_recheck_button).setOnClickListener {
            render()
            Toast.makeText(this, R.string.accessibility_checked, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.setup_return_button).setOnClickListener { finish() }
        render()
    }

    override fun onResume() {
        super.onResume()
        // REQ-A11Y-02: the app re-checks accessibility state automatically on return.
        render()
    }

    private fun render() {
        findViewById<TextView>(R.id.accessibility_steps).setText(R.string.accessibility_steps_copy)
        val enabled = StudySetup.accessibilityServiceEnabled(this)
        findViewById<TextView>(R.id.accessibility_status).setText(
            if (enabled) R.string.accessibility_enabled else R.string.accessibility_disabled,
        )
        findViewById<TextView>(R.id.accessibility_guidance).text =
            if (enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                ""
            } else {
                getString(R.string.accessibility_restricted_hint)
            }
    }
}
