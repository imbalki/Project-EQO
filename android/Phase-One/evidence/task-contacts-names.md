# Contacts names: local implementation evidence

Task: t_212ea16c. Branch: w-contacts-local, base main 667f0bb.

## Behavior

- SEND_SMS, SEND_WHATSAPP, SEND_TELEGRAM and MAKE_CALL resolve a saved name to a phone destination. SEND_EMAIL resolves the `to` name against Contacts' Email rows, not Phone rows.
- Telegram handles are explicit (`@name`). A single word such as Balan is a contact name, and a missing contact is never converted to a guessed Telegram handle.
- Multiple matching names or multiple destinations under one name stop with a plain names-only clarification message. No first-row/exact-match preference, contact-memory preference, number-bearing picker metadata, or five-match truncation remains. Duplicate identical name/destination rows are harmless.
- Contacts permission denial, including a SecurityException if Android revokes permission during a query, is a distinct result: Contacts permission is needed and nothing was done. Literal phone numbers/email addresses and explicit Telegram handles do not request Contacts permission.
- The typed-request and debug-plan paths resolve recipients locally before showing the plan. The owner sees the resolved name and actual destination. The immutable ApprovedTaskPlan receives the literal destination, so a later Contacts change cannot silently change the approved recipient. Resolved names are display-only local labels, not extra planner parameters, and never get sent back to the model.
- Removed contact-resolution logging, exception-message logging from communication helpers, and debug preview-text logging. Permission/action/result codes remain usable for diagnosis without contact data.
- Updated task-screen and home copy, and the registry planner's name/number/email/no-invention hint. The legacy lower-case study-only planner still describes its genuine draft-only limitations.

## Tests

New fake Contacts provider tests exercise phone/email queries, ambiguity (including exact versus partial and same-name endpoints), duplicate handling, denied/revoked permissions, literal input, wildcard escaping, and log privacy.

New fake-resolver registry tests exercise all five executors' launched destinations, names-only ambiguity failures, denied permission with zero lookup/launch, missing Telegram names, literal/handle bypass, pre-approval resolution, and immutable destination execution after the fake Contacts data changes.

NaturalTaskFlowTest now expects the corrected Contacts copy rather than the old intentional `cannot read contacts` assertion. Its reflective showPlan call was adapted to the added display-label argument without removing its format-character safety assertions. Added an actual approval-dialog name/number and debug-log privacy regression. RegistryPlannerTest additionally pins the contact-name/number/email/no-invention hints.

The last MAKE_VIDEO_CALL legacy first-match helper was also routed through the same fail-closed resolver so removing the unsafe legacy resolver does not leave a bypass. This is not a new action.

## Local gates

Verified against implementation commit e1aea7395e8ec05f7f63a7f5b398faa1d08f260d:

- `:core-agent:ktlintCheck`, `:actions-android:ktlintCheck`, `:app:ktlintCheck`: PASS.
- `:core-agent:detekt`, `:actions-android:detekt`, `:app:detekt`: PASS after fixing initial code/style errors; no baseline or rule changes.
- `:core-agent:lint`, `:actions-android:lint`: PASS. App lint was still running when the two-hour run limit approached; do not treat it as passed without its final report.
- `:core-agent:testDebugUnitTest`: 90 tests, zero failures/errors/skips. All seven ContactResolverTest cases passed.
- `:app:testDebugUnitTest`: 115 tests, zero failures/errors/skips, including all eight NaturalTaskFlowTest cases.
- `:actions-android:testDebugUnitTest`: 78 tests, one failure, zero errors/skips. All seven ContactRecipientsTest cases passed. The unchanged `AutomationExecutorsTest` case `delete and list macros keep system macros and sort names` hit its existing explicit 10-second runTest deadline (UncompletedCoroutinesError). It needs an isolated rerun; the test was not weakened or edited.
- `bash scripts/check-branding.sh`: PASS, 406 tracked Kotlin files / 406 provenance rows.
- `bash scripts/check.sh` and `git diff --check`: PASS.

The host ran several sibling Gradle builds concurrently; available memory was measured as low as 0.15 GB. This worker used one Gradle worker, a 768 MB heap and in-process Kotlin compilation for the final runs. Memory contention is a plausible cause of the macro timeout, not a proven diagnosis. This handoff is NOT an all-green verification claim.

Local command transcripts: `android/contacts-gates-final.log` (app tests/lint), `android/contacts-recheck.log` (final core/actions checks/tests). Initial detekt and test-fixture compilation errors in the first transcript were fixed before the recheck. These local logs are not committed.

## Phone checks left to the lead

No phone was accessed, installed, called or messaged by this worker. On Realme RM10 / Android 11, verify:

1. With Contacts permission absent, plan `text Balan`, allow permission, check the full resolved name and number in the preview, then explicitly approve. Verify the actual SMS app recipient and send/draft outcome.
2. Repeat saved-name requests for WhatsApp, Telegram, MAKE_CALL, and SEND_EMAIL (a contact with an email address). Verify each app/call recipient and actual outcome; intent launch is not a delivery receipt.
3. Deny Contacts permission and confirm the plain failure and no app launch/call/send. Literal phone/email plans must still work without Contacts access.
4. Use two matching contacts and a same-name contact with two numbers/emails. Confirm names-only clarification, no preview approval/run, and no side effect. Retry with a full name or literal destination.
5. After preview, change the contact's destination externally; execution must keep the number/address already shown and approved.
6. Check permission-dialog lifecycle/takeover behavior and installed-app handling on the real device; these are not proven by Robolectric.

## Integration hotspot

TaskActivity's makePlan/showPlan/debug-plan seam is shared with sibling UI/run-status work. Preserve prepareAndShowPlan and removal of preview-text logging when combining branches. This worker commits locally only; the lead owns push, PR, review and phone acceptance.
