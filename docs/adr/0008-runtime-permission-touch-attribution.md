# ADR-0008: Runtime-permission touch attribution on Android 11

- Status: accepted by lead/owner on 2026-10-10; implemented, awaiting review/phone validation
- Date: 2026-10-10
- Deciders: lead/owner, round-1 implementer and reviewer
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

## Accepted decision

Obtain the planned runtime prerequisites before starting the action
loop, following the existing All files access preflight pattern. Human permission
interaction then happens outside an active approved action. Approval still occurs
once for the unchanged plan; no sending, typing or other execution happens before
approval. Runtime checks remain mandatory. If access is revoked after preflight,
hand back with clear Needs-you text and require explicit restart/reapproval; never
clear takeover or auto-resume.

The lead approved this replacement for the requested mid-run flow in the card
comment on 2026-10-10. The shared registry inventory lists runtime prerequisites in
the preview; missing access uses an Allow now button before Android's dialog.
Granted permissions are checked, not requested again; Android remembers grants.
No actions execute until the unchanged plan reaches its existing approval boundary.

The inventory covers named Contacts lookup, sharing Location, current-location
weather, Camera for flashlight, direct Calendar insertion and supported phone calls.
Literal numbers/email addresses, supplied weather cities and Calendar drafts avoid
unnecessary lookup/location/calendar requests. All files Settings preflight remains.

Stop cancels the pending request and invalidates preparation; stale callbacks cannot
approve/start that preparation. The activity requester refuses new missing grants
during a run. The foreground service uses a check-only requester: revocation stops
the step with Needs-you copy requiring Stop and explicit restart/reapproval.
No system permission dialog or Settings is launched from an active run.

The attempted permission-controller touch exemption is removed entirely. All
unknown/redacted/system-window touches retain the ordinary takeover path. EQO never
presses Allow or clears a takeover latch in response to the permission result.

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

- The card can proceed to implementation review using the accepted pre-run flow.
- The 120-second wait, Stop handling and stale request-code isolation remain.
- Fake/Robolectric tests cover inventory/deduplication, no read/send before approval,
  actual task-screen preview/Allow-now, Stop/late grant, explicit retry reaching
  approval without execution, active-run refusal, and stale/timeout behavior.
- Source regressions assert absence of the permission-touch bypass. No reliable
  cross-UID attribution or same-step mid-run permission continuation is claimed.
- The independent Keep planner regression is fixed and host-tested separately.
- NOT TESTED ON PHONE: actual OEM prompts, delayed input/lifecycle ordering, denial,
  Settings return and mid-run revocation require lead-owned phone testing and CI.
