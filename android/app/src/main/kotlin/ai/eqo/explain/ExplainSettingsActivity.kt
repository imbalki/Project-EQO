package ai.eqo.explain

import ai.eqo.R
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ExplainSettingsActivity : Activity() {
    private lateinit var notification: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }
        layout.addView(
            TextView(this).apply {
                setText(R.string.explain_consent)
                textSize = 20f
            },
        )
        layout.addView(option(R.string.explain_allow_setting, "allow_provider", ExplainSettings.allowed(this)))
        layout.addView(option(R.string.explain_auto_read, "auto_read", ExplainSettings.autoRead(this)))
        notification = option(R.string.explain_notification_setting, "notification", ExplainSettings.notification(this))
        layout.addView(notification)
        layout.addView(
            TextView(this).apply {
                setText(R.string.explain_use_shortcut)
                textSize = 20f
            },
        )
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun option(
        label: Int,
        key: String,
        checked: Boolean,
    ): CheckBox =
        CheckBox(this).apply {
            setText(label)
            textSize = 20f
            minHeight = (56 * resources.displayMetrics.density).toInt()
            isChecked = checked
            setOnCheckedChangeListener { _, enabled ->
                if (key == "notification" &&
                    enabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    isChecked = false
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
                } else {
                    ExplainSettings.set(this@ExplainSettingsActivity, key, enabled)
                    if (key == "notification") ExplainNotificationService.update(this@ExplainSettingsActivity)
                }
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST) {
            notification.isChecked = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val NOTIFICATION_REQUEST = 701
    }
}
