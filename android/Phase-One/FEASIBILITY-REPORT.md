# EQO Android — Phase One Feasibility Report

**Status:** CONDITIONAL GO for Phase One study/spikes. **NO-GO for release** until the gates in §8 pass.
**Date:** 2026-10-01. **Author:** CTO synthesis of independent role-agent reports (see §11).
**Scope:** feasibility only. No combined APK has been built, installed, or device-tested. Every runtime claim below is source- or CI-derived unless explicitly marked otherwise.

---

## 1. Source provenance (verified commits, read-only inspection)

| Role in EQO | Repository | Commit inspected | License (verified from LICENSE file) |
|---|---|---|---|
| Main product repo | `imbalki/Project-EQO` | `4b2d77f3` | project-owned |
| Base app / agent loop | `yashab-cyber/opendroid` | `6ff5a061` | Apache-2.0 (OpenDroid Contributors) |
| Donor: CDP / ADB pairing / virtual display | `imoonkey/closepaw` | `75dae265` | Apache-2.0 (ClosePaw Authors) + NOTICE file |
| Privileged helper core | `RikkaApps/Shizuku` | `b844bc49` | Apache-2.0 + naming restrictions (§6) |
| Helper client/API | `RikkaApps/Shizuku-API` | `a27f6e41` | MIT (RikkaW) |

"No third-party repository was modified. Clones are local study copies only."

## 2. What each upstream actually gives us

- **OpenDroid (base):** Kotlin + Compose app, agent loop with tool calling, OpenRouter/remote-LLM support, Accessibility automation, MediaProjection + AccessibilityNodeInfo fallback, Room memory, minSdk 26 (so Android 12+ fits), JDK 21 / AGP 9 toolchain, Shizuku client already a dependency (`dev.rikka.shizuku:api/provider:13.1.5`).
- **ClosePaw (donor):** Chrome CDP control via the `chrome_devtools_remote` abstract socket plus `/data/local/tmp/chrome-command-line` and the `enable-command-line-on-non-rooted-devices` flag (documented cold-restart requirement), wireless **ADB pairing protocol implementation** (pure-Kotlin SPAKE2-25519, BouncyCastle, bundled Conscrypt TLS exporter), virtual display via Shizuku shell-uid processes, hidden-API bypass, minSdk 31, JDK 17.
- **Shizuku + Shizuku-API:** the privileged manager/server/native starter that EQO must absorb into its own branded setup. Apache-2.0 reuse is allowed, but the upstream explicitly forbids reusing its name, applicationId, manager permissions, and icons — which **aligns with** the EQO-only-branding requirement (§6).

## 3. Verified build / CI state (exact inspected commits)

- **OpenDroid @ `6ff5a061`:** unit tests + debug APK, unsigned release (R8), instrumented tests API 26 and API 36 all **succeeded**; **Android Lint FAILED**. Lint job logs returned HTTP 401 and its artifact store was empty, so the exact lint findings are **unknown** — do not assume they are minor. The repo's `lint-baseline.xml` carries 16 issue entries and `warningsAsErrors=true` is enabled (`app/build.gradle` L134-171).
- **ClosePaw @ `75dae265`:** Release workflow **succeeded**; its privacy-policy Pages deploy jobs failed (HTTP 410 Gone) — unrelated to the app build.
- **Project-EQO (main):** scaffold only (`android/README.md`); its CI has the Android job **commented out**, so no Android build is proven there.
- **Local machine:** no JDK installed, no Android SDK configured, `adb` present but **zero attached devices**. No local Gradle run was possible; none is claimed.

## 4. Critical integration gap: the single-APK branded helper

The agreed requirement is **one EQO APK with the helper included and EQO-branded — no separate upstream-branded app install**. This is the largest open engineering item. Verified rename surface (all must change together):

| Location | Hardcoded identity |
|---|---|
| `shizuku/server/.../ServerConstants.java` L8 | `MANAGER_APPLICATION_ID = "moe.shizuku.privileged.api"` |
| `shizuku/manager/build.gradle` L15 | `applicationId "moe.shizuku.privileged.api"` |
| `shizuku/manager/src/main/jni/starter.cpp` L34 | `#define PACKAGE_NAME "moe.shizuku.privileged.api"` |
| `shizuku-api/rish/.../ShizukuShellLoader.java` | `setPackage("moe.shizuku.privileged.api")` + APK-sourceDir classloader |
| `shizuku/starter/.../ServiceStarter.java` L24/L95 | binder extra + package constants |

Coordinated changes across server, native code, client, provider authority and permissions are therefore mandatory, plus an NDK build of the starter and a decision on the manager↔server signature/permission trust model. **None of this has been compiled or device-proven yet.** An unchanged stock Shizuku install is explicitly out of scope per the product brief.

## 5. Other blockers and risks

1. **Auth planes are separate and must stay separate in testing:** wireless-ADB *pairing/auth* vs helper *authorization* vs Accessibility vs CDP consent. One passing does not prove another.
2. **Toolchain divergence:** OpenDroid JDK 21 vs ClosePaw JDK 17; a merged build needs one pinned toolchain.
3. **Hidden API / OEM variability** in virtual display and input injection; needs per-device probing, not a blanket capability promise.
4. **Main-repo architecture conflict:** `AGENTS.md`/`docs/ARCHITECTURE.md` specify offline-default Gemma + hosted Composio integrations. The latest brief specifies OpenRouter bring-your-own-key and free/open-source tooling only. This conflict is **recorded, not silently resolved** — it needs an owner decision (ADR).
5. **ClosePaw bundles a Python Termux bridge asset** (`closepaw_bridge_py`) conflicting with the Phase One "no Node/Python on the phone" constraint; it must be optional/excluded and confirmed in the APK review.
6. **Distribution risk:** sideloaded APK, Android 13 restricted-settings flow, Play Protect scanning, and app-level anti-automation remain real limits. Android owns its own settings and permission names.

## 6. Branding and licensing

- EQO-only branding in all UI we control is compatible with upstream licenses. Shizuku's README **forbids** using its name/appId/permissions/icons — so a rebrand is required by upstream terms anyway, and legal attribution stays in a dedicated Legal/Open-source notices area (Apache-2.0 + MIT notice retention; ClosePaw NOTICE file must be preserved).
- Trademark interpretation of the naming restrictions needs a legal sign-off before public distribution. That is a review item, not an engineering blocker for a private study build.

## 7. Environment readiness

| Item | State |
|---|---|
| JDK (17/21 pin needed) | missing |
| Android SDK / NDK | missing |
| Attached test device (Android 12 + 13) | none |
| Model routing | MiMo 2.6 Pro/Flash via OpenCode Go verified working; saved MiMo primary key returns 401 (credential issue, not a product issue) |

## 8. Phase One exit gates (all must pass before release claims)

1. Reproducible build of the combined EQO APK on pinned toolchain (lint clean or baseline-shrunk with named findings captured).
2. Integrated EQO-branded helper: starts over wireless ADB, authorizes EQO, survives binder death — device-proven.
3. Wireless ADB pairing + authenticated connection + loss/revocation/reboot recovery — device-proven on Android 12 and 13 (plus one OEM skin).
4. Accessibility observe/tap/scroll/text on a test app — device-proven.
5. Chrome + CDP navigate + fill test form after documented debug prep/restart — device-proven.
6. Virtual display: create, launch compatible app, perceive, act, clean up; foreground fallback with consent — device-proven.
7. Pause / Stop / takeover with in-flight action reconciliation — device-proven.
8. Branding scan clean in normal flow; secrets never in prompts/logs; approvals enforced.

## 9. Phase One work plan (ordered spikes → first study APK)

1. **S0 Build parity:** pin JDK/AGP/SDK/NDK; reproduce upstream CI locally; capture real lint findings (not a blind baseline regenerate).
2. **S1 Helper integration spike:** apply the rename/authority change set; NDK-build the starter; prove start + authorization + binder death on device. *Largest risk — run first.*
3. **S2 ADB pairing spike:** ClosePaw pairing code against Android 12/13 wireless debugging; test wrong code, port confusion (pairing port ≠ connection port), revoke, reboot.
4. **S3 CDP spike:** Chrome debug prep, restart, socket bind verification, form fill.
5. **S4 Virtual-display spike:** create/perceive/input/cleanup + foreground-fallback consent.
6. **S5 Orchestrator skeleton:** single Kotlin action loop — permissions check → approval → execute → observe → verify → repeat; Pause/Stop/takeover; idempotency for irreversible actions.
7. **S6 Study APK:** first combined install + guided onboarding walkthrough on real devices; novice usability pass.
8. **Exit:** the eight gates in §8, then and only then plan Phase Two scope.

## 10. Later phases

Deliberately **not planned yet**, per the instruction to review the study APK first. The roadmap will be drafted after S6 acceptance, informed by what the device matrix actually proves.

## 11. Agent team record (real separate workers, model verified per session)

| Role | Worker model / provider | Artifact | Outcome |
|---|---|---|---|
| Architect | mimo-v2.6-pro / opencode-go | `team/architect/report.md` (+ `controller-verification.md` corrections) | conditional feasibility, major adaptation |
| Developer | mimo-v2.6-pro / opencode-go | `team/developer/developer-readiness.md` | 17-task backlog; no-go until lint/compile gates |
| QA | mimo-v2.6-pro / opencode-go | `team/qa/qa-feasibility-report.md` | conditionally feasible; test matrices defined |
| Integration | mimo-v2.6-pro / opencode-go | `team/integration/report.md` | **first run timed out — re-run dispatched** |
| Security/Privacy/Compliance | mimo-v2.6-pro / opencode-go | `team/security/review.md` | feasible with conditions; dependency/consent gates |
| Product/UX | mimo-v2.6-flash / opencode-go | `PRD.md`, `USER-FLOWS.md` | drafted + reviewed |
| Study-APK static review | mimo-v2.6-pro / opencode-go | `team/apk-review/` | **in progress** |

## 12. Honest limits of this report

No APK was built, installed, or exercised. CI outcomes are upstream metadata at the listed commits, not our own test runs. Lint failure details remain unretrieved (401 on logs). The single-APK helper integration is a **hypothesis with a concrete change list**, not a demonstrated capability. Nothing here should be read as a claim that EQO currently works.
