# ADR-0008: Runtime-permission touch attribution on Android 11

- Status: proposed; owner decision required before implementation
- Date: 2026-10-10
- Deciders: lead/owner (pending), round-1 implementer and reviewer
- Task: `t_7a91b291`

## Context

Round-1 location sharing needs a human runtime-permission answer without counting
that dialog interaction as takeover. The same approved step should continue on
Allow, while unrelated touches must still pause and must never auto-resume.

EQO currently observes screen touches using a 1x1 accessibility overlay with
`FLAG_WATCH_OUTSIDE_TOUCH`. Its `ACTION_OUTSIDE` event has no destination window
identifier. On Android 11, InputDispatcher removes coordinates when the touch
recipient and outside-touch observer have different owner UIDs. EQO and the system
permission controller have different UIDs. Consequently an Allow tap and an
outside-dialog touch can both arrive at EQO as `(0,0)`.

Verified source: [Android 11 InputDispatcher.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-11.0.0_r1/services/inputflinger/dispatcher/InputDispatcher.cpp),
lines 1846–1859 (cross-UID `FLAG_ZERO_COORDS`) and 2523–2529 (pointer coordinates
cleared). This source was fetched directly again during the rework.

The existing bounds/package check is fail-closed for redacted touches, but does
not fix Allow takeover. Its Boolean helper test does not reproduce real Android
input attribution. No passing phone or service-level proof is claimed.

Window state can establish that a permission dialog is visible, not that this
particular touch landed in it. An accessibility click source can identify a
control after activation, but this implementation has no guaranteed one-to-one
association between that click and an earlier anonymous outside-touch event.
Dropping pending anonymous touches when a click/result later arrives would also
risk dropping unrelated touches. A successful permission callback is proof of the
answer, not proof that every observed touch belonged to that dialog.

## Decision requested

Recommended: obtain the planned runtime prerequisites before starting the action
loop, following the existing All files access preflight pattern. Human permission
interaction then happens outside an active approved action. Approval still occurs
once for the unchanged plan; no sending, typing or other execution happens before
approval. Runtime checks remain mandatory. If access is revoked after preflight,
hand back with clear Needs-you text and require explicit restart/reapproval; never
clear takeover or auto-resume.

This changes the requested mid-run permission flow and needs owner approval.
Implementation must also prove late permission/input events cannot affect a new
run and that Stop cancels a pending preflight. Preflight is a proposal, not code
already delivered by this rework.

Alternative: retain the strict mid-run flow only if a trusted input-attribution
surface can identify the actual touched window on the supported Android 11/OEM
path. The current anonymous overlay events do not provide that information.
Any different surface/privileged-helper design needs its own privacy/security
review and service-level fake plus device evidence before exemptions are added.

Rejected: ignoring all touches while permissions are pending; exempting Settings
or an entire permission-controller foreground window; inferring touch destination
from `(0,0)`; clearing the latch on Allow; optimistic time-based click correlation.
These weaken the card's unrelated-touch/no-auto-resume requirements.

## Consequences

- The card remains blocked, not complete and not ready for final review.
- The 120-second wait, Stop handling and stale request-code isolation remain, but
  runtime Allow can still pause the run on the real cross-UID Android 11 path.
- Permission/takeover production code is unchanged by this rework. The failure is
  documented instead of hidden behind an unsafe exemption or invented test proof.
- The independent Keep planner regression is fixed and host-tested separately.
- After the owner selects a flow, implementation and explicit regressions are
  required before final review and lead-owned phone testing/publication.
