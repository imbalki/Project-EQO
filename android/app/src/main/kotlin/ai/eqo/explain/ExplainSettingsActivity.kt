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
                val padding = (PAGE_PADDING_DP * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
        layout.addView(
            TextView(this).apply {
                setText(R.string.explain_consent)
                textSize = BODY_SP
            },
        )
        layout.addView(option(R.string.explain_allow_setting, "allow_provider", ExplainSettings.allowed(this)))
        layout.addView(option(R.string.explain_auto_read, "auto_read", ExplainSettings.autoRead(this)))
        notification = option(R.string.explain_notification_setting, "notification", ExplainSettings.notification(this))
        layout.addView(notification)
        layout.addView(
            TextView(this).apply {
                setText(R.string.explain_primary_entry)
                textSize = BODY_SP
            },
        )
        setContentView(ScrollView(this).apply { addView(layout) })
        layout.addView(
            TextView(this).apply {
                setText(R.string.explain_tile_steps)
                textSize = BODY_SP
            },
        )
        layout.addView(
            android.widget.Button(this).apply {
                setText(R.string.explain_editor_hint)
                setOnClickListener {
                    android.widget.Toast
                        .makeText(
                            this@ExplainSettingsActivity,
                            R.string.explain_editor_manual,
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            layout.addView(
                android.widget.Button(this).apply {
                    setText(R.string.explain_add_tile)
                    setOnClickListener { requestTile() }
                },
            )
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestTile() {
        getSystemService(android.app.StatusBarManager::class.java).requestAddTileService(
            android.content.ComponentName(this, ExplainTileService::class.java),
            getString(R.string.explain_title),
            android.graphics.drawable.Icon
                .createWithResource(this, R.drawable.ic_launcher),
            mainExecutor,
        ) { /* Android owns the confirmation and reports cancellation; manual steps remain visible. */ }
    }

    private fun option(
        label: Int,
        key: String,
        checked: Boolean,
    ): CheckBox =
        CheckBox(this).apply {
            setText(label)
            textSize = BODY_SP
            minHeight = (TOUCH_TARGET_DP * resources.displayMetrics.density).toInt()
            isChecked = checked
            setOnCheckedChangeListener { _, enabled ->
                if (key == "notification" &&
                    enabled &&
                    needsNotificationPermission()
                ) {
                    isChecked = false
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
                } else {
                    ExplainSettings.set(this@ExplainSettingsActivity, key, enabled)
                    if (key == "notification") ExplainNotificationService.update(this@ExplainSettingsActivity)
                }
            }
        }

    private fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

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
        private const val PAGE_PADDING_DP = 24
        private const val BODY_SP = 20f
        private const val TOUCH_TARGET_DP = 56
    }
}
