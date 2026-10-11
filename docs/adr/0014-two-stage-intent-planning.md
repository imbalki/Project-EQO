# ADR-0014: Understand intent before planning

Status: Accepted for intent-understanding v1 (owner direction, 2026-10-11).
Scope: Android task planning; no executor, runtime dependency or fixed-protection change.

## Context

Request-keyword guards confused note creation with communication drafts and sometimes
reduced a multi-step task to opening an app. The owner requires dynamic understanding
of the requested action and app, with clarification before planning when unsure.
Installed-app labels are available through LaunchableAppResolver. Existing approved
execution already supports Keep's speed dial, note fields and Back autosave.

## Decision

1. IntentUnderstanding uses the existing configured provider at temperature zero with
   a small instruction-only prompt, JSON response and 600-token output cap. Its input
   is the request, cached installed launcher labels and six skill names; no screenshot,
   executor catalog or screen context is included. Requests and answers are ephemeral.
2. TaskIntent contains goal, installed label or null, a fixed action kind, string-valued
   params, channel, send=yes/no/unknown, confidence, question and up to four labels.
   Telegram is retained alongside SMS/WhatsApp/email for existing supported requests.
   Plain messages default to SMS. Only the user's instruction determines send/channel;
   quoted message/note content is never an instruction to send or change channel.
3. IntentPlanningSession asks before Stage two when confidence is below 0.7, slots are
   missing, or the model identifies alternatives. A protected in-app choice sheet
   includes an editable answer and Cancel. Each answer is appended to the request and
   understanding runs again. At most two answers; remaining uncertainty is disclosed
   as a best guess in the preview. Missing slots still must not be invented by planning.
   Cancellation, Stop, leaving the task or a stale generation cannot start a run.
4. TaskPlanner.plan is Stage two, explicitly accepting the understood TaskIntent.
   Production typed requests enter through the session; its legacy/default-intent
   entry remains for existing Stage-two tests and constrained callers, not as a
   substitute for production understanding.
5. IntentSkills fills validated templates from slots for Keep note creation, direct
   CALCULATE, SMS/WhatsApp/email/Telegram messaging, telephone/WhatsApp calls, app
   opening and search. Missing slots, unavailable executors and unknown note apps
   fall back to the existing registry planner with the structured intent and app hints.
   Keep uses Create a note > id:new_note_button > id:editable_title and/or
   id:edit_note_text > PRESS_BACK; title-only plans do not invent a body.
6. Remove request-keyword draft/note/channel regex guards. IntentPlanProtection runs
   after planning, checks structured channel and send=no, and refuses Send/submit
   controls or outward send executors. SMS/email's existing native draft executors are
   explicitly draft-only; WhatsApp drafts require draftOnly=true. The schema and intent
   protection share one model repair budget. A second invalid plan is refused.
7. Preview begins with "I understood" and the goal, plus best-guess disclosure when
   necessary. Execution still uses the immutable approval snapshot. Existing auto-mode
   opt-in, password-field refusal, untappable EQO windows, takeover/Pause/Stop and
   user-only resume are unchanged; clarification is never execution approval.
8. Exact "open <installed label>" commands and a few established aliases bypass the
   understanding call, not preview/approval. Added instructions invalidate the shortcut.
   Launcher inventory is cached within the resolver/activity lifetime, never persisted
   or logged; recreation refreshes it. Diagnostics contain intent kinds/confidence
   buckets and a fixed repair marker, never requests, answers, slots or validator text.

## Consequences and limits

## Owner addendum: answers and chaining (build a465525 finding)

- PlainArithmetic shares bounded decimal evaluation between preview and CALCULATE.
  One operation, signed decimal numbers, times/plus/minus/divided by/percent of and
  multiplication/division glyphs are supported. Complete plain arithmetic requests
  can skip inference; stripping never discards an additional send/app instruction.
  The preview already names the literal answer. Compound requests retain Stage one
  and free planning; local_arithmetic supplies the literal result for messages.
- Successful run output has a dedicated visible Result line, independent of the
  receipt's completed badge. Voice-origin drafts opt into device-language TTS readout
  of successful messages, deduplicated by step ID across UI redraws/recreation in
  the live process. Stop/new run clears queued speech; destruction shuts TTS down.
  Typed requests and failed/unknown steps never auto-read as successful answers.
  Speech requires the task UI to remain attached/alive, and an available device TTS
  engine/language; its network synthesis policy is the same as existing Explain.
- Later payloads may contain {{step:1:result}} or {{step:1:message}}. Preview names
  the result of step N rather than showing a raw token. Only backward references in
  message/body/subject/typing/query/expression payload fields are accepted; recipients,
  app names and control targets stay fixed. The approved symbolic snapshot remains
  unchanged; its executor substitutes values only after verifying that original
  step is approved. Result strings are untrusted data, never recursively expanded.
  Missing/failed/oversized output refuses dispatch. Results are process-local to a
  single executor; no cross-run/retry persistence or dynamic recipient permission.
- Existing calculate-then-send messages containing literal answers are unchanged.

The normal known-skill path costs one small model call, not two. Free planning costs
an additional call and possibly one repair. Clarification may add two understanding
calls; provider/network failure cancels the attempt rather than restarting the entire
session and silently resetting its question budget.

The configured provider receives installed app labels with the request. This is not
local inference; existing BYOK/model setup applies. No new storage or analytics is added.
The cached app snapshot can become stale after install/uninstall until activity recreation.
Executor capabilities are unchanged: SMS/email remain user-sent drafts, and CALCULATE
retains the existing local evaluator's supported arithmetic and failure limitations.

The 125 paraphrase regressions use deliberately supplied fake model outputs. They verify
transport, contracts, slot preservation and skill selection, not semantic accuracy of any
live model. Phone/live-provider accuracy, multilingual quality, dialog layout and real
Keep autosave require the owner checklist in PHASE-ONE-TEST-LOG.md. NOT TESTED ON PHONE.
