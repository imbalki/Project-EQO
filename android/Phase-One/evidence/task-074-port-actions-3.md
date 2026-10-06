# TASK-074 — action executor port, batch 3 (Refs #20)

Status: implementation checkpoint, NOT verified complete. No phone, merge, or PR.
Branch agent/android/74-port-actions-3 starts from origin/main 821f752, per lead's superseding comment.
Donor HEAD checked: 6ff5a061755b597b0558fed1f565587837ed4d51.
Toolchain pins: JDK 21, Gradle 9.7.0, AGP 9.3.1, Kotlin 2.4.0.
Live Java: Temurin 21.0.12.1+1-LTS. Kotlin/Gradle build versions have not yet been independently executed in this checkpoint.

## Port map and scope

All executor code is in android/actions-android/src/main/java/ai/eqo/actions/impl.

| Donor | Destination | Registered actions |
|---|---|---|
| CalendarActions.kt 51–579 | CalendarActions.kt, AlarmTimeParser.kt, ProductivityDependencies.kt | CREATE_CALENDAR_EVENT, LIST_CALENDAR_TODAY, LIST_CALENDAR_WEEK, SET_ALARM, SET_TIMER, SET_REMINDER, ADD_NOTE, READ_NOTES, CREATE_TASK, READ_AND_REMEMBER_SCREEN, UPDATE_PREFERENCE, RECALL_MEMORY, QUERY_KNOWLEDGE_GRAPH |
| RoutineActions.kt 58–72 | CalendarActions.kt, ProductivityDependencies.kt | GET_MORNING_BRIEFING |
| InformationActions.kt 30–312 | InformationActions.kt, InformationHttp.kt | WEB_SEARCH, GET_NEWS, GET_WEATHER, SUMMARIZE_URL, TRANSLATE, DEFINE_WORD, FACT_CHECK, CALCULATE, CONVERT_UNITS, CURRENCY_CONVERT, CHECK_STOCK |
| SystemActions.kt 997–1048 and schema AGENT vocabulary | ConversationActions.kt | CHAT, ASK_USER |

27 new names in the reused registry; original 30 remain (57 total on this base).
All names already exist in ActionSchema; no competing schema or dispatcher was created.
RECALL_MEMORY/QUERY_KNOWLEDGE_GRAPH are physically in CalendarActions, not InformationActions, upstream.
GET_MORNING_BRIEFING is physically in RoutineActions, not CalendarActions, upstream.
CHAT has no distinct donor executor: donor display-info aliases carry the conversational message. EQO uses the schema response key without claiming Toast display.
Registry retains its coroutine dispatch permit and existing unknown-name-only telemetry.
Donor aliases are mapped before existing schema validation (note name/text/body, note/recall topic, screen query, reminder title/time, CHAT message/text/content, ASK_USER message).

## Persistence dependencies — explicit non-success

ProductivityStore is an interface; no Room/Hilt plugin, database construction, filesystem, SharedPreferences note store, or invented production storage was added.
Its typed ProductivityAvailability.NeedsDatabase state maps to ActionResult.Failure with fallback discriminator needs_database_batch_2 and the message 'needs the database (batch 2)'.
Default runtime is unavailable for ADD_NOTE, READ_NOTES, CREATE_TASK, UPDATE_PREFERENCE, RECALL_MEMORY, QUERY_KNOWLEDGE_GRAPH, READ_AND_REMEMBER_SCREEN and GET_MORNING_BRIEFING.
Missing storage returns before reading a screen, extracting information, or saying anything was saved. Batch-2 branch has no matching productivity adapter on this main base; it was inspected but not cherry-picked.
When an adapter is supplied, note key/date formatting, semantic note filtering, newest-first 10-note/5-recall limits and category normalization follow the donor. Query-graph rendering/matching and morning-briefing generation remain interface responsibilities because their upstream engines/database are absent; these are not fabricated locally.
CREATE_TASK's upstream implementation only claimed success without storing anything. That fake success is deliberately removed; an unavailable adapter refuses.
ScreenMemoryExtractor is an optional explicit boundary: it receives bounded, fenced text from EqoAutomation.observe, not raw nodes, tools, or an unconstrained screenshot; absent extractor refuses. Rechecks takeover after suspended extraction before saving.

## Permission matrix and UI gating

| Action | Access |
|---|---|
| CREATE_CALENDAR_EVENT direct supported now/today event | WRITE_CALENDAR requested after schema validation and takeover preflight, rechecked against actual Android grant; write is inside EqoAutomation.runAction |
| CREATE_CALENDAR_EVENT unsupported date/time/details | Gated calendar compose only; no permission request; owner must enter requested details and save |
| GET_WEATHER explicit city | INTERNET/ACCESS_NETWORK_STATE normal manifest permissions; no location permission |
| GET_WEATHER current location | ACCESS_COARSE_LOCATION requested at need after online and takeover preflight; actual grant rechecked; network-provider last known location only |
| SET_ALARM / SET_TIMER | com.android.alarm.permission.SET_ALARM normal manifest permission; no notification/exact-alarm access; gated Clock intents |
| Calendar display/reminder | No provider read permission: donor opens Calendar rather than retrieving events |
| Browser information actions | Normal network permission, narrow HTTPS intent visibility; gated ACTION_VIEW |
| Notes/knowledge/preferences/task/briefing/conversation | No Android permission requested |
| Screen remember | Existing accessibility enabled/secure-screen/password filtering through EqoAutomation.observe |

Permissions and narrow Calendar/Clock/HTTPS queries are declared in the library manifest and merge into app; no extra app-manifest duplicate entries.
No raw startActivity outside existing GatedIntentLauncher, performAction, Runtime.exec/su/termux, shell, or connector path is introduced.

## Faithful donor limitations and safety deviations

- Browsing/search/translation/definition/fact check/unit/currency/stock actions only open donor web URLs. They return UserActionRequired, never invented web data or a completed conversion/fact check. SUMMARIZE_URL is donor page-opening, not extraction or LLM summarization.
- Weather is the sole direct HTTP request, fixed HTTPS wttr.in host/path/query through InformationHttp. 3-second connect/read timeouts, redirects disabled, 4096-byte response cap, no credentials, no URL/body/exception logging. Offline fails without UI/network activity; service failure can open donor weather search, clearly manual. Cancellation propagates. Weather body is bounded/fenced as untrusted data, never interpreted as automation instructions.
- Geocoder was removed to avoid a second hidden network seam. Approximate network-provider coordinates retain donor fallback. No GPS/fine location/background location requested; absent coordinates yield NeedsInput(city), not 'my location' guessed results.
- SUMMARIZE_URL only permits public-looking HTTPS addresses with no credentials, nonstandard port, private numeric literals or local/internal host names. This validates a browser handoff; it does NOT control the external browser's DNS, redirects, private-resolution policy or network. No user-supplied URL is fetched inside EQO.
- Calendar direct donor insertion still uses calendar id 1, now and one hour. EQO cannot parse arbitrary dates/times: these requests hand off to Calendar without silently creating the wrong event or requesting write access. Calendar display/week have donor's same 'time/now' view URI; no events retrieved, no enforced week view promised. Reminder does not parse supplied time; manual handoff says owner must set it.
- Alarm/timer EXTRA_SKIP_UI=false and manual confirmation, rather than false 'set/running' claims after launching an intent. Clock package fallback retained; a takeover refusal never triggers fallback bypass. Alarm parser range hardening rejects 0/13+ am/pm, invalid quarter-to target, and embedded garbage.
- Simple calculator remains a number or one binary +,-,*,/ operation. It rejects compound-expression truncation, non-finite results and division by zero; unsupported expressions use donor gated web-search fallback. No programming-language evaluator or shell.
- ASK_USER returns existing NeedsInput with optional options/paramKey; existing caller owns display/resume, avoiding donor's unbounded await on a second injected AgentLoop. CHAT returns the response; no Toast or 'displayed' claim.
- No logging surface added. Registry's existing generic exception boundary does not echo exception messages, note/preference values, coordinates, search text or keys. Notes/screen/graph/briefing outputs are bounded and fenced as untrusted content.

## Verification checkpoint

New ProductivityInformationTest has explicit 10-second runTest timeouts and registration, missing DB, conversation, browser URL/encoding, offline/permission/error/cancellation, calendar handoff/takeover, clock no-handler/invalid input, parser/calculator, unsafe URL cases.
Existing registry count assertion updated to 57.
Test success, clock happy path, direct provider insertion, store-backed note/screen happy path and actual HTTP transport tests are NOT yet verified at this checkpoint.

Initial compile invocation reached core-security/core-llm dependencies; Kotlin daemon repeatedly refused connections and used fallback compilation. It has not yet returned a completed actions-android compile result. No daemon for other worktrees was stopped.
Touched-module ktlintFormat invoked ONCE. Exact full gate queued from android:
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
Then scripts/check-branding.sh and scripts/check.sh. Results will be appended only after completion.

NOT verified on a device: calendar id/provider correctness, actual calendar save, Calendar/Clock/OEM intent handling or visual confirmation, runtime permission dialog/revocation/lifecycle, location provider accuracy or sharing coordinates with weather provider, actual weather HTTP/TLS/offline transitions, browser behavior, secure-window enforcement, memory engine integration or owner input presentation. No phone was used.


## Cap checkpoint — 2026-10-05, verification incomplete

Static source verification counted exactly 27 distinct new action names and checked each against ActionSchema, plus Origin headers and absence of Runtime.exec/ProcessBuilder/performAction/log calls in the six new implementation files.
The sole ktlintFormat invocation ran 16m12s and returned exit 1 after applying formatting: two non-autocorrectable max-line-length reports (InformationActions.kt and ProductivityInformationTest.kt). Formatting changes are preserved; ktlintCheck is not yet verified. A local TimerDurationParser additionally validates complete input, positive duration and Int overflow before any Clock dispatch.
Initial dependency compile and the exact full gate repeatedly reported Kotlin daemon connection failure and fallback compilation. Neither has reached a completed batch-3 compile/test result at this checkpoint. Full gate is still progressing through dependencies; no APK/test/lint/detekt success is claimed.
Standalone scripts/check-branding.sh and scripts/check.sh each exceeded 90-second waits under machine load after starting scans; no PASS claim. The full-gate runner also queues both scripts after Gradle.
Two checkpoints were committed as 147663c and d20da64. Push subsequently reported 'Everything up-to-date' and installed origin tracking at d20da643899b55ac26741f0dacad8adf8ebc6989, but separate ls-remote attempts timed out; server HEAD read-back is not yet verified.
Shared hotspots: registry factory/alias integration, library manifest, provenance map. Batch-2 integration will need a careful manual combination of family arguments/registration, not a replacement.

Remaining before completion: actual Kotlin compile and JVM/Robolectric test execution; clock-intent/provider/store-backed/screen happy-path coverage; ktlintCheck and non-autocorrectable style findings; detekt findings/baseline review; lint and both APK assemblies; completed branding/check scans; read-back of remote final HEAD. No phone, PR or merge.

## Rebase onto main and gate results (cloud/74-port-actions-3)

Rebased onto main f9c5cbe (batches 1 and 2 present): registry now exposes 83 enabled names (56 + 27), asserted in AndroidActionRegistryTest.
Registry wiring: AndroidActionRegistry.create/createWithStore take a single RegistryOptions (screen analyzer, productivity store, screen-memory extractor; internal test seams for call verifier, HTTP and memory store) so no detekt LongParameterList entry is needed.
Executor split to satisfy detekt without baselines or suppressions: CalendarActions (calendar/reminder, with a private ClockActions for alarm/timer), ProductivityMemoryActions (notes/tasks/preferences/memory/graph/briefing, with a private ScreenMemoryActions), InformationActions, ConversationActions; shared RegisteredExecutor wrapper in ProductivityDependencies.kt.
No detekt baseline was added or regenerated; the only baseline edit removes the stale createWithStore LongParameterList entry.
Removed unreachable 'timer must be positive' branch (TimerDurationParser already rejects non-positive input).
Verified on the JVM: :actions-android testDebugUnitTest (45 tests, 0 failures), ktlintCheck, detekt, lintDebug, :app:compileDebugKotlin; scripts/check-branding.sh and scripts/check.sh pass.
Still NOT verified on a device: everything listed under the device checkpoint above.
