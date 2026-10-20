// Origin: EQO-authored ephemeral handle timing; no platform dependencies or persisted content.
package ai.eqo.accessibility.handle

import ai.eqo.platform.R
import android.view.View

/** Deadlines use monotonic time in production and an injectable clock in tests. */
class HandlePresentation(
    private val now: () -> Long,
) {
    var expanded = false
        private set
    private var collapseAt = 0L
    private var resultUntil = 0L
    private var hiddenPackage: String? = null

    fun open() {
        expanded = true
        interact()
    }

    fun interact() {
        if (expanded) collapseAt = now() + COLLAPSE_MS
    }

    fun collapse() {
        expanded = false
    }

    fun tick(): Boolean {
        val expired = expanded && now() >= collapseAt
        if (expired) collapse()
        return expired
    }

    fun inserted() {
        resultUntil = now() + RESULT_MS
    }

    fun resultVisible(): Boolean = now() < resultUntil

    fun hide(packageName: String) {
        hiddenPackage = packageName
        collapse()
    }

    fun hidden(packageName: String): Boolean = hiddenPackage == packageName

    fun foregroundChanged() {
        hiddenPackage = null
        resultUntil = 0L
        collapse()
    }

    companion object {
        const val COLLAPSE_MS = 3_000L
        const val RESULT_MS = 2_000L
    }
}

/** Percent of the screen height the compact panel may never exceed. */
const val PANEL_HEIGHT_CAP_PERCENT = 70

private const val PERCENT_BASE = 100

/** Compact icon grid beside the handle: two columns, one button per action. */
const val PANEL_COLUMNS = 2

/** Panel padding (dp) and icon-button metrics: 40dp visible circle inside a 48dp touch cell. */
const val PANEL_PADDING_DP = 8

const val PANEL_CELL_DP = 48
const val PANEL_ICON_DP = 40

/** Empty space (dp) between cells and around the grid, per the Material 3 compact look. */
const val PANEL_CELL_GAP_DP = 8

/** Panel container corner radius (dp). */
const val PANEL_RADIUS_DP = 16

/** Gap (dp) between the icon and its name chip. */
const val TOOLTIP_GAP_DP = 8

/** Touch-and-hold duration (ms) before the action-name chip appears. */
const val TOOLTIP_LONG_PRESS_MS = 400L

/** Keys for the actions the panel owns next to the registered shortcuts. */
const val PANEL_KEY_CLOSE = "close"

const val PANEL_KEY_HIDE_NOW = "hide_now"
const val PANEL_KEY_HIDE_APP = "hide_app"

/** One icon button: stable [key], [iconRes] mark and [labelRes] spoken name. */
data class PanelActionSpec(
    val key: String,
    val iconRes: Int,
    val labelRes: Int,
    val viewId: Int,
)

/** A registered shortcut as the panel sees it: [key], [iconRes] mark and [labelRes] spoken name. */
data class PanelShortcutSpec(
    val key: String,
    val iconRes: Int,
    val labelRes: Int,
)

/** Top-left/bottom-right box in panel pixel coordinates. */
data class PanelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Name-chip offset inside the panel. */
data class TooltipPlacement(
    val x: Int,
    val y: Int,
)

private data class BuiltinAction(
    val iconRes: Int,
    val labelRes: Int,
)

private val PANEL_BUILTINS =
    mapOf(
        PANEL_KEY_CLOSE to BuiltinAction(R.drawable.ic_handle_close, R.string.edge_handle_close),
        PANEL_KEY_HIDE_NOW to BuiltinAction(R.drawable.ic_handle_hide_now, R.string.edge_handle_hide_now),
        PANEL_KEY_HIDE_APP to BuiltinAction(R.drawable.ic_handle_hide_app, R.string.edge_handle_hide_app),
    )

private val PANEL_ACTION_VIEW_IDS =
    mapOf(
        PANEL_KEY_CLOSE to R.id.eqo_handle_action_close,
        PANEL_KEY_HIDE_NOW to R.id.eqo_handle_action_hide_now,
        PANEL_KEY_HIDE_APP to R.id.eqo_handle_action_hide_app,
        "explain_screen" to R.id.eqo_handle_action_explain_screen,
        "ask_eqo" to R.id.eqo_handle_action_ask_eqo,
        "pause" to R.id.eqo_handle_action_pause,
        "stop" to R.id.eqo_handle_action_stop,
        "open_eqo" to R.id.eqo_handle_action_open_eqo,
        "dictation" to R.id.eqo_handle_action_dictation,
        "recording" to R.id.eqo_handle_action_recording,
        "screenshot" to R.id.eqo_handle_action_screenshot,
    )

/** Stable view id for known actions; unknown shortcut keys carry [View.NO_ID] and rely on the name. */
fun panelActionViewId(key: String): Int = PANEL_ACTION_VIEW_IDS[key] ?: View.NO_ID

/** Every button shows a mark: a shortcut that ships no icon falls back to the generic one. */
fun panelIconRes(iconRes: Int): Int = if (iconRes != 0) iconRes else R.drawable.ic_handle_generic

/** Panel actions in order: Close and Hide for now first, the shortcuts, then Hide for this app. */
fun panelActionSpecs(
    shortcuts: List<PanelShortcutSpec>,
    hideAppAvailable: Boolean,
): List<PanelActionSpec> {
    val entries = mutableListOf(builtinPanelAction(PANEL_KEY_CLOSE), builtinPanelAction(PANEL_KEY_HIDE_NOW))
    entries +=
        shortcuts.map { shortcut ->
            PanelActionSpec(
                shortcut.key,
                panelIconRes(shortcut.iconRes),
                shortcut.labelRes,
                panelActionViewId(shortcut.key),
            )
        }
    if (hideAppAvailable) entries += builtinPanelAction(PANEL_KEY_HIDE_APP)
    return entries
}

private fun builtinPanelAction(key: String): PanelActionSpec {
    val builtin = PANEL_BUILTINS.getValue(key)
    return PanelActionSpec(key, builtin.iconRes, builtin.labelRes, panelActionViewId(key))
}

/** Panel window width: padding plus one spaced cell per column. */
fun panelWidthDp(columns: Int): Int {
    val count = columns.coerceAtLeast(1)
    return PANEL_PADDING_DP * 2 + count * (PANEL_CELL_DP + PANEL_CELL_GAP_DP)
}

/** Row spans an [itemCount]-entry grid fills with [columns] columns, in entry order. */
fun panelRows(
    itemCount: Int,
    columns: Int,
): List<IntRange> {
    val width = columns.coerceAtLeast(1)
    val count = itemCount.coerceAtLeast(0)
    return (0 until count step width).map { start -> start until minOf(start + width, count) }
}

/** Panel window height: fit every measured row, capped at [PANEL_HEIGHT_CAP_PERCENT] of the screen. */
fun panelHeight(
    contentHeight: Int,
    screenHeight: Int,
): Int {
    val cap = screenHeight.coerceAtLeast(0) * PANEL_HEIGHT_CAP_PERCENT / PERCENT_BASE
    return minOf(contentHeight.coerceAtLeast(0), cap)
}

/** Name chip top-left: beside the icon when it fits there, otherwise on its other side. */
fun tooltipPlacement(
    anchor: PanelRect,
    panel: PanelRect,
    chipWidth: Int,
    chipHeight: Int,
    gap: Int,
): TooltipPlacement {
    val x =
        if (anchor.right + gap + chipWidth <= panel.right) {
            anchor.right + gap
        } else {
            anchor.left - gap - chipWidth
        }
    val centerY = (anchor.top + anchor.bottom) / 2 - chipHeight / 2
    return TooltipPlacement(
        x.coerceIn(panel.left, maxOf(panel.left, panel.right - chipWidth)),
        centerY.coerceIn(panel.top, maxOf(panel.top, panel.bottom - chipHeight)),
    )
}

/** The name chip shows while the icon is hovered or held and hides when neither holds. */
fun tooltipVisible(
    hovering: Boolean,
    holding: Boolean,
): Boolean = hovering || holding

/** Stay above the IME and outside the focused input, or disappear when neither side has room. */
fun fitHandleY(
    preferred: Int,
    height: Int,
    available: Int,
    inputTop: Int?,
    inputBottom: Int?,
): Int? {
    val fitted = preferred.coerceIn(0, maxOf(0, available - height))
    return when {
        height > available -> null
        inputTop == null || inputBottom == null || fitted + height <= inputTop || fitted >= inputBottom -> fitted
        inputTop >= height -> inputTop - height
        available - inputBottom >= height -> inputBottom
        else -> null
    }
}
