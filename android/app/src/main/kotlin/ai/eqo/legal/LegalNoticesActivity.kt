/*
 * EQO (TASK-015, issue #20): the Legal / open-source notices screen (UF-14, S-28).
 *
 * REQ-INS-03: reachable outside the normal flow (the `eqo://legal` deep link and every
 * main screen) and renders offline — the content ships in resources, no network.
 */
package ai.eqo.legal

import ai.eqo.R
import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class LegalNoticesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.legal_notices)
        val info = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.legal_build_info).text =
            getString(R.string.legal_version_label, info.versionName, info.longVersionCode)
        findViewById<Button>(R.id.legal_close_button).setOnClickListener { finish() }
    }
}
