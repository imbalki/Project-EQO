# TASK-006 Evidence — OpenRouter BYOK, secret storage, redaction, error handling

Branch: `agent/android/11-byok-security` (re-based onto `origin/main` `9120e1d`;
HEAD `1d9161d` at gate time, plus this evidence commit)
Area: android (`:app`, `:core-llm`, `:core-security`) · Gate 8 (DEV-10, DEV-13)
Date: 2026-10-02 (run 46 continuation); security-fix follow-up 2026-10-03 (run 69);
re-base onto TASK-005 and TASK-014 2026-10-03 (run 73) - see the re-base section

## Deliverables in this branch

Commits, `git log --oneline origin/main..HEAD` (verbatim, newest first, 21 commits
at gate time, all Conventional Commits ending `Refs #11`):

```
1d9161d style(android): re-sort import blocks the ai.eqo rename reordered (Refs #11)
ac0ecad fix(android): declare explicit backup exclusion rules at targetSdk 36 (Refs #11)
259caef docs(android): record TASK-006 BYOK/redaction files in the provenance map (Refs #11)
2a06c9d docs(android): refresh TASK-006 evidence from real commands and record the security pass (Refs #11)
d79bfa1 test(android): OpenRouterProvider registers its key for the request lifetime only (Refs #11)
a14d101 fix(android): redact LLMConfig.toString and withhold on any redactor fault (Refs #11)
8e16cad fix(android): require https for connection-test endpoints outside loopback (Refs #11)
22abc41 fix(android): disable app backups of secret-bearing files with a manifest guard test (Refs #11)
42ba8b9 fix(android): send the Gemini key as a header and never log raw exception messages (Refs #11)
ccc50fe docs(android): disclose zero production redactor callers and full unchanged Log surface in TASK-006 evidence (Refs #11)
e95f0c5 test(android): rename mislabelled LogRedactor companion test to match its body (Refs #11)
86170d8 docs(android): TASK-006 BYOK security evidence from real gate output (Refs #11)
0169824 fix(android): satisfy ktlint signature wrapping and final-newline rules (Refs #11)
d3111de test(android): first OpenRouter key use blocked until cost disclosure acknowledged (Refs #11)
81dadea feat(android): register stored provider keys with redactor at load and save (Refs #11)
6063641 feat(android): route app logs and crash reports through fail-closed LogRedactor (Refs #11)
a9df76e chore(android): trim trailing blank line in ConnectionTestRunner (Refs #11)
7b8fb6d fix(android): resolve detekt/ktlint findings in BYOK test runner and redactor (Refs #11)
9b04537 test(android): OpenRouter connection test against mock OpenAI-compatible server for success, 401, 429, offline, timeout (Refs #11)
643823f feat(android): fail-closed LogRedactor pipeline with 1000-secret fuzz acceptance test (Refs #11)
c5a37b8 feat(android): OpenRouter BYOK cost-disclosure gate and encrypted key-storage acceptance tests (Refs #11)
```

(18 of those are the pre-rebase commits re-played onto `9120e1d` - the old ->
new SHA map is in the re-base section - plus `259caef` (provenance rows),
`ac0ecad` (backup-exclusion rules required by lint) and `1d9161d` (import
ordering). The present evidence commit is the 22nd.)

`git diff origin/main HEAD --stat` (verbatim at `1d9161d`, before this evidence
commit):

```
.gitignore                                         |   3 +
 .../Phase-One/evidence/task-005-provenance-map.md  |  35 ++-
 .../Phase-One/evidence/task-006-byok-security.md   | 244 +++++++++++++++++++++
 android/app/src/main/AndroidManifest.xml           |   3 +
 android/app/src/main/res/xml/backup_rules.xml      |  10 +
 .../app/src/main/res/xml/data_extraction_rules.xml |  15 ++
 .../test/kotlin/ai/eqo/AllowBackupManifestTest.kt  |  78 +++++++
 android/core-llm/build.gradle.kts                  |   3 +
 .../java/ai/eqo/core/llm/ConnectionTestRunner.kt   | 216 ++++++++++++++++++
 .../src/main/java/ai/eqo/core/llm/ModelFetcher.kt  |  16 +-
 .../eqo/core/llm/providers/OpenRouterProvider.kt   |  10 +
 .../java/ai/eqo/core/llm/security/LogRedactor.kt   | 114 ++++++++++
 .../eqo/core/llm/security/LogRedactorCrashHook.kt  |  49 +++++
 .../java/ai/eqo/core/llm/security/RedactingLog.kt  | 111 ++++++++++
 .../java/ai/eqo/core/util/NetworkErrorFormatter.kt |  13 +-
 .../src/main/java/ai/eqo/data/models/LLMConfig.kt  |   7 +-
 .../ai/eqo/data/repository/SettingsRepository.kt   |  30 ++-
 .../ConnectionTestRunnerEndpointSecurityTest.kt    | 151 +++++++++++++
 .../ai/eqo/core/llm/ModelFetcherSecurityTest.kt    | 187 ++++++++++++++++
 .../llm/providers/OpenRouterConnectionTestTest.kt  | 193 ++++++++++++++++
 .../providers/OpenRouterProviderRedactionTest.kt   | 161 ++++++++++++++
 .../core/llm/security/LogRedactorCrashHookTest.kt  |  75 +++++++
 .../ai/eqo/core/llm/security/LogRedactorTest.kt    | 167 ++++++++++++++
 .../ai/eqo/core/llm/security/RedactingLogTest.kt   |  81 +++++++
 .../core/util/NetworkErrorFormatterSecurityTest.kt |  77 +++++++
 .../ai/eqo/data/models/LLMConfigRedactionTest.kt   |  34 +++
 .../SettingsRepositoryProviderCredentialsTest.kt   |  25 +++
 .../ai/eqo/core/security/ProviderCostDisclosure.kt |  99 +++++++++
 .../eqo/core/security/OpenRouterKeyStorageTest.kt  | 165 ++++++++++++++
 .../security/ProviderCostDisclosureGateTest.kt     |  89 ++++++++
 30 files changed, 2445 insertions(+), 16 deletions(-)
```

## Gates (real command output, exit codes)

Post-re-base full-gate run on the delivered tree (run 73, single invocation):

```
$ ./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug ktlintCheck detekt \
    --console=plain --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.daemon.jvmargs=-Xmx1024m
BUILD SUCCESSFUL in 2m 7s
459 actionable tasks: 42 executed, 417 up-to-date
GRADLE_EXIT=0
```

Two earlier full invocations on the re-based tree failed and their findings are
quoted in the re-base section (run 1: `:app:lintDebug FAILED` on lint
[DataExtractionRules]; run 3: `:core-llm:ktlintTestSourceSetCheck FAILED` on
import ordering). Both were fixed (commits `ac0ecad`, `1d9161d`), and the green
run above re-executed the `lintDebug`, `ktlintCheck` and `detekt` tasks for the
changed modules plus `:app:lintVitalRelease`; the five `testDebugUnitTest`
tasks report UP-TO-DATE in the green run because the follow-up fixes compile to
identical bytecode (import reorderings) or touch only app resources/tests, and
Gradle's up-to-date verdict is what keeps the per-module test-results XMLs
below current for this tree.

    $ bash scripts/check-branding.sh
    ... kt files: 183; provenance rows: 183
    BRANDING GATE PASSED / BRANDING_EXIT=0

    $ bash scripts/check.sh
    ... OK / CHECK_EXIT=0   (secret scan + shell syntax + branding gate)

    $ grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml
    -> no output, exit 1 (no matches; gate satisfied)

    $ git diff origin/main..HEAD | grep -nE "(sk-or-v1-[0-9a-f]{24,}|AIza[0-9A-Za-z_-]{20,}|Bearer [A-Za-z0-9]{40,})"
    -> no output, exit 1 (no matches; gate satisfied)

    $ git grep -InE '(sk-or-v1-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{30,})' -- . ':!scripts/check.sh'
    -> no output, exit 1 (no matches; gate satisfied)

### Unit tests — counts from test-results XMLs (parsed by script)

```
app:           files= 3 tests=  8 failures=0 errors=0 skipped=0
core-agent:    files= 5 tests= 24 failures=0 errors=0 skipped=0
core-llm:      files=36 tests=261 failures=0 errors=0 skipped=1
core-security: files= 5 tests= 47 failures=0 errors=0 skipped=1
platform-a11y: files= 1 tests=  3 failures=0 errors=0 skipped=0
TOTAL:          files=50 tests=343 failures=0 errors=0 skipped=2
```

(302 tests on main at `9120e1d` incl. 2 skipped + 40 TASK-006 tests across 13
classes (incl. the +1 `SettingsRepositoryProviderCredentialsTest` wiring case)
+ 1 additive backup-rules guard added by this re-base = 343.)

Per acceptance class (XML attributes, all failures=0 errors=0):

```
ModelFetcherSecurityTest                  2 tests (F2: header-only Gemini key, safe log line)
NetworkErrorFormatterSecurityTest         2 tests (F2: safe log line, both overloads)
AllowBackupManifestTest                   2 tests (F3: android:allowBackup must be "false";
                                              the two backup-exclusion attributes point at
                                              the exclude-everything rules resources)
ConnectionTestRunnerEndpointSecurityTest  3 tests (F4: https probed / cleartext non-loopback
                                              rejected with typed failure and 0 mock requests /
                                              http loopback allowed)
LLMConfigRedactionTest                    1 test  (F5: fixed "<redacted LLMConfig>")
OpenRouterProviderRedactionTest           2 tests (N9: key registered for the request and
                                              released after - success and failure paths)
LogRedactorTest                           7 tests (incl. N3 non-Exception Throwable -> Withheld,
                                              1000-secret fuzz, fail-closed, re-entry)
RedactingLogTest                          3 tests (Robolectric, real logcat via ShadowLog)
LogRedactorCrashHookTest                  3 tests
SettingsRepositoryProviderCredentialsTest 6 tests (incl. redaction-registration wiring)
OpenRouterConnectionTestTest              6 tests (MockWebServer: success/401/429/offline/timeout)
OpenRouterKeyStorageTest                  3 tests (encrypted envelope at rest)
ProviderCostDisclosureGateTest            6 tests (incl. first-use blocked until acknowledged)
```

### Greps

```
$ grep -rniE "liquid|leap-sdk|ai\.liquid" android/ --include=*.gradle --include=*.kts --include=*.kt --include=*.toml
-> exit 1 (no matches)  [no-Leap gate]

$ git diff origin/main..HEAD | grep -nE "(sk-or-v1-[0-9a-f]{24,}|AIza[0-9A-Za-z_-]{20,}|Bearer [A-Za-z0-9]{40,})"
-> exit 1 (no real-key-shaped secrets in the diff)
```

## Security pass (eqo-security, report reviewed at `34f0970`)

Verdict of the pass: **NO BLOCKING FINDING** in the delivered diff; 5 should-fix
(F1..F5) and 10 notes (N1..N10). AC1..AC4 PASS. **AC5 NOT MET** - the
cost-disclosure gate is advisory state that no code consults, and the disclosure
screen is deferred to TASK-015 (see F1 below). **AC5 must not be checked off.**

Fixed in the follow-up run - every fix has a test that fails before and passes
after (the pre-fix failures were captured from real runs of the new tests):

| Finding | Fix | Test |
|---|---|---|
| F2 | `ModelFetcher` sends the Gemini key as the `x-goog-api-key` header (as `GeminiProvider` does for chat), never as a `?key=` query parameter; the two exception-message log sites (`ModelFetcher`, `NetworkErrorFormatter`) log the exception class name and fixed text only - no raw message, no throwable rendering | `ModelFetcherSecurityTest` "gemini model list request sends the key as a header and never as a query parameter" and "a model fetch failure logs the exception class name and never the raw message"; `NetworkErrorFormatterSecurityTest` "an unhandled throwable logs the class name and fixed text, never the raw message" and "the string overload logs no raw message either" |
| F3 | `android:allowBackup="false"` on the `:app` `<application>` element | `AllowBackupManifestTest` "application manifest declares allowBackup false" |
| F4 | `ConnectionTestRunner.run()` requires https; `http` is accepted only for a loopback host (localhost, 127.0.0.1, ::1); a non-https non-loopback endpoint is rejected with the typed `ConnectionTestState.Failed(error = LLMError.RequestInvalid)` - the state has no free-text field - before any request is built | `ConnectionTestRunnerEndpointSecurityTest` (3 tests). The rejection test routes the client through a MockWebServer HTTP proxy and asserts `server.requestCount == 0` ("the mock saw 0 requests") |
| F5 | `LLMConfig.toString()` renders the fixed `"<redacted LLMConfig>"` (same contract as `ProviderRequestConfig`) | `LLMConfigRedactionTest` "toString renders fixed redacted text and no key value" |
| N3 | `LogRedactor`'s pipeline catches `Throwable` (not only ISE/RuntimeException/Error) | `LogRedactorTest` "a plain Throwable subclass in the pipeline yields withheld text" |
| N9 | (the missing test from the earlier review) the per-request `LogRedactor.register` inside `OpenRouterProvider.complete` is now asserted, including release after the request | `OpenRouterProviderRedactionTest` "the resolved key is registered for the request and released after it", "a failed request releases the key too" |

Collateral test-harness adaptation (not a product change): `OpenRouterConnectionTestTest.endpoint()`
now pins the mock URL host to the literal loopback `127.0.0.1` - the mock's host
reverse-resolves to a machine-specific name on some machines, which the F4 policy
correctly refuses for plain http.

### Open follow-ups for TASK-015 - none of these is done in this branch

- **F1 - NOT done.** Enforce the cost-disclosure gate at the provider/request
  boundary (`OpenRouterProvider.complete`, `ConnectionTestRunner.run` and
  `SettingsRepository.storeSecretsAndStrip` all round-trip a key without asking
  `requirementBeforeFirstUse`) and ship the disclosure screen.
  **Acceptance criterion 5 is NOT MET and must not be checked off.**
- **Application-class wiring of `RedactingLog` and `LogRedactorCrashHook` - NOT
  done.** Both still have zero production callers (see honest limits).
- **Direct `android.util.Log` call sites - NOT done.** 168 at `57c6822`
  (172 raw matches minus `RedactingLog.kt`'s own 4 logcat-sink calls); the
  post-re-base re-measurement is **167** (171 raw) because TASK-014's
  compose-only `ReplyDispatcher` change removed one Log site (see the
  disclosure below); TASK-006's F2 fixes changed what two of those lines log,
  not how many there are.
- **N1 shape-based redaction rules (sk-/ghp-/AIza/hf_/xai-...) - NOT done.**
- **N2 `SecretRegistry` release of rotated/revoked keys - NOT done** (the
  process-lifetime heap copy stays until process death).
- **N4 URL-encoded / JSON-escaped / case-varied renderings of a registered key are
  not masked - NOT done** (exact-substring redaction remains the contract).
- **N5 `RedactingLog.sink` is a mutable process-global - NOT done.**
- **N6 `SecretRegistry.register` silently no-ops for secrets shorter than 4
  characters - NOT done.**
- **N7 a later crash SDK could capture the unredacted throwable regardless of
  `LogRedactorCrashHook`, and the delegated stack trace is the hook's own - NOT
  done.**
- **N10 the six `printStackTrace()` calls in
  `platform-a11y/.../OpenDroidAccessibilityService.kt:313,335,347,359,685,696`
  - NOT done** (outside the in-scope modules).

## Acceptance criteria mapping

1. **Key stored encrypted; test scans log output and finds no key text**
   - `OpenRouterKeyStorageTest` (core-security): the stored OpenRouter key is a versioned `v1.` ciphertext envelope, never plaintext; scan of every loggable store string (record keys/values, IV, recovery state) contains no key text/head/tail.
   - `RedactingLogTest` "real log output after storing a key contains no key text" (core-llm, Robolectric): key registered as the repository does on hydrate, one line emitted through `RedactingLog` (default sink = `android.util.Log`), **actual logcat capture via `ShadowLog.getLogs()`** contains zero occurrences of the key and carries the redacted mask.
   - F2 follow-up: `ModelFetcherSecurityTest` asserts the Gemini key is not a URL query parameter and that the fetch-failure log line carries neither the raw exception message nor the throwable.
2. **Redactor exception -> withheld text, never raw text**
   - `LogRedactorTest` "redactor exception results in withheld text never raw text"; `RedactingLogTest` "redactor fault on the log path emits withheld text never raw"; `LogRedactorCrashHookTest` "render withholds text when the pipeline faults" - all assert the observable string equals the fixed withheld marker and never contains the key. N3 follow-up: the same holds for a throwing non-`Exception` `Throwable` subclass ("a plain Throwable subclass in the pipeline yields withheld text").
3. **Fuzz: 1000 synthetic secrets, zero leaks**
   - `LogRedactorTest` "1000 synthetic secrets through the redactor produce zero leaks": 1000 distinct synthetic key shapes (sk-or-v1/cred/jur/hf/endpoint prefixes), each rendered inside a noisy line, no head/tail fragment leaks, and none of the fuzz secrets remain registered afterwards. Companion case "a registered secret is masked next to an unregistered lookalike across 500 variants".
4. **Connection test against a mock OpenAI-compatible server (success, 401, 429, offline)**
   - `OpenRouterConnectionTestTest` (MockWebServer3, in-process): 200->Connected with latency, 401->`AuthInvalid`, 429->rate-limit category, DNS-failure->`Network`, timeout->`Network`; Authorization header is asserted `Bearer <candidate>` at the mock, and the candidate key never appears in any rendered failure. F4 follow-up: `ConnectionTestRunnerEndpointSecurityTest` pins the transport policy (https, or http only on loopback) on top of that harness.
5. **Cost disclosure screen shown before the key is first used - NOT MET**
   - Gate logic and copy live in `ProviderCostDisclosure`/:core-security. `ProviderCostDisclosureGateTest` "first key use is blocked until the disclosure is acknowledged": the gate returns `FirstUseRequirement.ShowDisclosure` for the first use attempt (attempt is blocked and acknowledges nothing), and only an explicit `acknowledge` clears it to `Cleared`.
   - **This acceptance criterion is NOT met and is deliberately not checked off (F1):** the gate is consulted by no production call path, and the disclosure SCREEN does not exist in the skeleton (`MainActivity` is a bare `Activity`). Enforcement at the provider/request boundary and the screen are recorded as TASK-015 work.

## Production wiring delivered this run

- `SettingsRepository` (`:core-llm`): `mergeSecretsForRead` and `storeSecretsAndStrip` now call `registerSecretsForRedaction` - every key hydrated from storage or being saved is registered with `LogRedactor` (idempotent, process-lifetime). Registration only takes effect on text that is actually rendered through the redactor; no production line is rendered through it yet (see honest limits), so no stored key is scrubbed from production log output today.
- `OpenRouterProvider.complete()` (`:core-llm`): the resolved key is `LogRedactor.register`-ed for the lifetime of each request (via `use`), alongside the existing `knownSecrets` typed-error scrubbing. Now covered by `OpenRouterProviderRedactionTest` (N9).
- `RedactingLog` (`:core-llm`, new): the single log fan-out - every severity routes the message/throwable rendering through `LogRedactor` before the default `android.util.Log` sink (sink is swappable for tests). **Zero production callers in this skeleton**: `grep -rln RedactingLog` over non-test main sources matches only `RedactingLog.kt` itself, so no production log line passes through the redactor today. The fan-out is entirely driven from the new redaction layer until TASK-015 wires the app's call sites (see honest limits).
- `LogRedactorCrashHook` (`:core-llm`, new): wraps the process uncaught-exception path; the delegated handler receives a `RedactedCrashException` whose message/stack is the redacted report (fault -> withheld). `install()` is tested but has **zero production callers** - the skeleton has no Application class / process-start hook yet (`grep -rnE 'class .*: Application'` -> 0 matches), so no production crash path is redacted today; installation is wired when the app gains one (same deferred lane as TASK-015).
- Security-pass follow-up (this run): `ModelFetcher`/`NetworkErrorFormatter` log sites no longer interpolate exception messages, the Gemini model-list key travels in the `x-goog-api-key` header, `LLMConfig.toString()` cannot render a key, and the app declares `android:allowBackup="false"`.

## What is NOT verified (honest limits)

- No device/emulator run: the connection test exercises an **in-process MockWebServer only**; nothing was run against a real OpenRouter endpoint or a real device (headless session, no emulator).
- No Compose screen: the disclosure SCREEN does not exist in the skeleton (see AC5) - deferred to TASK-015.
- Android Keystore hardware path: the encrypted-storage tests use the audited upstream envelope with an **in-memory `TestCipher`**; real Android Keystore provisioning/attestation is not exercised (unit-test-only scope).
- `core-agent`'s upstream `CrashLogRedactor` (heuristic regex redactor for the share/history sink, used by `ActionSequenceExecutor`/`ExecutionHistoryPrivacy`) is **untouched** - it is a different sink outside `:core-llm`/`:core-security`; consolidation with this fail-closed pipeline is already tracked upstream as issue #40.
- **No production log line passes through the redactor today.** `RedactingLog` and `LogRedactorCrashHook.install()` are **not called by any production code path**: `grep -rln RedactingLog` over non-test main sources matches only `RedactingLog.kt` itself (same for `LogRedactorCrashHook` -> only its own file), and no `Application` class exists (`grep -rnE 'class .*: Application'` -> exit 1). Both are wired in TASK-015 (Application class / process-start hook); the fail-closed redaction pipeline is currently exercised by tests only.
- **167 direct `android.util.Log` call sites remain unchanged across 19 main files** (re-measured post-re-base at `1d9161d`, excluding `RedactingLog.kt`'s own 4 logcat-sink calls), 104 of them inside the in-scope modules - `:core-llm` 84 (`ActionDispatcher` 27, `LiteRTLMProvider` 25, `DeviceStateProvider` 14, `HybridOnDeviceProvider` 8, `ModelDownloadWorker` 6, `LLMConfig`/`NetworkErrorFormatter`/`ModelFetcher`/`LLMProviderFactory` 1 each) and `:core-security` 20 (`KeystoreSecretStorage` 15, `SocialCredentialStore` 5); the remaining 63 are `:core-agent` 36 and `:platform-a11y` 27 (incl. `AutoReplyEngine` 14, `ContactResolver` 13, `ReplyDispatcher` 9, `VisionEngine` 8, `AgentLoop` 7, `WhatsAppAutomator` 6, `TelegramAutomator` 3, `SmsAutomator` 3). The F2 fixes changed what two of those lines log (exception class name + fixed text instead of the raw exception message) but not the call-site count; TASK-014's compose-only change (merged into this branch by the re-base) removed one `ReplyDispatcher` site (10 -> 9, 168 -> 167); their payloads are scrubbed only if they happen to match a registered secret, and none are routed through `RedactingLog`. Measured with:

```
$ grep -rEn 'Log\.(d|i|w|e|v|wtf)\(' --include='*.kt' app/src/main core-agent/src/main core-llm/src/main core-security/src/main platform-a11y/src/main | sed 's|^\([^:]*\):.*|\1|' | sort | uniq -c | sort -rn
     27 core-llm/src/main/java/ai/eqo/actions/ActionDispatcher.kt
     25 core-llm/src/main/java/ai/eqo/core/llm/providers/LiteRTLMProvider.kt
     15 core-security/src/main/java/ai/eqo/core/security/KeystoreSecretStorage.kt
     14 core-llm/src/main/java/ai/eqo/core/agent/DeviceStateProvider.kt
     14 core-agent/src/main/java/ai/eqo/core/agent/AutoReplyEngine.kt
     13 core-agent/src/main/java/ai/eqo/core/agent/ContactResolver.kt
      9 core-agent/src/main/java/ai/eqo/core/agent/ReplyDispatcher.kt
      8 platform-a11y/src/main/java/ai/eqo/core/agent/VisionEngine.kt
      8 core-llm/src/main/java/ai/eqo/core/llm/providers/HybridOnDeviceProvider.kt
      7 platform-a11y/src/main/java/ai/eqo/core/agent/AgentLoop.kt
      6 platform-a11y/src/main/java/ai/eqo/accessibility/WhatsAppAutomator.kt
      6 core-llm/src/main/java/ai/eqo/core/llm/ModelDownloadWorker.kt
      5 core-security/src/main/java/ai/eqo/core/security/SocialCredentialStore.kt
      4 core-llm/src/main/java/ai/eqo/core/llm/security/RedactingLog.kt   (its own logcat-sink calls, excluded)
      3 platform-a11y/src/main/java/ai/eqo/accessibility/TelegramAutomator.kt
      3 platform-a11y/src/main/java/ai/eqo/accessibility/SmsAutomator.kt
      1 core-llm/src/main/java/ai/eqo/data/models/LLMConfig.kt
      1 core-llm/src/main/java/ai/eqo/core/util/NetworkErrorFormatter.kt
      1 core-llm/src/main/java/ai/eqo/core/llm/ModelFetcher.kt
      1 core-llm/src/main/java/ai/eqo/core/llm/LLMProviderFactory.kt
    -> 171 raw matches, minus RedactingLog.kt's 4 = 167 unchanged call sites in 19 files
```

Per-module totals of the same grep (excluding RedactingLog.kt): `:core-llm` 84, `:core-security` 20, `:core-agent` 36, `:platform-a11y` 27 = 167.
- The previous run's untrusted artifacts (`task-006-verification-log.md`, `x-testfile-001.txt`) were removed and do not exist in the worktree (verified via `git status`).

## Re-base onto TASK-005 and TASK-014 (2026-10-03)

TASK-006 (18 commits) passed its independent review and its security pass at
`1bc28f7` on the old main `a567321`. TASK-005 merged as `97fc72b`
(`com.opendroid.ai` -> `ai.eqo`, `OpenDroid*` -> `EQO*`) and TASK-014 as
`9120e1d`. Re-based onto `origin/main` (`9120e1d`), nothing else:

    $ git fetch origin
    pre-rebase head:  1bc28f7  (git rev-parse HEAD)
    origin/main:      9120e1d
    $ git branch backup/011-pre-rebase 1bc28f7      (local only, never pushed)
    $ git rebase --onto origin/main a567321 agent/android/11-byok-security

18 picks, each followed by an after-pick step that moves any file git left under
`com/opendroid/ai/` to its `ai/eqo/` path and rewrites the commit's own new
package references (provenance lines `// Origin:` / `upstream ...` never
touched). Old -> new SHA map of the replayed commits (subjects unchanged; every
SHA cited earlier in this document maps through this table, e.g. the security
pass at `34f0970` is now `ccc50fe`):

    bef8a07 -> c5a37b8  feat(android): OpenRouter BYOK cost-disclosure gate and encrypted key-storage acceptance tests (Refs #11)
    a85c666 -> 643823f  feat(android): fail-closed LogRedactor pipeline with 1000-secret fuzz acceptance test (Refs #11)
    caf8495 -> 9b04537  test(android): OpenRouter connection test against mock OpenAI-compatible server for success, 401, 429, offline, timeout (Refs #11)
    818e822 -> 7b8fb6d  fix(android): resolve detekt/ktlint findings in BYOK test runner and redactor (Refs #11)
    5b98ba5 -> a9df76e  chore(android): trim trailing blank line in ConnectionTestRunner (Refs #11)
    86dfffc -> 6063641  feat(android): route app logs and crash reports through fail-closed LogRedactor (Refs #11)
    287a1a9 -> 81dadea  feat(android): register stored provider keys with redactor at load and save (Refs #11)
    92002ec -> d3111de  test(android): first OpenRouter key use blocked until cost disclosure acknowledged (Refs #11)
    f8927b7 -> 0169824  fix(android): satisfy ktlint signature wrapping and final-newline rules (Refs #11)
    acfca7e -> 86170d8  docs(android): TASK-006 BYOK security evidence from real gate output (Refs #11)
    37bbfb6 -> e95f0c5  test(android): rename mislabelled LogRedactor companion test to match its body (Refs #11)
    34f0970 -> ccc50fe  docs(android): disclose zero production redactor callers and full unchanged Log surface in TASK-006 evidence (Refs #11)
    970b47a -> 42ba8b9  fix(android): send the Gemini key as a header and never log raw exception messages (Refs #11)
    90d19d2 -> 22abc41  fix(android): disable app backups of secret-bearing files with a manifest guard test (Refs #11)
    50f0d26 -> 8e16cad  fix(android): require https for connection-test endpoints outside loopback (Refs #11)
    3c82582 -> a14d101  fix(android): redact LLMConfig.toString and withhold on any redactor fault (Refs #11)
    57c6822 -> d79bfa1  test(android): OpenRouterProvider registers its key for the request lifetime only (Refs #11)
    1bc28f7 -> 2a06c9d  docs(android): refresh TASK-006 evidence from real commands and record the security pass (Refs #11)

Conflicts resolved during the replay (all mechanical, both sides read):

- 7 replay commits (`c5a37b8`, `643823f`, `9b04537`, `6063641`, `42ba8b9`,
  `8e16cad`, `a14d101`, `d79bfa1`): git's directory-rename detection reported
  `CONFLICT (file location)` for the 17 files TASK-006 adds under
  `com/opendroid/ai/**`, suggesting the `ai/eqo/**` paths. Every suggestion was
  accepted, package declarations and imports rewritten `com.opendroid.ai` ->
  `ai.eqo`; no other content changed. `643823f`'s two files were placed at the
  old paths without a conflict notice and were moved by the same step.
- `81dadea` ("register stored provider keys with redactor at load and save"):
  content conflicts in `OpenRouterProvider.kt`, `SettingsRepository.kt` and
  `SettingsRepositoryProviderCredentialsTest.kt`, the import block only -
  TASK-005 re-sorted the renamed import block to the top of each file while this
  commit adds `LogRedactor` into the old-name block. Resolved by keeping
  TASK-005's renamed block and inserting the one new import
  `import ai.eqo.core.llm.security.LogRedactor` in sorted position; the body
  hunks auto-merged and are untouched (TASK-006's intent kept).
- `.gitignore`, `core-llm/build.gradle.kts`, `ModelFetcher.kt`,
  `NetworkErrorFormatter.kt`, `LLMConfig.kt`, `LogRedactor.kt` and
  `AndroidManifest.xml` auto-merged (git followed the moved files);
  TASK-006's `android:allowBackup="false"` coexists with TASK-005's manifest
  (comments, `eqo://` deep links, `ai.eqo.app.fileprovider`).

Mechanical proof that the re-base is just the rename (step 4):

    $ git diff a567321 1bc28f7 -- '*.kt' '*.xml' '*.kts' > pre-code.diff
    $ git diff origin/main HEAD -- '*.kt' '*.xml' '*.kts' > post-code.diff
    $ sed -e 's|com\.opendroid\.ai|ai.eqo|g' -e 's|com/opendroid/ai|ai/eqo|g' \
          -e 's|OpenDroid|EQO|g' -e 's|OPENDROID|EQO|g' -e 's|opendroid|eqo|g'
    $ grep -E '^[+-]' <side>.norm | grep -vE '^(\+\+\+|---)' | sort > <side>.npm
    $ comm -3 pre-code.npm post-code.npm

At the pure re-base tree (`259caef`): pre-code **2127** changed lines, post-code
**2127** changed lines, `comm -3` residual = **0 lines** (identical multisets).
The all-file comparison is also 2374 = 2374 with residual 0. Per-file (diff
blocks compared after dropping `index <sha>` and `@@` offset lines): **24 of 27
files byte-identical after normalisation**. The 3 exceptions are the import
files of `81dadea`; their +/- lines sit inside the 0-residual comparison and
only the hunk SHAPE differs (TASK-005 re-sorted the import block, so the same
insertion lands in one hunk near the file head with the `// Origin:` header as
context instead of mid-block import context). The full-diff residual
(`diff pre-all.norm post-all.norm`, 132 lines) is entirely non-content: 50
`index <sha>..<sha>` blob-hash lines (25 files), 8 `@@` hunk-offset lines, 30
`diff --git` block separators, 30 diff position markers, 14 context-shape lines
(the hunk shapes just described). `// Origin:` headers in the modified MOVE
files keep the ORIGINAL upstream paths (`yashab-cyber/opendroid @ 6ff5a06...`,
`com/opendroid/ai/...`) and were deliberately NOT rewritten (provenance truth).

At the delivered tree (`1d9161d`): the comparison differs from the pure re-base
result only by the deliberate delta below - post-code 2179 lines and
`comm -3` = **52 lines, all post-only additions** (0 pre-only lines):
`AndroidManifest.xml` +2, `res/xml/backup_rules.xml` +10,
`res/xml/data_extraction_rules.xml` +15, `AllowBackupManifestTest.kt` +25
(equals the 55 insertions / 3 deletions of commits `ac0ecad` + `1d9161d`; the
three import reorderings net to zero in the sorted comparison).

Deliberate delta beyond the mechanical rename (gate-forced, no behavior change):
the first post-re-base gate run failed on TASK-006's own F3 line:

    > Task :app:lintDebug FAILED
    ...AndroidManifest.xml:4: Error: The attribute android:allowBackup is
    deprecated from Android 12 and higher and may be removed in future versions.
    Consider adding the attribute android:dataExtractionRules ... [DataExtractionRules]
    BUILD FAILED in 7m 37s / GRADLE_EXIT=1

and the next run, after adding `android:dataExtractionRules`, failed with
"only applies for Android 12 and higher; since minSdkVersion is API 30 you
should also set android:fullBackupContent" (BUILD FAILED in 2m 5s). The lead's
F3 spec anticipates exactly this ("android:fullBackupContent=... if lint asks;
for targetSdk 36 also dataExtractionRules excluding everything if lint
requires"), so `ac0ecad` declares both attributes pointing at exclude-everything
rules (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`):
backups stay disabled at every API level, `android:allowBackup="false"` is
kept, and no lint baseline and no `severity=ignore` were added (the same 4
reviewed lint ignores as everywhere else). `AllowBackupManifestTest` keeps its
original assertion and gained one additive guard for the two companion
attributes. A third run then failed `:core-llm:ktlintTestSourceSetCheck` on
import ordering (the in-place package rewrite left `ai.eqo` imports above
`androidx`/`gson` lines in 3 files; ktlint requires lexicographic order) -
fixed by `1d9161d`, pure reordering, no content change.

Step 5 name fixes: all 17 new Kotlin files declare/import the `ai.eqo`
packages and live under `ai/eqo/**` (test assertions unchanged); the manifest
guard targets `app/src/main/AndroidManifest.xml` as before.
Step 6 provenance: the 17 new files are recorded as EQO-NEW rows in
`task-005-provenance-map.md` (`259caef`), exactly like TASK-014's guard rows;
`bash scripts/check-branding.sh` -> `kt files: 183; provenance rows: 183`,
`BRANDING GATE PASSED`, exit 0 - the gate script itself is untouched.
