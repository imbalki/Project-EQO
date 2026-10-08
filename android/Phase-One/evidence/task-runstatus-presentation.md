# Run-status presentation (t_4fa4cbd6)

Base: `667f0bb`, local branch `w-runstatus-local`. No push or GitHub operation.

## Changes and boundaries

- Task-screen handoffs have a neutral `Needs you` presentation and a separate receipt list. Compose-only SMS/email drafts do not count as sent or as steps that never ran.
- The app preserves typed `UserActionRequired`, `PendingUserAction` and `NeedsInput` metadata for presentation. A contact question does not falsely claim a draft opened.
- Executor results, loop terminal values, retry rules, approval, permission grants, sending and takeover behaviour are unchanged. A registry handoff still ends the loop; subsequent steps do not run.
- Permission dialogs/settings instructions show `Needs you` while waiting. Denied access reports the missing permission/access and does not continue.
- Known accessibility, app-not-installed and missing-button/field failures use plain sentences and the planned label (including registry `searchText` / `appName`). Unknown failures explicitly say no specific reason was received, rather than blaming setup.
- Model-key missing, invalid key, network unreachable and timeout have distinct text. The only core-llm change is an additive non-secret timeout hint; timeout stays `Network` with the existing retry policy.
- `EqoRun` no longer includes arbitrary executor reason strings, rejected-plan exception messages or full debug plan text. Step diagnostics contain allowlisted codes only.
- New Kotlin files have provenance rows.

## Tests and gates

Final verification is incomplete: this worker's two-hour run limit was approaching while concurrent worktree Gradle builds made local gates unusually slow. The final rerun was terminated through its tracked process handle; no unrelated build/process was stopped. This task must not be treated as approved or complete until the commands below pass on the committed code.

Observed results:

- Branding/provenance: PASS (406 tracked Kotlin files / 406 provenance rows).
- `git diff --check`: PASS.
- First full gate attempt: app ran 123 tests, with one failure only in the new controller fixture (required SEND_SMS parameters missing). The fixture was corrected; existing tests and all mapping tests passed at that snapshot.
- First full gate attempt: core-llm lint and tests completed successfully. A detekt constructor-signature finding was subsequently fixed by preserving the original constructor and adding an internally set timeout property instead.
- Final rerun before stopping: both modules' ktlint and core-llm detekt passed. App detekt found the new combined reason test at the method-length boundary; it was split without deleting assertions. The fixed split has not yet been rechecked by detekt.
- Android lint's new receipt-concatenation finding was corrected with a resource format. The final lint/test graph had not finished, so final-code lint/unit-test results remain UNVERIFIED.
- No test or lint baseline was changed. No phone tests were run.

Commands to finish verification (format separately from checks to avoid racing formatter/static analysis):

```
cd android
./gradlew :app:ktlintFormat
./gradlew :app:ktlintCheck :app:detekt :app:lint :app:test \
  :core-llm:ktlintCheck :core-llm:detekt :core-llm:lint :core-llm:test \
  --continue --max-workers=2 --console=plain
cd ..
bash scripts/check-branding.sh
```

`RunStatusMappingTest` covers neutral handoffs, no false draft claim for contact input, receipts, uncertainty/failure/stop preservation, named permission/app/field reasons, model categories and safe diagnostic codes. `StudyDispatchTest` exercises the controller through the real loop: registry handoff still returns the underlying FAILED terminal and never dispatches the next step, but its presentation is Needs you. `TaskPermissionRequesterTest` asserts the actual task-screen permission waiting text.

Intentional existing expectation update: `SampleRunRegressionTest` now expects DONE for its first two steps and NEEDS_YOU for compose_sms, while still requiring the original SMS intent, recipient, package, body, approval/obscuration checks and unchanged underlying COMPLETED terminal.

## Phone checks left for the lead

Not run on Realme RM10 / Android 11 in this worker. No phone settings or keys were accessed.

1. Open an SMS/email draft that requires manual Send. Return to EQO: header and step should say Needs you, not Failed; no subsequent step should run after a typed registry handoff. Nothing should be claimed as sent.
2. Ask for a contact-dependent step with Contacts permission missing. The Android prompt should be unchanged; EQO should say Needs you and name Contacts while waiting. Grant continues the existing flow; deny gives a named access reason and does not run the step.
3. Run with accessibility off/disconnected; ask to open uninstalled Meet; ask to click/type a nonexistent label. Each should give its specific plain reason and the requested app/label.
4. Test missing key, unreachable network and a slow/timed-out model separately. No key, request, draft content or arbitrary exception text should appear in EqoRun.
5. Check Pause/Stop/takeover and return-from-settings behaviour are unchanged, including never automatically resuming/replaying a handoff.
