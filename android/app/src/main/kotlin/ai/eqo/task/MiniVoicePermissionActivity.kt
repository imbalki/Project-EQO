package ai.eqo.task

import android.Manifest
import android.app.Activity
import android.os.Bundle

/** Only the system microphone grant, after the panel's human rationale tap. Never starts voice or a run. */
class MiniVoicePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST) finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val callback = completed
            completed = null
            callback?.invoke()
        }
    }

    companion object {
        internal var completed: (() -> Unit)? = null
        private const val REQUEST = 6912
    }
}
