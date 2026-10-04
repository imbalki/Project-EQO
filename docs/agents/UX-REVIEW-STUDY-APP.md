# Study app UX review (Refs #20)

Code-only review; no phone, screenshot or device accessibility verification is claimed.
Base: origin/agent/android/63-setup-hub-ui (4acbe4a).
Read android/Phase-One/docs/USER-FLOWS.md (the actual location, not docs/USER-FLOWS.md) and android/Phase-One/evidence/task-015-study-apk.md.

## Before / after implementation

- Home: four equal buttons, helper action first, no purpose or setup guidance; task-first navigation loses setup entry. Change: setup first, explain sample and build limits, hide helper diagnostic unless helper is active, keep its existing refusal gate.
- Setup: status words fixed by 63, but internal probe details still show binder/security jargon; no next action or highlighted row. Change: resource-backed per-row instructions, next-step banner, textual Next badge plus bold highlight, touch targets and keyboard focus.
- Model: provider cost/key explanation is incomplete, model id jargon, blank input guidance is erased, checking permits repeated submission. Change: plain instructions/cost disclosure, visible validation/empty/error guidance, disable repeated checking, return-to-setup affordance.
- Accessibility: external guide hard-coded strings, repair instructions shown before regular setup, no explicit return action. Change: resource-backed OS instructions, primary accessibility settings action first, repair path secondary, recheck feedback and return action.
- Wireless guide: security issue IDs and pinning jargon, anonymous initial rows, unused entry fields suggest working activation. Change: clear unavailable limitation, label each individual check at entry, disable unused fields while gated, explain that no setting changes are needed for unavailable features.
- Chrome consent: technical security wording, empty unanswered status and no return action. Change: plain build limitation, explicit decision prompt, persisted answer and return action. No consent or transport policy changes.
- Task: blank initial state, internal enum/action names, no setup entry, technical receipt labels and no explanation of sample. Change: setup entry, idle instructions, resource-backed labels, named step receipts and approval context. Keep approval, timeout, touch guard and explicit resume handlers unchanged.
- Notices: offline attribution retained; Close remains explicit; launcher uses the scrollable notices activity while legal deep link remains compatible.
- Shared layouts: content scrolls except home, wrap-content controls and clickable rows may miss 48dp. Change: scrollable home, full-width minimum 48dp controls, focusable rows, explicit native light/night themes with resource text colours and backgrounds (contrast calculated below; device rendering not measured), live status announcements. Text carries status rather than colour alone; no fixed text heights/single-line labels.

## Presentation decisions

Next step selects the first actionable not-ready row in display order. Checking and unavailable rows do not strand the user before later actionable rows. If only blocked/checking rows remain, the first not-ready row is highlighted with an honest limitation/wait instruction. All-ready says open the sample task. Readiness remains independently probed; presentation never changes a row's state.

## Owner phone checklist (pending)

Android 11+; repeat at 200% font size, light/dark theme, TalkBack and keyboard navigation:
1. Fresh home: Set up EQO first, sample secondary, no helper diagnostic; setup return from task.
2. Hub: missing/failed/checking/ready model, accessibility off/on, unavailable wireless/browser, helper inactive/dead/active; one Next row, each status spoken, repeated recheck feedback, no resource numbers or security jargon.
3. Model: blank inputs, checking, valid key, rejected key, rate limit, no credit, incompatible model, offline, secure-storage failure; Back returns to setup, no secret spoken/logged or restored draft claim.
4. Accessibility: Android 11/12 ordinary path and Android 13+ restricted-settings path; settings return, recheck, off/on state, Back and return button. OEM settings wording remains owner feedback.
5. Wireless: initial five named unavailable checks, disabled input fields, recheck does not pair/connect/start anything; no misleading successful status.
6. Browser: unanswered, accept, decline, relaunch restore; unavailable functions and no content transmission stated; Back/return.
7. Task: initial pending steps, run/progress, paused/takeover, resume dialog cancel/confirm, approval approve/reject/Back/expiry, obscured-touch rejection, stopped/failed/completed receipts and unknown results. Nothing resumes without confirmation; SMS remains draft-only.
8. Notices: launcher, hub, eqo://legal deep link; scroll long text, Close/Back return to origin, full attribution unchanged.

No new transport, helper execution, approval policy, automatic resume or takeover behavior is in scope. The study build still cannot finish every setup row; fixing blocked transport security is not UI work. Device contrast, OEM paths, touch sizing at large fonts, TalkBack order, task lifecycle and overlay behavior require owner verification.

## Verification

Toolchain observed: Temurin JDK 21.0.12.1+1-LTS (`jdk-21.0.12.101-hotspot`), Gradle 9.7.0 (`./gradlew --version`), AGP 9.3.1 and Kotlin 2.4.0 (version catalog). minSdk remains 30 / Android 11. No dependencies, analysis baselines or toolchain versions changed.

Host tests cover every setup row/state, independent next selection (unprobed, ready, failed, checking and gated), exactly one moving Next highlight, repeated recheck feedback, eight entry screens, minimum 48dp resource targets, hidden inactive helper diagnostic, task setup entry, task state/receipt wording, unknown/unrun steps, literal approval message preservation and explicit night-theme text/background. New text is in strings.xml; internal probe diagnostics remain in the readiness model but are not copied verbatim to the owner-facing UI. Buttons and fields have visible text/hints rather than redundant contentDescription; no new icon-only targets. Text labels and natural XML order supply TalkBack semantics; actual speech/order and 200% layout remain phone checks.

Colour contrast calculation (WCAG sRGB luminance, foreground against the resource background; not a screen measurement):
- Light #FFFFFF: primary #1B1B1B = 17.22:1, secondary #505050 = 8.06:1, accent #005AC1 = 6.50:1.
- Night #121212: primary #FFFFFF = 18.73:1, secondary #CCCCCC = 11.67:1, accent #AAC7FF = 10.99:1.
Status rows use words, a Next prefix and bold emphasis, not colour-only indications. Native disabled-state colours and actual widget backgrounds still require device review.

Safety diff against 4acbe4a: StudyFlowGate, StudySetup probes, StudyLoopWiring, StudyTaskController, ConfirmationTouchGuard, ProtectedConfirmationTouches and takeover implementation are unchanged. The only resume-confirmation mint remains the existing positive-button user-gesture handler. Both transport gates remain false. Task controls are unavailable when meaningless (for example Resume before a pause); this does not grant or bypass any controller permission. Helper diagnostic visibility reads helper readiness, but its original separate WirelessAdbActivation refusal gate is retained. No helper command is added or run.

Initial verification was red and corrected: the inherited exact activation-copy regression needed to test the new UI instruction while retaining the original gate literal assertion; first full gate found one now-unused activation_active string; targeted Detekt found three overlong lines. A final receipt-guidance test edit then exposed two more line-length findings, also fixed. No check was suppressed or relaxed. The first successful complete gate was at 8d400a7; the last copy/receipt refinement is re-verified by the exact complete command below.

Final code verified: dc965bfd6b883591e6a7bca7cb82ad3b1b1a06b1. Subsequent commit updates only this review evidence.

From android/, one Gradle invocation at a time:

```sh
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m --console=plain
# BUILD SUCCESSFUL in 9m 7s; exit 0
# 901 actionable tasks: 73 executed, 828 up-to-date
../scripts/check-branding.sh
# BRANDING GATE PASSED; exit 0; 317 tracked Kotlin files / 317 provenance rows
../scripts/check.sh
# secret scan, shell syntax and branding/provenance checks passed; OK; exit 0
```

All-module XML aggregation: **618 tests, 0 failures, 0 errors, 1 existing skipped**, **100 suites**. Per module: adb-pairing 82, app 62, browser-cdp 35, core-agent 62, core-llm 261 (the skip), core-security 47, helper-server 5, platform-a11y 64. SetupHubActivityTest: 2 passing; StudyUxPresentationTest: 6 passing. Unchanged safety regression suites: ConfirmationTouchGuardTest 7, StudyLoopWiringTest 11, TakeoverResumeUserOnlyTest 4, all passing. One explicit resume mint site was counted; both dispatch wrappers and all policy/transport/controller files listed above have an empty diff against the base.

Rebuilt artifacts (not installed): app/build/outputs/apk/debug/app-debug.apk, 58,915,998 bytes; app/build/outputs/apk/release/app-release-unsigned.apk, 50,858,741 bytes. Release remains unsigned. Existing compiler/Gradle deprecation warnings remain; no new lint baseline, analysis suppression or dependency change.

Full/targeted gate logs and shell-check logs are retained in the kanban task workspace. The first complete run outlived the terminal wait window; its exact wrapper exit was observed before retrying. The two final complete runs used tracked background processes with explicit exit-code retrieval, not concurrent Gradle builds. No screenshot or successful device run is implied by these host results.
