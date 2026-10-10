# ADR 0012: Compact accessibility-hosted task panel

Status: Proposed implementation; independent review and phone verification required.
Task: t_edb1be59. Supersedes only Ask's full-screen entry in ADR 0010.

## Decision

Ask opens a process-only TYPE_ACCESSIBILITY_OVERLAY at the bottom of the current app,
roughly 30% of display height, with translucent background, draggable/collapsible header,
editable request and the existing Phone/AI voice adapters. Plan and preview never execute.
A separate Approve button hands an immutable ApprovedTaskPlan to the existing foreground
TaskRunService. Pause and Stop use the existing controller. Closing is not Stop, and closing,
expanding, navigating or Android lifecycle callbacks never authorize resuming a paused run.
Resume remains exclusively on the full task screen with its existing protected human dialog.

Full EQO opens home. The registry's generic HandleEntry records the foreground package
before the shortcut panel replaces the app's active window; entries receive it without
hard-coded shortcut IDs. A Back button uses the package label and launch intent, then moves
EQO's task behind the previous task. Package/app labels remain in-memory/entry intents only,
never in preferences, logs or receipts. A missing launch target falls back to task navigation.

Explain renders headings/bold/bullet/numbered lines with native text spans, never HTML,
WebView, URL navigation or image loading. Speech strips formatting and has separate
speaking/paused/stopped state. Continue reading repeats the interrupted chunk, not an agent
resume. Closing destroys speech and session context. The model is asked to explain rather
than copy the screen, in device language, about 120 words unless asked for more.

## Threat model and invariants

- The panel is owned by EQO's UID/package. Existing own-window guards reject agent node
  actions on it. A separate compact rectangle composes with handle and task-control
  rectangles to reject coordinate gestures while allowing work on the underlying app.
- The compact rectangle is not the full-screen handle-menu guard: making that guard global
  would block every action while live progress is visible. Updating/removing the edge handle
  must not clear the compact rectangle or a takeover latch.
- Approve uses ProtectedConfirmationTouches, including gesture poisoning after obscured
  DOWN/MOVE/UP. An accessibility overlay is not permission to weaken obscured-touch checks.
  No new UserResumeConfirmation mint site or parameterless resume is introduced.
- Only panel touches are excluded from takeover. Touches elsewhere still latch takeover;
  opening the handle menu still requests Pause and waits for in-flight work to settle.
- Before starting work, the panel drops focus and hides the IME so the underlying app can
  become active. A cancelled/edited preview or panel closure invalidates delayed preparation
  and the delayed service start. Closing an already-started panel does not cancel the service.
- Requests, plans, model responses and screen content are not logged or persisted. Input
  disables state saving, autofill and personalized IME learning. FLAG_SECURE stays enabled.
- Microphone access requires the existing disclosure and an explicit human system grant.
  A non-exported translucent grant-only activity returns to the panel; the owner taps Mic
  again. It never records, plans or starts a run automatically. AI consent/capability and
  temporary-file cleanup retain the voice-v2 adapter behavior.
- Missing action runtime/Special Access is fail-closed, obtained using Full EQO's existing
  pre-run prerequisite flow. The panel never requests permissions during approved execution.
- Service disconnect closes the panel and clears ephemeral UI. Process death never replays
  an approved plan. All file-send disclosure/selection safety remains owned by the existing
  service; unavailable foreground file choice must fail rather than guess.

## Limitations and verification

No new runtime dependency or SYSTEM_ALERT_WINDOW permission. Android/OEM focus, keyboard,
rotation, microphone background policy, launch-intent return fidelity, window redraw and
obscuration behavior require real phone testing. A launch intent can reopen an app rather
than its exact prior document. Missing prerequisites currently require Full EQO; compact
file-chooser UI is not introduced here. Speech continuation can repeat a partial chunk.

Host fake-planner/speech/controller, hitbox, lifecycle and obscured-touch tests must pass
alongside app/platform debug lint, ktlint and detekt before local publication for review.
See PHASE-ONE-TEST-LOG.md for actual results; this ADR is not a claim of phone verification.
