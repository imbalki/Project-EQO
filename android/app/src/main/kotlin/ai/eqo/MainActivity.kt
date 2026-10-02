package ai.eqo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.core.net.toUri

/** Entry activity. Hosts the launcher UI and the Legal / open-source notices screen (UF-14, S-28). */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findViewById<Button>(R.id.notices_button).setOnClickListener { showNotices() }
    }

    /** Handles `eqo://` deep links (TASK-005: scheme is `eqo`, host `legal`). */
    override fun onStart() {
        super.onStart()
        intent?.data?.takeIf { it.scheme.equals("eqo", ignoreCase = true) }?.let { uri ->
            if (uri.host.equals("legal", ignoreCase = true)) showNotices()
        }
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
