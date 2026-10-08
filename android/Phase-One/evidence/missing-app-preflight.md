# Missing-app preflight: local implementation evidence

## Decision and boundaries

Implemented the operator's updated design, which supersedes the original three-choice dialog:

- An installed OPEN_APP target keeps its original proposal unchanged.
- Before approval, missing targets trigger one automatic Chrome replan with the original request and a missing-app constraint. The installed-app check uses only local PackageManager calls; no installed-app list, observation, or request text is logged.
- The replacement preview names the unavailable app(s) and explains that Chrome is used instead. Approval runs the exact replacement snapshot. With plan approval disabled, that snapshot starts directly and the notice remains in the screen preview.
- The secondary approval-dialog button opens only a Play Store search for the first missing target. If the market handler is unavailable, it opens the Play web search. It does not approve or execute either task proposal or install an app.
- Missing Chrome, another missing target in the replacement, or a replacement without OPEN_URL stops with plain text. There is no repeated fallback loop.

LaunchableAppResolver is shared by preflight and SystemActions OPEN_APP. Known aliases resolve only to their actual package. Exact label/package matches take precedence over a unique partial launcher match; blank, ambiguous and non-launchable matches are rejected. The existing MAIN/LAUNCHER manifest query provides Android 11 visibility without QUERY_ALL_PACKAGES.

OPEN_URL adds an optional browser=chrome enum. Replacement URL steps are pinned to Chrome before snapshot/preview, rather than relying on the default browser. All other URL callers retain their existing behavior. The existing URL credential/scheme checks and action execution gates remain in place. Debug plan-preview logging was removed because previews can contain request/message text; planner rejection logs use a reason code.

## Verified locally

From android/:

    ./gradlew :app:ktlintFormat :actions-android:ktlintFormat :core-llm:ktlintFormat \
      :app:ktlintCheck :actions-android:ktlintCheck :core-llm:ktlintCheck \
      :app:detekt :actions-android:detekt :core-llm:detekt \
      :app:testDebugUnitTest :actions-android:testDebugUnitTest :core-llm:testDebugUnitTest \
      --continue --no-daemon --max-workers=2

Result: BUILD SUCCESSFUL in 7m 16s, 248 actionable tasks (25 executed, 223 up-to-date).

JUnit XML results:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| app | 140 | 0 | 0 | 0 |
| actions-android | 71 | 0 | 0 | 0 |
| core-llm | 271 | 0 | 0 | 1 |

Total: 481 passed, one pre-existing skipped pre-Android-Q foreground-service test. Nine new fallback regressions all passed. Tests cover unchanged installed plans and no planner/network callback during preflight, automatic replan and notice, second missing plan, missing Chrome, label/package/alias/ambiguity resolution, encoded market search with web fallback, approval/cancel snapshot identity, the secondary store choice running no task, and approval-off direct dispatch. SystemActions tests additionally verify explicit Chrome intent targeting and refusal of an unsupported browser.

Repository checks:

- bash scripts/check-branding.sh: PASSED, 412 Kotlin files and 412 provenance rows.
- git diff --check: PASSED.
- Full Android lint was not run: system memory was 97% used with 0.43 GiB available while concurrent local gates were running. No lint/test rules or baselines were weakened. Narrow ReturnCount annotations document explicit fail-closed exits in the two new preflight methods.

## Not verified on a phone

No phone installation, API key access, real model request, or device automation was performed. Remaining manual checks: Android 11 launcher visibility on the test device; a real Flipkart request with Flipkart absent and Chrome installed; real model compliance with the Chrome constraint; Chrome opening when another browser is the default; readable notice/secondary-button layout; store-app absence/web fallback; approval-off foreground execution; and cancellation. Multi-app notices are supported, with the single secondary button searching for the first missing app.

TaskActivity is a collision hotspot with the sibling run-status changes. Its diff is limited to pre-approval preparation, preview/approval presentation, and request-safe logs. Publication and PR review remain with the lead; this branch is local-only.
