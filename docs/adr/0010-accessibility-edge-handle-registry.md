# ADR-0010: Opt-in accessibility edge handle and feature registry

Status: proposed for review (task t_a3fa16d0, branch feat/edge-handle).

## Decision

The existing EQO AccessibilityService owns one additional TYPE_ACCESSIBILITY_OVERLAY window.
No SYSTEM_ALERT_WINDOW permission, new service, runtime or agent framework is added.
The handle defaults OFF. The home screen's "Edge handle shortcuts" settings page enables it,
chooses shortcuts, reorders them with Up/Down, resets defaults and restores hidden apps.
A 48dp-wide touch target contains a thin translucent bar; vertical dragging stores a normalized
position and crossing the screen changes edge. Tap or an inward swipe opens the shortcut panel.
Outside tap, Back or Close dismisses it. Long press opens the same panel with "Hide for this app".
The app-private preference stores only IDs, choices, dock placement and hidden package names.

The generic HandleShortcut<C> contract has stable id, labelRes, iconRes, defaultEnabled,
unavailableReason(context) and run(context). Null availability reason means available; a resource ID
explains unavailability in settings. The panel omits unavailable entries, retaining user choices.
HandleShortcutRegistry<C> uses an injected HandleChoiceStore, so registry tests need no Android.
Android uses HandlePreferences with KTX edits. Removed feature choices are retained for re-registration.
New IDs are appended, OFF unless explicitly marked defaultEnabled. Existing OFF choices are respected
across upgrades. Reset restores registration order and defaultEnabled choices, not global enablement.

EqoApplication installs the registry before the accessibility service is created. Each feature registers
with one line: `registry.register(MyFeatureShortcut())`. Service and settings use the same registry.
Built-ins: ask_eqo, pause, stop, open_eqo (default choices ON; master handle still OFF).
Ask only opens TaskActivity and focuses the request box/keyboard: it neither plans nor runs anything.
There is no voice microphone in the existing task screen, so this change does not add one.
Pause/Stop call StudyTaskController.pause()/stop() through the live TaskRunSession controller,
the same public entry points as the task UI and notification controls. No loop internals or resume
confirmations are exposed. A missing run hides both controls; availability is rechecked on activation.

## Future feature registrations (placeholders only, NOT wired)

| Stable ID reserved | Feature owner follow-up | Registration after merge |
|---|---|---|
| explain_screen | feat/explain-screen | `registry.register(ExplainScreenShortcut())` |
| routines | Routines feature UI | `registry.register(RoutinesShortcut())` |
| share_location | User-facing location sharing entry | `registry.register(ShareLocationShortcut())` |
| recent_files | Recent-files feature UI | `registry.register(RecentFilesShortcut())` |

These class names illustrate future adapters, not existing imports or dependencies. Adapters should
leave defaultEnabled false unless the product explicitly wants a new default. An availability resource
must explain any permission/service requirement. The overlay itself never captures or forwards content.

## Safety

The existing own-package node guard still refuses overlay nodes. Active roots can belong to the app
UNDER the overlay, so package checks alone are insufficient: HandleWindowGuard adds a coordinate
hitbox check to GatedServiceActions and the raw gesture dispatcher. While the panel is visible,
typed automation (including content observation) is refused, not allowed to interact with an underlying
app or the panel. Global Back/Home cannot be used by an agent to dismiss this own surface while open.
Already-dispatched work cannot be recalled; phone testing must check opening the panel mid-run.

Touch-probe exclusion combines (does not replace) task-screen control hitboxes with overlay hitboxes.
Only the real handle footprint is excluded when collapsed; the open panel owns its full-screen window,
including the dismissal backdrop. No time-only grace, blanket exclusion of the underlying app,
new resume API or takeover reset is added. Bounds are removed with the overlay. If the coordinate
probe cannot be installed, the handle is not shown: the conservative no-coordinate takeover fallback
remains intact. Explicit Pause/Stop are user controls independent of the automation gate.

## Limitations and verification

The keyboard or full-screen videos/games may hide the handle; settings says this plainly.
OEM window layering, Back dispatch, edge drag, takeover event ordering and Pause during real task
execution remain NOT TESTED ON PHONE. Fake registry/safety tests and app preference/control tests
are the local gate; full CI remains the merge gate. See CURRENT-HANDOFF.md and PHASE-ONE-TEST-LOG.md.
