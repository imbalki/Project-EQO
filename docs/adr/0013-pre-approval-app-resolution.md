# ADR 0013: Resolve app names locally before approval

Status: Accepted for Phase One; device verification pending.

## Context

On the owner's Android 11 phone, `Google Keep` failed because the launcher label
was `Keep notes`; missing-app preflight silently proposed Chrome. Exact labels
are not how users name apps. Contacts are explicitly out of scope.

Standing product rule: EQO must never fail just because it cannot find something
the user named. Find it fuzzily, offer a short choice ("Is this the app you mean?"),
and remember the answer for next time. A missing installation still needs a plain
reason and the existing store/browser alternatives, not an invented success.

## Decision

- One local launcher inventory feeds both the planner's untrusted data hints and
  resolution. Query launchable apps only; deduplicate packages and omit apps that
  have no launch intent. No installed-app telemetry or app-name logging.
- Normalize Unicode letters/numbers, case and punctuation, leading `google `,
  trailing ` app` and ` notes`. AliasResolver packages rank first (120), then
  exact (100), prefix (90), word contains (80), edit distance <=2 (70), token
  overlap (40). Distance matching requires at least three query characters.
  An explicitly named installed package is authoritative (120); suffix cleanup
  must not turn an exact package into an ambiguous prefix of another package.
- Auto-select only score >=70 with a runner-up gap >=15. Otherwise show at most
  five ranked launcher apps, with live icon, name and package disambiguation, on
  the task screen. None of these invokes the existing Chrome replan and approval
  dialog with its explicit Play Store search. If Chrome is absent or the replan
  fails/is unusable, show store search independently, with no runnable/approvable
  browser plan. This recovery is only for an explicit chooser rejection; zero
  candidates retain the existing missing-app flow. Cancel/back cancels preparation
  or recovery without opening a store or executing a task. Coroutine cancellation
  propagates instead of being turned into recovery.
  Chooser content rejects obscured touches. New choices are persisted only after
  all app choices finish, so cancelling a later chooser discards earlier choices.
- Resolution occurs before approval and before an approved execution snapshot is
  created. Both `open_app` and `OPEN_APP` preserve the installed label in preview
  and carry a local-only `packageName` pin. Model parsing still rejects that extra
  parameter. ApprovedTaskPlan validates its syntax and hashes the complete pin.
- Execution uses the pinned package only. Uninstallation does not redirect an
  approved request to a similarly named app. Report a plain failure instead.
- Persist only the selected package, keyed by SHA-256 of the normalized name in
  app-private preferences. No raw app name, request, recipient or timestamp is
  stored. Hashes are not encryption and may be dictionary-tested; the intent is
  data minimization, not a claim of anonymous storage. Choice memory is local,
  never supplied to the model. The setup hub exposes Apps EQO remembers, with
  live labels/packages and per-entry deletion. Missing remembered packages do not
  auto-launch something else merely because memory existed.

## Consequences

Weak and ambiguous matches require a person; no extra model call is needed for
matching. Installed labels/packages are included in the existing opt-in planning
request as untrusted data; this is not a new background inventory upload. The
existing absent-app browser fallback still requires Chrome and a new approval. Store
search after None of these does not require Chrome or a successful model response. The fallback
checks the actual Chrome package, not merely a similar app label. New UI and
Android 11 package visibility/icon behavior require phone verification. Memory
cannot display the original phrase because it deliberately was not retained.

## Update: when EQO may ask which app

EQO asks "which app?" only in two cases: (a) it cannot find the requested app on
the phone, or (b) several installed apps fit a role the person named generically
(for example several browsers for "open the browser"). It never asks when the app
is clearly identified:

- an exact label ("open WhatsApp"), including when another app's label only
  starts with the same words ("WhatsApp" wins over "WhatsApp Business"; two apps
  with one identical label still ask);
- a near-typo of exactly one installed label (`Whatsap`, `watsapp`);
- a known alias (`Google Keep`, `Keep notes` for Keep, `Google Chrome`, `Gmail`);
- a unique prefix (`calc` for Calculator).

Mechanics:

- `InstalledAppResolver` (core-agent, pure, no Android types) maps a typed or
  model-returned name to one installed label: literal label, normalized label,
  unique prefix, unique whole word, then a unique label within two edits (query
  of at least three characters). Two or more fitting labels, or a generic role
  word (browser, mail, music, notes and similar) without an exact label, return
  null so the existing clarification asks. A name claiming a publisher
  (`Google Foo`) is never stripped to match a look-alike: only a literal label
  or a known alias counts, so the publisher guard in `LaunchableAppResolver.resolve`
  is unchanged.
- Stage one uses it for requests of the form `open|launch|start <name>` without
  a model call, and snaps a near-miss `app` in the model's answer to the
  installed label instead of rejecting it. An app that resolves to nothing is
  still rejected.
- Pre-approval resolution: an exact label (score 100) or alias (120) wins
  outright when no other candidate is also exact; otherwise the existing
  score >= 70 and gap >= 15 rule applies.
- The choice dialog follows a picked row with "This time" or "Always" (plus
  Cancel). "This time" pins the app for this plan only and stores nothing.
  "Always" stores the answer in `RememberedApps`, so it is never asked again;
  "Apps EQO remembers" in setup lists and clears these. The "None of these" and
  store-search behaviour is unchanged.

Not covered yet: a clarification the model itself asks at stage one (for example
"which browser?") is still a plain answer sheet without "Always".
