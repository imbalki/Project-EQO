# WhatsApp calls (t_d3c58c41)

Base: main 667f0bb. Local branch: w-wacall-local. No push or GitHub operations.

## Behaviour

- One enabled `WHATSAPP_CALL(contact, video=false)` action, defined in the existing ActionSchema and registered through CommunicationActions / AndroidActionRegistry. Enabled count increases from 94 to 95.
- Resolves numbers and names using SEND_WHATSAPP's contact-resolution path. READ_CONTACTS is requested only for names; ambiguous names require a picker, preserving the video parameter. Missing contacts require input, not a guessed recipient.
- SEND_WHATSAPP and WHATSAPP_CALL share a package-targeted ACTION_VIEW chat opener. Calls use `https://api.whatsapp.com/send?phone=...` without any text payload, typing or Send action. A resolved number is required for calls; no browser fallback.
- After a 3-second chat-settle delay, uses the takeover-gated accessibility facade to click an exact content description in the active com.whatsapp window: Voice call, or Call only when Voice call is absent; explicit video uses Video call only. Text/substring matches are not accepted. Protected windows, disabled accessibility and takeover refuse.
- A rejected click stops immediately; no second click or automatic re-execution. A missing voice control does not silently start video: the result asks the owner to finish manually. Successful receipts say only that the voice/video call control was pressed, not that ringing or connection was verified.
- The planner is told to use one WHATSAPP_CALL, not OPEN_APP/search/OPEN_URL/CLICK_TEXT. The preview states the recipient and voice/video mode and warns that it rings a real person.
- WHATSAPP_CALL requires plan approval even when the generic preview-approval preference is disabled, and is marked neverAutoApprove. Parsed registry steps are irreversible; an ActionLoop fake verifies that even a transient ambiguous failure is attempted only once.

## Tests and intentional assertion changes

Fake-tree/Robolectric tests cover opening from other-app/home/last-chat states, no message payload, exact voice fallback, explicit video, missing controls, rejected clicks, wrong foreground package, protected windows, takeover/service loss, invalid parameters, contacts denial, resolved names and ambiguous/missing contacts. No real call is made by these tests.

Existing enabled-count assertions deliberately change 94 to 95. NeverAutoApproveTest's exact set deliberately adds WHATSAPP_CALL while retaining the original twelve flagged actions and grantable messaging assertions. ForegroundPlanRunTest adds the call-specific override without changing other actions' approval preference.

Two new Kotlin files have provenance rows. Existing generic tap matching and SEND_WHATSAPP's draft/send flow remain in place; the shared numeric URI check uses the correctly escaped optional plus pattern.

## Local verification

Verification is incomplete; do not publish this branch as gate-green yet.

- Branding/provenance: passed (406 tracked Kotlin files, 406 provenance rows).
- All five touched modules' ktlintCheck and lint completed successfully in the first comprehensive run. platform-a11y, core-llm and app detekt passed. actions-android detekt passed on the corrected code in the later rerun.
- Unit results: WhatsAppCallTest 8/8 passed; platform-a11y 95 tests, no failures; core-llm 271 tests, no failures, 1 skipped; app 115 tests, no failures (including mandatory call-plan approval). actions-android: 79 tests, 1 failure in the unchanged macro test `delete and list macros keep system macros and sort names` (10-second UncompletedCoroutinesError).
- core-agent no-retry test's initial lambda signature was corrected after its compile failure. The preview's initial detekt complexity issue was corrected by selecting the call-specific preview outside the legacy describe function. Final core-agent ktlintCheck/detekt/unit-test/lint validation is still required, along with the full actions-android unit-suite retry.
- The shared host ran out of native JVM memory: `android/build/whatsapp-call-logs/hs_err_pid29472.log` records insufficient memory / a failed 922746880-byte mmap. Initial new-code line-length and complexity findings were fixed without suppressions, baseline changes or test weakening. Subsequent warm reruns and a 768 MB, single-worker, in-process Kotlin attempt did not finish before the task runtime budget, so the task is blocked for verification rather than marked complete.
- Raw local logs are retained under `android/build/whatsapp-call-logs/` (ignored build output, not committed).

Resume inside this worktree after reducing concurrent builds:

    cd android
    bash gradlew :core-agent:ktlintCheck :core-agent:detekt :core-agent:testDebugUnitTest :actions-android:testDebugUnitTest :core-agent:lint --continue --max-workers=1 --no-parallel --no-daemon '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=512m' -Pkotlin.compiler.execution.strategy=in-process --console=plain

Then rerun the complete touched-module gates on the final commit and branding; do not change test timeouts just to accommodate host pressure.

## Lead phone checks (not performed here)

On Realme RM10 / Android 11, approve one call to an owner-selected consenting recipient from: another app, WhatsApp home, and a different last-open chat. Verify that the intended recipient appears before/when the call starts and that the voice control rings the intended person. Separately approve an explicit video call if wanted. Check contact-name lookup/disambiguation, WhatsApp permission/confirmation prompts, cold-start timing, network/offline behaviour and Pause/takeover. Local fakes cannot establish WhatsApp's real navigation, accessible labels, permissions or ringing/connection. Never automatically retry an uncertain call result.
