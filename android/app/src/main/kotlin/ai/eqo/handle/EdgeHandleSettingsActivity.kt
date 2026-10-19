// Origin: EQO edge-handle task; opt-in and per-feature switches/order using platform widgets.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleShortcut
import ai.eqo.accessibility.handle.HandleShortcutRegistry
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class EdgeHandleSettingsActivity : Activity() {
    private val preferences by lazy { HandlePreferences(this) }
    private var recordingDialog: AlertDialog? = null
    private var recordingInput: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        val requested =
            savedInstanceState?.getBoolean(RECORDING_PROMPT)
                ?: intent.getBooleanExtra(HandleRecording.START_RECORDING, false)
        if (requested) {
            intent.removeExtra(HandleRecording.START_RECORDING)
            confirmRecording(savedInstanceState?.getString(RECORDING_DRAFT) ?: preferences.recordingLimit)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(RECORDING_PROMPT, recordingDialog?.isShowing == true)
        outState.putString(RECORDING_DRAFT, recordingInput?.text?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        recordingDialog?.dismiss()
        super.onDestroy()
    }

    private fun confirmRecording(initialLimit: String = preferences.recordingLimit) {
        val input =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setHint(R.string.recording_limit_hint)
                setText(initialLimit)
            }
        recordingInput = input
        val dialog =
            AlertDialog
                .Builder(this)
                .setTitle(ai.eqo.platform.R.string.edge_recording_button)
                .setMessage(R.string.recording_start_explanation)
                .setView(input)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    android.util.Log.i("EqoRun", "handle=record result=cancelled reason=user_cancelled")
                    finish()
                }.setNeutralButton(R.string.recording_no_limit) { _, _ -> startWithLimit("") }
                .setPositiveButton(R.string.recording_start, null)
                .create()
        dialog.setOnShowListener {
            HandleRecording.confirmationShown()
            android.util.Log.i("EqoRun", "handle=record result=prompt reason=visible_confirmation")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (RecordingLimit.parse(input.text.toString()) == null) {
                    input.error = getString(R.string.recording_limit_invalid)
                } else {
                    dialog.dismiss()
                    startWithLimit(input.text.toString())
                }
            }
        }
        dialog.setOnCancelListener { finish() }
        recordingDialog = dialog
        dialog.show()
    }

    private fun startWithLimit(limit: String) {
        preferences.recordingLimit = limit.trim()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_PERMISSION)
        } else if (!isFinishing && window.decorView.isShown) {
            RecordingService.visibleStartApproved = true
            RecordingService.startFinished = {
                moveTaskToBack(true)
                finish()
            }
            runCatching { startForegroundService(Intent(this, RecordingService::class.java)) }
                .onFailure {
                    RecordingService.visibleStartApproved = false
                    RecordingService.startFinished = null
                    HandleRecording.feedback(this, R.string.recording_refused, "refused", "foreground_start_blocked")
                }
        } else {
            HandleRecording.feedback(
                this,
                R.string.recording_confirmation_blocked,
                "refused",
                "confirmation_not_visible",
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == RECORD_PERMISSION) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                confirmRecording()
            } else {
                HandleRecording.feedback(this, R.string.recording_permission_required, "refused", "microphone_denied")
            }
        }
    }

    override fun onPause() {
        RecordingService.visibleStartApproved = false
        RecordingService.startFinished = null
        super.onPause()
    }

    private fun addRecordingLimit(column: LinearLayout) {
        column.addView(TextView(this).apply { setText(R.string.recording_limit) })
        val input =
            EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setHint(R.string.recording_limit_hint)
                setText(preferences.recordingLimit)
            }
        column.addView(input)
        column.addView(
            Button(this).apply {
                setText(R.string.recording_limit_save)
                setOnClickListener {
                    if (RecordingLimit.parse(input.text.toString()) == null) {
                        input.error = getString(R.string.recording_limit_invalid)
                    } else {
                        preferences.recordingLimit = input.text.toString().trim()
                    }
                }
            },
        )
        column.addView(
            Button(this).apply {
                setText(R.string.recording_no_limit)
                setOnClickListener {
                    preferences.recordingLimit = ""
                    input.setText("")
                }
            },
        )
    }

    private fun render() {
        val registry = EdgeHandleFeatures.registry ?: return
        title = getString(R.string.handle_settings_title)
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        val padding = (PAGE_PADDING_DP * resources.displayMetrics.density).toInt()
        column.setPadding(padding, padding, padding, padding)
        column.addView(TextView(this).apply { setText(R.string.handle_settings_explanation) })
        column.addView(
            Switch(this).apply {
                setText(R.string.handle_enable)
                isChecked = preferences.enabled
                setOnCheckedChangeListener { _, checked -> preferences.enabled = checked }
            },
        )
        registry.ordered().forEachIndexed { index, shortcut ->
            addShortcut(column, registry, shortcut, index)
        }
        addRecordingLimit(column)
        column.addView(
            Button(this).apply {
                setText(R.string.handle_reset)
                setOnClickListener {
                    registry.reset()
                    render()
                }
            },
        )
        column.addView(
            Button(this).apply {
                setText(R.string.handle_unhide_all)
                setOnClickListener {
                    preferences.clearHiddenApps()
                    render()
                }
            },
        )
        column.addView(
            TextView(this).apply {
                text = getString(R.string.handle_hidden_count, preferences.hiddenApps().size)
            },
        )
        preferences.hiddenApps().sorted().forEach { app ->
            column.addView(
                Button(this).apply {
                    text = getString(R.string.handle_unhide_app, app)
                    setOnClickListener {
                        preferences.unhide(app)
                        render()
                    }
                },
            )
        }
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun addShortcut(
        column: LinearLayout,
        registry: HandleShortcutRegistry<Context>,
        shortcut: HandleShortcut<Context>,
        index: Int,
    ) {
        val reason = shortcut.unavailableReason(this)
        column.addView(
            Switch(this).apply {
                setText(shortcut.labelRes)
                isChecked = registry.enabled(shortcut.id)
                // Choice remains editable when unavailable; the panel omits it until ready.
                setOnCheckedChangeListener { _, checked -> registry.setEnabled(shortcut.id, checked) }
            },
        )
        reason?.let { column.addView(TextView(this).apply { setText(it) }) }
        val row = LinearLayout(this)
        row.addView(
            Button(this).apply {
                setText(R.string.handle_move_up)
                contentDescription = getString(R.string.handle_move_up_named, getString(shortcut.labelRes))
                isEnabled = index > 0
                setOnClickListener {
                    registry.move(shortcut.id, -1)
                    render()
                }
            },
        )
        row.addView(
            Button(this).apply {
                setText(R.string.handle_move_down)
                contentDescription = getString(R.string.handle_move_down_named, getString(shortcut.labelRes))
                isEnabled = index < registry.ordered().lastIndex
                setOnClickListener {
                    registry.move(shortcut.id, 1)
                    render()
                }
            },
        )
        column.addView(row)
    }

    private companion object {
        const val RECORDING_PROMPT = "recording_prompt"
        const val RECORDING_DRAFT = "recording_draft"
        const val PAGE_PADDING_DP = 16
        const val RECORD_PERMISSION = 704
    }
}
