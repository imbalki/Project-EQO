// Origin: EQO edge-handle task; opt-in and per-feature switches/order using platform widgets.
package ai.eqo.handle

import ai.eqo.R
import ai.eqo.accessibility.handle.EdgeHandleFeatures
import ai.eqo.accessibility.handle.HandlePreferences
import ai.eqo.accessibility.handle.HandleShortcut
import ai.eqo.accessibility.handle.HandleShortcutRegistry
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class EdgeHandleSettingsActivity : Activity() {
    private val preferences by lazy { HandlePreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
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
                setOnClickListener { preferences.clearHiddenApps(); render() }
            },
        )
        column.addView(TextView(this).apply {
            text = getString(R.string.handle_hidden_count, preferences.hiddenApps().size)
        })
        preferences.hiddenApps().sorted().forEach { app ->
            column.addView(Button(this).apply {
                text = getString(R.string.handle_unhide_app, app)
                setOnClickListener { preferences.unhide(app); render() }
            })
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
        const val PAGE_PADDING_DP = 16
    }
}
