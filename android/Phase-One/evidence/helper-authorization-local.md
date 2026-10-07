# Helper authorization follow-up to PR #77 — local verification

Date: 2026-10-07. Board card: `t_eb1610ca`.

Branch: `helper-auth-local`. Base: `667f0bb6c74ca51fc12106ab17c7b0c139ec3077` (main already contains PR #77 and batch 2). Per the lead's card update: local commit only, no push, no `gh`, no draft PR or merge performed by this worker. The lead owns publication, CI and independent security review.

## Implementation

- After a ready helper Binder arrives, authorization reads actual server permission, calls `Shizuku.requestPermission(code)` when needed, and waits up to 55 seconds for the matching result.
- The embedded server uses the existing attached-application Binder callback to ask the foreground wireless setup screen to show “Allow EQO to use its helper?” / Allow / Don't allow. The existing confirmation touch guard protects both buttons and the entire dialog. No exported activity or broadcast is added.
- Server grants only the calling EQO process's client record after exact own UID/PID/code, pending expiry, allowlisted package and unshared-UID checks. No arbitrary target grants, persistent grants, runtime-permission grants or automatic manager execution grants remain in this path.
- Deny/back, timeout, interruption and leaving the screen fail closed. Cancellation and grant/revoke use the same server lock; close queues revocation on the shared background consent transport after request/reply, wakes the waiter and dismisses the dialog. The authorization worker awaits cleanup acknowledgement; UI close never waits for transport. Client death clears pending consent. New client attachments do not restore donor config grants.
- Binder health runs after authorization and verifies permission is still granted as well as Binder liveness. The setup hub appends the validated failing check name to existing resource-backed guidance.
- Four new Kotlin files have provenance rows. The three new Java files (`EqoPermissionRequests.java`, `EqoPermissionRequestsTest.java`, `HelperConsentBinderTest.java`) are EQO-authored consent-state logic and tests, not donor copies. Modified donor files retain their origin headers.

## Verified host checks — initial implementation, superseded by review correction below

Run from `android/`:

    bash gradlew :app:ktlintCheck :helper-client:ktlintCheck :helper-server:ktlintCheck :app:detekt :helper-client:detekt :helper-server:detekt :app:testDebugUnitTest :helper-client:testDebugUnitTest :helper-server:testDebugUnitTest --console=plain

Result: exit 0, `BUILD SUCCESSFUL in 2m 9s`; 202 actionable tasks, 9 executed and 193 up-to-date. Helper-client's unit-test task is `NO-SOURCE`; helper-server's unchanged final test inputs were `UP-TO-DATE` after an earlier real successful execution.

    bash gradlew :app:lint :helper-client:lint :helper-server:lint --console=plain

Result: exit 0, `BUILD SUCCESSFUL in 2m 40s`; 328 actionable tasks, 10 executed and 318 up-to-date. No lint checks were disabled. An intermediate invocation hit an internal FIR/ClassCastException in lint; subsequent invocations passed without suppression or dependency changes.

Run from repository root:

    git diff --cached --check
    bash scripts/check-branding.sh

Both exit 0. Branding result: `BRANDING GATE PASSED`; 408 tracked Kotlin files and 408 provenance rows, including the staged new files.

Parsed Gradle JUnit XML (not estimates):

| Module | Suites | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| app | 27 | 128 | 0 | 0 | 0 |
| helper-client | 0 | 0 | 0 | 0 | 0 |
| helper-server | 3 | 11 | 0 | 0 | 0 |
| Total | 30 | 139 | 0 | 0 | 0 |

20 added tests:

- `HelperAuthorizationTest`: 6 tests — explicit grant plus permission readback, typed denial, typed timeout, preserved interruption, existing-session reconnect, and activation-sequence binder-health execution versus deny/timeout.
- `HelperPermissionPromptTest`: 7 Robolectric tests — actual callback-to-dialog routing and Allow, Don't allow, back/closed-screen denial, stale permission cache, revoked permission at binder health, obscured/poisoned gestures, and cancellation before UI queue dismissal. Binder transport is a host fake; dialog/touch-guard dispatch uses real framework-shaped host objects.
- `EqoPermissionRequestsTest`: 6 pure JVM tests — exact own process matching, UID/PID/code mismatch, one-use/replay, timeout, concurrent replacement rejection, cancellation and ordered-denial cleanup. The original queue model is not device Binder evidence; see the review correction below for production-handler/transport regressions.
- Existing `SetupHubActivityTest`: one new failing-check-label regression.

## Trust assumptions for the draft PR

See `docs/adr/0006-process-scoped-helper-consent.md` for the full decision and limits. Carry these into the PR body:

1. Android Binder authenticates UID/PID; PackageManager, signature-checked installs/updates and app-private storage are trusted. Shared UIDs are rejected for consent.
2. EQO's own process and bundled shell/root helper are trusted. This does not defend against compromised EQO, root/shell, kernel or a hostile replacement APK after uninstall. The server validates a live matching request, not a cryptographic proof of a human tap.
3. The live helper/framework Binder calls make progress normally. Human-response waiting is bounded; synchronous request/result/revoke delivery runs on a shared background queue and server-to-app callbacks are oneway. Transport, permission reads and revoke acknowledgement are not hard-real-time guarantees against a wedged OS.
4. Existing touch policy rejects touch-point obscuration and poisons that gesture; partial obscuration elsewhere remains accepted under the established OEM policy. Platform input/accessibility delivery is trusted.
5. Bundled client and server must match; start a fresh helper after installing this APK because service request/result wire calls now use synchronous semantics.

## Not tested / lead handoff

No phone, emulator or ADB command was used. Still required: Realme Android 11 full PAIR → CONNECT → HELPER_START → AUTHORIZE → BINDER_HEALTH run; actual dialog visibility/focus with the OEM keyboard; Allow versus Don't allow/back and timeout; actual overlay flags; background/rotation cancellation and immediate retry; app/helper restart and fresh consent; cross-UID/replay rejection over real Binder; confirmation that privileged operations remain denied after cancellation. Use a newly started matching helper, not an old process still executing the prior APK.

No GitHub CI was run or claimed green. The lead must push, open a draft PR with these trust assumptions, run CI and arrange independent security/device review; never merge this branch directly.

## Review correction: authenticated synchronous consent transport

Round-1 review correctly rejected `86189424adfc50fc3f2441796cdfad0b2a4b9eff`: remote oneway service calls receive calling PID 0, so the original PID-bound request and result handlers could never authorize on a real device. Initial host passes above did not validate that transport contract.

Corrections:

- Service `requestPermission` and `dispatchPermissionConfirmationResult` now use synchronous AIDL; generated Proxy/Stub calls preserve authenticated calling PID. Both handlers reject nonpositive PIDs, and the server rejects unexpected oneway consent transaction flags before dispatch. Supplied PID is still matched against actual Binder identity; no identity check was weakened.
- Request, reply and revoke execute on one shared background queue, never the UI thread. Close queues revoke after delayed request/reply and before a new prompt's immediate retry. UI lifecycle close wakes the waiter and returns without transport blocking; the authorization worker awaits the same cleanup acknowledgement, even after an earlier lifecycle close and despite interruption.
- Orphaned prompt callbacks do not issue synchronous confirmation calls on main. They cannot grant; queued owner cleanup or server expiry fails closed.
- Production service handlers are tested with generated AIDL Proxy/Stub parcel dispatch. Test-only constructor skips OS helper startup; package lookup is injected, and Robolectric Binder identity models the remote oneway PID=0 rule explicitly. This is not a claim of kernel Binder/device validation.
- 12 additional tests in this correction: 9 `HelperConsentBinderTest` tests (grant and flags; deny/retry; cancel/retry/replay/revoke; PID=0; unexpected oneway transport; cross-UID/unattached caller; expiry/retry; attached sibling process; shared UID), plus 3 prompt tests (delayed request followed by lifecycle revoke; worker waits for lifecycle cleanup; immediate new-prompt retry). Prompt tests assert request/reply/revoke run off main and drain main click/cancel callbacks before the background transport and result callback; no test was suppressed.

Final touched-module invocation:

    bash gradlew :app:testDebugUnitTest :helper-client:testDebugUnitTest :helper-server:testDebugUnitTest :app:ktlintCheck :helper-client:ktlintCheck :helper-server:ktlintCheck :app:detekt :helper-client:detekt :helper-server:detekt :app:lint :helper-client:lint :helper-server:lint --console=plain

Exit 0, `BUILD SUCCESSFUL in 7m 45s`; 369 actionable tasks, 35 executed, 334 up-to-date. All touched-module ktlint, detekt, unit tests and full lint passed. Helper-client unit tests are `NO-SOURCE` (its generated transport is covered in helper-server). Raw log: `helper-auth-revision-verified.log` in the task attachments.

Parsed JUnit XML on the corrected source:

| Module | Suites | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| app | 27 | 131 | 0 | 0 | 0 |
| helper-client | 0 | 0 | 0 | 0 | 0 |
| helper-server | 4 | 20 | 0 | 0 | 0 |
| Total | 31 | 151 | 0 | 0 | 0 |

Branding and staged diff whitespace checks both pass: 408 Kotlin paths / 408 provenance rows. No new Kotlin file was added in the correction; the new Java test is EQO-authored.

Forced repeat on the same corrected source:

    bash gradlew :app:testDebugUnitTest --rerun :helper-server:testDebugUnitTest --rerun --console=plain

Exit 0, `BUILD SUCCESSFUL in 3m 38s`; 176 actionable tasks, 2 executed, 174 up-to-date. Both test tasks actually re-executed. Fresh XML again reports 31 suites / 151 tests / zero failures, errors or skips. Raw log: `helper-auth-revision-repeat-tests.log` in task attachments.

Hotspot: `SetupHubActivity.kt` and its companion test/resources overlap the UX styling lane. The change is limited to the failing wireless-check label and its regression; reconcile that small hunk when integrating.
