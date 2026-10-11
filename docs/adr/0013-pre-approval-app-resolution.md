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
- Auto-select only score >=70 with a runner-up gap >=15. Otherwise show at most
  five ranked launcher apps, with live icon, name and package disambiguation, on
  the task screen. None of these invokes the existing Chrome replan and approval
  dialog with its explicit Play Store search. Cancel/back cancels preparation.
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
existing absent-app fallback still requires Chrome and a new approval. New UI and
Android 11 package visibility/icon behavior require phone verification. Memory
cannot display the original phrase because it deliberately was not retained.
